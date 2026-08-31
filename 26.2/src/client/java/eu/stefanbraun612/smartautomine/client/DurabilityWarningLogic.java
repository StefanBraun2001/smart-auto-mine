package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.Map;

/**
 * Always-on watchdog, independent of the mod's own enabled toggle: warns (by sound) when
 * a held or worn item is already below the Min durability/% threshold that would make Auto
 * Mine itself refuse to use it. Two independent toggles:
 * - Tool warning (durabilityWarningEnabled): main hand + offhand, filtered by keyword list.
 * - Armor warning (armorDurabilityWarningEnabled): all 4 armor slots (elytra included, since
 *   it occupies the chest slot), no keyword filter - any equipped item counts.
 * Plays once on equip, then loops (capped at once every 2 seconds) while a held slot is in
 * use (attack or use key) or, for armor, just periodically while still equipped and low.
 */
public class DurabilityWarningLogic {
	private static final int SOUND_COOLDOWN_TICKS = 40; // 20 ticks/sec * 2 sec between plays

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	private static class SlotState {
		int lastHotbarSlot = -1; // only meaningful for MAINHAND - see tickHeldSlot()
		Item lastItem = null;
		int soundCooldownTicks = 0;

		void reset() {
			lastHotbarSlot = -1;
			lastItem = null;
			soundCooldownTicks = 0;
		}
	}

	private static final Map<EquipmentSlot, SlotState> STATES = new EnumMap<>(EquipmentSlot.class);

	static {
		STATES.put(EquipmentSlot.MAINHAND, new SlotState());
		STATES.put(EquipmentSlot.OFFHAND, new SlotState());
		for (EquipmentSlot slot : ARMOR_SLOTS) {
			STATES.put(slot, new SlotState());
		}
	}

	public static void tick(Minecraft client) {
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		Player player = client.player;
		if (player == null || (!config.durabilityWarningEnabled && !config.armorDurabilityWarningEnabled)) {
			resetAll();
			return;
		}

		if (config.durabilityWarningEnabled) {
			boolean inUse = client.options.keyAttack.isDown() || client.options.keyUse.isDown();
			tickHeldSlot(client, player, config, EquipmentSlot.MAINHAND, player.getInventory().getSelectedSlot(), inUse);
			tickHeldSlot(client, player, config, EquipmentSlot.OFFHAND, -1, inUse);
		} else {
			STATES.get(EquipmentSlot.MAINHAND).reset();
			STATES.get(EquipmentSlot.OFFHAND).reset();
		}

		if (config.armorDurabilityWarningEnabled) {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				tickArmorSlot(client, player, config, slot);
			}
		} else {
			for (EquipmentSlot slot : ARMOR_SLOTS) {
				STATES.get(slot).reset();
			}
		}
	}

	// hotbarSlot is only meaningful for MAINHAND (its selected hotbar index) - pass -1 for
	// OFFHAND, which has no such concept and relies on item identity alone.
	private static void tickHeldSlot(Minecraft client, Player player, SmartAutoMineConfig config,
			EquipmentSlot slot, int hotbarSlot, boolean inUse) {
		SlotState state = STATES.get(slot);
		if (state.soundCooldownTicks > 0) {
			state.soundCooldownTicks--;
		}

		ItemStack held = player.getItemBySlot(slot);
		Item item = held.isEmpty() ? null : held.getItem();
		// Slot-index change (MAINHAND only) always counts as a fresh equip, even swapping
		// between two stacks of the identical Item type, since their durability may differ.
		boolean justEquipped = item != state.lastItem || (slot == EquipmentSlot.MAINHAND && hotbarSlot != state.lastHotbarSlot);
		state.lastItem = item;
		state.lastHotbarSlot = hotbarSlot;

		if (!matchesAnyKeyword(held, config) || AutoMineLogic.hasEnoughDurability(held, config)) {
			return;
		}

		if (justEquipped) {
			SoundUtil.play(client, config.durabilityWarningSound);
			soundStartsCooldown(state); // still starts the cooldown so an immediate re-equip can't double-fire
		} else if (state.soundCooldownTicks <= 0 && inUse) {
			SoundUtil.play(client, config.durabilityWarningSound);
			soundStartsCooldown(state);
		}
	}

	// Armor has no interaction key of its own - loops purely on the cooldown timer instead
	// of a key-held check, for as long as a low-durability piece stays equipped.
	private static void tickArmorSlot(Minecraft client, Player player, SmartAutoMineConfig config, EquipmentSlot slot) {
		SlotState state = STATES.get(slot);
		if (state.soundCooldownTicks > 0) {
			state.soundCooldownTicks--;
		}

		ItemStack worn = player.getItemBySlot(slot);
		Item item = worn.isEmpty() ? null : worn.getItem();
		boolean justEquipped = item != state.lastItem;
		state.lastItem = item;

		if (worn.isEmpty() || AutoMineLogic.hasEnoughDurability(worn, config)) {
			return;
		}

		if (justEquipped || state.soundCooldownTicks <= 0) {
			SoundUtil.play(client, config.durabilityWarningSound);
			soundStartsCooldown(state);
		}
	}

	private static void soundStartsCooldown(SlotState state) {
		state.soundCooldownTicks = SOUND_COOLDOWN_TICKS;
	}

	private static boolean matchesAnyKeyword(ItemStack stack, SmartAutoMineConfig config) {
		for (String keyword : config.durabilityWarningKeywords) {
			if (AutoMineLogic.matchesKeyword(stack, keyword)) {
				return true;
			}
		}
		return false;
	}

	private static void resetAll() {
		for (SlotState state : STATES.values()) {
			state.reset();
		}
	}
}
