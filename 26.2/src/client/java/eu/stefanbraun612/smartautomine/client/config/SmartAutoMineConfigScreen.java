package eu.stefanbraun612.smartautomine.client.config;

import eu.stefanbraun612.smartautomine.client.MinePreset;
import eu.stefanbraun612.smartautomine.client.PresetManager;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.gui.entries.EnumListEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Hand-built (not annotation-generated) Cloth Config screen, so that fields can be
 * grouped into tabs and dependent fields can be hidden via Requirement - neither is
 * possible with AutoConfig's reflection-based screen generation.
 */
public class SmartAutoMineConfigScreen {

	private static final String PREFIX = "text.autoconfig.smartautomine.";

	private static Component option(String field) {
		return Component.translatable(PREFIX + "option." + field);
	}

	private static Component tooltip(String field) {
		return Component.translatable(PREFIX + "option." + field + ".@Tooltip");
	}

	private static Component category(String key) {
		return Component.translatable(PREFIX + "category." + key);
	}

	// Fully-qualified name of the (optional, separate) Smart Auto Attack mod's config class -
	// checked via reflection, same no-compile-time-dependency pattern as the Reconnect
	// signal in SmartAutoMineClient, so this mod builds and runs fine whether or not
	// Smart Auto Attack is installed, or whether its version has this feature at all.
	private static final String SIBLING_ATTACK_CONFIG_CLASS = "eu.stefanbraun612.smartautoattack.client.config.SmartAutoAttackConfig";

	private static boolean isSiblingDurabilityWarningEnabled() {
		try {
			Class<?> attackConfigClass = Class.forName(SIBLING_ATTACK_CONFIG_CLASS);
			Object attackConfig = AutoConfig.getConfigHolder(attackConfigClass.asSubclass(ConfigData.class)).getConfig();
			return attackConfigClass.getField("durabilityWarningEnabled").getBoolean(attackConfig);
		} catch (Throwable t) {
			return false; // Smart Auto Attack not installed, doesn't have this feature yet, or any reflection issue
		}
	}

	public static Screen build(Screen parent) {
		ConfigHolder<SmartAutoMineConfig> holder = AutoConfig.getConfigHolder(SmartAutoMineConfig.class);
		SmartAutoMineConfig config = holder.getConfig();
		SmartAutoMineConfig defaults = new SmartAutoMineConfig();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(PREFIX + "title"));
		ConfigEntryBuilder entryBuilder = builder.entryBuilder();

		// --- Mining tab ---

		ConfigCategory mining = builder.getOrCreateCategory(category("mining"));

		mining.addEntry(entryBuilder
				.startEnumSelector(option("regularMineMode"), SmartAutoMineConfig.RegularMineMode.class, config.regularMineMode)
				.setDefaultValue(defaults.regularMineMode)
				.setTooltip(tooltip("regularMineMode"))
				.setSaveConsumer(v -> config.regularMineMode = v)
				.build());

		mining.addEntry(entryBuilder
				.startEnumSelector(option("placeMineMenuMode"), SmartAutoMineConfig.PlaceMineMenuMode.class, config.placeMineMenuMode)
				.setDefaultValue(defaults.placeMineMenuMode)
				.setTooltip(tooltip("placeMineMenuMode"))
				.setSaveConsumer(v -> config.placeMineMenuMode = v)
				.build());

		mining.addEntry(entryBuilder
				.startBooleanToggle(option("finishLastBlockOnEmptyOffhand"), config.finishLastBlockOnEmptyOffhand)
				.setDefaultValue(defaults.finishLastBlockOnEmptyOffhand)
				.setTooltip(tooltip("finishLastBlockOnEmptyOffhand"))
				.setSaveConsumer(v -> config.finishLastBlockOnEmptyOffhand = v)
				.build());

		mining.addEntry(entryBuilder
				.startBooleanToggle(option("pauseTimerWhileMiningPaused"), config.pauseTimerWhileMiningPaused)
				.setDefaultValue(defaults.pauseTimerWhileMiningPaused)
				.setTooltip(tooltip("pauseTimerWhileMiningPaused"))
				.setSaveConsumer(v -> config.pauseTimerWhileMiningPaused = v)
				.build());

