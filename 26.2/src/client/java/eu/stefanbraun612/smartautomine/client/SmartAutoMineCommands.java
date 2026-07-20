package eu.stefanbraun612.smartautomine.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class SmartAutoMineCommands {
	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
				ClientCommands.literal("smartautomine")
						.then(ClientCommands.literal("preset")
								.then(ClientCommands.literal("list")
										.executes(ctx -> listPresets(ctx.getSource())))
								.then(ClientCommands.literal("apply")
										.then(ClientCommands.<String>argument("name", StringArgumentType.word())
												.executes(ctx -> applyPreset(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
								.then(ClientCommands.literal("save")
										.then(ClientCommands.<String>argument("name", StringArgumentType.word())
												.executes(ctx -> savePreset(ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
								.then(ClientCommands.literal("delete")
										.then(ClientCommands.<String>argument("name", StringArgumentType.word())
												.executes(ctx -> deletePreset(ctx.getSource(), StringArgumentType.getString(ctx, "name"))))))));
	}

	private static int listPresets(FabricClientCommandSource source) {
		Map<String, MinePreset> presets = PresetManager.all();
		if (presets.isEmpty()) {
			source.sendFeedback(Component.literal("Smart Auto Mine: no presets saved."));
			return 1;
		}
		source.sendFeedback(Component.literal("Smart Auto Mine presets: " + String.join(", ", presets.keySet())));
		return 1;
	}

	private static int applyPreset(FabricClientCommandSource source, String name) {
		MinePreset preset = PresetManager.get(name);
		if (preset == null) {
			source.sendError(Component.literal("Smart Auto Mine: no preset named '" + name + "'."));
			return 0;
		}
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		preset.applyTo(config);
		AutoConfig.getConfigHolder(SmartAutoMineConfig.class).save();
		source.sendFeedback(Component.literal("Smart Auto Mine: applied preset '" + name + "'."));
		return 1;
	}

	private static int savePreset(FabricClientCommandSource source, String name) {
		SmartAutoMineConfig config = AutoConfig.getConfigHolder(SmartAutoMineConfig.class).getConfig();
		PresetManager.save(name, MinePreset.fromConfig(config));
		source.sendFeedback(Component.literal("Smart Auto Mine: saved current settings as preset '" + name + "'."));
		return 1;
	}

	private static int deletePreset(FabricClientCommandSource source, String name) {
		if (PresetManager.delete(name)) {
			source.sendFeedback(Component.literal("Smart Auto Mine: deleted preset '" + name + "'."));
			return 1;
		}
		source.sendError(Component.literal("Smart Auto Mine: no preset named '" + name + "'."));
		return 0;
	}
}
