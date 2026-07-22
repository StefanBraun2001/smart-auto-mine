package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;

public class AutoMineLogic {
	// 0.75s of extra runtime after the offhand empties, so the block that was just placed
	// still gets mined instead of being left sitting there. Only ever applies to the
	// offhand-empty stop - see tick().
	private static final int OFFHAND_EMPTY_GRACE_TICKS = 15;
	// Ticks between interacts when place-mine is driving the game directly (screen open,
	// Advanced mode). Matches vanilla's own held-right-click cadence (rightClickDelay = 4).
	private static final int INTERACT_DELAY_TICKS = 4;
	// One vanilla destroyDelay's worth of mining hold-off at the start of a direct-drive
	// place-mine burst, so the first block gets its follow-up interact (e.g. a shovel till)
	// before mining engages, exactly as the held-key path gets for free from vanilla timing.
	private static final int STARTUP_MINE_SUPPRESS_TICKS = 5;

	private static long elapsedActiveTicks = 0;
	// -1 = not counting; set to the grace length once the offhand first reads empty and
	// counts down to the stop. No need to un-set it on refill: an empty offhand slot can't
	// be topped up automatically (a dropper can't place into it), so once it's empty it
	// stays empty until the run ends and reset() clears this back to -1.
	private static int offhandEmptyGraceTicks = -1;
	// Direct-drive place-mine pacing (Advanced mode, screen open only). Reset whenever we're
	// NOT direct-driving place-mine, so each screen-open burst starts fresh.
	private static int placeInteractDelay = 0;
	private static int placeMineStartupTicks = STARTUP_MINE_SUPPRESS_TICKS;

	public static void reset() {
		elapsedActiveTicks = 0;
		offhandEmptyGraceTicks = -1;
		resetDirectPlaceMineState();
	}

	private static void resetDirectPlaceMineState() {
		placeInteractDelay = 0;
		placeMineStartupTicks = STARTUP_MINE_SUPPRESS_TICKS;
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

		boolean screenOpen = client.gui.screen() != null;
		if (SmartAutoMineClient.isPlaceMineActive()) {
			tickPlaceMine(client, player, config, screenOpen);
		} else {
			tickRegularMine(client, player, config, screenOpen);
		}
	}

	// The default drive is to hold the mouse buttons and let vanilla's own per-tick input
	// handling do all the mining/placing - literally what the F3+T technique produces (the
	// client believing the buttons never got released). This gives vanilla's exact timing
	// (rightClickDelay, destroyDelay, main-then-offhand hand priority) for free and, crucially,
	// causes zero conflict: driving gameMode.continueDestroyBlock/useItemOn directly while
	// vanilla's handleKeybinds is also running means vanilla's own continueAttack() calls
	// stopDestroyBlock() every tick (the attack key isn't physically held), aborting the mod's
	// break and resetting the attack-strength ticker - the constantly-blinking attack indicator.
	//
	// The catch: handleKeybinds() is skipped entirely while a screen is open, so held keys do
	// nothing then. To keep working through inventory/chat we switch to driving the game
	// directly - which is safe precisely because, with a screen open, vanilla's conflicting
	// continueAttack() isn't running to fight it. The mode options below pick between these.
	private static void tickRegularMine(Minecraft client, LocalPlayer player, SmartAutoMineConfig config,
			boolean screenOpen) {
		resetDirectPlaceMineState(); // not place-mine; keep its burst state fresh for next time
		switch (config.regularMineMode) {
			case LEGACY -> {
				// Always drive directly (pre-A0.4.1 behaviour): continues through screens, but
				// blinks the attack indicator when no screen is open, same as Toro's Auto Mine.
				releaseInputs(client);
				directMine(client, player, true);
			}
			case VANILLA_INPUT -> {
				if (screenOpen) {
					releaseInputs(client); // pause; releasing also lets vanilla clear missTime on close
				} else {
					holdInputs(client, false);
				}
			}
			case CONTINUOUS -> {
				if (screenOpen) {
					releaseInputs(client);
					directMine(client, player, true);
				} else {
					holdInputs(client, false);
				}
			}
		}
	}

	private static void tickPlaceMine(Minecraft client, LocalPlayer player, SmartAutoMineConfig config,
			boolean screenOpen) {
		boolean directNow = screenOpen && config.placeMineMenuMode == SmartAutoMineConfig.PlaceMineMenuMode.ADVANCED;
		if (!directNow) {
			// Held-key path (no screen), or a paused/Vanilla screen. Keep the direct-drive
			// burst state fresh so it starts clean the moment a screen opens in Advanced mode.
			resetDirectPlaceMineState();
			if (screenOpen) {
				releaseInputs(client); // pause; releasing also lets vanilla clear missTime on close
			} else {
				holdInputs(client, true);
			}
			return;
		}
		releaseInputs(client);
		directPlaceMine(client, player);
	}

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

	// Direct-drive mining, mirroring vanilla Minecraft.continueAttack(down=true): on a solid
	// block, continueDestroyBlock (which internally handles start-vs-continue and the 5-tick
	// post-break destroyDelay); otherwise stopDestroyBlock. Place-mine passes false for
	// abortWhenOffBlock, since stopDestroyBlock resets the attack-strength ticker and the
	// place/mine cycle briefly leaves the crosshair off a block between placing and mining.
	private static void directMine(Minecraft client, LocalPlayer player, boolean abortWhenOffBlock) {
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			BlockPos pos = hitResult.getBlockPos();
			if (!client.level.getBlockState(pos).isAir()
					&& client.gameMode.continueDestroyBlock(pos, hitResult.getDirection())) {
				player.swing(InteractionHand.MAIN_HAND);
			}
			return;
		}
		if (abortWhenOffBlock) {
			client.gameMode.stopDestroyBlock();
		}
	}

	// Direct-drive place-mine, used only while a screen is open in Advanced mode (vanilla's
	// own input handling is switched off then, so we reproduce it): interact once per gap on
	// the rightClickDelay cadence (main hand first, then offhand - vanilla's hand priority, so
	// a shovel tills existing dirt and the offhand places a new block when there's nothing to
	// till), gated by gameMode.isDestroying() so it only fires between breaks, then mine.
	private static void directPlaceMine(Minecraft client, LocalPlayer player) {
		if (placeInteractDelay > 0) {
			placeInteractDelay--;
		}
		if (placeInteractDelay == 0 && !client.gameMode.isDestroying() && client.hitResult != null
				&& client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			InteractionResult result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
			InteractionHand successHand = InteractionHand.MAIN_HAND;
			if (!(result instanceof InteractionResult.Success) && !(result instanceof InteractionResult.Fail)) {
				result = client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
				successHand = InteractionHand.OFF_HAND;
			}
			if (result instanceof InteractionResult.Success) {
				player.swing(successHand);
			}
			placeInteractDelay = INTERACT_DELAY_TICKS;
		}
		if (placeMineStartupTicks > 0) {
			placeMineStartupTicks--;
			return;
		}
		directMine(client, player, false);
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