		mining.addEntry(entryBuilder
				.startStrField(option("maxDuration"), config.maxDuration)
				.setDefaultValue(defaults.maxDuration)
				.setTooltip(tooltip("maxDuration"))
				.setSaveConsumer(v -> config.maxDuration = v)
				.build());

		// --- Throttle tab ---

		ConfigCategory throttle = builder.getOrCreateCategory(category("throttle"));

		BooleanListEntry throttleEnabled = entryBuilder
				.startBooleanToggle(option("throttleEnabled"), config.throttleEnabled)
				.setDefaultValue(defaults.throttleEnabled)
				.setTooltip(tooltip("throttleEnabled"))
				.setSaveConsumer(v -> config.throttleEnabled = v)
				.build();
		throttle.addEntry(throttleEnabled);

		throttle.addEntry(entryBuilder
				.startStrField(option("throttleMineDuration"), config.throttleMineDuration)
				.setDefaultValue(defaults.throttleMineDuration)
				.setTooltip(tooltip("throttleMineDuration"))
				.setSaveConsumer(v -> config.throttleMineDuration = v)
				.setDisplayRequirement(Requirement.isTrue(throttleEnabled))
				.build());

		throttle.addEntry(entryBuilder
				.startStrField(option("throttlePauseDuration"), config.throttlePauseDuration)
				.setDefaultValue(defaults.throttlePauseDuration)
				.setTooltip(tooltip("throttlePauseDuration"))
				.setSaveConsumer(v -> config.throttlePauseDuration = v)
				.setDisplayRequirement(Requirement.isTrue(throttleEnabled))
				.build());

		throttle.addEntry(entryBuilder
				.startBooleanToggle(option("freezeDurationDuringThrottlePause"), config.freezeDurationDuringThrottlePause)
				.setDefaultValue(defaults.freezeDurationDuringThrottlePause)
				.setTooltip(tooltip("freezeDurationDuringThrottlePause"))
				.setSaveConsumer(v -> config.freezeDurationDuringThrottlePause = v)
				.setDisplayRequirement(Requirement.isTrue(throttleEnabled))
				.build());

		// --- Safety tab ---

		ConfigCategory safety = builder.getOrCreateCategory(category("safety"));

		safety.addEntry(entryBuilder
				.startIntField(option("minDurability"), config.minDurability)
				.setDefaultValue(defaults.minDurability)
				.setTooltip(tooltip("minDurability"))
				.setSaveConsumer(v -> config.minDurability = v)
				.build());

		safety.addEntry(entryBuilder
				.startIntField(option("minDurabilityPercent"), config.minDurabilityPercent)
				.setDefaultValue(defaults.minDurabilityPercent)
				.setTooltip(tooltip("minDurabilityPercent"))
				.setSaveConsumer(v -> config.minDurabilityPercent = v)
				.build());

		BooleanListEntry useMoreTools = entryBuilder
				.startBooleanToggle(option("useMoreTools"), config.useMoreTools)
				.setDefaultValue(defaults.useMoreTools)
				.setTooltip(tooltip("useMoreTools"))
				.setSaveConsumer(v -> config.useMoreTools = v)
				.build();
		safety.addEntry(useMoreTools);

		EnumListEntry<SmartAutoMineConfig.ToolRotationMode> toolRotationMode = entryBuilder
				.startEnumSelector(option("toolRotationMode"), SmartAutoMineConfig.ToolRotationMode.class, config.toolRotationMode)
				.setDefaultValue(defaults.toolRotationMode)
				.setTooltip(tooltip("toolRotationMode"))
				.setSaveConsumer(v -> config.toolRotationMode = v)
				.setDisplayRequirement(Requirement.isTrue(useMoreTools))
				.build();
		safety.addEntry(toolRotationMode);

		safety.addEntry(entryBuilder
				.startStrField(option("toolKeyword"), config.toolKeyword)
				.setDefaultValue(defaults.toolKeyword)
				.setTooltip(tooltip("toolKeyword"))
				.setSaveConsumer(v -> config.toolKeyword = v)
				.setDisplayRequirement(Requirement.all(
						Requirement.isTrue(useMoreTools),
						Requirement.isValue(toolRotationMode, SmartAutoMineConfig.ToolRotationMode.KEYWORD)))
				.build());

