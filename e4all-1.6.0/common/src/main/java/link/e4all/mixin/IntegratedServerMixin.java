package link.e4all.mixin;

import link.e4all.Config;
import link.e4all.E4allClient;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that sets the IntegratedServer to offline mode (disables Mojang
 * authentication) when the user enables the offline mode toggle in the config.
 *
 * This is the same approach used by offline-e4mc: intercept the "open to LAN"
 * call and set online-mode to false before the server starts accepting
 * connections.
 *
 * The method regex covers multiple mapping names:
 *   - publishServer: Mojang 1.20.2+
 *   - shareToLan: Mojang alternate / older
 *   - openToLan: Yarn / Fabric intermediary
 *   - method_3823: Fabric intermediary
 *   - m_129486_: SRG (Forge 1.20.1)
 */
@Mixin(IntegratedServer.class)
public class IntegratedServerMixin {

    @Inject(method = "/^(publishServer|shareToLan|openToLan|method_3823|m_129486_)$/", at = @At("HEAD"), require = 0)
    private void e4all$onOpenToLan(CallbackInfoReturnable<Boolean> cir) {
        if (Config.INSTANCE.offlineMode.value()) {
            E4allClient.LOGGER.info("[e4all] Offline mode active — setting server online-mode to false");
            ((IntegratedServer)(Object)this).setUsesAuthentication(false);
        }
    }
}
