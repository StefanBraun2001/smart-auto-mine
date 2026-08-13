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
	public boolean ignoreHungerSafetyWhileRegenerating = false;

	@ConfigEntry.Gui.Tooltip
	public boolean healthSafetyStopEnabled = true;

	@ConfigEntry.Gui.Tooltip
	public float healthSafetyStopThreshold = 6; // health points, 0-20 scale (each heart = 2 points)

	@ConfigEntry.Gui.Tooltip
	public boolean eatToRegenerateHealth = false;

	@ConfigEntry.Gui.Tooltip
	public boolean ignoreHealthSafetyWhileRegenerating = false;

	@ConfigEntry.Gui.Tooltip
	public boolean paranoiaSwitchEnabled = false;

	// --- Timing ---

	@ConfigEntry.Gui.Tooltip
	public String maxDuration = ""; // e.g. "90m", "1.5h", "5400s", "1h30m" - empty = unlimited

	// --- Tool rotation ---

	@ConfigEntry.Gui.Tooltip
	public boolean useMoreTools = false;

	// How "use more tools" decides whether a hotbar item is a valid replacement.
	public enum ToolRotationMode {
		// Substring match (case-insensitive) against the item's registry ID - see toolKeyword.
		KEYWORD,
		// Same tool class as the one that just ran low (e.g. any pickaxe replaces any
		// pickaxe), regardless of material - no keyword needed.
		SAME_TYPE,
		// Exact same item as the one that just ran low (e.g. only another diamond pickaxe
		// replaces a diamond pickaxe) - the strictest mode.
		EXACT_MATCH
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public ToolRotationMode toolRotationMode = ToolRotationMode.KEYWORD;

	@ConfigEntry.Gui.Tooltip
	public String toolKeyword = "pickaxe"; // substring match against the item's registry ID - only used in KEYWORD mode

	// Place-mine mode is not a config toggle - it's triggered by its own keybinding
	// (default L) since only one mode can run at a time; see SmartAutoMineClient.

	// How regular mining is driven. See AutoMineLogic for the full reasoning.
	public enum RegularMineMode {
		// Hold the mouse button while playing (clean, no attack-indicator blink), switch to
		// driving the game directly only while a screen is open, so mining continues through
		// inventory/chat. Default.
		CONTINUOUS,
		// Only ever hold the mouse button. No blink, but pauses while a screen is open.
		VANILLA_INPUT,
		// Always drive the game directly (the pre-A0.4.1 behaviour). Continues through
		// screens, but the attack indicator blinks constantly, same as Toro's Auto Mine.
		LEGACY
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public RegularMineMode regularMineMode = RegularMineMode.CONTINUOUS;

	// What place-mine does while a screen (inventory/chat) is open.
	public enum PlaceMineMenuMode {
		// Pause while a screen is open, resume on close. Default - simple and reliable.
		VANILLA,
		// Experimental: keep placing/mining through an open screen by driving the game
		// directly. May not place/till perfectly reliably while the screen is open.
		ADVANCED
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public PlaceMineMenuMode placeMineMenuMode = PlaceMineMenuMode.VANILLA;

	@ConfigEntry.Gui.Tooltip
	public boolean finishLastBlockOnEmptyOffhand = false;

	@ConfigEntry.Gui.Tooltip
	public boolean pauseTimerWhileMiningPaused = false;

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
	public boolean autoEatSearchAnySlot = false;

	@ConfigEntry.Gui.Tooltip
	public int autoEatSlot = 0; // 0 = disabled, 1-9 hotbar slot - only used when autoEatSearchAnySlot is off

	@ConfigEntry.Gui.Tooltip
	public int autoEatHungerThreshold = 20; // hunger points, 0-20 (matches the vanilla hunger bar: 20 = full, each drumstick icon = 2 points)

	public enum AutoEatAmountMode {
		// Eats exactly one bite per dip below the threshold, then waits for hunger to rise
		// back above it before it's willing to eat again - even if one bite wasn't enough.
		EAT_ONCE,
		// Keeps eating bite after bite, but skips/stops the moment a bite's nutrition
		// would push hunger past the 20-point cap, so it never wastes food.
		DONT_OVEREAT,
		// Keeps eating bite after bite until hunger is fully at 20, no matter how much of
		// a bite's nutrition would go to waste.
		FILL_HUNGER
	}

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public AutoEatAmountMode autoEatAmountMode = AutoEatAmountMode.EAT_ONCE;

	@ConfigEntry.Gui.Tooltip
	@ConfigEntry.Gui.EnumHandler(option = ConfigEntry.Gui.EnumHandler.EnumDisplayOption.BUTTON)
	public FoodSafetyPreset foodSafetyPreset = FoodSafetyPreset.LIGHT;
}