		BooleanListEntry hungerSafetyStopEnabled = entryBuilder
				.startBooleanToggle(option("hungerSafetyStopEnabled"), config.hungerSafetyStopEnabled)
				.setDefaultValue(defaults.hungerSafetyStopEnabled)
				.setTooltip(tooltip("hungerSafetyStopEnabled"))
				.setSaveConsumer(v -> config.hungerSafetyStopEnabled = v)
				.build();
		safety.addEntry(hungerSafetyStopEnabled);

		safety.addEntry(entryBuilder
				.startIntField(option("hungerSafetyStopThreshold"), config.hungerSafetyStopThreshold)
				.setDefaultValue(defaults.hungerSafetyStopThreshold)
				.setTooltip(tooltip("hungerSafetyStopThreshold"))
				.setSaveConsumer(v -> config.hungerSafetyStopThreshold = v)
				.setDisplayRequirement(Requirement.isTrue(hungerSafetyStopEnabled))
				.build());

		safety.addEntry(entryBuilder
				.startBooleanToggle(option("ignoreHungerSafetyWhileRegenerating"), config.ignoreHungerSafetyWhileRegenerating)
				.setDefaultValue(defaults.ignoreHungerSafetyWhileRegenerating)
				.setTooltip(tooltip("ignoreHungerSafetyWhileRegenerating"))
				.setSaveConsumer(v -> config.ignoreHungerSafetyWhileRegenerating = v)
				.setDisplayRequirement(Requirement.isTrue(hungerSafetyStopEnabled))
				.build());

		BooleanListEntry healthSafetyStopEnabled = entryBuilder
				.startBooleanToggle(option("healthSafetyStopEnabled"), config.healthSafetyStopEnabled)
				.setDefaultValue(defaults.healthSafetyStopEnabled)
				.setTooltip(tooltip("healthSafetyStopEnabled"))
				.setSaveConsumer(v -> config.healthSafetyStopEnabled = v)
				.build();
		safety.addEntry(healthSafetyStopEnabled);

		safety.addEntry(entryBuilder
				.startFloatField(option("healthSafetyStopThreshold"), config.healthSafetyStopThreshold)
				.setDefaultValue(defaults.healthSafetyStopThreshold)
				.setTooltip(tooltip("healthSafetyStopThreshold"))
				.setSaveConsumer(v -> config.healthSafetyStopThreshold = v)
				.setDisplayRequirement(Requirement.isTrue(healthSafetyStopEnabled))
				.build());

		BooleanListEntry eatToRegenerateHealth = entryBuilder
				.startBooleanToggle(option("eatToRegenerateHealth"), config.eatToRegenerateHealth)
				.setDefaultValue(defaults.eatToRegenerateHealth)
				.setTooltip(tooltip("eatToRegenerateHealth"))
				.setSaveConsumer(v -> config.eatToRegenerateHealth = v)
				.setDisplayRequirement(Requirement.isTrue(healthSafetyStopEnabled))
				.build();
		safety.addEntry(eatToRegenerateHealth);

		safety.addEntry(entryBuilder
				.startBooleanToggle(option("ignoreHealthSafetyWhileRegenerating"), config.ignoreHealthSafetyWhileRegenerating)
				.setDefaultValue(defaults.ignoreHealthSafetyWhileRegenerating)
				.setTooltip(tooltip("ignoreHealthSafetyWhileRegenerating"))
				.setSaveConsumer(v -> config.ignoreHealthSafetyWhileRegenerating = v)
				.setDisplayRequirement(Requirement.isTrue(healthSafetyStopEnabled))
				.build());

		safety.addEntry(entryBuilder
				.startBooleanToggle(option("paranoiaSwitchEnabled"), config.paranoiaSwitchEnabled)
				.setDefaultValue(defaults.paranoiaSwitchEnabled)
				.setTooltip(tooltip("paranoiaSwitchEnabled"))
				.setSaveConsumer(v -> config.paranoiaSwitchEnabled = v)
				.setDisplayRequirement(Requirement.all(
						Requirement.isTrue(healthSafetyStopEnabled),
						Requirement.isTrue(eatToRegenerateHealth)))
				.build());

		// --- Durability warning tab ---

		ConfigCategory durabilityWarning = builder.getOrCreateCategory(category("durabilityWarning"));

