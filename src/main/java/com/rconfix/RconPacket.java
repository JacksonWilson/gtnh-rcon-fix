package com.rconfix;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * One RCON packet: little-endian length, request id and type, then a UTF-8 body and two zero bytes. The length counts
 * everything after itself.
 */
public final class RconPacket {

    public static final int TYPE_RESPONSE = 0;
    public static final int TYPE_COMMAND = 2;
    public static final int TYPE_AUTH_RESPONSE = 2;
    public static final int TYPE_LOGIN = 3;

    /** Largest request we accept. Vanilla clients send at most 1460 bytes; 4 KB leaves room for long commands. */
    public static final int MAX_REQUEST_LENGTH = 4096 + 10;
    /** Vanilla splits long replies into bodies of at most this many characters. */
    public static final int MAX_RESPONSE_CHARS = 4096;

    public final int id;
    public final int type;
    public final String body;

    public RconPacket(int id, int type, String body) {
        this.id = id;
        this.type = type;
        this.body = body;
    }

    /** Reads one packet. Throws {@link EOFException} when the client closed the connection. */
    public static RconPacket read(DataInputStream in) throws IOException {
        int length = Integer.reverseBytes(in.readInt());
        if (length < 10 || length > MAX_REQUEST_LENGTH) {
            throw new IOException("bad packet length " + length);
        }
        byte[] data = new byte[length];
        in.readFully(data);
        ByteBuffer buf = ByteBuffer.wrap(data)
            .order(ByteOrder.LITTLE_ENDIAN);
        int id = buf.getInt();
        int type = buf.getInt();
        int end = 8;
        while (end < length && data[end] != 0) {
            end++;
        }
        return new RconPacket(id, type, new String(data, 8, end - 8, StandardCharsets.UTF_8));
    }

    public static void write(OutputStream out, int id, int type, String body) throws IOException {
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + 10 + text.length)
            .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(text.length + 10);
        buf.putInt(id);
        buf.putInt(type);
        buf.put(text);
        buf.put((byte) 0);
        buf.put((byte) 0);
        out.write(buf.array());
    }

    /** Writes a reply, split into several packets with the same id if it's long, like vanilla. */
    public static void writeResponse(OutputStream out, int id, String body) throws IOException {
        int start = 0;
        do {
            int end = Math.min(body.length(), start + MAX_RESPONSE_CHARS);
            write(out, id, TYPE_RESPONSE, body.substring(start, end));
            start = end;
        } while (start < body.length());
        out.flush();
    }
}
