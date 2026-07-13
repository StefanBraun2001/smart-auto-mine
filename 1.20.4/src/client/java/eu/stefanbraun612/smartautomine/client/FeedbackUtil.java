package eu.stefanbraun612.smartautomine.client;

import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class FeedbackUtil {
	public static void send(MinecraftClient client, SmartAutoMineConfig config, String message) {
		if (client.player == null || config.feedbackMode == SmartAutoMineConfig.FeedbackMode.SILENT) {
			return;
		}
		boolean actionBar = config.feedbackMode == SmartAutoMineConfig.FeedbackMode.ACTION_BAR;
		client.player.sendMessage(Text.of(message), actionBar);
	}
}
