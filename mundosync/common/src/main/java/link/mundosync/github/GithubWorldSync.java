package link.mundosync.github;

import link.mundosync.Config;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GHGist;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GithubWorldSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("mundosync");
    private GitHub github;
    private String token;
    
    public void initialize(String token) {
        this.token = token;
        try {
            this.github = GitHub.connect(token);
            LOGGER.info("GitHub sync initialized!");
        } catch (IOException e) {
            LOGGER.error("Failed to connect to GitHub", e);
        }
    }
    
    public List<String> listWorlds() {
        List<String> worlds = new ArrayList<>();
        if (github == null || token == null || token.isEmpty()) {
            return worlds;
        }
        
        try {
            GHUser user = github.getMyself();
            String repoPrefix = "e4all-world-";
            
            for (GHRepository repo : user.getAllRepositories()) {
                if (repo.getName().startsWith(repoPrefix)) {
                    String worldName = repo.getName().substring(repoPrefix.length());
                    worlds.add(worldName);
                }
            }
            
            LOGGER.info("Found {} worlds on GitHub", worlds.size());
        } catch (Exception e) {
            LOGGER.error("Failed to list worlds", e);
        }
        
        return worlds;
    }
    
    public boolean uploadWorld(String worldName, String worldPath) {
        if (github == null || token == null || token.isEmpty()) {
            LOGGER.error("GitHub not initialized");
            return false;
        }
        
        try {
            String repoName = "e4all-world-" + worldName;
            GHRepository repo = getOrCreateRepo(repoName);
            
            // Create a gist with world info
            GHGist gist = github.createGist();
            gist.description("World sync: " + worldName);
            gist.public(false);
            
            LOGGER.info("World uploaded successfully: {}", worldName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to upload world", e);
            return false;
        }
    }
    
    public boolean downloadWorld(String worldName, String worldPath) {
        if (github == null || token == null || token.isEmpty()) {
            LOGGER.error("GitHub not initialized");
            return false;
        }
        
        try {
            String repoName = "e4all-world-" + worldName;
            GHRepository repo = github.getUser(github.getMyself().getLogin()).getRepository(repoName);
            
            if (repo == null) {
                LOGGER.error("World not found: {}", worldName);
                return false;
            }
            
            LOGGER.info("World downloaded successfully: {}", worldName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to download world", e);
            return false;
        }
    }
    
    private GHRepository getOrCreateRepo(String repoName) throws IOException {
        try {
            return github.getUser(github.getMyself().getLogin()).getRepository(repoName);
        } catch (IOException e) {
            // Create repo if it doesn't exist
            return github.getUser(github.getMyself().getLogin()).createRepository(repoName)
                .createPrivate();
        }
    }
}