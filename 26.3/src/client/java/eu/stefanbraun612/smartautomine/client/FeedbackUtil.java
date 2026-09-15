package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class FeedbackUtil {
	public static void send(Minecraft client, SmartAutoMineConfig config, String message) {
		if (client.player == null || config.feedbackMode == SmartAutoMineConfig.FeedbackMode.SILENT) {
			return;
		}
		if (config.feedbackMode == SmartAutoMineConfig.FeedbackMode.ACTION_BAR) {
			client.gui.hud.setOverlayMessage(Component.literal(message), false);
		} else {
			client.player.sendSystemMessage(Component.literal(message));
		}
	}
}
