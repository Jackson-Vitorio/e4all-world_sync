package link.e4all.mundosync;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import link.e4all.Config;
import link.e4all.Mirror;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class MundoSyncCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("e4all-mundosync");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("e4all")
                        .then(Commands.literal("mundo")
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
                                .then(Commands.literal("gui").executes(ctx -> {
                                    Mirror.sendFailureToSource(ctx.getSource(),
                                        Mirror.literal("§cGUI disponível apenas no cliente."));
                                    return 1;
                                }))
                                .then(Commands.literal("help").executes(ctx -> {
                                    String msg = "§e=== MundoSync Commands ===\n"
                                        + "§7/e4all mundo github init <token> §8- Inicializar GitHub\n"
                                        + "§7/e4all mundo github upload §8- Upload mundo\n"
                                        + "§7/e4all mundo github download §8- Download mundo\n"
                                        + "§7/e4all mundo github status §8- Ver status\n"
                                        + "§7/e4all mundo forceupload §8- Upload forçado\n"
                                        + "§7/e4all mundo status §8- Status do lock\n"
                                        + "§7/e4all mundo lock/unlock §8- Gerenciar lock\n"
                                        + "§7/e4all mundo detectip §8- Enviar IP p/ Discord\n"
                                        + "§7/e4all mundo config §8- Ver configuração";
                                    Mirror.sendSuccessToSource(ctx.getSource(), Mirror.literal(msg));
                                    return 1;
                                }))
                                .then(Commands.literal("config").executes(ctx -> {
                                    String s = "§e=== MundoSync Config ===\n"
                                        + "§7Usuário: §f" + val(Config.INSTANCE.mundoSyncUsername.value()) + "\n"
                                        + "§7Mundo Path: §f" + val(Config.INSTANCE.mundoSyncWorldPath.value()) + "\n"
                                        + "§7Mundo Nome: §f" + val(Config.INSTANCE.mundoSyncWorldName.value()) + "\n"
                                        + "§7Webhook: §f" + val(Config.INSTANCE.mundoSyncWebhookUrl.value()) + "\n"
                                        + "§7GitHub Token: §f" + maskToken(Config.INSTANCE.githubToken.value()) + "\n"
                                        + "§7GitHub Enabled: §f" + Config.INSTANCE.githubSyncEnabled.value();
                                    Mirror.sendSuccessToSource(ctx.getSource(), Mirror.literal(s));
                                    return 1;
                                }))
                                .then(Commands.literal("github")
                                    .then(Commands.literal("init")
                                        .then(Commands.argument("token", StringArgumentType.string())
                                            .executes(ctx -> {
                                                String token = StringArgumentType.getString(ctx, "token");
                                                Config.INSTANCE.githubToken.setValue(token, true);
                                                Config.INSTANCE.githubSyncEnabled.setValue(true, true);
                                                boolean success = MundoSyncManager.getInstance().initializeGithubSync(token);
                                                if (success) {
                                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                                        Mirror.literal("§a✅ GitHub sync inicializado!\n§7Token salvo e sync ativado."));
                                                } else {
                                                    Mirror.sendFailureToSource(ctx.getSource(),
                                                        Mirror.literal("§c❌ Falha ao inicializar GitHub sync."));
                                                }
                                                return 1;
                                            })
                                        )
                                    )
                                    .then(Commands.literal("upload").executes(ctx -> {
                                        String worldName = Config.INSTANCE.mundoSyncWorldName.value();
                                        String worldPath = Config.INSTANCE.mundoSyncWorldPath.value();
                                        
                                        // Garante que o GitHub sync está inicializado
                                        if (!MundoSyncManager.getInstance().initializeGithubSync(
                                                Config.INSTANCE.githubToken.value())) {
                                            Mirror.sendFailureToSource(ctx.getSource(),
                                                Mirror.literal("§c❌ GitHub sync não inicializado.\n"
                                                    + "§7Use: /e4all mundo github init <token>"));
                                            return 1;
                                        }
                                        
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§e🚀 Iniciando upload...\n"
                                                + "§7Mundo: " + worldName + "\n"
                                                + "§7Isso pode demorar alguns minutos."));
                                        
                                        // Atualiza o status periodicamente
                                        var statusUpdater = new java.util.concurrent.ScheduledThreadPoolExecutor(1);
                                        statusUpdater.scheduleAtFixedRate(() -> {
                                            Mirror.sendSuccessToSource(ctx.getSource(),
                                                Mirror.literal("§e⏳ Enviando... Verifique o log para detalhes."));
                                        }, 5, 10, java.util.concurrent.TimeUnit.SECONDS);
                                        
                                        MundoSyncManager.getInstance().githubSyncUpload(worldName, worldPath)
                                            .thenAccept(success -> {
                                                statusUpdater.shutdown();
                                                if (success) {
                                                    Mirror.sendSuccessToSource(ctx.getSource(),
                                                        Mirror.literal("§a✅ Upload concluído com sucesso!\n"
                                                            + "§7Repositório: e4all-world-" + worldName));
                                                } else {
                                                    Mirror.sendFailureToSource(ctx.getSource(),
                                                        Mirror.literal("§c❌ Falha no upload.\n"
                                                            + "§7Verifique o log para detalhes."));
                                                }
                                            });
                                        return 1;
                                    }))
                                    .then(Commands.literal("download").executes(ctx -> {
                                        String worldName = Config.INSTANCE.mundoSyncWorldName.value();
                                        String worldPath = Config.INSTANCE.mundoSyncWorldPath.value();
                                        File worldDir = new File(worldPath, worldName);
                                        
                                        // Garante que o GitHub sync está inicializado
                                        if (!MundoSyncManager.getInstance().initializeGithubSync(
                                                Config.INSTANCE.githubToken.value())) {
                                            Mirror.sendFailureToSource(ctx.getSource(),
                                                Mirror.literal("§c❌ GitHub sync não inicializado.\n"
                                                    + "§7Use: /e4all mundo github init <token>"));
                                            return 1;
                                        }
                                        
                                        String downloadMsg = "§e📥 Download iniciado!\n"
                                            + "§7Mundo: §f" + worldName + "\n\n"
                                            + "§7FLUXO:\n"
                                            + "§7- Este comando baixa/atualiza o mundo §f" + worldName + "§7\n"
                                            + "§7- Você pode ficar em qualquer mundo (recomendado: temporário)\n"
                                            + "§7- O download acontece em background\n\n"
                                            + (worldDir.exists() 
                                                ? "§7Ação: §fAtualizando arquivos diferentes...\n"
                                                : "§7Ação: §fClonando repositório completo (primeira vez)...\n")
                                            + "\n"
                                            + "§cQuando ver a mensagem de conclusão:\n"
                                            + "§7- Feche este mundo\n"
                                            + "§7- Entre no mundo §f" + worldName + "§7\n"
                                            + "§7- O mundo estará atualizado!";
                                        
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal(downloadMsg));
                                        
                                        // Executa download em background (não bloqueia)
                                        MundoSyncManager.getInstance().githubSyncDownload(worldName, worldPath)
                                            .thenAccept(success -> {
                                                // Quando completar, armazena mensagem para mostrar na próxima interação
                                                if (success) {
                                                    MundoSyncManager.getInstance().setPendingNotification(
                                                        "§a✅ Download concluído!\n§7- Feche este mundo\n§7- Entre em: " + worldName);
                                                } else {
                                                    MundoSyncManager.getInstance().setPendingNotification(
                                                        "§c❌ Download falhou!\n§7Verifique o log para detalhes.");
                                                }
                                            });
                                        return 1;
                                    }))
                                    .then(Commands.literal("status").executes(ctx -> {
                                        String token = Config.INSTANCE.githubToken.value();
                                        String maskedToken = maskToken(token);
                                        Mirror.sendSuccessToSource(ctx.getSource(),
                                            Mirror.literal("§e=== GitHub Sync Status ===\n"
                                                + "§7Token: " + maskedToken + "\n"
                                                + "§7Enabled: §f" + Config.INSTANCE.githubSyncEnabled.value()));
                                        
                                        // Mostra notificação pendente se houver
                                        String pending = MundoSyncManager.getInstance().getAndClearPendingNotification();
                                        if (pending != null) {
                                            Mirror.sendSuccessToSource(ctx.getSource(),
                                                Mirror.literal(pending));
                                        }
                                        return 1;
                                    }))
                                )
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

    /**
     * Exibe o token GitHub apenas de forma mascarada (prefixo + sufixo),
     * para nunca vazar o token completo no chat ou em logs.
     */
    private static String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "§7<não configurado>";
        }
        if (token.length() <= 10) {
            return "§f" + token.substring(0, Math.min(4, token.length())) + "****";
        }
        return "§f" + token.substring(0, 6) + "..." + token.substring(token.length() - 4);
    }

}