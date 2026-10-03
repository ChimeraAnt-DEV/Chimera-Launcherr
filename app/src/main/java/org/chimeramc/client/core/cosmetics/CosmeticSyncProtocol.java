package org.chimeramc.client.core.cosmetics;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The wire format for cosmetic sync: a small datagram that advertises which cape, accessory and
 * pet a player has equipped, so other Chimera users in the same world can see it.
 *
 * <p><b>A separate protocol from voice, on purpose.</b> Voice has its own magic bytes and a
 * versioned layout pinned by cross-language golden vectors against the Go relay; adding cosmetic
 * fields there would break that pin and force the relay to change. Cosmetic sync is its own
 * datagram with its own magic, so the two features share the LAN transport idea but neither can
 * corrupt the other's parser. {@link #MAGIC} differs from voice's, so each module ignores the
 * other's packets.
 *
 * <p><b>Ids, not artwork.</b> Only the catalogue ids travel; every client already ships the same
 * catalogue, so a peer resolves an id to the same cape the sender sees. That keeps a datagram
 * under 200 bytes and means a new cape is a catalogue entry, not a protocol change.
 *
 * <p><b>Missing ids read as "none".</b> A malformed or short string is not an error — it is
 * treated as no cosmetic, so a future version that omits a field degrades to "wearing nothing"
 * rather than failing to parse.
 *
 * <p>Pure encoding and decoding, so the round trip is unit-testable with no network or device.
 */
public final class CosmeticSyncProtocol {

    public static final byte[] MAGIC = {'C', 'S'};
    public static final byte VERSION = 1;

    /** A full advertisement of the sender's equipped set. */
    public static final byte TYPE_ADVERTISE = 1;

    /**
     * A prompt to re-advertise immediately.
     *
     * <p>Sent when a peer is first heard: multicast announcements are periodic, so a player who
     * joins after us could wait a full interval to be told what we wear. The request makes every
     * listener answer at once, which is what makes cosmetics appear promptly "on join".
     */
    public static final byte TYPE_REQUEST = 2;

    /** Caps a string so a hostile sender cannot make us allocate without bound. */
    public static final int MAX_STRING = 64;

    /** Comfortably above the largest real datagram; used to size receive buffers. */
    public static final int MAX_PAYLOAD = 512;

    private CosmeticSyncProtocol() {
    }

    /** A decoded packet. Ids are never null; an absent cosmetic is {@link CosmeticCatalog#NONE}. */
    public static final class Advert {
        /** {@link #TYPE_ADVERTISE} or {@link #TYPE_REQUEST}. */
        public final byte type;
        public final String peerId;
        public final String name;
        public final String capeId;
        public final String accessoryId;
        public final String petId;

        Advert(byte type, String peerId, String name, String capeId, String accessoryId,
               String petId) {
            this.type = type;
            this.peerId = peerId == null ? "" : peerId;
            this.name = name == null ? "" : name;
            this.capeId = capeId == null ? CosmeticCatalog.NONE : capeId;
            this.accessoryId = accessoryId == null ? CosmeticCatalog.NONE : accessoryId;
            this.petId = petId == null ? CosmeticCatalog.NONE : petId;
        }

        public boolean isRequest() {
            return type == TYPE_REQUEST;
        }
    }

    /** Encodes one advertisement. Null ids become {@link CosmeticCatalog#NONE}. */
    public static byte[] encode(String peerId, String name, String capeId, String accessoryId,
                                String petId) {
        return encode(TYPE_ADVERTISE, peerId, name, capeId, accessoryId, petId);
    }

    /** Encodes a request for peers to re-advertise immediately. */
    public static byte[] encodeRequest(String peerId, String name) {
        return encode(TYPE_REQUEST, peerId, name,
                CosmeticCatalog.NONE, CosmeticCatalog.NONE, CosmeticCatalog.NONE);
    }

    private static byte[] encode(byte type, String peerId, String name, String capeId,
                                 String accessoryId, String petId) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.write(MAGIC);
            out.writeByte(VERSION);
            out.writeByte(type);
            writeString(out, peerId);
            writeString(out, name);
            writeString(out, capeId == null ? CosmeticCatalog.NONE : capeId);
            writeString(out, accessoryId == null ? CosmeticCatalog.NONE : accessoryId);
            writeString(out, petId == null ? CosmeticCatalog.NONE : petId);
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            return new byte[0];
        }
    }

    /** Decodes a datagram, or returns null when it is not ours or is malformed. */
    public static Advert decode(byte[] data) {
        if (data == null || data.length < MAGIC.length + 2) return null;
        if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) return null;
        if (data[2] != VERSION) return null;
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            in.skipBytes(MAGIC.length);
            in.readByte(); // version, already checked
            byte type = in.readByte();
            if (type != TYPE_ADVERTISE && type != TYPE_REQUEST) return null;
            String peerId = readString(in);
            String name = readString(in);
            String capeId = readString(in);
            String accessoryId = readString(in);
            String petId = readString(in);
            return new Advert(type, peerId, name, capeId, accessoryId, petId);
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING) {
            byte[] clipped = new byte[MAX_STRING];
            System.arraycopy(bytes, 0, clipped, 0, MAX_STRING);
            bytes = clipped;
        }
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING) throw new IOException("string too long");
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
