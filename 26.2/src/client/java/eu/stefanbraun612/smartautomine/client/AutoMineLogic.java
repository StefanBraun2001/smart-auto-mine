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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class AutoMineLogic {
	// Safety cap on the mining phase of place-mine, in case the interacted block never
	// actually breaks (wrong tool, an interaction that didn't produce anything minable,
	// ran out of items) - without this it would get stuck mining the same spot forever
	// instead of ever interacting again.
	private static final int PLACE_MINE_STUCK_TIMEOUT_TICKS = 100;

	private static long elapsedActiveTicks = 0;
	private static BlockPos lastBreakingPos = null;
	// Counts down between place-mine cycles - placing/interacting every single tick
	// (20/s) is faster than the manual "hold both buttons + F3+T glitch" technique it's
	// meant to replace, so this adds the same slight delay back in.
	private static int placeCooldownTicks = 0;
	// Place-mine alternates between interacting (offhand right-click) and mining - true
	// means "interact next", false means "mine the result of the last interaction".
	private static boolean placeMineAwaitingInteract = true;
	// True for the one/two ticks right after interacting, while we wait to see which of
	// the two candidate positions below actually changed before committing to a mining
	// target - see tickPlaceMine for why this can't just use the live crosshair target.
	private static boolean placeMineResolving = false;
	private static BlockPos placeMineCandidatePos = null;
	private static BlockPos placeMineCandidateAdjPos = null;
	private static BlockState placeMineBeforeState = null;
	private static BlockState placeMineBeforeAdjState = null;
	private static BlockPos placeMineTargetPos = null;
	private static Direction placeMineDirection = null;
	private static int placeMineStuckTicks = 0;

	public static void reset() {
		elapsedActiveTicks = 0;
		lastBreakingPos = null;
		placeCooldownTicks = 0;
		placeMineAwaitingInteract = true;
		placeMineResolving = false;
		placeMineCandidatePos = null;
		placeMineCandidateAdjPos = null;
		placeMineBeforeState = null;
		placeMineBeforeAdjState = null;
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

	// Replicates the manual "hold RMB, then LMB, then F3+T" cheese used to place/interact
	// with something (a raw ore block for a Fortune pickaxe farm, tilling dirt into a path
	// before mining it, etc.) and then mine exactly the result of that action.
	//
	// Once interacted, we deliberately stop reading the live crosshair target
	// (client.hitResult) to decide what to mine: as soon as the placed/transformed block
	// breaks, the crosshair naturally re-targets whatever is now revealed behind it (e.g.
	// the neighbouring block you aimed at to place an ore block against) - mining THAT
	// instead is the bug that made this feel like it "waits for ages": it would silently
	// start chewing through an unrelated, possibly much harder block before ever cycling
	// back to interact. Instead we remember exactly which of the two possible positions
	// (the clicked block itself, for tool transforms like shovel->path; or the position
	// adjacent to the clicked face, for placing a new block against it) actually changed
	// right after interacting, and lock onto that single position for the whole mining
	// phase, regardless of where the crosshair drifts to afterwards.
	private static void tickPlaceMine(Minecraft client, LocalPlayer player, SmartAutoMineConfig config) {
		if (placeMineAwaitingInteract) {
			if (placeCooldownTicks > 0) {
				placeCooldownTicks--;
				return;
			}
			if (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK) {
				return; // nothing to interact with yet, keep waiting
			}
			BlockHitResult hitResult = (BlockHitResult) client.hitResult;
			placeMineCandidatePos = hitResult.getBlockPos();
			placeMineCandidateAdjPos = placeMineCandidatePos.relative(hitResult.getDirection());
			placeMineBeforeState = client.level.getBlockState(placeMineCandidatePos);
			placeMineBeforeAdjState = client.level.getBlockState(placeMineCandidateAdjPos);
			placeMineDirection = hitResult.getDirection();

			client.gameMode.useItemOn(player, InteractionHand.OFF_HAND, hitResult);
			player.swing(InteractionHand.OFF_HAND);

			placeMineAwaitingInteract = false;
			placeMineResolving = true;
			placeMineStuckTicks = 0;
			return;
		}

		if (placeMineResolving) {
			BlockState nowAdjState = client.level.getBlockState(placeMineCandidateAdjPos);
			if (!nowAdjState.equals(placeMineBeforeAdjState)) {
				placeMineTargetPos = placeMineCandidateAdjPos;
			} else if (!client.level.getBlockState(placeMineCandidatePos).equals(placeMineBeforeState)) {
				placeMineTargetPos = placeMineCandidatePos;
			} else if (++placeMineStuckTicks > 2) {
				// Interaction didn't visibly do anything within a couple of ticks (wrong
				// tool, out of items, blocked) - go back to interacting instead of
				// committing to a mining target that was never actually placed.
				placeMineAwaitingInteract = true;
				placeMineResolving = false;
				placeCooldownTicks = nextPlaceCooldown(config);
				return;
			} else {
				return; // still waiting to see which position changed
			}
			placeMineResolving = false;
			placeMineStuckTicks = 0;
			lastBreakingPos = null; // force a fresh startDestroyBlock
		}

		if (client.level.getBlockState(placeMineTargetPos).isAir()
				|| ++placeMineStuckTicks > PLACE_MINE_STUCK_TIMEOUT_TICKS) {
			// Either the target is already gone (mined) or it's stuck and not breaking -
			// either way, go back to interacting instead of mining forever.
			placeMineAwaitingInteract = true;
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
