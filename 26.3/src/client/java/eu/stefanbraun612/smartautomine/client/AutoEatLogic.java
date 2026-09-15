package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

public class AutoEatLogic {
	// Always excluded regardless of the food-safety preset: cake can only be placed
	// (not eaten via right-click like normal food) and chorus fruit randomly
	// teleports you, which would wreck whatever the auto-attack/auto-mine loop is doing.
	private static final Set<Identifier> HARDCODED_BAN = Set.of(
			Identifier.fromNamespaceAndPath("minecraft", "cake"),
			Identifier.fromNamespaceAndPath("minecraft", "chorus_fruit")
	);

	// "Light" preset (default): only the genuinely unsafe/non-renewable-only foods.
	private static final Set<Identifier> LIGHT_BAN = Set.of(
			Identifier.fromNamespaceAndPath("minecraft", "enchanted_golden_apple"),
			Identifier.fromNamespaceAndPath("minecraft", "pufferfish")
	);

	// "Food Inspector" preset: Light + foods that are safe but unpleasant (poison/hunger risk raw).
	private static final Set<Identifier> FOOD_INSPECTOR_EXTRA_BAN = Set.of(
			Identifier.fromNamespaceAndPath("minecraft", "rotten_flesh"),
			Identifier.fromNamespaceAndPath("minecraft", "spider_eye"),
			Identifier.fromNamespaceAndPath("minecraft", "chicken"),
			Identifier.fromNamespaceAndPath("minecraft", "poisonous_potato")
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

	// EAT_ONCE: true once we've had our one bite for the current dip below the threshold -
	// cleared the moment hunger rises back above it, so the next dip gets its own single bite.
	private static boolean eatOnceLatched = false;
	// DONT_OVEREAT / FILL_HUNGER: true from the moment hunger first dips below the threshold
	// until the mode's own stop condition is met - lets those modes keep eating well past the
	// threshold (up to no-waste or full-20) instead of stopping the instant one bite crosses it.
	private static boolean eatingSessionActive = false;

	public static void reset() {
		eatOnceLatched = false;
		eatingSessionActive = false;
	}

	public static void tick(Minecraft client) {
		tick(client, false);
	}

	// forceFullHunger is set by AutoMineLogic while a health-guard recovery pause is in
	// progress: eats regardless of the autoEatEnabled toggle or amount mode, straight up to a
	// full hunger bar. Still needs a food source available (autoEatSlot or any-slot search) -
	// with nothing eligible this is a no-op and the pause just waits out its timeout.
	public static void tick(Minecraft client, boolean forceFullHunger) {
		Player player = client.player;
		if (player == null || client.gameMode == null) {
			return;
		}

		if (settleTicksLeft > 0) {
			settleTicksLeft--;
			return;
		}

		if (eatingTicksLeft > 0) {
			eatingTicksLeft--;
			if (eatingTicksLeft == 0) {
				SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
				finishEating(client, player);
				settleTicksLeft = config.waitAfterEatEnabled ? WAIT_AFTER_EAT_TICKS : 1;
			}
			return;
		}

		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		int foodLevel = player.getFoodData().getFoodLevel();

		if (forceFullHunger) {
			eatOnceLatched = false; // stale normal-mode state - let it re-arm cleanly once this ends
			eatingSessionActive = false;
			if (foodLevel >= 20) {
				return;
			}
			int slotIndex = findFoodSlot(player, config);
			if (slotIndex >= 0) {
				beginBite(client, player, slotIndex);
			}
			return;
		}

		if (!config.autoEatEnabled) {
			eatOnceLatched = false;
			eatingSessionActive = false;
			return;
		}

		if (config.autoEatAmountMode == SmartAutoMineConfig.AutoEatAmountMode.EAT_ONCE) {
			if (foodLevel >= config.autoEatHungerThreshold) {
				eatOnceLatched = false;
				return;
			}
			if (eatOnceLatched) {
				return;
			}
			int slotIndex = findFoodSlot(player, config);
			if (slotIndex < 0) {
				return;
			}
			eatOnceLatched = true;
			beginBite(client, player, slotIndex);
			return;
		}

		// DONT_OVEREAT / FILL_HUNGER: once triggered, keep going past the threshold until the
		// mode's own stop condition is met, rather than stopping the instant one bite crosses it.
		if (!eatingSessionActive) {
			if (foodLevel >= config.autoEatHungerThreshold) {
				return;
			}
			eatingSessionActive = true;
		}
		int slotIndex = findFoodSlot(player, config);
		if (slotIndex < 0) {
			eatingSessionActive = false; // nothing left to eat - give up this session
			return;
		}
		if (config.autoEatAmountMode == SmartAutoMineConfig.AutoEatAmountMode.FILL_HUNGER) {
			if (foodLevel >= 20) {
				eatingSessionActive = false;
				return;
			}
		} else { // DONT_OVEREAT
			ItemStack candidate = player.getInventory().getItem(slotIndex);
			var food = candidate.get(DataComponents.FOOD);
			int nutrition = food != null ? food.nutrition() : 0;
			if (nutrition <= 0 || foodLevel + nutrition > 20) {
				eatingSessionActive = false; // next bite would waste nutrition past the cap - stop here
				return;
			}
		}
		beginBite(client, player, slotIndex);
	}

	public static boolean isEating() {
		return eatingTicksLeft > 0 || settleTicksLeft > 0;
	}

	// Searches either the single configured slot, or the whole hotbar when autoEatSearchAnySlot
	// is on, for the first item that's actually food and not banned by the safety preset. Only
	// the hotbar (not the main inventory) is searched - eating something requires holding it,
	// and moving items out of storage into the hotbar first would need extra inventory-click
	// packets this mod doesn't otherwise touch.
	private static int findFoodSlot(Player player, SmartAutoMineConfig config) {
		if (config.autoEatSearchAnySlot) {
			for (int slot = 0; slot < 9; slot++) {
				if (isEligibleFood(player.getInventory().getItem(slot), config.foodSafetyPreset)) {
					return slot;
				}
			}
			return -1;
		}
		if (config.autoEatSlot <= 0) {
			return -1;
		}
		int slotIndex = config.autoEatSlot - 1;
		return isEligibleFood(player.getInventory().getItem(slotIndex), config.foodSafetyPreset) ? slotIndex : -1;
	}

	private static boolean isEligibleFood(ItemStack stack, SmartAutoMineConfig.FoodSafetyPreset preset) {
		return !stack.isEmpty() && stack.get(DataComponents.FOOD) != null && !isBanned(stack, preset);
	}

	private static void beginBite(Minecraft client, Player player, int slotIndex) {
		ItemStack foodStack = player.getInventory().getItem(slotIndex);
		previousSlot = player.getInventory().getSelectedSlot();
		player.getInventory().setSelectedSlot(slotIndex); // useItem() below syncs this to the server itself
		client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
		// Minecraft's own tick cancels item-use the instant it sees the use-key not
		// held (isUsingItem() && !keyUse.isDown() -> releaseUsingItem()), so a single
		// useItem() call alone gets cancelled again on the very next tick. We have to
		// hold the key down ourselves for the whole bite, same as physically holding right-click.
		client.options.keyUse.setDown(true);
		// Not every food takes the same 32 ticks: "snack" foods like dried kelp use 16,
		// and some items override it outright (honey bottle is 40). Read the real
		// per-item duration instead of assuming the default.
		eatingTicksLeft = foodStack.getUseDuration(player);
	}

	private static void finishEating(Minecraft client, Player player) {
		client.options.keyUse.setDown(false);
		if (previousSlot >= 0) {
			player.getInventory().setSelectedSlot(previousSlot);
			previousSlot = -1;
		}
	}

	private static boolean isBanned(ItemStack stack, SmartAutoMineConfig.FoodSafetyPreset preset) {
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
