package eu.stefanbraun612.smartautomine.client;

import com.mojang.blaze3d.platform.InputConstants;
import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class SmartAutoMineClient implements ClientModInitializer {
	public static final String MOD_ID = "smartautomine";

	// Fully-qualified name of the (optional, separate) Smart Auto Reconnect mod's
	// signal class - checked via reflection so this mod builds and runs fine whether
	// or not that mod is installed, with no compile-time dependency between them.
	private static final String RECONNECT_SIGNAL_CLASS = "eu.stefanbraun612.smartautoreconnect.ReconnectSignal";
	private static final long RECONNECT_SIGNAL_WINDOW_MILLIS = 5000;

	private static KeyMapping toggleKey;
	private static KeyMapping placeMineToggleKey;
	private static boolean enabled = false;
	private static boolean placeMineActive = false;
	// Sat for a few ticks after (re)joining a world before AutoEat/AutoMine are
	// allowed to act, so nothing mines/eats against a still-loading world state.
	private static int joinSettleTicksLeft = 0;

	@Override
	public void onInitializeClient() {
		AutoConfig.register(SmartAutoMineConfig.class, GsonConfigSerializer::new);
		SmartAutoMineCommands.register();

		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		// Default keys are K (regular) and L (place-mine), distinct from Smart Auto
		// Attack's J so all three can be bound without a clash if both mods are installed.
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.smartautomine.toggle",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_K,
				category
		));

		placeMineToggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.smartautomine.toggle_place_mine",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_L,
				category
		));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			if (!enabled) {
				return;
			}
			if (wasScriptedReconnect()) {
				// Smart Auto Reconnect just handled this - always resume, no matter
				// what "resume after manual reconnect" is set to.
				joinSettleTicksLeft = 60;
				return;
			}
			SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
			if (config.resumeAfterManualReconnect) {
				joinSettleTicksLeft = 60;
			} else {
				setEnabled(false, client);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleKey.consumeClick()) {
				handleToggle(false, client);
			}
			while (placeMineToggleKey.consumeClick()) {
				handleToggle(true, client);
			}
			if (joinSettleTicksLeft > 0) {
				joinSettleTicksLeft--;
				return;
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

	private static boolean wasScriptedReconnect() {
		try {
			Class<?> signalClass = Class.forName(RECONNECT_SIGNAL_CLASS);
			long timestamp = (long) signalClass.getField("lastAutoReconnectAtMillis").get(null);
			return timestamp > 0 && (System.currentTimeMillis() - timestamp) < RECONNECT_SIGNAL_WINDOW_MILLIS;
		} catch (Throwable t) {
			return false; // Smart Auto Reconnect not installed, or any reflection issue - treat as manual
		}
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
	private static void handleToggle(boolean requestedPlaceMine, Minecraft client) {
		if (enabled && placeMineActive == requestedPlaceMine) {
			setEnabled(false, client);
		} else {
			placeMineActive = requestedPlaceMine;
			setEnabled(true, client);
		}
	}

	public static void setEnabled(boolean value, Minecraft client) {
		enabled = value;
		AutoMineLogic.reset();
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		String modeLabel = placeMineActive ? " (place-mine)" : "";
		FeedbackUtil.send(client, config, value ? "Smart Auto Mine: ON" + modeLabel : "Smart Auto Mine: OFF");
	}
}
