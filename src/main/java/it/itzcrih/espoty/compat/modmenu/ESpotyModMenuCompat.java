package it.itzcrih.espoty.compat.modmenu;

import it.itzcrih.espoty.gui.screen.SpotifyScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ESpotyModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SpotifyScreen();
    }
}
