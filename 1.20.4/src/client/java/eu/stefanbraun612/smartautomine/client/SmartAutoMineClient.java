package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class SmartAutoMineClient implements ClientModInitializer {
	public static final String MOD_ID = "smartautomine";

	private static KeyBinding toggleKey;
	private static KeyBinding placeMineToggleKey;
	private static boolean enabled = false;
	private static boolean placeMineActive = false;

	@Override
	public void onInitializeClient() {
		AutoConfig.register(SmartAutoMineConfig.class, GsonConfigSerializer::new);

		// Default keys are K (regular) and L (place-mine), distinct from Smart Auto
		// Attack's J so all three can be bound without a clash if both mods are installed.
		toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.smartautomine.toggle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				"key.categories.smartautomine"
		));

		placeMineToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.smartautomine.toggle_place_mine",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_L,
				"key.categories.smartautomine"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.wasPressed()) {
				handleToggle(false, client);
			}
			while (placeMineToggleKey.wasPressed()) {
				handleToggle(true, client);
			}
			if (enabled) {
				AutoEatLogic.tick(client);
				// Don't mine while mid-chew: mining actions would otherwise interrupt
				// the vanilla eat-use action the same way attacking does.
				if (!AutoEatLogic.isEating()) {
					AutoMineLogic.tick(client);
				}
			}
		});
	}

	public static boolean isEnabled() {
		return enabled;
	}

	// Only true while actually running in place-mine mode - use this rather than
	// reading a config field, since which mode is active is now a runtime choice
	// (which key you pressed), not a persisted preference.
	public static boolean isPlaceMineActive() {
		return enabled && placeMineActive;
	}

	// Pressing the key for the mode that's already running turns it off; pressing
	// the other mode's key switches straight over, since only one mode can run at once.
	private static void handleToggle(boolean requestedPlaceMine, MinecraftClient client) {
		if (enabled && placeMineActive == requestedPlaceMine) {
			setEnabled(false, client);
		} else {
			placeMineActive = requestedPlaceMine;
			setEnabled(true, client);
		}
	}

	public static void setEnabled(boolean value, MinecraftClient client) {
		enabled = value;
		AutoMineLogic.reset();
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		String modeLabel = placeMineActive ? " (place-mine)" : "";
		FeedbackUtil.send(client, config, value ? "Smart Auto Mine: ON" + modeLabel : "Smart Auto Mine: OFF");
	}
}
