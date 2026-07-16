package link.mundosync.commands;

import link.mundosync.Config;
import link.mundosync.MundoSyncManager;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MundoSyncCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("mundosync");
    private static final Minecraft mc = Minecraft.getInstance();
    
    public static void register() {
        // Commands will be registered in the client initializer
        LOGGER.info("MundoSync commands registered!");
    }
    
    public static String getConfigInfo() {
        return "=== MundoSync Config ===\n" +
               "Username: " + Config.username + "\n" +
               "World Path: " + Config.worldPath + "\n" +
               "World Name: " + Config.worldName + "\n" +
               "Webhook: " + (Config.webhookUrl.isEmpty() ? "<not configured>" : Config.webhookUrl) + "\n" +
               "GitHub Token: " + (Config.githubToken.isEmpty() ? "<not configured>" : Config.githubToken.substring(0, 8) + "...") + "\n" +
               "GitHub Enabled: " + Config.githubSyncEnabled;
    }
    
    public static String getStatus() {
        if (!Config.isConfigured()) {
            return "MundoSync: Not configured. Use /mundosync config to set up.";
        }
        
        return "MundoSync: Configured\n" +
               "World: " + Config.worldName + "\n" +
               "GitHub: " + (Config.githubSyncEnabled ? "Enabled" : "Disabled");
    }
}