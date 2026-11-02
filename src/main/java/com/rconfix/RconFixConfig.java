package com.rconfix;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** {@code config/rconfix.cfg}. Read once in preInit, before the server starts RCON. */
public final class RconFixConfig {

    /** Address the RCON listener binds to. Empty means vanilla behaviour: {@code server-ip}, or every interface. */
    public static String bindAddress = "127.0.0.1";
    /** Client addresses allowed to connect. Empty allows any address that can reach the listener. */
    public static String[] allowedAddresses = new String[0];
    /** Passwords shorter than this stop RCON from starting on anything but a loopback address. */
    public static int minRemotePasswordLength = 16;
    public static int maxFailedLogins = 5;
    public static int lockoutSeconds = 300;
    public static int loginTimeoutSeconds = 10;
    public static int idleTimeoutSeconds = 600;
    public static int commandTimeoutSeconds = 30;

    private RconFixConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        String network = "network";
        String security = "security";
        bindAddress = config.getString(
            "bindAddress",
            network,
            bindAddress,
            "Address the RCON listener binds to. 127.0.0.1 accepts connections from this machine only. "
                + "Leave empty for vanilla behaviour (server-ip from server.properties, or every interface).");
        allowedAddresses = config.getStringList(
            "allowedAddresses",
            network,
            allowedAddresses,
            "Client IP addresses allowed to connect, one per line. Empty allows any address that can reach the "
                + "listener.");
        minRemotePasswordLength = config.getInt(
            "minRemotePasswordLength",
            security,
            minRemotePasswordLength,
            0,
            256,
            "If RCON listens on anything but a loopback address, it refuses to start with a shorter rcon.password.");
        maxFailedLogins = config.getInt(
            "maxFailedLogins",
            security,
            maxFailedLogins,
            1,
            1000,
            "Failed logins from one address before that address is locked out.");
        lockoutSeconds = config.getInt(
            "lockoutSeconds",
            security,
            lockoutSeconds,
            0,
            86400,
            "How long a locked-out address is refused. Each further lockout doubles it, up to a day.");
        loginTimeoutSeconds = config.getInt(
            "loginTimeoutSeconds",
            security,
            loginTimeoutSeconds,
            1,
            300,
            "A connection that hasn't logged in after this long is closed.");
        idleTimeoutSeconds = config.getInt(
            "idleTimeoutSeconds",
            security,
            idleTimeoutSeconds,
            1,
            86400,
            "A logged-in connection that sends nothing for this long is closed.");
        commandTimeoutSeconds = config.getInt(
            "commandTimeoutSeconds",
            "commands",
            commandTimeoutSeconds,
            1,
            600,
            "Commands run on the server thread. If the server doesn't get to one within this time, RCON replies "
                + "with an error. The command may still run later.");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
