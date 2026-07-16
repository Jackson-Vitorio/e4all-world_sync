package link.mundosync.fabric;

import link.mundosync.Config;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MundoSyncClientFabric implements ClientModInitializer {
    public static final String MOD_ID = "mundosync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing MundoSync client...");
        
        // Initialize config
        Config.init();
        
        // Register commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Commands will be registered here
        });
        
        LOGGER.info("MundoSync client initialized!");
    }
}