		BooleanListEntry durabilityWarningEnabled = entryBuilder
				.startBooleanToggle(option("durabilityWarningEnabled"), config.durabilityWarningEnabled)
				.setDefaultValue(defaults.durabilityWarningEnabled)
				.setTooltip(tooltip("durabilityWarningEnabled"))
				.setSaveConsumer(v -> config.durabilityWarningEnabled = v)
				.setErrorSupplier(v -> v && isSiblingDurabilityWarningEnabled()
						? Optional.of(Component.translatable(PREFIX + "durabilityWarningEnabled.conflict"))
						: Optional.empty())
				.build();
		durabilityWarning.addEntry(durabilityWarningEnabled);

		durabilityWarning.addEntry(entryBuilder
				.startStrList(option("durabilityWarningKeywords"), config.durabilityWarningKeywords)
				.setDefaultValue(defaults.durabilityWarningKeywords)
				.setTooltip(tooltip("durabilityWarningKeywords"))
				.setSaveConsumer(v -> config.durabilityWarningKeywords = v)
				.setDisplayRequirement(Requirement.isTrue(durabilityWarningEnabled))
				.build());

		durabilityWarning.addEntry(entryBuilder
				.startStrField(option("durabilityWarningSound"), config.durabilityWarningSound)
				.setDefaultValue(defaults.durabilityWarningSound)
				.setTooltip(tooltip("durabilityWarningSound"))
				.setSaveConsumer(v -> config.durabilityWarningSound = v)
				.setDisplayRequirement(Requirement.isTrue(durabilityWarningEnabled))
				.build());

		// --- General tab ---

		ConfigCategory general = builder.getOrCreateCategory(category("general"));

		general.addEntry(entryBuilder
				.startEnumSelector(option("feedbackMode"), SmartAutoMineConfig.FeedbackMode.class, config.feedbackMode)
				.setDefaultValue(defaults.feedbackMode)
				.setTooltip(tooltip("feedbackMode"))
				.setSaveConsumer(v -> config.feedbackMode = v)
				.build());

		general.addEntry(entryBuilder
				.startBooleanToggle(option("waitAfterEatEnabled"), config.waitAfterEatEnabled)
				.setDefaultValue(defaults.waitAfterEatEnabled)
				.setTooltip(tooltip("waitAfterEatEnabled"))
				.setSaveConsumer(v -> config.waitAfterEatEnabled = v)
				.build());

		BooleanListEntry playSoundOnAutoStop = entryBuilder
				.startBooleanToggle(option("playSoundOnAutoStop"), config.playSoundOnAutoStop)
				.setDefaultValue(defaults.playSoundOnAutoStop)
				.setTooltip(tooltip("playSoundOnAutoStop"))
				.setSaveConsumer(v -> config.playSoundOnAutoStop = v)
				.build();
		general.addEntry(playSoundOnAutoStop);

		general.addEntry(entryBuilder
				.startStrField(option("autoStopSound"), config.autoStopSound)
				.setDefaultValue(defaults.autoStopSound)
				.setTooltip(tooltip("autoStopSound"))
				.setSaveConsumer(v -> config.autoStopSound = v)
				.setDisplayRequirement(Requirement.isTrue(playSoundOnAutoStop))
				.build());

		general.addEntry(entryBuilder
				.startBooleanToggle(option("resumeAfterManualReconnect"), config.resumeAfterManualReconnect)
				.setDefaultValue(defaults.resumeAfterManualReconnect)
				.setTooltip(tooltip("resumeAfterManualReconnect"))
				.setSaveConsumer(v -> config.resumeAfterManualReconnect = v)
				.build());

		// --- Auto-eat tab ---

		ConfigCategory autoEat = builder.getOrCreateCategory(category("autoeat"));

		BooleanListEntry autoEatEnabled = entryBuilder
				.startBooleanToggle(option("autoEatEnabled"), config.autoEatEnabled)
				.setDefaultValue(defaults.autoEatEnabled)
				.setTooltip(tooltip("autoEatEnabled"))
				.setSaveConsumer(v -> config.autoEatEnabled = v)
				.build();
		autoEat.addEntry(autoEatEnabled);

		BooleanListEntry autoEatSearchAnySlot = entryBuilder
				.startBooleanToggle(option("autoEatSearchAnySlot"), config.autoEatSearchAnySlot)
				.setDefaultValue(defaults.autoEatSearchAnySlot)
				.setTooltip(tooltip("autoEatSearchAnySlot"))
				.setSaveConsumer(v -> config.autoEatSearchAnySlot = v)
				.setDisplayRequirement(Requirement.isTrue(autoEatEnabled))
				.build();
		autoEat.addEntry(autoEatSearchAnySlot);

