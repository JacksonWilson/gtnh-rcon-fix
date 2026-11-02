package com.rconfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Runs sessions over real loopback sockets, the way RConThreadMain hands them over. The tests share a listener field,
 * so they run one at a time.
 */
@Execution(ExecutionMode.SAME_THREAD)
class RconSessionTest {

    private static final String PASSWORD = "correct horse battery staple";

    private ServerSocket listener;
    private List<String> ran;
    private LoginGuard guard;
    private RconSession.Settings settings;

    @BeforeEach
    void listen() throws IOException {
        listener = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
        ran = new ArrayList<>();
        guard = new LoginGuard(System::currentTimeMillis);
        settings = new RconSession.Settings();
        settings.maxFailedLogins = 2;
        settings.loginTimeoutMillis = 2_000;
        settings.idleTimeoutMillis = 2_000;
    }

    @AfterEach
    void close() throws IOException {
        listener.close();
    }

    /** Accepts one connection and serves it on a background thread, like an RConThreadClient. */
    private Socket connect() throws IOException {
        Socket client = new Socket(listener.getInetAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread t = new Thread(() -> new RconSession(server, PASSWORD, command -> {
            synchronized (ran) {
                ran.add(command);
            }
            return "ran " + command;
        }, guard, settings, LogManager.getLogger("RconSessionTest")).run());
        t.setDaemon(true);
        t.start();
        client.setSoTimeout(5_000);
        return client;
    }

    private static RconPacket send(Socket socket, int id, int type, String body) throws IOException {
        OutputStream out = socket.getOutputStream();
        RconPacket.write(out, id, type, body);
        out.flush();
        return RconPacket.read(new DataInputStream(socket.getInputStream()));
    }

    @Test
    void runsSeveralCommandsOnOneConnection() throws IOException {
        try (Socket s = connect()) {
            RconPacket login = send(s, 1, RconPacket.TYPE_LOGIN, PASSWORD);
            assertEquals(1, login.id);
            assertEquals(RconPacket.TYPE_AUTH_RESPONSE, login.type);
            assertEquals("ran list", send(s, 2, RconPacket.TYPE_COMMAND, "list").body);
            assertEquals("ran tp Alex 0 80 0", send(s, 3, RconPacket.TYPE_COMMAND, "tp Alex 0 80 0").body);
        }
        assertEquals(2, ran.size());
    }

    @Test
    void wrongPasswordGetsMinusOneAndCommandsAreRefused() throws IOException {
        try (Socket s = connect()) {
            assertEquals(-1, send(s, 1, RconPacket.TYPE_LOGIN, "nope").id);
            assertEquals(-1, send(s, 2, RconPacket.TYPE_COMMAND, "stop").id);
        }
        assertTrue(ran.isEmpty());
    }

    @Test
    void locksOutAfterRepeatedFailures() throws IOException {
        try (Socket s = connect()) {
            send(s, 1, RconPacket.TYPE_LOGIN, "nope");
            send(s, 2, RconPacket.TYPE_LOGIN, "nope"); // second failure: locked out, connection closed
            assertThrows(EOFException.class, () -> RconPacket.read(new DataInputStream(s.getInputStream())));
        }
        try (Socket s = connect()) {
            // Locked out: even the right password gets no reply, the connection just closes.
            OutputStream out = s.getOutputStream();
            RconPacket.write(out, 3, RconPacket.TYPE_LOGIN, PASSWORD);
            out.flush();
            assertThrows(IOException.class, () -> RconPacket.read(new DataInputStream(s.getInputStream())));
        }
        assertTrue(guard.lockedFor("127.0.0.1") > 0);
    }

    @Test
    void refusesAddressesOutsideTheAllowList() throws IOException {
        settings.allowedAddresses = new String[] { "10.0.0.5" };
        try (Socket s = connect()) {
            OutputStream out = s.getOutputStream();
            RconPacket.write(out, 1, RconPacket.TYPE_LOGIN, PASSWORD);
            out.flush();
            assertThrows(IOException.class, () -> RconPacket.read(new DataInputStream(s.getInputStream())));
        }
    }

    @Test
    void closesConnectionsThatNeverLogIn() throws IOException {
        settings.loginTimeoutMillis = 200;
        try (Socket s = connect()) {
            assertThrows(EOFException.class, () -> RconPacket.read(new DataInputStream(s.getInputStream())));
        }
    }
}
