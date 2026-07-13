package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AutoMineLogic {
	private static long elapsedActiveTicks = 0;
	private static BlockPos lastBreakingPos = null;
	// Counts down between offhand place attempts in place-mine mode - placing every
	// single tick (20/s) is faster than the manual "hold both buttons + F3+T glitch"
	// technique it's meant to replace, so this adds the same slight delay back in.
	private static int placeCooldownTicks = 0;

	public static void reset() {
		elapsedActiveTicks = 0;
		lastBreakingPos = null;
		placeCooldownTicks = 0;
	}

	public static void tick(MinecraftClient client) {
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null || client.interactionManager == null) {
			return;
		}

		elapsedActiveTicks++;

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

		if (SmartAutoMineClient.isPlaceMineActive() && player.getOffHandStack().isEmpty()) {
			stop(client, config, "Smart Auto Mine: stopped (offhand is empty)");
			return;
		}

		if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
			lastBreakingPos = null;
			return;
		}
		BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
		BlockPos pos = hitResult.getBlockPos();
		if (client.world.getBlockState(pos).isAir()) {
			lastBreakingPos = null;
			return;
		}

		if (!pos.equals(lastBreakingPos)) {
			client.interactionManager.attackBlock(pos, hitResult.getSide());
			lastBreakingPos = pos;
		} else {
			client.interactionManager.updateBlockBreakingProgress(pos, hitResult.getSide());
		}
		player.swingHand(Hand.MAIN_HAND);

		if (SmartAutoMineClient.isPlaceMineActive()) {
			if (placeCooldownTicks > 0) {
				placeCooldownTicks--;
			} else {
				// Right-clicking the same hit result while the targeted block still exists
				// generally can't place there directly - vanilla instead tries to place
				// against the adjacent position derived from the hit side, which in a
				// forward-tunneling pattern is usually the space just mined. That's what
				// gives the "wall up behind you while mining" effect; this is the part most
				// likely to need retuning once tested against a real tunnel.
				client.interactionManager.interactBlock(player, Hand.OFF_HAND, hitResult);
				placeCooldownTicks = nextPlaceCooldown(config);
			}
		}
	}

	private static int nextPlaceCooldown(SmartAutoMineConfig config) {
		if (!config.randomizePlaceMineDelay) {
			return Math.max(0, config.placeMineIntervalTicks);
		}
		int min = Math.min(config.placeMineIntervalMin, config.placeMineIntervalMax);
		int max = Math.max(config.placeMineIntervalMin, config.placeMineIntervalMax);
		return Math.max(0, ThreadLocalRandom.current().nextInt(min, max + 1));
	}

	private static void stop(MinecraftClient client, SmartAutoMineConfig config, String message) {
		SmartAutoMineClient.setEnabled(false, client);
		FeedbackUtil.send(client, config, message);
		playAutoStopSound(client, config);
	}

	// Only called from auto-stop paths (this method), never from the player manually
	// pressing the toggle key - that's handled separately in SmartAutoMineClient and
	// intentionally doesn't play a sound, since the player already knows they stopped it.
	private static void playAutoStopSound(MinecraftClient client, SmartAutoMineConfig config) {
		if (!config.playSoundOnAutoStop) {
			return;
		}
		Identifier id = Identifier.tryParse(config.autoStopSound);
		if (id == null) {
			return;
		}
		SoundEvent sound = Registries.SOUND_EVENT.get(id);
		if (sound == null) {
			return;
		}
		client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0f));
	}

	private static boolean passesHungerSafety(PlayerEntity player, SmartAutoMineConfig config) {
		if (!config.hungerSafetyStopEnabled) {
			return true;
		}
		return player.getHungerManager().getFoodLevel() >= config.hungerSafetyStopThreshold;
	}

	// Returns true if the main hand currently holds a tool with enough durability to
	// keep going (rotating to another matching tool first if "use more tools" is on
	// and the current one just dropped below the threshold). Returns false only when
	// there's nothing left usable and the mod should stop.
	private static boolean ensureUsableTool(MinecraftClient client, PlayerEntity player, SmartAutoMineConfig config) {
		if (hasEnoughDurability(player.getMainHandStack(), config)) {
			return true;
		}
		if (!config.useMoreTools) {
			return false;
		}

		PlayerInventory inventory = player.getInventory();
		for (int slot = 0; slot < 9; slot++) {
			if (slot == inventory.selectedSlot) {
				continue;
			}
			ItemStack candidate = inventory.getStack(slot);
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
		String id = Registries.ITEM.getId(stack.getItem()).getPath();
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
		int remaining = maxDamage - stack.getDamage();
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

	// Changing PlayerInventory.selectedSlot alone only updates the client's local
	// view - the server keeps tracking whatever slot it last heard about, so any
	// mining/interact packets sent afterward would act on the wrong item server-side
	// unless we also send this packet, same as vanilla does on scroll/number-key input.
	private static void selectSlot(MinecraftClient client, int slotIndex) {
		client.player.getInventory().selectedSlot = slotIndex;
		if (client.getNetworkHandler() != null) {
			client.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slotIndex));
		}
	}
}