		autoEat.addEntry(entryBuilder
				.startIntField(option("autoEatSlot"), config.autoEatSlot)
				.setDefaultValue(defaults.autoEatSlot)
				.setTooltip(tooltip("autoEatSlot"))
				.setSaveConsumer(v -> config.autoEatSlot = v)
				.setDisplayRequirement(Requirement.all(
						Requirement.isTrue(autoEatEnabled),
						Requirement.isFalse(autoEatSearchAnySlot)))
				.build());

		autoEat.addEntry(entryBuilder
				.startIntField(option("autoEatHungerThreshold"), config.autoEatHungerThreshold)
				.setDefaultValue(defaults.autoEatHungerThreshold)
				.setTooltip(tooltip("autoEatHungerThreshold"))
				.setSaveConsumer(v -> config.autoEatHungerThreshold = v)
				.setDisplayRequirement(Requirement.isTrue(autoEatEnabled))
				.build());

		autoEat.addEntry(entryBuilder
				.startEnumSelector(option("autoEatAmountMode"), SmartAutoMineConfig.AutoEatAmountMode.class, config.autoEatAmountMode)
				.setDefaultValue(defaults.autoEatAmountMode)
				.setTooltip(tooltip("autoEatAmountMode"))
				.setSaveConsumer(v -> config.autoEatAmountMode = v)
				.setDisplayRequirement(Requirement.isTrue(autoEatEnabled))
				.build());

		autoEat.addEntry(entryBuilder
				.startEnumSelector(option("foodSafetyPreset"), SmartAutoMineConfig.FoodSafetyPreset.class, config.foodSafetyPreset)
				.setDefaultValue(defaults.foodSafetyPreset)
				.setTooltip(tooltip("foodSafetyPreset"))
				.setSaveConsumer(v -> config.foodSafetyPreset = v)
				.setDisplayRequirement(Requirement.isTrue(autoEatEnabled))
				.build());

		// --- Presets tab ---
		// No button widgets exist in Cloth Config's declarative entry API, so apply/save/
		// delete aren't instant like the retired command was - they're captured into these
		// three ephemeral fields (not real config fields) and only acted on together, once,
		// in the savingRunnable below, when the screen's own Save & Done is pressed.

		ConfigCategory presetsCategory = builder.getOrCreateCategory(category("presets"));
		Set<String> presetNames = PresetManager.all().keySet();

		presetsCategory.addEntry(entryBuilder
				.startTextDescription(Component.translatable(PREFIX + "presets.saved", String.join(", ", presetNames)))
				.build());

		String[] applyPresetName = {""};
		presetsCategory.addEntry(entryBuilder
				.startStringDropdownMenu(option("presetApply"), "")
				.setDefaultValue("")
				.setSelections(presetNames)
				.setSuggestionMode(true)
				.setTooltip(tooltip("presetApply"))
				.setSaveConsumer(v -> applyPresetName[0] = v)
				.build());

		String[] savePresetName = {""};
		presetsCategory.addEntry(entryBuilder
				.startStrField(option("presetSaveAs"), "")
				.setDefaultValue("")
				.setTooltip(tooltip("presetSaveAs"))
				.setSaveConsumer(v -> savePresetName[0] = v)
				.build());

		String[] deletePresetName = {""};
		presetsCategory.addEntry(entryBuilder
				.startStringDropdownMenu(option("presetDelete"), "")
				.setDefaultValue("")
				.setSelections(presetNames)
				.setSuggestionMode(true)
				.setTooltip(tooltip("presetDelete"))
				.setSaveConsumer(v -> deletePresetName[0] = v)
				.build());

		builder.setSavingRunnable(() -> {
			if (!applyPresetName[0].isBlank()) {
				MinePreset preset = PresetManager.get(applyPresetName[0]);
				if (preset != null) {
					preset.applyTo(config);
				}
			}
			if (!savePresetName[0].isBlank()) {
				PresetManager.save(savePresetName[0], MinePreset.fromConfig(config));
			}
			if (!deletePresetName[0].isBlank()) {
				PresetManager.delete(deletePresetName[0]);
			}
			holder.save();
		});

		return builder.build();
	}
}
