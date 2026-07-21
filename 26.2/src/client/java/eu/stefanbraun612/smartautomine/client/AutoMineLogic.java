package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AutoMineLogic {
	// Safety cap on the mining phase of place-mine, in case the placed block never
	// actually breaks - without this it would get stuck mining the same spot forever
	// instead of ever placing again.
	private static final int PLACE_MINE_STUCK_TIMEOUT_TICKS = 100;

	private static long elapsedActiveTicks = 0;
	private static BlockPos lastBreakingPos = null;
	// Counts down between place-mine offhand interacts - interacting every single tick
	// (20/s) is faster than the manual "hold both buttons + F3+T glitch" technique it's
	// meant to replace, so this adds the same slight delay back in.
	private static int placeCooldownTicks = 0;
	// Null means "awaiting the next placement". Once a block is placed, place-mine locks
	// onto this position for the whole mining phase - it deliberately ignores where the
	// live crosshair drifts to afterwards (see tickPlaceMine for why).
	private static BlockPos placeMineTargetPos = null;
	private static Direction placeMineDirection = null;
	private static int placeMineStuckTicks = 0;

	public static void reset() {
		elapsedActiveTicks = 0;
		lastBreakingPos = null;
		placeCooldownTicks = 0;
		placeMineTargetPos = null;
		placeMineDirection = null;
		placeMineStuckTicks = 0;
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

	private static void tickRegularMining(Minecraft client, LocalPlayer player) {
		if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
			lastBreakingPos = null;
			return;
		}
		BlockHitResult hitResult = (BlockHitResult) client.hitResult;
		BlockPos pos = hitResult.getBlockPos();
		if (client.level.getBlockState(pos).isAir()) {
			lastBreakingPos = null;
			return;
		}
		mineBlockAt(client, player, pos, hitResult.getDirection());
	}

	private static void mineBlockAt(Minecraft client, LocalPlayer player, BlockPos pos, Direction direction) {
		if (!pos.equals(lastBreakingPos)) {
			client.gameMode.startDestroyBlock(pos, direction);
			lastBreakingPos = pos;
		} else {
			client.gameMode.continueDestroyBlock(pos, direction);
		}
		player.swing(InteractionHand.MAIN_HAND);
	}

	// Replicates the manual "hold RMB, then LMB, then F3+T" cheese: place the offhand
	// item, then mine exactly the block that placement created, then place again.
	//
	// Placing a block from your offhand doesn't happen AT the position you clicked -
	// vanilla places it adjacent to the clicked face (BlockPos.relative(direction)),
	// unless the clicked block itself is replaceable (tall grass, snow layer, etc.), in
	// which case it lands at the clicked position directly. Mining the clicked position
	// itself (what earlier versions of this did) mines whatever you're using as a
	// reference surface to place against - not the block you actually placed - which
	// is why it looked like it was "mining the block behind" the intended target and
	// slowly digging through unrelated terrain.
	//
	// The mining target is computed once, right when placing, and locked for the whole
	// mining phase: once the placed block is gone, the crosshair naturally re-targets
	// the reference surface again, and re-deriving the position from the live crosshair
	// at that point would walk one block further with each cycle instead of placing
	// against the same fixed reference every time.
	private static void tickPlaceMine(Minecraft client, LocalPlayer player, SmartAutoMineConfig config) {
		if (placeMineTargetPos == null) {
			if (placeCooldownTicks > 0) {
				placeCooldownTicks--;
				return;
			}
			if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
				return; // nothing to place against yet, keep waiting
			}
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			BlockPos clickedPos = hitResult.getBlockPos();
			Direction direction = hitResult.getDirection();
			boolean placesAtClickedPos = client.level.getBlockState(clickedPos).canBeReplaced();
			BlockPos newTargetPos = placesAtClickedPos ? clickedPos : clickedPos.relative(direction);

			client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
			player.swing(InteractionHand.OFF_HAND);

			placeMineTargetPos = newTargetPos;
			placeMineDirection = direction;
			placeMineStuckTicks = 0;
			lastBreakingPos = null; // force a fresh startDestroyBlock
			return; // mine on the next tick, once the placement has taken effect
		}

		if (client.level.getBlockState(placeMineTargetPos).isAir()
				|| ++placeMineStuckTicks > PLACE_MINE_STUCK_TIMEOUT_TICKS) {
			// If the target is air right away (stuckTicks still 0), the placement itself
			// silently failed - most commonly because the spot to place into overlapped
			// the player's own hitbox (vanilla refuses to place a block there). Nothing to
			// mine either way; go back to placing after the usual cooldown.
			placeMineTargetPos = null;
			placeCooldownTicks = nextPlaceCooldown(config);
			return;
		}

		mineBlockAt(client, player, placeMineTargetPos, placeMineDirection);
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
