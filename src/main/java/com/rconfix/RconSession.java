package com.rconfix;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

import org.apache.logging.log4j.Logger;

/**
 * One RCON connection, from accept to close. Replaces vanilla's {@code RConThreadClient.run()}, which closed the
 * socket after the first packet (its {@code finally} sat inside the read loop), so no command could ever run.
 *
 * <p>
 * Same wire protocol as vanilla, so any RCON client works. Differences: the connection stays open until the client
 * leaves or goes idle, packets are read whole even when TCP splits them, the password check takes the same time
 * whatever the input, and repeated failed logins lock the client's address out.
 * </p>
 */
public final class RconSession {

    /** Runs one console command and returns its output. */
    public interface CommandHandler {

        String run(String command) throws Exception;
    }

    /** Settings for one session; {@link RconFixConfig} in the game, fixed values in tests. */
    public static final class Settings {

        public String[] allowedAddresses = new String[0];
        public int maxFailedLogins = 5;
        public long lockoutMillis = 300_000;
        public int loginTimeoutMillis = 10_000;
        public int idleTimeoutMillis = 600_000;

        public static Settings fromConfig() {
            Settings s = new Settings();
            s.allowedAddresses = RconFixConfig.allowedAddresses;
            s.maxFailedLogins = RconFixConfig.maxFailedLogins;
            s.lockoutMillis = RconFixConfig.lockoutSeconds * 1000L;
            s.loginTimeoutMillis = RconFixConfig.loginTimeoutSeconds * 1000;
            s.idleTimeoutMillis = RconFixConfig.idleTimeoutSeconds * 1000;
            return s;
        }
    }

    private final Socket socket;
    private final byte[] password;
    private final CommandHandler commands;
    private final LoginGuard guard;
    private final Settings settings;
    private final Logger log;
    private final String address;

    public RconSession(Socket socket, String password, CommandHandler commands, LoginGuard guard, Settings settings,
        Logger log) {
        this.socket = socket;
        this.password = password.getBytes(StandardCharsets.UTF_8);
        this.commands = commands;
        this.guard = guard;
        this.settings = settings;
        this.log = log;
        this.address = socket.getInetAddress()
            .getHostAddress();
    }

    /** Serves the connection until it closes. Always closes the socket. */
    public void run() {
        try {
            serve();
        } catch (EOFException e) {
            // The client closed the connection.
        } catch (SocketTimeoutException e) {
            log.info("RCON connection from {} timed out", address);
        } catch (IOException e) {
            log.info("RCON connection from {} closed: {}", address, e.getMessage());
        } catch (Exception e) {
            log.error("RCON connection from {} failed", address, e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private void serve() throws IOException {
        if (settings.allowedAddresses.length > 0 && !Arrays.asList(settings.allowedAddresses)
            .contains(address)) {
            log.warn("Refused RCON connection from {}: not in allowedAddresses", address);
            return;
        }
        long locked = guard.lockedFor(address);
        if (locked > 0) {
            log.warn("Refused RCON connection from {}: locked out for another {} s", address, locked / 1000);
            return;
        }
        socket.setSoTimeout(settings.loginTimeoutMillis);
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        OutputStream out = new BufferedOutputStream(socket.getOutputStream());
        boolean loggedIn = false;
        while (true) {
            RconPacket packet = RconPacket.read(in);
            if (packet.type == RconPacket.TYPE_LOGIN) {
                if (password.length > 0
                    && MessageDigest.isEqual(password, packet.body.getBytes(StandardCharsets.UTF_8))) {
                    loggedIn = true;
                    guard.succeed(address);
                    socket.setSoTimeout(settings.idleTimeoutMillis);
                    log.info("RCON login from {}", address);
                    RconPacket.write(out, packet.id, RconPacket.TYPE_AUTH_RESPONSE, "");
                    out.flush();
                } else {
                    loggedIn = false;
                    RconPacket.write(out, -1, RconPacket.TYPE_AUTH_RESPONSE, "");
                    out.flush();
                    long lockout = guard.fail(address, settings.maxFailedLogins, settings.lockoutMillis);
                    if (lockout > 0) {
                        log.warn("Wrong RCON password from {}; locked out for {} s", address, lockout / 1000);
                        return;
                    }
                    log.warn("Wrong RCON password from {}", address);
                }
            } else if (packet.type == RconPacket.TYPE_COMMAND) {
                if (!loggedIn) {
                    RconPacket.write(out, -1, RconPacket.TYPE_AUTH_RESPONSE, "");
                    out.flush();
                    continue;
                }
                log.info("RCON {}: {}", address, packet.body);
                String reply;
                try {
                    reply = commands.run(packet.body);
                } catch (Exception e) {
                    reply = "Error executing: " + packet.body + " (" + e + ")";
                }
                RconPacket.writeResponse(out, packet.id, reply == null ? "" : reply);
            } else {
                RconPacket.writeResponse(out, packet.id, "Unknown request " + Integer.toHexString(packet.type));
            }
        }
    }
}
