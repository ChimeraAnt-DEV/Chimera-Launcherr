package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * The {@link CosmeticFrame} wire layout, pinned against the native reader.
 *
 * <p>The layout is a hand-rolled little-endian buffer both sides must agree on; a wrong field order
 * or a missing length prefix is invisible in Java and only shows up when the native render hook
 * misreads every value after the mistake — with no device in the build environment to catch it. So
 * the exact bytes are asserted here.
 */
public class CosmeticFrameTest {

    private static ByteBuffer le(byte[] bytes) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    public void anEmptyFrameCarriesTheMagicAndNoFlags() {
        byte[] bytes = CosmeticFrame.empty().toBytes();
        ByteBuffer buf = le(bytes);
        assertEquals(CosmeticFrame.MAGIC, buf.getInt());
        assertEquals(0, buf.getInt());
        assertEquals(0, buf.getInt()); // cape segments
    }

    @Test
    public void aFullFrameRoundTripsThroughTheWireLayout() {
        PetPose pose = new PetPose();
        pose.compute(CosmeticCatalog.PetSpecies.CAT, CosmeticCatalog.PetLocomotion.WALK, 0.25f);
        CosmeticFrame frame = CosmeticFrame.build(
                CosmeticCatalog.cape("chimera"), CosmeticCatalog.accessory("headphones"),
                CosmeticCatalog.pet("cat"), pose,
                0.5, false, 0.0, 1.25, 0.0, 30.0, 0f, 0f, CapeGeometry.SEGMENT_COUNT);
        byte[] bytes = frame.toBytes();
        ByteBuffer buf = le(bytes);

        assertEquals(CosmeticFrame.MAGIC, buf.getInt());
        int flags = buf.getInt();
        assertTrue("cape flag", (flags & CosmeticFrame.FLAG_CAPE) != 0);
        assertTrue("accessory flag", (flags & CosmeticFrame.FLAG_ACCESSORY) != 0);
        assertTrue("pet flag", (flags & CosmeticFrame.FLAG_PET) != 0);

        int capeSegments = buf.getInt();
        assertEquals(CapeGeometry.SEGMENT_COUNT, capeSegments);
        for (int i = 0; i < capeSegments * 2; i++) {
            float value = buf.getFloat();
            assertTrue("cape value finite", Float.isFinite(value));
        }
        assertEquals(CosmeticCatalog.AccessoryKind.HEADPHONES.ordinal(), buf.getInt());
        buf.getFloat(); // head pitch
        buf.getFloat(); // head yaw
        int bones = buf.getInt();
        assertTrue("pet bones emitted", bones >= 3);
        for (int i = 0; i < bones; i++) {
            int nameLen = buf.getShort() & 0xFFFF;
            byte[] name = new byte[nameLen];
            buf.get(name);
            String bone = new String(name, StandardCharsets.UTF_8);
            assertTrue("bone named", bone.length() > 0);
            assertTrue(Float.isFinite(buf.getFloat()));
            assertTrue(Float.isFinite(buf.getFloat()));
            assertTrue(Float.isFinite(buf.getFloat()));
        }
        // Trailing pet body/offset floats must be present and finite.
        for (int i = 0; i < 6; i++) {
            assertTrue(Float.isFinite(buf.getFloat()));
        }
        assertEquals("buffer fully consumed", 0, buf.remaining());
    }

    @Test
    public void noCapeMeansNoSegmentsAndTheFlagIsClear() {
        CosmeticFrame frame = CosmeticFrame.build(null, null, null, null,
                0.0, false, 0.0, 0.0, 0.0, 0.0, 0f, 0f, CapeGeometry.SEGMENT_COUNT);
        byte[] bytes = frame.toBytes();
        ByteBuffer buf = le(bytes);
        assertEquals(CosmeticFrame.MAGIC, buf.getInt());
        assertEquals(0, buf.getInt());
        assertEquals(0, buf.getInt());
    }
}
