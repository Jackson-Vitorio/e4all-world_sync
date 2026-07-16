package link.e4all.mundosync;

import link.e4all.Config;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Sistema de sincronizacao de mundos via GitHub para host dinamico.
 * Permite que multiplos jogadores hosteiem o mesmo mundo e sincronizem automaticamente.
 */
public class GithubWorldSync {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-github-sync");
    private static final String REPO_PREFIX = "e4all-world-";
    private static GithubWorldSync instance;
    private GitHub github;
    private String token;
    private boolean initialized = false;

    private GithubWorldSync() {}

    public static synchronized GithubWorldSync getInstance() {
        if (instance == null) instance = new GithubWorldSync();
        return instance;
    }

    public boolean initialize(String githubToken) {
        if (initialized && github != null) return true;
        
        this.token = githubToken;
        try {
            this.github = new GitHubBuilder().withOAuthToken(token).build();
            this.initialized = true;
            LOGGER.info("GitHub World Sync inicializado com sucesso!");
            return true;
        } catch (IOException e) {
            LOGGER.error("Falha ao inicializar GitHub: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Cria ou obtem um repositorio para o mundo
     */
    public CompletableFuture<String> getOrCreateWorldRepo(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String repoName = REPO_PREFIX + worldName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
                
                // Tenta buscar o repositorio existente
                try {
                    var repo = github.getRepository(Config.INSTANCE.mundoSyncUsername.value() + "/" + repoName);
                    LOGGER.info("Repositorio encontrado: {}", repo.getFullName());
                    return repo.getFullName();
                } catch (Exception e) {
                    LOGGER.info("Repositorio nao encontrado, criando...");
                }
                
                // Cria novo repositorio privado
                var repo = github.createRepository(repoName)
                    .private_(true)
                    .create();
                
                LOGGER.info("Repositorio criado: {}", repo.getFullName());
                return repo.getFullName();
                
            } catch (Exception e) {
                LOGGER.error("Erro ao criar/obter repositorio: {}", e.getMessage());
                return null;
            }
        });
    }

    /**
     * Sincroniza o mundo local para o GitHub (upload)
     */
    public CompletableFuture<Boolean> syncWorldToGitHub(String worldName, String worldPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                LOGGER.error("GitHub World Sync nao inicializado");
                return false;
            }

            try {
                String repoFullName = getOrCreateWorldRepo(worldName).get();
                if (repoFullName == null) {
                    LOGGER.error("Nao foi possivel obter/criar repositorio");
                    return false;
                }

                File worldDir = new File(worldPath, worldName);
                if (!worldDir.exists() || !worldDir.isDirectory()) {
                    LOGGER.error("Diretorio do mundo nao encontrado: {}", worldDir);
                    return false;
                }

                // Cria/abre repositorio Git local
                Path gitDir = Paths.get(worldDir.getAbsolutePath(), ".git");
                Git git;
                
                if (Files.exists(gitDir)) {
                    git = Git.open(worldDir);
                } else {
                    git = Git.init().setDirectory(worldDir).call();
                    
                    // Cria .gitignore
                    String gitignore = """
                        *.lock
                        session.lock
                        .gitignore
                        """;
                    Files.write(Paths.get(worldDir.getAbsolutePath(), ".gitignore"), gitignore.getBytes());
                    
                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Initial commit - e4all world sync").call();
                }

                // Adiciona todas as mudancas
                git.add().addFilepattern(".").call();
                
                // Commit
                git.commit().setMessage("Sync: " + System.currentTimeMillis()).call();
                
                // Push para GitHub
                String[] parts = repoFullName.split("/");
                String username = parts[0];
                String repo = parts[1];
                
                git.push()
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .setRemote("https://github.com/" + repoFullName + ".git")
                    .call();
                
                LOGGER.info("Mundo sincronizado com sucesso: {}", worldName);
                return true;
                
            } catch (Exception e) {
                LOGGER.error("Erro ao sincronizar mundo: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Sincroniza o mundo do GitHub para local (download)
     */
    public CompletableFuture<Boolean> syncWorldFromGitHub(String worldName, String worldPath) {
        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) {
                LOGGER.error("GitHub World Sync nao inicializado");
                return false;
            }

            try {
                String repoFullName = getOrCreateWorldRepo(worldName).get();
                if (repoFullName == null) {
                    LOGGER.error("Nao foi possivel obter/criar repositorio");
                    return false;
                }

                File worldDir = new File(worldPath, worldName);
                Path gitDir = Paths.get(worldDir.getAbsolutePath(), ".git");
                
                // Se o mundo ja existe localmente, faz pull
                if (Files.exists(gitDir) && worldDir.exists()) {
                    Git git = Git.open(worldDir);
                    git.pull()
                        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                        .setRemote("https://github.com/" + repoFullName + ".git")
                        .call();
                    
                    LOGGER.info("Mundo atualizado do GitHub: {}", worldName);
                    return true;
                }
                
                // Se nao existe, clona
                if (!worldDir.exists()) {
                    worldDir.mkdirs();
                }
                
                Git.cloneRepository()
                    .setURI("https://github.com/" + repoFullName + ".git")
                    .setDirectory(worldDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();
                
                LOGGER.info("Mundo clonado do GitHub: {}", worldName);
                return true;
                
            } catch (Exception e) {
                LOGGER.error("Erro ao sincronizar mundo do GitHub: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Verifica se ha atualizacoes no GitHub
     */
    public CompletableFuture<Boolean> hasRemoteUpdates(String worldName, String worldPath) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                File worldDir = new File(worldPath, worldName);
                if (!Files.exists(Paths.get(worldDir.getAbsolutePath(), ".git"))) {
                    return false;
                }
                
                Git git = Git.open(worldDir);
                var status = git.status().call();
                
                // Se tem arquivos modificados, precisa sincronizar
                return !status.getModified().isEmpty() || !status.getAdded().isEmpty();
                
            } catch (Exception e) {
                LOGGER.error("Erro ao verificar atualizacoes: {}", e.getMessage());
                return false;
            }
        });
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void shutdown() {
        initialized = false;
        github = null;
        token = null;
    }
}