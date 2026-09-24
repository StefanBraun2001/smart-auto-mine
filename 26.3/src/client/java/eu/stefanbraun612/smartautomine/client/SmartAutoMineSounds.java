package eu.stefanbraun612.smartautomine.client;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

// Bundled custom sound events, registered on client init so they're resolvable by ID
// the same way vanilla sounds are (see SoundUtil.play's BuiltInRegistries lookup).
public class SmartAutoMineSounds {
	public static final SoundEvent AUTO_STOP = register("auto_stop");
	public static final SoundEvent DURABILITY_GONG = register("durability_gong");
	public static final SoundEvent DURABILITY_CRITICAL = register("durability_critical");

	public static void init() {
		// No-op - just forces this class (and its static SoundEvent registrations) to load.
	}

	private static SoundEvent register(String path) {
		Identifier id = Identifier.fromNamespaceAndPath(SmartAutoMineClient.MOD_ID, path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}
