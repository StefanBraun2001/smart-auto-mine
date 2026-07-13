package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

import java.util.Set;

public class AutoEatLogic {
	// Always excluded regardless of the food-safety preset: cake can only be placed
	// (not eaten via right-click like normal food) and chorus fruit randomly
	// teleports you, which would wreck whatever the auto-attack/auto-mine loop is doing.
	private static final Set<Identifier> HARDCODED_BAN = Set.of(
			new Identifier("minecraft", "cake"),
			new Identifier("minecraft", "chorus_fruit")
	);

	// "Light" preset (default): only the genuinely unsafe/non-renewable-only foods.
	private static final Set<Identifier> LIGHT_BAN = Set.of(
			new Identifier("minecraft", "enchanted_golden_apple"),
			new Identifier("minecraft", "pufferfish")
	);

	// "Food Inspector" preset: Light + foods that are safe but unpleasant (poison/hunger risk raw).
	private static final Set<Identifier> FOOD_INSPECTOR_EXTRA_BAN = Set.of(
			new Identifier("minecraft", "rotten_flesh"),
			new Identifier("minecraft", "spider_eye"),
			new Identifier("minecraft", "chicken"),
			new Identifier("minecraft", "poisonous_potato")
	);

	private static int eatingTicksLeft = 0;
	// One extra tick after the bite finishes before auto-mine is allowed to resume,
	// so any per-tick checks always run at least one full tick after the tool is
	// back in hand rather than on the exact same tick as the slot swap.
	private static int settleTicksLeft = 0;
	private static int previousSlot = -1;

	// "Wait after eat" delay: 0.5 seconds, long enough to be a real safety buffer
	// (as opposed to the flat 1-tick settle used regardless, just to let the
	// weapon/tool swap back before anything re-checks state).
	private static final int WAIT_AFTER_EAT_TICKS = 10;

	public static void tick(MinecraftClient client) {
		PlayerEntity player = client.player;
		if (player == null || client.interactionManager == null) {
			return;
		}

		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();

		if (settleTicksLeft > 0) {
			settleTicksLeft--;
			return;
		}

		if (eatingTicksLeft > 0) {
			eatingTicksLeft--;
			if (eatingTicksLeft == 0) {
				finishEating(client, player);
				settleTicksLeft = config.waitAfterEatEnabled ? WAIT_AFTER_EAT_TICKS : 1;
			}
			return;
		}

		if (config.autoEatSlot <= 0) {
			return;
		}
		if (player.getHungerManager().getFoodLevel() >= config.autoEatHungerThreshold) {
			return;
		}

		int slotIndex = config.autoEatSlot - 1;
		ItemStack foodStack = player.getInventory().getStack(slotIndex);
		if (foodStack.isEmpty() || foodStack.getItem().getFoodComponent() == null) {
			return;
		}
		if (isBanned(foodStack, config.foodSafetyPreset)) {
			return;
		}

		previousSlot = player.getInventory().selectedSlot;
		player.getInventory().selectedSlot = slotIndex; // interactItem() below syncs this to the server itself
		client.interactionManager.interactItem(player, Hand.MAIN_HAND);
		// MinecraftClient's own tick cancels item-use the instant it sees the use-key
		// not held (isUsingItem() && !useKey.isPressed() -> stopUsingItem()), so a single
		// interactItem() call alone gets cancelled again on the very next tick. We have to
		// hold the key down ourselves for the whole bite, same as physically holding right-click.
		client.options.useKey.setPressed(true);
		// Not every food takes the same 32 ticks: "snack" foods like dried kelp use 16,
		// and some items override it outright (honey bottle is 40). Read the real
		// per-item duration instead of assuming the default.
		eatingTicksLeft = foodStack.getMaxUseTime();
	}

	public static boolean isEating() {
		return eatingTicksLeft > 0 || settleTicksLeft > 0;
	}

	private static void finishEating(MinecraftClient client, PlayerEntity player) {
		client.options.useKey.setPressed(false);
		if (previousSlot >= 0) {
			player.getInventory().selectedSlot = previousSlot;
			previousSlot = -1;
		}
	}

	private static boolean isBanned(ItemStack stack, SmartAutoMineConfig.FoodSafetyPreset preset) {
		Identifier id = Registries.ITEM.getId(stack.getItem());
		if (HARDCODED_BAN.contains(id)) {
			return true;
		}
		return switch (preset) {
			case RAT -> false;
			case LIGHT -> LIGHT_BAN.contains(id);
			case FOOD_INSPECTOR -> LIGHT_BAN.contains(id) || FOOD_INSPECTOR_EXTRA_BAN.contains(id);
		};
	}
}
