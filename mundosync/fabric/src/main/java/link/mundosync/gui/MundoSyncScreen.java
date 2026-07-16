package link.mundosync.gui;

import link.mundosync.Config;
import link.mundosync.MundoSyncManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class MundoSyncScreen extends Screen {
    private final Screen previous;
    private final MundoSyncManager manager;
    private Button upBtn, downBtn, refBtn, statusBtn;
    private volatile List<String> worlds = new CopyOnWriteArrayList<>();

    public MundoSyncScreen(Screen previous) {
        super(Component.literal("MundoSync"));
        this.previous = previous;
        this.manager = MundoSyncManager.getInstance();
    }

    @Override
    protected void init() {
        int cx = width / 2;
        statusBtn = addRenderableWidget(Button.builder(Component.literal("§7Ready"), b -> {}).bounds(cx - 155, 10, 310, 20).build());
        upBtn = addRenderableWidget(Button.builder(Component.literal("§eUpload"), b -> doUpload()).bounds(cx - 155, 35, 100, 20).build());
        downBtn = addRenderableWidget(Button.builder(Component.literal("§aDownload"), b -> doDownload()).bounds(cx - 50, 35, 100, 20).build());
        refBtn = addRenderableWidget(Button.builder(Component.literal("§bList"), b -> loadWorlds()).bounds(cx + 55, 35, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§cBack"), b -> minecraft.setScreen(previous)).bounds(cx - 50, height - 30, 100, 20).build());
        loadWorlds();
    }

    private void setStatus(String s) {
        statusBtn.setMessage(Component.literal(s));
    }

    private void loadWorlds() {
        setStatus("§eLoading...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(Config.githubToken);
                worlds = new CopyOnWriteArrayList<>(manager.listAvailableWorlds());
                setStatus("§a" + worlds.size() + " world(s)");
                rebuildWorldButtons();
            } catch (Exception e) {
                setStatus("§c" + e.getMessage());
            }
        });
    }

    private void rebuildWorldButtons() {
        clearWidgets();
        int cx = width / 2;
        Component currentStatus = statusBtn.getMessage();
        addRenderableWidget(statusBtn = Button.builder(currentStatus, b -> {}).bounds(cx - 155, 10, 310, 20).build());
        addRenderableWidget(upBtn = Button.builder(Component.literal("§eUpload"), b -> doUpload()).bounds(cx - 155, 35, 100, 20).build());
        addRenderableWidget(downBtn = Button.builder(Component.literal("§aDownload"), b -> doDownload()).bounds(cx - 50, 35, 100, 20).build());
        addRenderableWidget(refBtn = Button.builder(Component.literal("§bList"), b -> loadWorlds()).bounds(cx + 55, 35, 100, 20).build());
        
        for (int i = 0; i < worlds.size() && i < 10; i++) {
            final String w = worlds.get(i);
            int y = 65 + i * 22;
            addRenderableWidget(Button.builder(Component.literal("§7📁 " + w), b -> {
                setStatus("§b" + w + " selected");
                Config.worldName = w;
                Config.saveConfig();
            }).bounds(cx - 155, y, 310, 20).build());
        }
        
        addRenderableWidget(Button.builder(Component.literal("§cBack"), b -> minecraft.setScreen(previous)).bounds(cx - 50, height - 30, 100, 20).build());
    }

    private void setButtonsEnabled(boolean enabled) {
        upBtn.active = enabled;
        downBtn.active = enabled;
        refBtn.active = enabled;
    }

    private void doUpload() {
        if (Config.worldName.isEmpty()) {
            setStatus("§cSelect a world first!");
            return;
        }
        
        setButtonsEnabled(false);
        setStatus("§eUploading...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(Config.githubToken);
                boolean ok = manager.githubSyncUpload(Config.worldName, Config.worldPath);
                setStatus(ok ? "§aUpload OK!" : "§cFailed");
                setButtonsEnabled(true);
                if (ok) loadWorlds();
            } catch (Exception e) {
                setStatus("§c" + e.getMessage());
                setButtonsEnabled(true);
            }
        });
    }

    private void doDownload() {
        if (Config.worldName.isEmpty()) {
            setStatus("§cSelect a world first!");
            return;
        }
        
        setButtonsEnabled(false);
        setStatus("§eDownloading...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(Config.githubToken);
                boolean ok = manager.githubSyncDownload(Config.worldName, Config.worldPath);
                setStatus(ok ? "§aDownload OK!" : "§cFailed");
                setButtonsEnabled(true);
            } catch (Exception e) {
                setStatus("§c" + e.getMessage());
                setButtonsEnabled(true);
            }
        });
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g, mx, my, pt);
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}