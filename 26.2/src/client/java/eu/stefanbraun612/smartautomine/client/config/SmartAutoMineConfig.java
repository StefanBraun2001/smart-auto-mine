package eu.stefanbraun612.smartautomine.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "smartautomine")
public class SmartAutoMineConfig implements ConfigData {

	// --- Safety ---

	@ConfigEntry.Gui.Tooltip
	public int minDurability = 0; // 0 = disabled

	@ConfigEntry.Gui.Tooltip
	public int minDurabilityPercent = 0; // 0 = disabled

	@ConfigEntry.Gui.Tooltip
	public boolean hungerSafetyStopEnabled = true;

	@ConfigEntry.Gui.Tooltip
	public int hungerSafetyStopThreshold = 6; // hunger points, 0-20 scale

	// --- Timing ---

	@ConfigEntry.Gui.Tooltip
	public String maxDuration = ""; // e.g. "90m", "1.5h", "5400s", "1h30m" - empty = unlimited

	// --- Tool rotation ---

	@ConfigEntry.Gui.Tooltip
	public boolean useMoreTools = false;

	@ConfigEntry.Gui.Tooltip
	public String toolKeyword = "pickaxe"; // substring match against the item's registry ID

	// Place-mine mode is not a config toggle - it's triggered by its own keybinding
	// (default L) since only one mode can run at a time; see SmartAutoMineClient.

	@ConfigEntry.Gui.Tooltip
	public int placeMineIntervalTicks = 4; // ticks between offhand place attempts in place-mine mode

	@ConfigEntry.Gui.Tooltip
	public boolean randomizePlaceMineDelay = false;

	@ConfigEntry.Gui.Tooltip
	public int placeMineIntervalMin = 2; // used instead of placeMineIntervalTicks when randomize is on

	@ConfigEntry.Gui.Tooltip
	public int placeMineIntervalMax = 6; // used instead of placeMineIntervalTicks when randomize is on

	// --- General / feedback ---

	public enum FeedbackMode {
		CHAT,
		ACTION_BAR,
		SILENT
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public FeedbackMode feedbackMode = FeedbackMode.ACTION_BAR;

	@ConfigEntry.Gui.Tooltip
	public boolean waitAfterEatEnabled = true;

	@ConfigEntry.Gui.Tooltip
	public boolean playSoundOnAutoStop = true;

	@ConfigEntry.Gui.Tooltip
	public String autoStopSound = "minecraft:block.bell.use"; // full sound event ID

	// --- Auto-eat (same infra as Smart Auto Attack) ---

	public enum FoodSafetyPreset {
		LIGHT,
		FOOD_INSPECTOR,
		RAT
	}

	@ConfigEntry.Gui.Tooltip
	public int autoEatSlot = 0; // 0 = disabled, 1-9 hotbar slot

	@ConfigEntry.Gui.Tooltip
	public int autoEatHungerThreshold = 20; // hunger points, 0-20 (matches the vanilla hunger bar: 20 = full, each drumstick icon = 2 points)

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public FoodSafetyPreset foodSafetyPreset = FoodSafetyPreset.LIGHT;
}
