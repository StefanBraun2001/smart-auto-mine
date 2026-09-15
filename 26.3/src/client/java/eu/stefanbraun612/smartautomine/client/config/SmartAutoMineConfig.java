package eu.stefanbraun612.smartautomine.client.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

import java.util.ArrayList;
import java.util.List;

@Config(name = "smartautomine")
public class SmartAutoMineConfig implements ConfigData {

	// --- Safety ---

	public int minDurability = 0; // 0 = disabled

	public int minDurabilityPercent = 0; // 0 = disabled

	public boolean hungerSafetyStopEnabled = true;

	public int hungerSafetyStopThreshold = 6; // hunger points, 0-20 scale

	public boolean ignoreHungerSafetyWhileRegenerating = false;

	public boolean healthSafetyStopEnabled = true;

	public float healthSafetyStopThreshold = 6; // health points, 0-20 scale (each heart = 2 points)

	public boolean eatToRegenerateHealth = false;

	public boolean ignoreHealthSafetyWhileRegenerating = false;

	public boolean paranoiaSwitchEnabled = false;

	// --- Durability warning ---
	// Runs independently of the toggle above - an always-on watchdog for accidentally
	// hand-using a tool that would already fail the Min durability/% guard, even while
	// this mod isn't mining with it. Reuses that same threshold rather than a separate
	// one. Mutually exclusive with Smart Auto Attack's equivalent feature if installed -
	// see SmartAutoMineConfigScreen's error supplier on durabilityWarningEnabled.

	public boolean durabilityWarningEnabled = false;

	public List<String> durabilityWarningKeywords = new ArrayList<>(); // e.g. "pickaxe", "axe" - substring match, same as toolKeyword

	public String durabilityWarningSound = "smartautomine:durability_gong"; // full sound event ID - bundled two-tone gong, or any other valid sound event ID

	// Separate toggle: checks all 4 armor slots (+ elytra, which occupies the chest slot)
	// for durability regardless of item type - no keyword list, since "is this a helmet"
	// isn't a meaningful question the way "is this a pickaxe" is. Shares durabilityWarningSound.
	public boolean armorDurabilityWarningEnabled = false;

	// --- Timing ---

	public String maxDuration = ""; // e.g. "90m", "1.5h", "5400s", "1h30m" - empty = unlimited

	// --- Throttle ---

	public boolean throttleEnabled = false;

	public String throttleMineDuration = "5m"; // same free-text format as maxDuration

	public String throttlePauseDuration = "1m";

	public boolean freezeDurationDuringThrottlePause = true; // pauses the max-duration timer while throttle is paused

	// --- Tool rotation ---

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

	public ToolRotationMode toolRotationMode = ToolRotationMode.KEYWORD;

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

	public RegularMineMode regularMineMode = RegularMineMode.CONTINUOUS;

	// What place-mine does while a screen (inventory/chat) is open.
	public enum PlaceMineMenuMode {
		// Pause while a screen is open, resume on close. Default - simple and reliable.
		VANILLA,
		// Experimental: keep placing/mining through an open screen by driving the game
		// directly. May not place/till perfectly reliably while the screen is open.
		ADVANCED
	}

	public PlaceMineMenuMode placeMineMenuMode = PlaceMineMenuMode.VANILLA;

	public boolean finishLastBlockOnEmptyOffhand = false;

	public boolean pauseTimerWhileMiningPaused = false;

	// --- General / feedback ---

	public enum FeedbackMode {
		CHAT,
		ACTION_BAR,
		SILENT
	}

	public FeedbackMode feedbackMode = FeedbackMode.ACTION_BAR;

	public boolean waitAfterEatEnabled = true;

	public boolean playSoundOnAutoStop = true;

	public String autoStopSound = "smartautomine:auto_stop"; // full sound event ID - bundled twin-bell ring, or any other valid sound event ID

	public boolean resumeAfterManualReconnect = false; // scripted reconnects (Smart Auto Reconnect) always resume regardless of this

	// --- Auto-eat (same infra as Smart Auto Attack) ---

	public enum FoodSafetyPreset {
		LIGHT,
		FOOD_INSPECTOR,
		RAT
	}

	public boolean autoEatEnabled = true;

	public boolean autoEatSearchAnySlot = false;

	public int autoEatSlot = 0; // 0 = disabled, 1-9 hotbar slot - only used when autoEatSearchAnySlot is off

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

	public AutoEatAmountMode autoEatAmountMode = AutoEatAmountMode.EAT_ONCE;

	public FoodSafetyPreset foodSafetyPreset = FoodSafetyPreset.LIGHT;
}
