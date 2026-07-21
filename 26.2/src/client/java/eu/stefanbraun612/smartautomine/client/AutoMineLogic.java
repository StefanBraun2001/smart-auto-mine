package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public class AutoMineLogic {
	// 0.75s of extra runtime after the offhand empties, so the block that was just placed
	// still gets mined instead of being left sitting there. Only ever applies to the
	// offhand-empty stop - see tick().
	private static final int OFFHAND_EMPTY_GRACE_TICKS = 15;

	private static long elapsedActiveTicks = 0;
	// -1 = not counting; set to the grace length once the offhand first reads empty and
	// counts down to the stop. No need to un-set it on refill: an empty offhand slot can't
	// be topped up automatically (a dropper can't place into it), so once it's empty it
	// stays empty until the run ends and reset() clears this back to -1.
	private static int offhandEmptyGraceTicks = -1;

	public static void reset() {
		elapsedActiveTicks = 0;
		offhandEmptyGraceTicks = -1;
	}

	public static void tick(Minecraft client) {
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		LocalPlayer player = client.player;
		if (player == null || client.level == null || client.gameMode == null) {
			return;
		}

		elapsedActiveTicks++;

		if (!passesHungerSafety(player, config)) {
			stop(client, config, "Smart Auto Mine: stopped (hunger too low)");
			return;
		}

		if (!passesHealthSafety(player, config)) {
			stop(client, config, "Smart Auto Mine: stopped (health too low)");
			return;
		}

		if (!ensureUsableTool(client, player, config)) {
			stop(client, config, "Smart Auto Mine: stopped (no usable tool left)");
			return;
		}

		long maxDurationTicks = DurationParser.parseTicks(config.maxDuration);
		if (maxDurationTicks > 0 && elapsedActiveTicks >= maxDurationTicks) {
			stop(client, config, "Smart Auto Mine: stopped (time limit reached)");
			return;
		}

		if (SmartAutoMineClient.isPlaceMineActive() && player.getOffhandItem().isEmpty()) {
			// Deliberately the only stop condition with a grace period: it's not a safety
			// condition, just "ran out of material", so finishing the block already placed
			// is harmless. Durability/hunger/health/time all still stop immediately.
			if (!config.finishLastBlockOnEmptyOffhand || offhandEmptyGraceTicks == 0) {
				stop(client, config, "Smart Auto Mine: stopped (offhand is empty)");
				return;
			}
			if (offhandEmptyGraceTicks < 0) {
				offhandEmptyGraceTicks = OFFHAND_EMPTY_GRACE_TICKS;
			}
			offhandEmptyGraceTicks--;
		}

		holdInputs(client, SmartAutoMineClient.isPlaceMineActive());
	}

	// This is the whole mining/placing implementation: hold the mouse buttons down and let
	// vanilla's own per-tick input handling do everything, which is literally what the F3+T
	// technique produces (the client believing the buttons never got released).
	//
	// Driving gameMode.continueDestroyBlock/useItemOn directly instead - what every previous
	// version did - actively fights vanilla. Minecraft.handleKeybinds() runs continueAttack()
	// every single tick, and with the attack key not physically held it takes the "else"
	// branch straight to gameMode.stopDestroyBlock(). So each tick vanilla ABORTED the break
	// the mod had just started and called resetAttackStrengthTicker() - that's both the
	// constantly-resetting attack indicator and why mining/tilling was erratic and kept
	// missing: no break ever got to run uninterrupted. Holding the keys removes the conflict
	// entirely and gives exactly vanilla's timing (rightClickDelay, destroyDelay, hand
	// priority) for free, so place-mine matches the manual technique instead of approximating it.
	private static void holdInputs(Minecraft client, boolean placeMine) {
		client.options.keyAttack.setDown(true);
		// Set explicitly rather than only on true: plain mining must not interact with
		// anything, and switching straight from place-mine to plain mining would otherwise
		// leave right-click stuck down from the previous mode.
		client.options.keyUse.setDown(placeMine);
	}

	// Must be called whenever the mod stops driving input (toggled off, auto-stopped),
	// otherwise the buttons stay stuck down exactly like the F3+T glitch and keep going.
	public static void releaseInputs(Minecraft client) {
		client.options.keyAttack.setDown(false);
		client.options.keyUse.setDown(false);
	}

	// Used while auto-eat is mid-chew: mining has to stop (a held attack key cancels the
	// bite), but the use key is deliberately left alone because auto-eat drives it itself
	// to hold the food down - clearing it here would cancel the very bite we're waiting on.
	public static void releaseMiningInput(Minecraft client) {
		client.options.keyAttack.setDown(false);
	}

	private static void stop(Minecraft client, SmartAutoMineConfig config, String message) {
		SmartAutoMineClient.setEnabled(false, client);
		FeedbackUtil.send(client, config, message);
		playAutoStopSound(client, config);
	}

	// Only called from auto-stop paths (this method), never from the player manually
	// pressing the toggle key - that's handled separately in SmartAutoMineClient and
	// intentionally doesn't play a sound, since the player already knows they stopped it.
	private static void playAutoStopSound(Minecraft client, SmartAutoMineConfig config) {
		if (!config.playSoundOnAutoStop) {
			return;
		}
		Identifier id = Identifier.tryParse(config.autoStopSound);
		if (id == null) {
			return;
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
		if (sound == null) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f));
	}

	private static boolean passesHungerSafety(Player player, SmartAutoMineConfig config) {
		if (!config.hungerSafetyStopEnabled) {
			return true;
		}
		return player.getFoodData().getFoodLevel() >= config.hungerSafetyStopThreshold;
	}

	private static boolean passesHealthSafety(Player player, SmartAutoMineConfig config) {
		if (!config.healthSafetyStopEnabled) {
			return true;
		}
		return player.getHealth() >= config.healthSafetyStopThreshold;
	}

	// Returns true if the main hand currently holds a tool with enough durability to
	// keep going (rotating to another matching tool first if "use more tools" is on
	// and the current one just dropped below the threshold). Returns false only when
	// there's nothing left usable and the mod should stop.
	private static boolean ensureUsableTool(Minecraft client, Player player, SmartAutoMineConfig config) {
		if (hasEnoughDurability(player.getMainHandItem(), config)) {
			return true;
		}
		if (!config.useMoreTools) {
			return false;
		}

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (slot == inventory.getSelectedSlot()) {
				continue;
			}
			ItemStack candidate = inventory.getItem(slot);
			if (candidate.isEmpty() || !matchesKeyword(candidate, config.toolKeyword)) {
				continue;
			}
			if (!hasEnoughDurability(candidate, config)) {
				continue;
			}
			selectSlot(client, slot);
			return true;
		}
		return false;
	}

	private static boolean matchesKeyword(ItemStack stack, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
	}

	private static boolean hasEnoughDurability(ItemStack stack, SmartAutoMineConfig config) {
		if (stack.isEmpty()) {
			// Nothing equipped - either the previous tool just broke or there was
			// never one to begin with. Never "enough", even with the guard fully
			// disabled (0/0), since "use more tools" exists precisely to keep some
			// tool equipped and mining bare-handed is never actually useful here.
			return false;
		}
		if (config.minDurability <= 0 && config.minDurabilityPercent <= 0) {
			return true;
		}
		int maxDamage = stack.getMaxDamage();
		if (maxDamage <= 0) {
			return true; // item has no durability (e.g. bare hand, unbreakable tool)
		}
		int remaining = maxDamage - stack.getDamageValue();
		// <= (not <): minDurability/minDurabilityPercent represent uses left to
		// preserve, so the guard must trip *at* the threshold, before that last
		// use is spent - otherwise the tool consumes its final durability point
		// and breaks (or, for "use more tools", vanishes to an empty stack that
		// then falsely reads as "no durability restriction" and never rotates).
		if (config.minDurability > 0 && remaining <= config.minDurability) {
			return false;
		}
		if (config.minDurabilityPercent > 0) {
			float percent = (remaining * 100f) / maxDamage;
			if (percent <= config.minDurabilityPercent) {
				return false;
			}
		}
		return true;
	}

	// Changing Inventory.selectedSlot alone only updates the client's local view -
	// the server keeps tracking whatever slot it last heard about, so any
	// mining/interact packets sent afterward would act on the wrong item server-side
	// unless we also send this packet, same as vanilla does on scroll/number-key input.
	private static void selectSlot(Minecraft client, int slotIndex) {
		client.player.getInventory().setSelectedSlot(slotIndex);
		if (client.getConnection() != null) {
			client.getConnection().send(new ServerboundSetCarriedItemPacket(slotIndex));
		}
	}
}
