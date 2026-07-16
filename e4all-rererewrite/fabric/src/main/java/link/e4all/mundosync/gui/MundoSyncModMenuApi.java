package link.e4all.mundosync.gui;

import com.terraformersmc.modmenu.api.ModMenuApi;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import net.minecraft.client.gui.screens.Screen;

public class MundoSyncModMenuApi implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return (previous) -> new MundoSyncScreen(previous);
    }
}