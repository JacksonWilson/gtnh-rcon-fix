package com.rconfix.mixins.early;

import java.net.Socket;

import net.minecraft.network.rcon.IServer;
import net.minecraft.network.rcon.RConThreadBase;
import net.minecraft.network.rcon.RConThreadClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.rconfix.RconFix;
import com.rconfix.RconFixConfig;
import com.rconfix.RconSession;
import com.rconfix.ServerThreadQueue;

@Mixin(RConThreadClient.class)
public abstract class MixinRConThreadClient extends RConThreadBase {

    @Shadow
    private Socket clientSocket;
    @Shadow
    private String rconPassword;

    private MixinRConThreadClient(IServer server, String name) {
        super(server, name);
    }

    /**
     * @author rconfix
     * @reason Vanilla's loop closes the socket in a {@code finally} that runs after every packet, so the connection
     *         dies right after login and no command ever runs. Commands also ran on this thread instead of the server
     *         thread.
     */
    @Overwrite
    public void run() {
        IServer target = this.server;
        try {
            new RconSession(
                clientSocket,
                rconPassword,
                command -> ServerThreadQueue
                    .call(() -> target.handleRConCommand(command), RconFixConfig.commandTimeoutSeconds),
                RconFix.LOGIN_GUARD,
                RconSession.Settings.fromConfig(),
                RconFix.LOG).run();
        } finally {
            clientSocket = null;
            // Lets RConThreadMain drop this thread from its client map; vanilla never cleared the flag.
            running = false;
        }
    }
}
