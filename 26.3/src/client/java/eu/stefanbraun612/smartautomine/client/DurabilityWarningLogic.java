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
 * a held or worn item is close to breaking. Two independent toggles:
 * - Tool warning (durabilityWarningEnabled): main hand + offhand, filtered by keyword list.
 * - Armor warning (armorDurabilityWarningEnabled): all 4 armor slots (elytra included, since
 *   it occupies the chest slot), no keyword filter - any equipped item counts.
 * Both share durabilityWarningMode, which decides the thresholds: the Min durability/% tool
 * guard (critical tier only), or custom critical / low + critical thresholds.
 * Plays once on equip or when an item drops into a worse tier, then loops (low every 6 s,
 * critical every 5 s) while a held slot is in use (attack or use key) or, for armor, just
 * periodically while still equipped and low.
 */
public class DurabilityWarningLogic {
	private static final int LOW_SOUND_COOLDOWN_TICKS = 120; // 20 ticks/sec * 6 sec
	private static final int CRITICAL_SOUND_COOLDOWN_TICKS = 100; // 20 ticks/sec * 5 sec

	private static final EquipmentSlot[] ARMOR_SLOTS = {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
	};

	// Ordered by severity - ordinal comparison detects an item dropping into a worse tier.
	private enum Tier {
		NONE,
		LOW,
		CRITICAL
	}

	private static class SlotState {
		int lastHotbarSlot = -1; // only meaningful for MAINHAND - see tickHeldSlot()
		Item lastItem = null;
		Tier lastTier = Tier.NONE;
		int soundCooldownTicks = 0;

		void reset() {
			lastHotbarSlot = -1;
			lastItem = null;
			lastTier = Tier.NONE;
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
			boolean suppressed = isToolWarningSuppressed(config);
			tickHeldSlot(client, player, config, EquipmentSlot.MAINHAND, player.getInventory().getSelectedSlot(), inUse, suppressed);
			tickHeldSlot(client, player, config, EquipmentSlot.OFFHAND, -1, inUse, suppressed);
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

	// Custom thresholds are usually set above the tool guard, so while Auto Mine itself is
	// running they'd keep firing on a tool it's still safely allowed to use - the guard will
	// stop/rotate it in time anyway. Only when the guard is actually effective, though: with
	// it disabled (0/0) nothing else protects the tool, so the warning stays audible.
	// Armor isn't covered by the tool guard, so this never applies to it.
	private static boolean isToolWarningSuppressed(SmartAutoMineConfig config) {
		return config.durabilityWarningMode != SmartAutoMineConfig.DurabilityWarningMode.TOOL_GUARD
				&& SmartAutoMineClient.isEnabled()
				&& (config.minDurability > 0 || config.minDurabilityPercent > 0);
	}

	// hotbarSlot is only meaningful for MAINHAND (its selected hotbar index) - pass -1 for
	// OFFHAND, which has no such concept and relies on item identity alone.
	private static void tickHeldSlot(Minecraft client, Player player, SmartAutoMineConfig config,
			EquipmentSlot slot, int hotbarSlot, boolean inUse, boolean suppressed) {
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

		Tier tier = matchesAnyKeyword(held, config) ? tierOf(held, config) : Tier.NONE;
		boolean escalated = tier.ordinal() > state.lastTier.ordinal();
		state.lastTier = tier;

		if (tier == Tier.NONE || suppressed) {
			return;
		}

		// Equip/escalation plays regardless of cooldown (but still restarts it, so an
		// immediate re-equip can't double-fire); otherwise loop only while in use.
		if (justEquipped || escalated || (state.soundCooldownTicks <= 0 && inUse)) {
			playWarning(client, config, state, tier);
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

		Tier tier = worn.isEmpty() ? Tier.NONE : tierOf(worn, config);
		boolean escalated = tier.ordinal() > state.lastTier.ordinal();
		state.lastTier = tier;

		if (tier == Tier.NONE) {
			return;
		}

		if (justEquipped || escalated || state.soundCooldownTicks <= 0) {
			playWarning(client, config, state, tier);
		}
	}

	private static Tier tierOf(ItemStack stack, SmartAutoMineConfig config) {
		switch (config.durabilityWarningMode) {
			case TOOL_GUARD:
				return AutoMineLogic.isDurabilityAtOrBelow(stack, config.minDurability, config.minDurabilityPercent)
						? Tier.CRITICAL : Tier.NONE;
			case CRITICAL_ONLY:
				return AutoMineLogic.isDurabilityAtOrBelow(stack, config.criticalWarningDurability, config.criticalWarningDurabilityPercent)
						? Tier.CRITICAL : Tier.NONE;
			case LOW_AND_CRITICAL:
			default:
				if (AutoMineLogic.isDurabilityAtOrBelow(stack, config.criticalWarningDurability, config.criticalWarningDurabilityPercent)) {
					return Tier.CRITICAL;
				}
				return AutoMineLogic.isDurabilityAtOrBelow(stack, config.lowWarningDurability, config.lowWarningDurabilityPercent)
						? Tier.LOW : Tier.NONE;
		}
	}

	private static void playWarning(Minecraft client, SmartAutoMineConfig config, SlotState state, Tier tier) {
		if (tier == Tier.CRITICAL) {
			SoundUtil.play(client, config.durabilityCriticalWarningSound, 1.0f);
			state.soundCooldownTicks = CRITICAL_SOUND_COOLDOWN_TICKS;
		} else {
			SoundUtil.play(client, config.durabilityWarningSound, 1.0f);
			state.soundCooldownTicks = LOW_SOUND_COOLDOWN_TICKS;
		}
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
