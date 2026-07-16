package link.mundosync;

import link.mundosync.github.GithubWorldSync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MundoSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("mundosync");
    private static MundoSyncManager instance;
    private final GithubWorldSync githubSync;
    
    public MundoSyncManager() {
        this.githubSync = new GithubWorldSync();
    }
    
    public static MundoSyncManager getInstance() {
        if (instance == null) {
            instance = new MundoSyncManager();
        }
        return instance;
    }
    
    public void initializeGithubSync(String token) {
        githubSync.initialize(token);
    }
    
    public List<String> listAvailableWorlds() {
        return githubSync.listWorlds();
    }
    
    public boolean githubSyncUpload(String worldName, String worldPath) {
        return githubSync.uploadWorld(worldName, worldPath);
    }
    
    public boolean githubSyncDownload(String worldName, String worldPath) {
        return githubSync.downloadWorld(worldName, worldPath);
    }
    
    public String getGithubToken() {
        return Config.githubToken;
    }
    
    public String getWorldName() {
        return Config.worldName;
    }
    
    public String getWorldPath() {
        return Config.worldPath;
    }
}