package link.e4all.mundosync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import link.e4all.Config;
import link.e4all.Mirror;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class MundoSyncCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("e4all")
                        .then(Commands.literal("mundo")
                                .then(Commands.literal("download").executes(ctx -> {
                                    MundoSyncManager mgr = MundoSyncManager.getInstance();
                                    if (mgr.isSyncing()) {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§cJá existe sincronização em andamento."));
                                        return 0;
                                    }
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§eBaixando mundo da nuvem..."));
                                    mgr.syncWorld(MundoSyncManager.SyncDirection.DOWNLOAD,
                                        getMundoSyncCallback(ctx));
                                    return 1;
                                }))
                                .then(Commands.literal("upload").executes(ctx -> {
                                    MundoSyncManager mgr = MundoSyncManager.getInstance();
                                    if (mgr.isSyncing()) {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§cJá existe sincronização em andamento."));
                                        return 0;
                                    }
                                    MundoSyncManager.LockInfo lock = mgr.getActiveLock();
                                    String user = Config.INSTANCE.mundoSyncUsername.value();
                                    if (lock != null && !lock.host.equals(user)) {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§c⚠ Lock ativo por " + lock.host
                                                + ". Use /e4all mundo forceupload"));
                                        return 0;
                                    }
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§eEnviando mundo para a nuvem..."));
                                    mgr.syncWorld(MundoSyncManager.SyncDirection.UPLOAD,
                                        getMundoSyncCallback(ctx));
                                    return 1;
                                }))
                                .then(Commands.literal("forceupload").executes(ctx -> {
                                    MundoSyncManager mgr = MundoSyncManager.getInstance();
                                    if (mgr.isSyncing()) {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§cJá existe sincronização em andamento."));
                                        return 0;
                                    }
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§eEnviando mundo (forçado)..."));
                                    mgr.syncWorld(MundoSyncManager.SyncDirection.UPLOAD,
                                        getMundoSyncCallback(ctx));
                                    return 1;
                                }))
                                .then(Commands.literal("status").executes(ctx -> {
                                    MundoSyncManager mgr = MundoSyncManager.getInstance();
                                    MundoSyncManager.LockInfo lock = mgr.getActiveLock();
                                    if (lock != null) {
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§e🔒 Lock ativo por " + lock.host
                                                + " (expira: " + lock.expiraEm + ")"));
                                    } else {
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§a🔓 Nenhum lock ativo."));
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("lock").executes(ctx -> {
                                    boolean ok = MundoSyncManager.getInstance().acquireLock();
                                    if (ok) {
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§a✅ Lock adquirido!"));
                                    } else {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§c❌ Falha ao adquirir lock."));
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("unlock").executes(ctx -> {
                                    MundoSyncManager.getInstance().removeRemoteLock();
                                    MundoSyncManager.getInstance().removeLocalLock();
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§a✅ Lock removido."));
                                    return 1;
                                }))
                                .then(Commands.literal("detectip").executes(ctx -> {
                                    MundoSyncManager.getInstance().detectAndSendIp();
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§a✅ IP enviado para o Discord!"));
                                    return 1;
                                }))
                                .then(Commands.literal("checkrclone").executes(ctx -> {
                                    boolean found = MundoSyncManager.getInstance().checkRclone();
                                    if (found) {
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§a✅ Rclone encontrado!"));
                                    } else {
                                        Mirror.sendFailureToSource(ctx.getSource(),
                                            Mirror.literal("§c❌ Rclone não encontrado!"));
                                    }
                                    return 1;
                                }))
                                .then(Commands.literal("setupgdrive").executes(ctx -> {
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§eConfigurando Google Drive..."));
                                    MundoSyncManager.getInstance().setupGoogleDrive().thenAccept(success -> {
                                        if (success) {
                                            Mirror.sendSuccessToSource(ctx.getSource(),
                                                Mirror.literal("§a✅ Google Drive configurado!"));
                                        } else {
                                            Mirror.sendFailureToSource(ctx.getSource(),
                                                Mirror.literal("§c❌ Falha ao configurar."));
                                        }
                                    });
                                    return 1;
                                }))
                                .then(Commands.literal("gui").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cGUI disponível apenas no cliente."));
                                    return 1;
                                }))
                                .then(Commands.literal("help").executes(ctx -> {
                                    String msg = "§e=== MundoSync Commands ===\n"
                                        + "§7/e4all mundo download §8- Baixar mundo\n"
                                        + "§7/e4all mundo upload §8- Enviar mundo\n"
                                        + "§7/e4all mundo forceupload §8- Enviar ignorando lock\n"
                                        + "§7/e4all mundo status §8- Status do lock\n"
                                        + "§7/e4all mundo lock §8- Adquirir lock\n"
                                        + "§7/e4all mundo unlock §8- Liberar lock\n"
                                        + "§7/e4all mundo detectip §8- Enviar IP p/ Discord\n"
                                        + "§7/e4all mundo checkrclone §8- Verificar rclone\n"
                                        + "§7/e4all mundo setupgdrive §8- Configurar GDrive\n"
                                        + "§7/e4all mundo config §8- Ver configuração\n"
                                        + "§7/e4all mundo gui §8- Abrir interface";
                                    Mirror.sendSuccessToSource(ctx.getSource(), Mirror.literal(msg));
                                    return 1;
                                }))
                                .then(Commands.literal("config").executes(ctx -> {
                                    String s = "§e=== MundoSync Config ===\n"
                                        + "§7Usuário: §f" + val(Config.INSTANCE.mundoSyncUsername.value()) + "\n"
                                        + "§7Mundo Path: §f" + val(Config.INSTANCE.mundoSyncWorldPath.value()) + "\n"
                                        + "§7Mundo Nome: §f" + val(Config.INSTANCE.mundoSyncWorldName.value()) + "\n"
                                        + "§7Remote: §f" + val(Config.INSTANCE.mundoSyncRemote.value()) + "\n"
                                        + "§7Webhook: §f" + val(Config.INSTANCE.mundoSyncWebhookUrl.value()) + "\n"
                                        + "§7Rclone Path: §f" + val(Config.INSTANCE.mundoSyncRclonePath.value()) + "\n"
                                        + "§7Rclone OK: §f" + (MundoSyncManager.getInstance().checkRclone() ? "§a✅" : "§c❌");
                                    Mirror.sendSuccessToSource(ctx.getSource(), Mirror.literal(s));
                                    return 1;
                                }))
                                .then(Commands.literal("github").executes(ctx -> {
                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                        Mirror.literal("§e=== GitHub World Sync ===\n"
                                            + "§7/github init <token> §8- Inicializar com token GitHub\n"
                                            + "§7/github upload §8- Upload mundo para GitHub\n"
                                            + "§7/github download §8- Download mundo do GitHub\n"
                                            + "§7/github status §8- Ver status da sincronizacao"));
                                    return 1;
                                }))
                                .then(Commands.literal("init").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cUso: /e4all mundo github init <token>"));
                                    return 1;
                                }))
                                .then(Commands.literal("upload").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cUso: /e4all mundo github upload"));
                                    return 1;
                                }))
                                .then(Commands.literal("download").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cUso: /e4all mundo github download"));
                                    return 1;
                                }))
                                .then(Commands.literal("status").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cUso: /e4all mundo github status"));
                                    return 1;
                                }))
                        )
        );
    }

    private static MundoSyncManager.SyncCallback getMundoSyncCallback(
            CommandContext<CommandSourceStack> ctx) {
        return new MundoSyncManager.SyncCallback() {
            @Override
            public void onProgress(int percent, String status) {}

            @Override
            public void onComplete(boolean success, String message) {
                if (success) {
                    Mirror.sendSuccessToSource(ctx.getSource(),
                        Mirror.literal("§a✅ " + message));
                } else {
                    Mirror.sendFailureToSource(ctx.getSource(),
                        Mirror.literal("§c❌ " + message));
                }
            }
        };
    }

    private static String val(String s) {
        return (s == null || s.isEmpty()) ? "§7<não configurado>" : s;
    }
}