package eu.stefanbraun612.smartautomine.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class SoundUtil {
	// Invalid/unknown sound event IDs simply play nothing rather than erroring.
	// volume is explicit because forUI's two-argument overload silently plays at 0.25.
	public static void play(Minecraft client, String soundId, float volume) {
		Identifier id = Identifier.tryParse(soundId);
		if (id == null) {
			return;
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
		if (sound == null) {
			return;
		}
		client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, volume));
	}
}
