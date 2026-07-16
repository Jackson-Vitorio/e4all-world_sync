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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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
            // Testa a autenticação e obtém o username real
            var myself = github.getMyself();
            LOGGER.info("GitHub World Sync inicializado! Usuário: {}", myself.getLogin());
            this.initialized = true;
            return true;
        } catch (IOException e) {
            LOGGER.error("Falha ao inicializar GitHub: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Inicializa automaticamente se o token estiver no config
     */
    public boolean initializeFromConfig() {
        if (initialized) return true;
        
        String token = Config.INSTANCE.githubToken.value();
        if (token == null || token.isEmpty()) {
            LOGGER.warn("Token GitHub nao configurado");
            return false;
        }
        
        return initialize(token);
    }

    /**
     * Cria ou obtem um repositorio para o mundo
     */
    public CompletableFuture<String> getOrCreateWorldRepo(String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String repoName = REPO_PREFIX + worldName.toLowerCase().replaceAll("[^a-z0-9-]", "-");
                
                // Obtém o username real do GitHub autenticado
                String githubUser;
                try {
                    githubUser = github.getMyself().getLogin();
                } catch (Exception e) {
                    LOGGER.error("Nao foi possivel obter usuario GitHub: {}", e.getMessage());
                    return null;
                }
                
                String fullRepoName = githubUser + "/" + repoName;
                LOGGER.info("Buscando repositorio: {}", fullRepoName);
                
                // Tenta buscar o repositorio existente
                try {
                    var repo = github.getRepository(fullRepoName);
                    LOGGER.info("Repositorio encontrado: {}", repo.getFullName());
                    return repo.getFullName();
                } catch (org.kohsuke.github.GHFileNotFoundException e) {
                    LOGGER.info("Repositorio nao encontrado (404), criando novo...");
                } catch (Exception e) {
                    LOGGER.warn("Erro ao buscar repositorio: {}", e.getMessage());
                }
                
                // Cria novo repositorio privado
                try {
                    var repo = github.createRepository(repoName)
                        .private_(true)
                        .create();
                    
                    LOGGER.info("Repositorio criado: {}", repo.getFullName());
                    return repo.getFullName();
                } catch (Exception e) {
                    String errMsg = e.getMessage();
                    LOGGER.error("Erro ao criar repositorio: {}", errMsg);
                    
                    // Se o erro for "name already exists", tenta buscar novamente
                    if (errMsg != null && errMsg.contains("name already exists")) {
                        try {
                            var repo = github.getRepository(fullRepoName);
                            LOGGER.info("Repositorio ja existia, encontrado: {}", repo.getFullName());
                            return repo.getFullName();
                        } catch (Exception e2) {
                            LOGGER.error("Nao foi possivel encontrar o repositorio existente", e2);
                            return null;
                        }
                    }
                    return null;
                }
                
            } catch (Exception e) {
                LOGGER.error("Erro ao criar/obter repositorio: {}", e.getMessage(), e);
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
                LOGGER.info("Iniciando upload do mundo: {}", worldName);
                String repoFullName = getOrCreateWorldRepo(worldName).get();
                LOGGER.info("Repositorio: {}", repoFullName);
                
                if (repoFullName == null) {
                    LOGGER.error("Nao foi possivel obter/criar repositorio");
                    return false;
                }

                File worldDir = new File(worldPath, worldName);
                LOGGER.info("Diretorio do mundo: {}", worldDir.getAbsolutePath());
                
                if (!worldDir.exists() || !worldDir.isDirectory()) {
                    LOGGER.error("Diretorio do mundo nao encontrado: {}", worldDir);
                    return false;
                }

                // Lista arquivos do mundo
                LOGGER.info("Arquivos no mundo: {}",
                    Arrays.stream(worldDir.listFiles()).map(File::getName).toList());

                // Cria/abre repositorio Git local
                Path gitDir = Paths.get(worldDir.getAbsolutePath(), ".git");
                Git git;
                
                try {
                    // Sempre atualiza o .gitignore (para novos ou existentes)
                    String gitignore = """
                        *.lock
                        session.lock
                        .gitignore
                        voxy/
                        voxelmap/
                        journeymap/
                        xaero*
                        gravestones/
                        backups/
                        resourcepacks/
                        shaderpacks/
                        servers.dat
                        options.txt
                        hotbar.nbt
                        """;
                    Files.write(Paths.get(worldDir.getAbsolutePath(), ".gitignore"), gitignore.getBytes());
                    
                    if (Files.exists(gitDir)) {
                        LOGGER.info("Abrindo repositorio Git existente...");
                        git = Git.open(worldDir);
                    } else {
                        LOGGER.info("Inicializando novo repositorio Git...");
                        git = Git.init().setDirectory(worldDir).call();
                        
                        LOGGER.info("Adicionando arquivos iniciais...");
                        git.add().addFilepattern(".").call();
                        git.commit().setMessage("Initial commit - e4all world sync").call();
                        LOGGER.info("Commit inicial criado");
                    }
                } catch (Exception e) {
                    LOGGER.error("Erro ao inicializar Git local: {}", e.getMessage(), e);
                    return false;
                }

                // Adiciona todas as mudancas, ignorando erros de arquivos que sumiram
                LOGGER.info("Adicionando arquivos...");
                try {
                    git.add().addFilepattern(".").call();
                } catch (org.eclipse.jgit.api.errors.JGitInternalException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof FileNotFoundException) {
                        LOGGER.warn("Arquivo sumiu durante add (ignorando): {}", cause.getMessage());
                        // Tenta add novamente - provavelmente outro arquivo vai falhar
                        // Mas arquivos que sumiram não são mais um problema
                        Thread.sleep(100);
                        git.add().addFilepattern(".").call();
                    } else {
                        throw e;
                    }
                }
                
                // Commit
                LOGGER.info("Criando commit...");
                git.commit().setMessage("Sync: " + System.currentTimeMillis()).call();
                LOGGER.info("Commit criado");
                
                // Push para GitHub
                LOGGER.info("Fazendo push para GitHub...");
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
     * Otimizado: usa fetch + reset ao invés de clone completo
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
                String remoteUrl = "https://github.com/" + repoFullName + ".git";
                long startTime = System.currentTimeMillis();
                
                // Se já existe repositório local, atualiza apenas diferenças
                if (Files.exists(gitDir) && worldDir.exists()) {
                    LOGGER.info("Atualizando repositorio existente (apenas diferencas)...");
                    Git git = Git.open(worldDir);
                    
                    // Configura remote se necessário
                    try {
                        git.remoteSetUrl()
                            .setRemoteName("origin")
                            .setRemoteUri(new org.eclipse.jgit.transport.URIish(remoteUrl))
                            .call();
                    } catch (Exception e) {
                        git.remoteAdd()
                            .setName("origin")
                            .setUri(new org.eclipse.jgit.transport.URIish(remoteUrl))
                            .call();
                    }
                    
                    // Sincroniza com GitHub (fetch + reset hard)
                    LOGGER.info("Sincronizando com GitHub...");
                    try {
                        git.fetch()
                            .setRemote("origin")
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                            .call();
                        
                        // Detecta a branch padrão do repositório
                        String defaultBranch = "main";
                        try {
                            var repo = github.getRepository(repoFullName);
                            defaultBranch = repo.getDefaultBranch();
                            LOGGER.info("Branch padrão do repositório: {}", defaultBranch);
                        } catch (Exception e) {
                            LOGGER.warn("Não foi possível detectar branch padrão, usando 'main'");
                        }
                        
                        // Reset hard para a branch padrão
                        git.reset()
                            .setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
                            .setRef("origin/" + defaultBranch)
                            .call();
                    } catch (Exception e) {
                        LOGGER.warn("Fetch/Reset falhou, fazendo clone completo...", e);
                        // Se falhar, remove e clona novamente
                        git.close();
                        deleteDirectory(worldDir);
                        worldDir.mkdirs();
                        Git.cloneRepository()
                            .setURI(remoteUrl)
                            .setDirectory(worldDir)
                            .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                            .call();
                    }
                    
                    long duration = System.currentTimeMillis() - startTime;
                    LOGGER.info("Mundo atualizado em {}ms (apenas diferencas)", duration);
                    return true;
                }
                
                // Se não existe .git, remove diretório (se existir) e clona
                if (worldDir.exists()) {
                    LOGGER.info("Removendo diretorio existente para clone...");
                    deleteDirectory(worldDir);
                    worldDir.mkdirs();
                }
                
                // Clona completo
                LOGGER.info("Clonando repositorio completo {} em {}", remoteUrl, worldDir);
                Git.cloneRepository()
                    .setURI(remoteUrl)
                    .setDirectory(worldDir)
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider(token, ""))
                    .call();
                
                long duration = System.currentTimeMillis() - startTime;
                LOGGER.info("Mundo clonado do GitHub em {}ms: {}", duration, worldName);
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

    /**
     * Lista todos os mundos disponiveis no GitHub (repositorios com prefixo e4all-world-)
     */
    public CompletableFuture<List<String>> listAvailableWorlds() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!initialized || github == null) {
                    LOGGER.warn("GitHub nao inicializado");
                    return List.of();
                }
                
                var myself = github.getMyself();
                var repos = myself.listRepositories().toList();
                List<String> worlds = new ArrayList<>();
                
                for (var repo : repos) {
                    String name = repo.getName();
                    if (name.startsWith(REPO_PREFIX)) {
                        String worldName = name.substring(REPO_PREFIX.length());
                        worlds.add(worldName);
                    }
                }
                
                LOGGER.info("Mundos encontrados no GitHub: {}", worlds);
                return worlds;
            } catch (Exception e) {
                LOGGER.error("Erro ao listar mundos: {}", e.getMessage());
                return List.of();
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

    private boolean isDirectoryEmpty(File dir) {
        if (dir == null || !dir.exists()) return true;
        File[] files = dir.listFiles();
        return files == null || files.length == 0;
    }

    private void copyDirectory(File source, File dest) {
        if (source == null || !source.exists()) return;
        if (!dest.exists()) dest.mkdirs();
        File[] files = source.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equals(".git")) continue;
                File destFile = new File(dest, f.getName());
                if (f.isDirectory()) {
                    copyDirectory(f, destFile);
                } else {
                    try {
                        Files.copy(f.toPath(), destFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception e) {
                        LOGGER.warn("Erro ao copiar {}: {}", f.getName(), e.getMessage());
                    }
                }
            }
        }
    }

    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                } else {
                    f.delete();
                }
            }
        }
        dir.delete();
    }
}
