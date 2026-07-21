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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AutoMineLogic {
	// Temporary diagnostic logging for the place-mine gating bug - remove once fixed.
	private static final Logger PLACE_MINE_LOG = LoggerFactory.getLogger("smartautomine-placemine");

	private static long elapsedActiveTicks = 0;
	// Counts down between place-mine offhand interacts - interacting every single tick
	// (20/s) is faster than the manual "hold both buttons + F3+T glitch" technique it's
	// meant to replace, so this adds the same slight delay back in.
	private static int placeCooldownTicks = 0;

	public static void reset() {
		elapsedActiveTicks = 0;
		placeCooldownTicks = 0;
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
			stop(client, config, "Smart Auto Mine: stopped (offhand is empty)");
			return;
		}

		if (SmartAutoMineClient.isPlaceMineActive()) {
			tickPlaceMine(client, player, config);
		} else {
			tickRegularMining(client, player);
		}
	}

	// Mirrors vanilla Minecraft.continueAttack(down=true) as closely as a mod can: on a
	// solid block, call gameMode.continueDestroyBlock and swing; otherwise stopDestroyBlock.
	//
	// The important detail (confirmed by decompiling MultiPlayerGameMode.continueDestroyBlock)
	// is that we must ALWAYS go through continueDestroyBlock and never call startDestroyBlock
	// ourselves when the target changes. continueDestroyBlock sets destroyDelay = 5 after
	// finishing a break and waits those 5 ticks out at the top before touching anything -
	// that post-break pause is exactly the window in which a held right-click (rightClickDelay
	// = 4) gets to place/till the next block. Previous versions bypassed it with a direct
	// startDestroyBlock on target change, so mining chewed straight through with no gap for
	// the interact to act - which is why nothing ever got tilled after the first block.
	private static void tickRegularMining(Minecraft client, LocalPlayer player) {
		if (client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK) {
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			BlockPos pos = hitResult.getBlockPos();
			if (!client.level.getBlockState(pos).isAir()
					&& client.gameMode.continueDestroyBlock(pos, hitResult.getDirection())) {
				player.swing(InteractionHand.MAIN_HAND);
			}
			return;
		}
		// No block under the crosshair - matches continueAttack's final stopDestroyBlock().
		// It's an internal no-op unless actually mid-break, so it doesn't reset the attack
		// ticker (and flicker the indicator) except when there was genuinely a break to abort.
		client.gameMode.stopDestroyBlock();
	}

	// Replicates a genuinely-held right-click + left-click (what F3+T actually produces:
	// not repeated clicks, but the game believing both buttons are still physically down,
	// forever, processed by vanilla's own per-tick input handling).
	//
	// Two vanilla rules, confirmed by decompiling Minecraft.startUseItem(), drive this:
	//
	// 1. A single right-click tries the MAIN hand's item first, and only falls through to
	//    the OFFHAND item if the main hand's interaction doesn't apply (returns Pass, not
	//    Success or Fail). This is what makes a single held click do two different things
	//    depending on what's there: a shovel in the main hand tills an existing dirt/grass
	//    block on contact (succeeds, offhand never even tried that click); once there's
	//    nothing left to till, the shovel's interaction passes through and the offhand's
	//    placeable block (e.g. dirt) places instead. No manual switching needed - vanilla's
	//    own hand-priority order does it.
	//
	// 2. Interacting is completely blocked while gameMode.isDestroying() is true - you
	//    cannot place/till while actively mid-way through breaking a block. Combined with
	//    (1), this is what drives the whole place-then-till-then-mine cycle automatically:
	//    interacting only succeeds in the moments isDestroying is false (right as the
	//    previous target finishes breaking), at which point the newly placed/tilled block
	//    becomes the mining target (it's physically closer along the same ray than whatever
	//    you clicked against), gets mined to completion, isDestroying goes false again, and
	//    interacting succeeds again.
	private static void tickPlaceMine(Minecraft client, LocalPlayer player, SmartAutoMineConfig config) {
		boolean validTarget = client.hitResult != null && client.hitResult.getType() == HitResult.Type.BLOCK;
		if (placeCooldownTicks > 0) {
			placeCooldownTicks--;
		} else if (!client.gameMode.isDestroying() && validTarget) {
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			InteractionResult mainHandResult = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
			InteractionResult result = mainHandResult;
			InteractionHand successHand = InteractionHand.MAIN_HAND;
			if (!(mainHandResult instanceof InteractionResult.Success)
					&& !(mainHandResult instanceof InteractionResult.Fail)) {
				result = client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
				successHand = InteractionHand.OFF_HAND;
			}
			PLACE_MINE_LOG.info("INTERACT pos={} dir={} state={} mainHandResult={} finalResult={} via={}",
					hitResult.getBlockPos(), hitResult.getDirection(),
					client.level.getBlockState(hitResult.getBlockPos()), mainHandResult, result, successHand);
			if (result instanceof InteractionResult.Success) {
				player.swing(successHand);
			}
			placeCooldownTicks = nextPlaceCooldown(config);
		} else if (placeCooldownTicks == 0) {
			PLACE_MINE_LOG.info("BLOCKED isDestroying={} validTarget={} hitType={}",
					client.gameMode.isDestroying(), validTarget,
					client.hitResult == null ? "null" : client.hitResult.getType());
		}
		tickRegularMining(client, player);
	}

	private static int nextPlaceCooldown(SmartAutoMineConfig config) {
		if (!config.randomizePlaceMineDelay) {
			return Math.max(0, config.placeMineIntervalTicks);
		}
		int min = Math.min(config.placeMineIntervalMin, config.placeMineIntervalMax);
		int max = Math.max(config.placeMineIntervalMin, config.placeMineIntervalMax);
		return Math.max(0, ThreadLocalRandom.current().nextInt(min, max + 1));
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
