package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;

// Deliberately scoped to non-player-specific "technique" settings only: duration,
// durability, and the auto-eat/food-safety cluster. Never touches hotbar slots,
// keybinds, or feedback style, since those depend on the player's own setup.
public class MinePreset {
	public String maxDuration = "";
	public int minDurability = 0;
	public int minDurabilityPercent = 0;
	public boolean useMoreTools = false;
	public String toolKeyword = "pickaxe";
	public boolean autoEatEnabled = true;
	public int autoEatHungerThreshold = 20;
	public SmartAutoMineConfig.FoodSafetyPreset foodSafetyPreset = SmartAutoMineConfig.FoodSafetyPreset.LIGHT;
	public boolean hungerSafetyStopEnabled = true;
	public int hungerSafetyStopThreshold = 6;

	public static MinePreset fromConfig(SmartAutoMineConfig config) {
		MinePreset preset = new MinePreset();
		preset.maxDuration = config.maxDuration;
		preset.minDurability = config.minDurability;
		preset.minDurabilityPercent = config.minDurabilityPercent;
		preset.useMoreTools = config.useMoreTools;
		preset.toolKeyword = config.toolKeyword;
		preset.autoEatEnabled = config.autoEatEnabled;
		preset.autoEatHungerThreshold = config.autoEatHungerThreshold;
		preset.foodSafetyPreset = config.foodSafetyPreset;
		preset.hungerSafetyStopEnabled = config.hungerSafetyStopEnabled;
		preset.hungerSafetyStopThreshold = config.hungerSafetyStopThreshold;
		return preset;
	}

	public void applyTo(SmartAutoMineConfig config) {
		config.maxDuration = maxDuration;
		config.minDurability = minDurability;
		config.minDurabilityPercent = minDurabilityPercent;
		config.useMoreTools = useMoreTools;
		config.toolKeyword = toolKeyword;
		config.autoEatEnabled = autoEatEnabled;
		config.autoEatHungerThreshold = autoEatHungerThreshold;
		config.foodSafetyPreset = foodSafetyPreset;
		config.hungerSafetyStopEnabled = hungerSafetyStopEnabled;
		config.hungerSafetyStopThreshold = hungerSafetyStopThreshold;
	}
}
