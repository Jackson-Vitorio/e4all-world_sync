package link.e4all.mundosync;

import link.e4all.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Tela de configuracao e controle do MundoSync no estilo Minecraft.
 * Substitui a interface PySimpleGUI da aplicacao Python original.
 */
public class MundoSyncScreen extends Screen {
    private final Screen parent;
    private EditBox usernameBox, worldPathBox, worldNameBox, remoteBox, webhookBox, rclonePathBox;
    private Button syncDownloadBtn, syncUploadBtn;
    private String statusText = "";
    private boolean isChecking = false;

    public MundoSyncScreen(Screen parent) {
        super(Component.literal("MundoSync - Sincronizacao de Mundos"));
        this.parent = parent;
    }

    // ==================== INIT ====================

    @Override
    protected void init() {
        super.init();
        int cx = width / 2;
        int y = 40;

        addBtn("🔍 Verificar Rclone", cx - 100, 20, 200, this::onCheckRclone);
        addBtn("☁️ Configurar Google Drive", cx - 100, y, 200, this::onSetupGoogleDrive);
        y += 35;
        syncDownloadBtn = addBtn("⬇️ Baixar Mundo", cx - 100, y, 200, this::onSyncDownload);
        y += 25;
        syncUploadBtn = addBtn("⬆️ Enviar Mundo", cx - 100, y, 200, this::onSyncUpload);
        y += 30;
        addBtn("🔒 Status do Lock", cx - 100, y, 200, this::onLockStatus);
        y += 30;
        addBtn("🎮 Detectar e Enviar IP", cx - 100, y, 200, this::onDetectAndSendIp);

        y = 40;
        int lx = 20;
        usernameBox = configField(lx, y, "Usuario:", 70, 160);
        usernameBox.setValue(Config.INSTANCE.mundoSyncUsername.value());
        y += 25;
        worldPathBox = configField(lx, y, "Mundo Path:", 80, 200);
        worldPathBox.setValue(Config.INSTANCE.mundoSyncWorldPath.value());
        y += 25;
        worldNameBox = configField(lx, y, "Nome do Mundo:", 105, 175);
        worldNameBox.setValue(Config.INSTANCE.mundoSyncWorldName.value());
        y += 25;
        remoteBox = configField(lx, y, "Remote Rclone:", 105, 175);
        remoteBox.setValue(Config.INSTANCE.mundoSyncRemote.value());
        y += 25;
        webhookBox = configField(lx, y, "Webhook URL:", 95, 185);
        webhookBox.setValue(Config.INSTANCE.mundoSyncWebhookUrl.value());
        y += 25;
        rclonePathBox = configField(lx, y, "Rclone Path:", 90, 190);
        rclonePathBox.setValue(Config.INSTANCE.mundoSyncRclonePath.value());
        y += 30;
        addBtn("💾 Salvar Config", lx, y, 160, this::onSaveConfig);
        addBtn("✕ Fechar", width - 60, 5, 55, btn -> onClose());
    }


    // ==================== HELPERS ====================

    private Button addBtn(String label, int x, int y, int w, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action)
                .bounds(x, y, w, 20).build());
    }

    private EditBox configField(int x, int y, String label, int lw, int fw) {
        addBtn(label, x, y - 5, lw, btn -> {});
        EditBox box = new EditBox(font, x + lw, y - 5, fw, 20, Component.literal(""));
        addRenderableWidget(box);
        return box;
    }

    // ==================== ACTIONS ====================

    private void onSaveConfig(Button btn) {
        Config.INSTANCE.mundoSyncUsername.setValue(usernameBox.getValue(), true);
        Config.INSTANCE.mundoSyncWorldPath.setValue(worldPathBox.getValue(), true);
        Config.INSTANCE.mundoSyncWorldName.setValue(worldNameBox.getValue(), true);
        Config.INSTANCE.mundoSyncRemote.setValue(remoteBox.getValue(), true);
        Config.INSTANCE.mundoSyncWebhookUrl.setValue(webhookBox.getValue(), true);
        Config.INSTANCE.mundoSyncRclonePath.setValue(rclonePathBox.getValue(), true);
        statusText = "§a✅ Configuracao salva!";
    }

    private void onCheckRclone(Button btn) {
        if (isChecking) return;
        isChecking = true;
        statusText = "§eVerificando rclone...";
        new Thread(() -> {
            boolean found = MundoSyncManager.getInstance().checkRclone();
            minecraft.execute(() -> {
                statusText = found ? "§a✅ Rclone OK!" : "§c❌ Rclone nao encontrado!";
                isChecking = false;
            });
        }, "rclone-check").start();
    }

    private void onSetupGoogleDrive(Button btn) {
        statusText = "§eConfigurando Google Drive...";
        MundoSyncManager.getInstance().setupGoogleDrive().thenAccept(ok ->
            minecraft.execute(() -> statusText = ok
                ? "§a✅ Google Drive configurado!" : "§c❌ Falha ao configurar.")
        );
    }

    private void onSyncDownload(Button btn) {
        if (MundoSyncManager.getInstance().isSyncing()) {
            statusText = "§cSync em andamento."; return;
        }
        statusText = "§eBaixando mundo...";
        setButtons(false);
        MundoSyncManager.getInstance().syncWorld(
            MundoSyncManager.SyncDirection.DOWNLOAD, callback());
    }

    private void onSyncUpload(Button btn) {
        if (MundoSyncManager.getInstance().isSyncing()) {
            statusText = "§cSync em andamento."; return;
        }
        var lock = MundoSyncManager.getInstance().getActiveLock();
        if (lock != null && !lock.host.equals(Config.INSTANCE.mundoSyncUsername.value())) {
            statusText = "§c⚠ Lock ativo. Use /e4all mundo forceupload"; return;
        }
        statusText = "§eEnviando mundo...";
        setButtons(false);
        MundoSyncManager.getInstance().syncWorld(
            MundoSyncManager.SyncDirection.UPLOAD, callback());
    }

    private void setButtons(boolean active) {
        if (syncDownloadBtn != null) syncDownloadBtn.active = active;
        if (syncUploadBtn != null) syncUploadBtn.active = active;
    }

    private MundoSyncManager.SyncCallback callback() {
        return new MundoSyncManager.SyncCallback() {
            public void onProgress(int pct, String s) {
                minecraft.execute(() -> statusText = "§e" + s);
            }
            public void onComplete(boolean ok, String msg) {
                minecraft.execute(() -> {
                    statusText = ok ? "§a✅ " + msg : "§c❌ " + msg;
                    setButtons(true);
                });
            }
        };
    }

    private void onLockStatus(Button btn) {
        var lock = MundoSyncManager.getInstance().getActiveLock();
        statusText = (lock != null)
            ? "§e🔒 Lock: " + lock.host + " (exp: " + lock.expiraEm + ")"
            : "§a🔓 Sem lock ativo.";
    }

    private void onDetectAndSendIp(Button btn) {
        if (Config.INSTANCE.mundoSyncWebhookUrl.value().isEmpty()) {
            statusText = "§c❌ Sem webhook configurado."; return;
        }
        statusText = "§eEnviando IP para Discord...";
        MundoSyncManager.getInstance().detectAndSendIp();
        statusText = "§a✅ IP enviado!";
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        renderBackground(g, mx, my, delta);
        super.render(g, mx, my, delta);
        if (statusText != null && !statusText.isEmpty()) {
            g.drawCenteredString(font, statusText, width / 2, height - 30, 0xFFFFFF);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
