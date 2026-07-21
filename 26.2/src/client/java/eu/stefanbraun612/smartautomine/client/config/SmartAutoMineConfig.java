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

	@ConfigEntry.Gui.Tooltip
	public boolean healthSafetyStopEnabled = true;

	@ConfigEntry.Gui.Tooltip
	public float healthSafetyStopThreshold = 6; // health points, 0-20 scale (each heart = 2 points)

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
	// It has no timing/delay options: interacting is paced entirely by vanilla's own
	// "can't place while mid-break" rule, and any extra delay only broke it (see
	// AutoMineLogic.tickPlaceMine).

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

	@ConfigEntry.Gui.Tooltip
	public boolean resumeAfterManualReconnect = false; // scripted reconnects (Smart Auto Reconnect) always resume regardless of this

	// --- Auto-eat (same infra as Smart Auto Attack) ---

	public enum FoodSafetyPreset {
		LIGHT,
		FOOD_INSPECTOR,
		RAT
	}

	@ConfigEntry.Gui.Tooltip
	public boolean autoEatEnabled = true;

	@ConfigEntry.Gui.Tooltip
	public int autoEatSlot = 0; // 0 = disabled, 1-9 hotbar slot

	@ConfigEntry.Gui.Tooltip
	public int autoEatHungerThreshold = 20; // hunger points, 0-20 (matches the vanilla hunger bar: 20 = full, each drumstick icon = 2 points)

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public FoodSafetyPreset foodSafetyPreset = FoodSafetyPreset.LIGHT;
}
