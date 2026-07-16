package link.e4all.mundosync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import link.e4all.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MundoSyncManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-mundosync");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String LOCK_FILE = "em_uso.lock";
    private static final long LOCK_DURATION_HOURS = 2;
    private static MundoSyncManager instance;
    private Process currentProcess;
    private boolean isRunning = false;

    public enum SyncDirection { UPLOAD, DOWNLOAD }

    public interface SyncCallback {
        void onProgress(int percent, String status);
        void onComplete(boolean success, String message);
    }

    public static class LockInfo {
        public String host;
        public String expiraEm;
        public String criadoEm;
        public LockInfo() {}
        public LockInfo(String host, String expiraEm, String criadoEm) {
            this.host = host; this.expiraEm = expiraEm; this.criadoEm = criadoEm;
        }
        public boolean isExpired() {
            try { return Instant.now().isAfter(Instant.parse(expiraEm)); }
            catch (Exception e) { return true; }
        }
    }

    private MundoSyncManager() {}

    public static synchronized MundoSyncManager getInstance() {
        if (instance == null) instance = new MundoSyncManager();
        return instance;
    }

    public boolean checkRclone() {
        try {
            ProcessBuilder pb = new ProcessBuilder(findRclone(), "version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0) {
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                LOGGER.info("rclone: {}", out.lines().findFirst().orElse("?"));
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("rclone nao encontrado: {}", e.getMessage());
            return false;
        }
    }

    private String findRclone() {
        String custom = Config.INSTANCE.mundoSyncRclonePath.value();
        if (custom != null && !custom.isEmpty()) {
            File f = new File(custom);
            if (f.exists() && f.canExecute()) return custom;
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String name = os.contains("win") ? "rclone.exe" : "rclone";
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(File.pathSeparator)) {
                File f = new File(dir, name);
                if (f.exists() && f.canExecute()) return f.getAbsolutePath();
            }
        }
        return name;
    }

    public CompletableFuture<Boolean> setupGoogleDrive() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder(findRclone(), "lsd", "gdrive:");
                pb.redirectErrorStream(true);
                Process p1 = pb.start();
                if (p1.waitFor(5, TimeUnit.SECONDS) && p1.exitValue() == 0) {
                    LOGGER.info("gdrive ja configurado.");
                    return true;
                }
                LOGGER.info("Configurando gdrive...");
                pb = new ProcessBuilder(findRclone(), "config", "create",
                        "gdrive", "drive", "config_is_local=false");
                pb.redirectErrorStream(true);
                Process p2 = pb.start();
                boolean p2Done = p2.waitFor(30, TimeUnit.SECONDS);
                if (p2Done && p2.exitValue() != 0) {
                    String err = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    LOGGER.error("Falha config gdrive: {}", err);
                    return false;
                }
                if (!p2Done) {
                    LOGGER.error("Timeout ao configurar gdrive");
                    return false;
                }
                pb = new ProcessBuilder(findRclone(), "lsd", "gdrive:");
                Process p3 = pb.start();
                return p3.waitFor(10, TimeUnit.SECONDS) && p3.exitValue() == 0;
            } catch (Exception e) {
                LOGGER.error("Erro config gdrive: {}", e.getMessage());
                return false;
            }
        });
    }

    public CompletableFuture<Void> syncWorld(SyncDirection direction, SyncCallback callback) {
        return CompletableFuture.runAsync(() -> {
            if (isRunning) {
                callback.onComplete(false, "Ja existe sync em andamento.");
                return;
            }
            String remote = Config.INSTANCE.mundoSyncRemote.value();
            String worldPath = Config.INSTANCE.mundoSyncWorldPath.value();
            String worldName = Config.INSTANCE.mundoSyncWorldName.value();
            if (remote.isEmpty() || worldPath.isEmpty() || worldName.isEmpty()) {
                callback.onComplete(false, "Configure remote, caminho e nome.");
                return;
            }
            String source, dest;
            boolean removeLock = false;
            String username = Config.INSTANCE.mundoSyncUsername.value();
            if (direction == SyncDirection.DOWNLOAD) {
                source = remote;
                dest = worldPath + File.separator + worldName;
            } else {
                source = worldPath + File.separator + worldName;
                dest = remote;
                LockInfo lock = readRemoteLock();
                if (lock != null && lock.host.equals(username)) removeLock = true;
            }
            callback.onProgress(0, direction == SyncDirection.DOWNLOAD ? "Baixando..." : "Enviando...");
            try {
                isRunning = true;
                executeRcloneSync(source, dest, callback);
                if (removeLock) { removeRemoteLock(); removeLocalLock(); }
                callback.onComplete(true, "Sincronizacao concluida!");
            } catch (Exception e) {
                LOGGER.error("Erro sync: {}", e.getMessage());
                callback.onComplete(false, "Erro: " + e.getMessage());
            } finally { isRunning = false; }
        });
    }

    private void executeRcloneSync(String source, String dest, SyncCallback cb) throws Exception {
        List<String> cmd = new ArrayList<>(Arrays.asList(
                findRclone(), "sync", source, dest,
                "--progress", "--stats=1s", "--create-empty-src-dirs",
                "--exclude", LOCK_FILE));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        currentProcess = pb.start();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (Thread.currentThread().isInterrupted()) { currentProcess.destroy(); break; }
                parseProgress(line, cb);
            }
        }
        int code = currentProcess.waitFor();
        currentProcess = null;
        if (code != 0) throw new RuntimeException("rclone exit: " + code);
    }

    public boolean acquireLock() {
        try {
            String user = Config.INSTANCE.mundoSyncUsername.value();
            if (user.isEmpty()) { LOGGER.warn("Sem username"); return false; }
            LockInfo exist = readRemoteLock();
            if (exist != null && !exist.isExpired() && !exist.host.equals(user)) {
                LOGGER.warn("Lock com {}", exist.host); return false;
            }
            Path local = Path.of(Config.INSTANCE.mundoSyncWorldPath.value(),
                    Config.INSTANCE.mundoSyncWorldName.value(), LOCK_FILE);
            Files.createDirectories(local.getParent());
            Instant now = Instant.now();
            Files.writeString(local, GSON.toJson(new LockInfo(user,
                    now.plus(Duration.ofHours(LOCK_DURATION_HOURS)).toString(), now.toString())),
                    StandardCharsets.UTF_8);
            Process lockProcess = new ProcessBuilder(findRclone(), "copyto", local.toString(),
                    Config.INSTANCE.mundoSyncRemote.value() + "/" + LOCK_FILE)
                    .start();
            boolean ok = lockProcess.waitFor(10, TimeUnit.SECONDS) && lockProcess.exitValue() == 0;
            if (ok) LOGGER.info("Lock adquirido");
            return ok;
        } catch (Exception e) { LOGGER.error("Lock error: {}", e.getMessage()); return false; }
    }

    public LockInfo readRemoteLock() {
        try {
            ProcessBuilder pb = new ProcessBuilder(findRclone(), "cat",
                    Config.INSTANCE.mundoSyncRemote.value() + "/" + LOCK_FILE);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) return null;
            String json = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            return json.isEmpty() ? null : GSON.fromJson(json, LockInfo.class);
        } catch (Exception e) { return null; }
    }

    public void removeRemoteLock() {
        try { new ProcessBuilder(findRclone(), "deletefile",
                Config.INSTANCE.mundoSyncRemote.value() + "/" + LOCK_FILE).start();
        } catch (Exception e) { LOGGER.warn("Erro remove remote lock"); }
    }

    public void removeLocalLock() {
        try { Files.deleteIfExists(Path.of(Config.INSTANCE.mundoSyncWorldPath.value(),
                Config.INSTANCE.mundoSyncWorldName.value(), LOCK_FILE));
        } catch (Exception e) { LOGGER.warn("Erro remove local lock"); }
    }

    public LockInfo getActiveLock() {
        LockInfo l = readRemoteLock();
        return (l != null && !l.isExpired()) ? l : null;
    }

    private void parseProgress(String line, SyncCallback cb) {
        var m = java.util.regex.Pattern.compile(
                "Transferred:\\s+[\\d.]+\\s*\\w+\\s*/\\s*[\\d.]+\\s*\\w+,\\s*(\\d+)%"
        ).matcher(line);
        if (m.find()) cb.onProgress(Integer.parseInt(m.group(1)), m.group(1) + "%");
    }

    public void cancelSync() {
        if (currentProcess != null && currentProcess.isAlive()) currentProcess.destroy();
        currentProcess = null;
        isRunning = false;
    }

    public boolean isSyncing() { return isRunning; }

    public String detectLocalIp() {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 80);
            return s.getLocalAddress().getHostAddress();
        } catch (Exception e) {
            try { return InetAddress.getLocalHost().getHostAddress(); }
            catch (Exception ex) { return "127.0.0.1"; }
        }
    }

    public CompletableFuture<String> detectPublicIp() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpClient cl = HttpClient.newHttpClient();
                var req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.ipify.org"))
                        .timeout(Duration.ofSeconds(5)).GET().build();
                var resp = cl.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) return resp.body().trim();
            } catch (Exception e) { LOGGER.warn("IP fail: {}", e.getMessage()); }
            return detectLocalIp();
        });
    }

    public void notifyDiscord(String msg) {
        String url = Config.INSTANCE.mundoSyncWebhookUrl.value();
        if (url == null || url.isEmpty()) return;
        CompletableFuture.runAsync(() -> {
            try {
                String json = GSON.toJson(Map.of("content", msg));
                HttpClient cl = HttpClient.newHttpClient();
                cl.send(HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) { LOGGER.warn("Discord fail: {}", e.getMessage()); }
        });
    }

    public void detectAndSendIp() {
        String url = Config.INSTANCE.mundoSyncWebhookUrl.value();
        if (url == null || url.isEmpty()) { LOGGER.warn("Sem webhook"); return; }
        detectPublicIp().thenAccept(ip -> {
            notifyDiscord(String.format("🎮 **%s** hosteando **%s**!%n IP: `%s`",
                    Config.INSTANCE.mundoSyncUsername.value(),
                    Config.INSTANCE.mundoSyncWorldName.value(), ip));
            LOGGER.info("IP {} enviado.", ip);
        });
    }

    // ===== GitHub World Sync (Host Dinamico) =====
    
    public boolean initializeGithubSync(String githubToken) {
        return GithubWorldSync.getInstance().initialize(githubToken);
    }

    public CompletableFuture<Boolean> githubSyncUpload(String worldName, String worldPath) {
        return GithubWorldSync.getInstance().syncWorldToGitHub(worldName, worldPath);
    }

    public CompletableFuture<Boolean> githubSyncDownload(String worldName, String worldPath) {
        return GithubWorldSync.getInstance().syncWorldFromGitHub(worldName, worldPath);
    }

    public CompletableFuture<Boolean> githubHasUpdates(String worldName, String worldPath) {
        return GithubWorldSync.getInstance().hasRemoteUpdates(worldName, worldPath);
    }
}
