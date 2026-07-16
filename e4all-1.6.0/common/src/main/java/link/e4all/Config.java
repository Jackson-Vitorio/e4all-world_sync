package link.e4all;

import folk.sisby.kaleido.api.ReflectiveConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue;

public class Config extends ReflectiveConfig {
    public static final Config INSTANCE = Config.createToml(Agnos.configDir(), "e4all", "e4all", Config.class);

    @Comment("Whether to use the broker to get the best relay based on location or use a hard-coded relay.")
    public final TrackedValue<Boolean> useBroker = this.value(true);
    public final TrackedValue<String> brokerUrl = this.value("https://broker.e4mc.link/getBestRelay");    
    public final TrackedValue<String> relayHost = this.value("test.e4mc.link");
    public final TrackedValue<Integer> relayPort = this.value(25575);

    @Comment("Allows use of certain dedicated server commands such as /ban and /whitelist")
    public final TrackedValue<Boolean> restoreDedicatedCommands = this.value(true);
    @Comment("Whether to use whitelists on LAN worlds")
    public final TrackedValue<Boolean> useWhiteList = this.value(false);

    @Comment("Whether to enable sharing LAN worlds with e4all")
    public final TrackedValue<Boolean> hostEnabled = this.value(true);
    @Comment("Whether to enable Dialtone peer-to-peer connections as the host")
    public final TrackedValue<Boolean> dialtoneHostEnabled = this.value(true);
    @Comment("Whether to enable Dialtone peer-to-peer connections as the player")
    public final TrackedValue<Boolean> dialtonePlayerEnabled = this.value(true);
    @Comment("The URL to get the list of Iroh relays to use")
    public final TrackedValue<String> dialtoneRelayMap = this.value("https://natives.e4mc.link/relaymap.json");
    @Comment("Whether to hide direct IP addresses from the relay")
    public final TrackedValue<Boolean> dialtoneSanitizeTicket = this.value(true);

    @Comment("Whether to enable offline mode (disables Microsoft authentication for ALL LAN connections, including both tunneled and direct). Toggle via the 'Online Mode' button on the Open to LAN screen.")
    public final TrackedValue<Boolean> offlineMode = this.value(false);
    @Comment("Whether the offline mode warning has already been shown to the user")
    public final TrackedValue<Boolean> offlineWarningShown = this.value(false);

    @Comment("Whether to enable the Simple Voice Chat bridge (tunnels voice chat UDP through the e4all connection)")
    public final TrackedValue<Boolean> voiceChatBridgeEnabled = this.value(true);

    // ==================== MUNDO SYNC CONFIG ====================

    @Comment("Whether MundoSync world synchronization is enabled")
    public final TrackedValue<Boolean> mundoSyncEnabled = this.value(false);

    @Comment("Your username/nickname for lock identification")
    public final TrackedValue<String> mundoSyncUsername = this.value("");

    @Comment("Path to .minecraft directory")
    public final TrackedValue<String> mundoSyncMinecraftDir = this.value("");

    @Comment("Path to saves directory (e.g., /home/user/.minecraft/saves)")
    public final TrackedValue<String> mundoSyncWorldPath = this.value("");

    @Comment("Name of the world folder (e.g., 'world' or 'My World')")
    public final TrackedValue<String> mundoSyncWorldName = this.value("");

    @Comment("Rclone remote path (e.g., gdrive:MinecraftMundo)")
    public final TrackedValue<String> mundoSyncRemote = this.value("");

    @Comment("Discord webhook URL for notifications (optional)")
    public final TrackedValue<String> mundoSyncWebhookUrl = this.value("");

    @Comment("Custom path to rclone executable (optional, auto-detected if empty)")
    public final TrackedValue<String> mundoSyncRclonePath = this.value("");

    @Comment("Custom regex pattern for file filtering (optional)")
    public final TrackedValue<String> mundoSyncRegex = this.value("");

    // ==================== GITHUB WORLD SYNC CONFIG ====================

    @Comment("GitHub Personal Access Token for world synchronization (optional)")
    public final TrackedValue<String> githubToken = this.value("");

    @Comment("Whether GitHub World Sync is enabled")
    public final TrackedValue<Boolean> githubSyncEnabled = this.value(false);
}
