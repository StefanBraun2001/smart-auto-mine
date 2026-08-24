package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
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

	// Throttle: alternates between a "mining" phase and a "paused" phase for the configured
	// durations. Unlike Attack's throttle, this has no other timing feature to take priority
	// over - it's just a plain on/off gate.
	private static boolean throttleMiningPhase = true;
	private static long throttlePhaseTicksRemaining = 0;
	// Whether the throttle mechanism itself was engaged last tick - used to (re)start on a
	// fresh mining phase the moment it becomes engaged, distinct from throttlePhasePassedLastTick
	// below (which tracks the mining/pause phase transitions for feedback messages).
	private static Boolean throttleEngagedLastTick = null;
	private static Boolean throttlePhasePassedLastTick = null;
	// -1 = not counting; set to the grace length once the offhand first reads empty and
	// counts down to the stop. No need to un-set it on refill: an empty offhand slot can't
	// be topped up automatically (a dropper can't place into it), so once it's empty it
	// stays empty until the run ends and reset() clears this back to -1.
	private static int offhandEmptyGraceTicks = -1;
	// The offhand-empty grace only makes sense once we've actually placed something. Track
	// whether the offhand has ever held items this run so starting place-mine with an
	// already-empty offhand stops immediately instead of running a pointless grace period.
	private static boolean offhandHadItems = false;
	// Direct-drive place-mine pacing (Advanced mode, screen open only). Reset whenever we're
	// NOT direct-driving place-mine, so each screen-open burst starts fresh.
	private static int placeInteractDelay = 0;
	private static int placeMineStartupTicks = STARTUP_MINE_SUPPRESS_TICKS;
	// Remembers the last non-empty main-hand item, for SAME_TYPE/EXACT_MATCH tool rotation.
	// Needed because once a tool actually breaks (no durability floor set to catch it first),
	// the main hand reads as empty/air by the time the rotation search runs - by then the
	// item's own class/identity is gone, so without this there'd be nothing left to compare
	// candidates against for those two modes.
	private static ItemStack lastKnownMainHandItem = ItemStack.EMPTY;

	// Health-guard pause (only when Eat food to regenerate health is on): freezes everything
	// (including timers) once health drops below the configured threshold, force-feeds via
	// AutoEatLogic (see isCriticalHealthPauseActive()) until hunger is full and health has
	// climbed back to threshold+4, then resumes normally. Gives up and stops the mod if that
	// recovery doesn't happen within the timeout below.
	private static boolean criticalHealthPauseActive = false;
	private static int criticalHealthPauseTicks = 0;
	private static final float CRITICAL_HEALTH_RESUME_MARGIN = 4.0f; // 2 hearts above the threshold
	private static final int CRITICAL_HEALTH_PAUSE_TIMEOUT_TICKS = 900; // 45 seconds

	public static void reset() {
		elapsedActiveTicks = 0;
		throttleMiningPhase = true;
		throttlePhaseTicksRemaining = 0;
		throttleEngagedLastTick = null;
		throttlePhasePassedLastTick = null;
		offhandEmptyGraceTicks = -1;
		offhandHadItems = false;
		lastKnownMainHandItem = ItemStack.EMPTY;
		criticalHealthPauseActive = false;
		criticalHealthPauseTicks = 0;
		resetDirectPlaceMineState();
	}

	// Read by SmartAutoMineClient to tell AutoEatLogic to force-feed past its normal
	// threshold while a critical-health pause is in progress.
	public static boolean isCriticalHealthPauseActive() {
		return criticalHealthPauseActive;
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

		if (handleHealthGuard(client, player, config)) {
			return;
		}

		boolean placeMine = SmartAutoMineClient.isPlaceMineActive();
		boolean screenOpen = client.gui.screen() != null;
		boolean miningPaused = isMiningPaused(config, placeMine, screenOpen);
		boolean throttleGatePasses = tickThrottle(client, config);

		// Max-duration timer. Normally it counts every tick, but freezes while the mod is
		// paused - either a screen open in a mode that pauses mining, or a throttle pause with
		// its own freeze toggle on - so the limit measures actual working time rather than idle
		// time, matching how the timer already skips ticks spent auto-eating (that path doesn't
		// run tick() at all).
		boolean pausedForThrottlePause = config.throttleEnabled && !throttleGatePasses && config.freezeDurationDuringThrottlePause;
		if ((!miningPaused || !config.pauseTimerWhileMiningPaused) && !pausedForThrottlePause) {
			elapsedActiveTicks++;
		}

		if (!passesHungerSafety(player, config)) {
			stop(client, config, "Smart Auto Mine: stopped (hunger too low)");
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

		if (placeMine) {
			if (!player.getOffhandItem().isEmpty()) {
				offhandHadItems = true;
			} else {
				// The grace period is only for finishing the block we just placed, so it
				// applies only once the offhand has actually held items this run - starting
				// with an already-empty offhand has nothing to finish and stops right away.
				// Even with the grace, it's the only stop with a delay: running out of
				// material isn't a safety condition. Durability/hunger/health/time all still
				// stop immediately.
				boolean grace = config.finishLastBlockOnEmptyOffhand && offhandHadItems;
				if (!grace || offhandEmptyGraceTicks == 0) {
					stop(client, config, "Smart Auto Mine: stopped (offhand is empty)");
					return;
				}
				if (offhandEmptyGraceTicks < 0) {
					offhandEmptyGraceTicks = OFFHAND_EMPTY_GRACE_TICKS;
				}
				offhandEmptyGraceTicks--;
			}
		}

		if (!throttleGatePasses) {
			releaseInputs(client);
			return;
		}

		if (placeMine) {
			tickPlaceMine(client, player, config, screenOpen);
		} else {
			tickRegularMine(client, player, config, screenOpen);
		}
	}

	// Advances the throttle phase timer and returns whether mining should be allowed this tick.
	// Always returns true while Throttle is off. Misconfigured durations (unparseable/zero) also
	// pass through ungated rather than risk getting stuck permanently paused.
	private static boolean tickThrottle(Minecraft client, SmartAutoMineConfig config) {
		if (config.throttleEnabled && (throttleEngagedLastTick == null || !throttleEngagedLastTick)) {
			// Just became engaged - always (re)start on a fresh mining phase.
			throttleMiningPhase = true;
			throttlePhaseTicksRemaining = DurationParser.parseTicks(config.throttleMineDuration);
		}
		throttleEngagedLastTick = config.throttleEnabled;

		if (!config.throttleEnabled) {
			throttlePhasePassedLastTick = null; // stale state - re-evaluate cleanly once re-engaged
			return true;
		}

		long throttleMineTicks = DurationParser.parseTicks(config.throttleMineDuration);
		long throttlePauseTicks = DurationParser.parseTicks(config.throttlePauseDuration);
		if (throttleMineTicks <= 0 || throttlePauseTicks <= 0) {
			return true;
		}

		if (throttlePhaseTicksRemaining <= 0) {
			throttleMiningPhase = !throttleMiningPhase;
			throttlePhaseTicksRemaining = throttleMiningPhase ? throttleMineTicks : throttlePauseTicks;
		}
		boolean gatePasses = throttleMiningPhase;
		throttlePhaseTicksRemaining--;

		if (throttlePhasePassedLastTick == null || throttlePhasePassedLastTick != gatePasses) {
			FeedbackUtil.send(client, config, gatePasses
					? "Smart Auto Mine: throttle - mining"
					: "Smart Auto Mine: throttle - pausing");
		}
		throttlePhasePassedLastTick = gatePasses;
		return gatePasses;
	}

	// True only when the mod deliberately does nothing this tick because a screen is open in
	// a mode that pauses mining (regular Vanilla input, or place-mine Vanilla). The other
	// modes keep mining/placing through a screen by driving the game directly, and with no
	// screen open everything mines, so those never count as paused.
	private static boolean isMiningPaused(SmartAutoMineConfig config, boolean placeMine, boolean screenOpen) {
		if (!screenOpen) {
			return false;
		}
		return placeMine
				? config.placeMineMenuMode == SmartAutoMineConfig.PlaceMineMenuMode.VANILLA
				: config.regularMineMode == SmartAutoMineConfig.RegularMineMode.VANILLA_INPUT;
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
		SoundUtil.play(client, config.autoStopSound);
	}

	private static boolean passesHungerSafety(Player player, SmartAutoMineConfig config) {
		if (!config.hungerSafetyStopEnabled) {
			return true;
		}
		if (config.ignoreHungerSafetyWhileRegenerating && player.hasEffect(MobEffects.REGENERATION)) {
			return true;
		}
		return player.getFoodData().getFoodLevel() >= config.hungerSafetyStopThreshold;
	}

	// Returns true if tick() should return immediately (either a hard stop just fired, or
	// we're mid-recovery-pause). Merges the old "hard stop on low health" and "critical-health
	// panic pause" into a single threshold: Eat food to regenerate health decides which of the
	// two happens once health drops below healthSafetyStopThreshold.
	private static boolean handleHealthGuard(Minecraft client, Player player, SmartAutoMineConfig config) {
		if (criticalHealthPauseActive && (!config.healthSafetyStopEnabled || !config.eatToRegenerateHealth)) {
			criticalHealthPauseActive = false; // guard/eat-to-recover turned off mid-pause - resume immediately
		}
		if (!config.healthSafetyStopEnabled) {
			return false;
		}
		if (isRegenerationBypassActive(player, config)) {
			if (criticalHealthPauseActive) {
				criticalHealthPauseActive = false; // regen kicked in mid-pause - trust it, resume
			}
			return false;
		}

		if (!criticalHealthPauseActive && player.getHealth() < config.healthSafetyStopThreshold) {
			if (!config.eatToRegenerateHealth) {
				stop(client, config, "Smart Auto Mine: stopped (health too low)");
				return true;
			}
			criticalHealthPauseActive = true;
			criticalHealthPauseTicks = 0;
			FeedbackUtil.send(client, config, "Smart Auto Mine: paused (health critical - recovering)");
		}
		if (!criticalHealthPauseActive) {
			return false;
		}

		// Clamped to max health: a high threshold (e.g. 18) plus the margin must never target
		// above what the player can actually reach, or the pause would never resolve on its own.
		float resumeThreshold = Math.min(config.healthSafetyStopThreshold + CRITICAL_HEALTH_RESUME_MARGIN, player.getMaxHealth());
		if (player.getHealth() >= resumeThreshold) {
			criticalHealthPauseActive = false;
			FeedbackUtil.send(client, config, "Smart Auto Mine: resuming (health recovered)");
			return false;
		}
		criticalHealthPauseTicks++;
		if (criticalHealthPauseTicks >= CRITICAL_HEALTH_PAUSE_TIMEOUT_TICKS) {
			stop(client, config, "Smart Auto Mine: stopped (health failed to recover in time)");
			return true;
		}
		releaseInputs(client); // stop actively mining/placing while paused
		return true; // stays paused - AutoEatLogic force-feeds independently, see isCriticalHealthPauseActive()
	}

	// The Paranoia switch overrides the regen bypass specifically for the eat-to-recover path:
	// it never trusts Regeneration alone to keep hunger topped up, only ever relevant while
	// auto-eat can actually act on it.
	private static boolean isRegenerationBypassActive(Player player, SmartAutoMineConfig config) {
		if (!config.ignoreHealthSafetyWhileRegenerating || !player.hasEffect(MobEffects.REGENERATION)) {
			return false;
		}
		if (config.paranoiaSwitchEnabled && config.eatToRegenerateHealth && config.autoEatEnabled) {
			return false;
		}
		return true;
	}

	// Returns true if the main hand currently holds a tool with enough durability to
	// keep going (rotating to another matching tool first if "use more tools" is on
	// and the current one just dropped below the threshold). Returns false only when
	// there's nothing left usable and the mod should stop.
	private static boolean ensureUsableTool(Minecraft client, Player player, SmartAutoMineConfig config) {
		ItemStack currentTool = player.getMainHandItem();
		if (!currentTool.isEmpty()) {
			lastKnownMainHandItem = currentTool; // still equipped - remember it in case it breaks entirely
		}
		if (hasEnoughDurability(currentTool, config)) {
			return true;
		}
		if (!config.useMoreTools) {
			return false;
		}

		// If the tool has already broken to empty/air (no durability floor set to catch it
		// first), fall back to the last item we saw equipped so SAME_TYPE/EXACT_MATCH still
		// have something meaningful to compare against - matching against air's own item
		// class would otherwise match almost anything, including food.
		ItemStack referenceTool = currentTool.isEmpty() ? lastKnownMainHandItem : currentTool;

		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (slot == inventory.getSelectedSlot()) {
				continue;
			}
			ItemStack candidate = inventory.getItem(slot);
			if (candidate.isEmpty() || !matchesRotationCriteria(candidate, referenceTool, config)) {
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

	// Pickaxes and swords (and most other vanilla tools as of this MC version) no longer
	// have their own Item subclass - they're plain Item instances distinguished only by
	// data components/tags, so comparing getClass() lumps them in with everything else
	// that's also just a plain Item (including food). Use vanilla's own tool-category tags
	// instead, which is also more correct for modded tools that register into them.
	private static final List<TagKey<Item>> TOOL_TYPE_TAGS = List.of(
			ItemTags.PICKAXES, ItemTags.AXES, ItemTags.SHOVELS, ItemTags.HOES, ItemTags.SWORDS, ItemTags.SPEARS);

	private static boolean sameToolType(ItemStack candidate, ItemStack referenceTool) {
		for (TagKey<Item> tag : TOOL_TYPE_TAGS) {
			if (referenceTool.is(tag)) {
				return candidate.is(tag);
			}
		}
		// referenceTool isn't in any known tool-category tag (e.g. a trident, or a modded
		// tool that doesn't register into one) - fall back to class equality, which still
		// works for vanilla's remaining single-item-per-category tools.
		return candidate.getItem().getClass() == referenceTool.getItem().getClass();
	}

	// referenceTool is the tool that just ran low (or, if it already broke to empty, the
	// last non-empty item we saw equipped - see ensureUsableTool).
	private static boolean matchesRotationCriteria(ItemStack candidate, ItemStack referenceTool, SmartAutoMineConfig config) {
		if (referenceTool.isEmpty() && config.toolRotationMode != SmartAutoMineConfig.ToolRotationMode.KEYWORD) {
			// Never saw a tool equipped this run - nothing to compare against, so SAME_TYPE/
			// EXACT_MATCH can't mean anything yet (KEYWORD doesn't need a reference at all).
			return false;
		}
		return switch (config.toolRotationMode) {
			case KEYWORD -> matchesKeyword(candidate, config.toolKeyword);
			case SAME_TYPE -> sameToolType(candidate, referenceTool);
			case EXACT_MATCH -> candidate.getItem() == referenceTool.getItem();
		};
	}

	// Package-private: also reused by DurabilityWarningLogic (its list-of-keywords match
	// is just this, looped).
	static boolean matchesKeyword(ItemStack stack, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return id.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
	}

	// Package-private: also reused by DurabilityWarningLogic, which only ever calls this
	// with a keyword-matched (non-empty) stack, so the "nothing equipped" empty-stack
	// branch below never actually applies to it.
	static boolean hasEnoughDurability(ItemStack stack, SmartAutoMineConfig config) {
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
