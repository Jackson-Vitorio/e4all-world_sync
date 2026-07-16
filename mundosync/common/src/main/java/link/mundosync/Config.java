package link.mundosync;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

public class Config {
    private static final Logger LOGGER = LoggerFactory.getLogger("mundosync");
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/mundosync.properties");
    private static final Properties CONFIG = new Properties();
    
    // MundoSync Config
    public static String username = "";
    public static String worldPath = "";
    public static String worldName = "";
    public static String webhookUrl = "";
    
    // GitHub Config
    public static String githubToken = "";
    public static boolean githubSyncEnabled = false;
    
    public static void init() {
        loadConfig();
    }
    
    private static void loadConfig() {
        try {
            if (CONFIG_FILE.exists()) {
                CONFIG.load(new FileInputStream(CONFIG_FILE));
                username = CONFIG.getProperty("username", "");
                worldPath = CONFIG.getProperty("worldPath", "");
                worldName = CONFIG.getProperty("worldName", "");
                webhookUrl = CONFIG.getProperty("webhookUrl", "");
                githubToken = CONFIG.getProperty("githubToken", "");
                githubSyncEnabled = Boolean.parseBoolean(CONFIG.getProperty("githubSyncEnabled", "false"));
                LOGGER.info("MundoSync config loaded!");
            } else {
                saveConfig();
                LOGGER.info("MundoSync config created!");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load MundoSync config", e);
        }
    }
    
    public static void saveConfig() {
        try {
            CONFIG.setProperty("username", username);
            CONFIG.setProperty("worldPath", worldPath);
            CONFIG.setProperty("worldName", worldName);
            CONFIG.setProperty("webhookUrl", webhookUrl);
            CONFIG.setProperty("githubToken", githubToken);
            CONFIG.setProperty("githubSyncEnabled", String.valueOf(githubSyncEnabled));
            
            CONFIG_FILE.getParentFile().mkdirs();
            CONFIG.store(new FileOutputStream(CONFIG_FILE), "MundoSync Configuration");
            LOGGER.info("MundoSync config saved!");
        } catch (Exception e) {
            LOGGER.error("Failed to save MundoSync config", e);
        }
    }
    
    public static boolean isConfigured() {
        return !worldPath.isEmpty() && !worldName.isEmpty();
    }
}