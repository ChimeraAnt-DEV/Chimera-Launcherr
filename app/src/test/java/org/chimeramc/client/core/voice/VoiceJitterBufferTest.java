package org.chimeramc.client.core.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Pins the jitter buffer's reordering, pacing and adaptive depth. The buffer is pure, so the
 * behaviour a jittery link depends on is tested directly rather than inferred from audio.
 */
public class VoiceJitterBufferTest {

    private static byte[] frame(int marker) {
        return new byte[]{(byte) marker, (byte) marker};
    }

    @Test
    public void outOfOrderFramesAreReleasedInSequenceOrder() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;

        // Arrive 2, 1, 3 — the buffer must play 1, 2, 3.
        buffer.offer(2, frame(2), now);
        buffer.offer(1, frame(1), now + 1);
        buffer.offer(3, frame(3), now + 2);

        assertEquals(1, buffer.poll(now + 40)[0]);
        assertEquals(2, buffer.poll(now + 60)[0]);
        assertEquals(3, buffer.poll(now + 80)[0]);
    }

    @Test
    public void aFrameIsHeldUntilTheBufferHasDepth() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        buffer.offer(1, frame(1), now);

        // One frame is below the start depth (3), and it has not waited past the maximum, so the
        // buffer stays silent rather than playing immediately.
        assertNull(buffer.poll(now + 1));
    }

    @Test
    public void aBurstThatNeverFillsTheDepthStillPlaysAfterTheMaximumWait() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        buffer.offer(1, frame(1), now);

        // A link that delivers one frame and then nothing must not go permanently silent.
        byte[] played = buffer.poll(now + VoiceJitterBuffer.MAX_WAIT_MS + 1);
        assertNotNull(played);
        assertEquals(1, played[0]);
    }

    @Test
    public void aLostFrameIsSkippedRatherThanStalling() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        // Sequence 2 never arrives.
        buffer.offer(1, frame(1), now);
        buffer.offer(3, frame(3), now + 1);
        buffer.offer(4, frame(4), now + 2);

        assertEquals(1, buffer.poll(now + 40)[0]);
        // 2 is missing; after the wait, the buffer advances past it to 3.
        assertEquals(3, buffer.poll(now + 40 + VoiceJitterBuffer.MAX_WAIT_MS + 1)[0]);
        assertEquals(4, buffer.poll(now + 40 + VoiceJitterBuffer.MAX_WAIT_MS + 40)[0]);
        assertTrue(buffer.concealedCount() >= 1);
    }

    @Test
    public void aDuplicateFrameIsDropped() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        assertTrue(buffer.offer(1, frame(1), now));
        assertFalse(buffer.offer(1, frame(9), now + 1));
        assertTrue(buffer.droppedCount() >= 1);
    }

    @Test
    public void aFrameThatArrivesAfterItsSlotIsDropped() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        buffer.offer(1, frame(1), now);
        buffer.offer(2, frame(2), now + 1);
        buffer.offer(3, frame(3), now + 2);
        buffer.poll(now + 40); // plays 1
        buffer.poll(now + 60); // plays 2

        // 1 is now in the past; accepting it would replay old audio.
        assertFalse(buffer.offer(1, frame(1), now + 80));
    }

    @Test
    public void sequenceWraparoundIsHandled() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        int nearMax = Integer.MAX_VALUE - 1;

        buffer.offer(nearMax, frame(1), now);
        buffer.offer(Integer.MAX_VALUE, frame(2), now + 1);
        buffer.offer(Integer.MIN_VALUE, frame(3), now + 2); // wrapped

        // All three are consecutive once the wrap is accounted for, so all three play in order.
        assertEquals(1, buffer.poll(now + 40)[0]);
        assertEquals(2, buffer.poll(now + 60)[0]);
        assertEquals(3, buffer.poll(now + 80)[0]);
    }

    @Test
    public void aSteadyLinkNarrowsTheBufferAndAJitteryOneWidensIt() {
        VoiceJitterBuffer steady = new VoiceJitterBuffer(64);
        VoiceJitterBuffer jittery = new VoiceJitterBuffer(64);
        long now = 1000;
        for (int i = 0; i < 40; i++) {
            steady.offer(i, frame(i), now + i * VoiceAudioEngine.FRAME_MS);
            // The jittery stream alternates early/late by a frame or more.
            long offset = (i % 2 == 0) ? -15 : 25;
            jittery.offer(i, frame(i), now + i * VoiceAudioEngine.FRAME_MS + offset);
        }
        assertTrue("a steady link should settle at or near the minimum",
                steady.targetDepth() <= VoiceJitterBuffer.START_DEPTH);
        assertTrue("a jittery link should widen past the steady one",
                jittery.targetDepth() > steady.targetDepth());
    }

    @Test
    public void theDepthIsBounded() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        long now = 1000;
        // A wildly jittery stream must not push the depth past the maximum latency.
        for (int i = 0; i < 200; i++) {
            long offset = (i % 2 == 0) ? -500 : 500;
            buffer.offer(i, frame(i), now + i * VoiceAudioEngine.FRAME_MS + offset);
        }
        assertTrue(buffer.targetDepth() <= VoiceJitterBuffer.MAX_DEPTH);
        assertTrue(buffer.targetDepth() >= VoiceJitterBuffer.MIN_DEPTH);
    }

    @Test
    public void aFloodCannotGrowTheBufferWithoutEnd() {
        // The constructor floors the capacity at twice the maximum depth; that floor is the bound.
        int capacity = VoiceJitterBuffer.MAX_DEPTH * 2;
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(capacity);
        long now = 1000;
        for (int i = 0; i < 500; i++) {
            buffer.offer(i, frame(i), now + i);
        }
        assertTrue("buffer must stay bounded, was " + buffer.size(), buffer.size() <= capacity);
    }

    @Test
    public void anEmptyPayloadIsRejected() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        assertFalse(buffer.offer(1, new byte[0], 1000));
        assertFalse(buffer.offer(1, null, 1000));
    }

    @Test
    public void resetClearsTheState() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        buffer.offer(5, frame(5), 1000);
        buffer.reset();
        assertEquals(0, buffer.size());
        // A fresh stream after reset is sequenced from its own first frame.
        assertTrue(buffer.offer(2, frame(2), 2001));
        assertTrue(buffer.offer(3, frame(3), 2002));
        assertTrue(buffer.offer(4, frame(4), 2003));
        assertEquals(2, buffer.poll(2010)[0]);
    }

    @Test
    public void pollOnAnEmptyBufferReturnsNull() {
        VoiceJitterBuffer buffer = new VoiceJitterBuffer(64);
        assertNull(buffer.poll(1000));
    }
}
