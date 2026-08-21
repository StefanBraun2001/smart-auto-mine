package eu.stefanbraun612.smartautomine.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.stefanbraun612.smartautomine.client.config.SmartAutoMineConfigScreen;

public class SmartAutoMineModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return SmartAutoMineConfigScreen::build;
	}
}
