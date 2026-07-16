package link.e4all.mundosync.gui;

import link.e4all.mundosync.MundoSyncManager;
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
        statusBtn = addRenderableWidget(Button.builder(Component.literal("§7Pronto"), b -> {}).bounds(cx - 155, 10, 310, 20).build());
        upBtn = addRenderableWidget(Button.builder(Component.literal("§eUpload"), b -> doUpload()).bounds(cx - 155, 35, 100, 20).build());
        downBtn = addRenderableWidget(Button.builder(Component.literal("§aDownload"), b -> doDownload()).bounds(cx - 50, 35, 100, 20).build());
        refBtn = addRenderableWidget(Button.builder(Component.literal("§bLista"), b -> loadWorlds()).bounds(cx + 55, 35, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§cVoltar"), b -> minecraft.setScreen(previous)).bounds(cx - 50, height - 30, 100, 20).build());
        loadWorlds();
    }

    private void setStatus(String s) {
        statusBtn.setMessage(Component.literal(s));
    }

    private void loadWorlds() {
        setStatus("§eCarregando...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(manager.getGithubToken());
                worlds = new CopyOnWriteArrayList<>(manager.listAvailableWorlds());
                setStatus("§a" + worlds.size() + " mundo(s)");
                rebuildWorldButtons();
            } catch (Exception e) {
                setStatus("§c" + e.getMessage());
            }
        });
    }

    private void rebuildWorldButtons() {
        // Remove botões antigos de mundos
        clearWidgets();
        // Recria tudo
        int cx = width / 2;
        Component currentStatus = statusBtn.getMessage();
        addRenderableWidget(statusBtn = Button.builder(currentStatus, b -> {}).bounds(cx - 155, 10, 310, 20).build());
        addRenderableWidget(upBtn = Button.builder(Component.literal("§eUpload"), b -> doUpload()).bounds(cx - 155, 35, 100, 20).build());
        addRenderableWidget(downBtn = Button.builder(Component.literal("§aDownload"), b -> doDownload()).bounds(cx - 50, 35, 100, 20).build());
        addRenderableWidget(refBtn = Button.builder(Component.literal("§bLista"), b -> loadWorlds()).bounds(cx + 55, 35, 100, 20).build());
        
        // Botões para cada mundo
        for (int i = 0; i < worlds.size() && i < 10; i++) {
            final String w = worlds.get(i);
            int y = 65 + i * 22;
            addRenderableWidget(Button.builder(Component.literal("§7📁 " + w), b -> {
                setStatus("§b" + w + " selecionado");
            }).bounds(cx - 155, y, 310, 20).build());
        }
        
        addRenderableWidget(Button.builder(Component.literal("§cVoltar"), b -> minecraft.setScreen(previous)).bounds(cx - 50, height - 30, 100, 20).build());
    }

    private void setButtonsEnabled(boolean enabled) {
        upBtn.active = enabled;
        downBtn.active = enabled;
        refBtn.active = enabled;
    }

    private void doUpload() {
        setButtonsEnabled(false);
        setStatus("§eEnviando...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(manager.getGithubToken());
                boolean ok = manager.githubSyncUpload(manager.getWorldName(), manager.getWorldPath()).get();
                setStatus(ok ? "§aUpload OK!" : "§cFalha");
                setButtonsEnabled(true);
                if (ok) loadWorlds();
            } catch (Exception e) {
                setStatus("§c" + e.getMessage());
                setButtonsEnabled(true);
            }
        });
    }

    private void doDownload() {
        setButtonsEnabled(false);
        setStatus("§eBaixando...");
        CompletableFuture.runAsync(() -> {
            try {
                manager.initializeGithubSync(manager.getGithubToken());
                boolean ok = manager.githubSyncDownload(manager.getWorldName(), manager.getWorldPath()).get();
                setStatus(ok ? "§aDownload OK!" : "§cFalha");
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