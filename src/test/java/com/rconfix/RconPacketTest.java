package com.rconfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;

class RconPacketTest {

    @Test
    void roundTripsUtf8() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RconPacket.write(out, 42, RconPacket.TYPE_COMMAND, "say héllo");
        RconPacket p = RconPacket.read(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
        assertEquals(42, p.id);
        assertEquals(RconPacket.TYPE_COMMAND, p.type);
        assertEquals("say héllo", p.body);
    }

    @Test
    void readsBackToBackPackets() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RconPacket.write(out, 1, RconPacket.TYPE_LOGIN, "pw");
        RconPacket.write(out, 2, RconPacket.TYPE_COMMAND, "list");
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        assertEquals("pw", RconPacket.read(in).body);
        assertEquals("list", RconPacket.read(in).body);
    }

    @Test
    void rejectsOversizedPackets() {
        byte[] header = { (byte) 0xff, (byte) 0xff, 0, 0 }; // 65535, little-endian
        assertThrows(IOException.class, () -> RconPacket.read(new DataInputStream(new ByteArrayInputStream(header))));
    }

    @Test
    void splitsLongResponses() throws IOException {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < RconPacket.MAX_RESPONSE_CHARS + 10; i++) body.append('x');
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RconPacket.writeResponse(out, 7, body.toString());
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        assertEquals(RconPacket.MAX_RESPONSE_CHARS, RconPacket.read(in).body.length());
        assertEquals(10, RconPacket.read(in).body.length());
    }
}
