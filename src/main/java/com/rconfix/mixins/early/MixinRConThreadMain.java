package com.rconfix.mixins.early;

import java.net.InetAddress;

import net.minecraft.network.rcon.IServer;
import net.minecraft.network.rcon.RConThreadBase;
import net.minecraft.network.rcon.RConThreadMain;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.rconfix.RconFix;
import com.rconfix.RconFixConfig;

@Mixin(RConThreadMain.class)
public abstract class MixinRConThreadMain extends RConThreadBase {

    @Shadow
    private String hostname;
    @Shadow
    private String rconPassword;

    private MixinRConThreadMain(IServer server, String name) {
        super(server, name);
    }

    /** Vanilla listens on server-ip, or on every interface when that's empty. Bind where the config says instead. */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void rconfix$bindAddress(IServer server, CallbackInfo ci) {
        if (!RconFixConfig.bindAddress.isEmpty()) {
            hostname = RconFixConfig.bindAddress;
        }
    }

    /** Refuses to listen beyond this machine with a short password. Vanilla already refuses an empty one. */
    @Inject(method = "startThread", at = @At("HEAD"), cancellable = true)
    private void rconfix$checkPassword(CallbackInfo ci) {
        if (rconPassword.isEmpty() || isLoopback(hostname)) {
            return;
        }
        if (rconPassword.length() < RconFixConfig.minRemotePasswordLength) {
            RconFix.LOG.error(
                "RCON not started: it would listen on {}, which other machines can reach, and rcon.password is shorter "
                    + "than {} characters. Use a longer password, or set bindAddress to 127.0.0.1 in config/rconfix.cfg.",
                hostname,
                RconFixConfig.minRemotePasswordLength);
            ci.cancel();
        }
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host)
                .isLoopbackAddress();
        } catch (Exception e) {
            return false;
        }
    }
}
