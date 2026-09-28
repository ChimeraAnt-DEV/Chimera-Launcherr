package org.chimeramc.client.core.voice;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reorders and paces one peer's audio frames so playback stays smooth across a jittery link.
 *
 * <p>On a LAN, frames arrive in order and essentially on time, so playing each one the moment it
 * arrives is fine. Over the internet they do not: they arrive out of order, in bursts, and with
 * gaps. Playing on arrival turns that into chopped audio, because the speaker is fed a 20&nbsp;ms
 * frame at whatever irregular moment the network delivered it.
 *
 * <p>The buffer holds a small depth of frames, releases them in sequence order, and adapts that
 * depth to the jitter it observes: a calm link settles to one or two frames (~20-40&nbsp;ms of
 * added latency) and a rough one widens to absorb the spread, trading a little latency for
 * continuity. Frames that arrive after their slot has passed are dropped, and a slot that never
 * arrives is skipped once it has waited too long, so one lost packet is one silent 20&nbsp;ms and
 * not a stall.
 *
 * <p>It is pure — a map, a clock passed in by the caller, and arithmetic — so the pacing and the
 * adaptive depth are unit-tested without a socket or a device. One instance belongs to exactly one
 * peer, because sequencing only makes sense within a single sender's stream.
 */
public final class VoiceJitterBuffer {

    /** The smallest buffer depth, in frames: 20 ms of latency. */
    public static final int MIN_DEPTH = 1;
    /** The largest buffer depth, in frames: 200 ms, the point past which talking over someone is worse than a glitch. */
    public static final int MAX_DEPTH = 10;
    /** The depth a fresh peer starts at: 3 frames, 60 ms — enough to cover ordinary internet jitter. */
    public static final int START_DEPTH = 3;
    /** How long the head of the queue may wait before it is released even if the buffer is shallow. */
    public static final long MAX_WAIT_MS = 120;

    private final TreeMap<Long, Frame> frames = new TreeMap<>();
    private final int capacity;

    private int targetDepth = START_DEPTH;
    private boolean haveBase;
    private int lastRawSequence;
    private long extended;
    private long nextSequence = Long.MIN_VALUE;
    private long lastArrivalMs;
    private float jitterMs;
    private long dropped;
    private long concealed;
    private boolean started;
    private boolean primed;

    private static final class Frame {
        final byte[] payload;
        final long arrivalMs;

        Frame(byte[] payload, long arrivalMs) {
            this.payload = payload;
            this.arrivalMs = arrivalMs;
        }
    }

    /** @param capacity the most frames to hold before the oldest are discarded as too late */
    public VoiceJitterBuffer(int capacity) {
        this.capacity = Math.max(MAX_DEPTH * 2, capacity);
    }

    public static VoiceJitterBuffer forRelay() {
        return new VoiceJitterBuffer(MAX_DEPTH * 4);
    }

    /**
     * Accepts a frame from the peer.
     *
     * @param sequence the sender's sequence number (wraps as an int, handled here)
     * @param payload the encoded frame
     * @param nowMs the arrival time
     * @return true when the frame was buffered, false when it was too late or a duplicate
     */
    public boolean offer(int sequence, byte[] payload, long nowMs) {
        if (payload == null || payload.length == 0) return false;
        long position = extend(sequence);

        // A frame behind the play head is late only once the buffer has actually started
        // releasing; before that the lowest sequence seen is still the start of the stream, and
        // the first datagram to arrive is not necessarily that lowest one.
        if (started && primed && position < nextSequence) {
            dropped++;
            return false;
        }
        if (frames.containsKey(position)) {
            dropped++;
            return false;
        }

        frames.put(position, new Frame(payload, nowMs));
        updateJitter(nowMs);

        if (!started) {
            started = true;
        }
        // Until the first release the head is whatever the lowest buffered sequence is, so an
        // early frame that overtook the one that arrived first is not mistaken for a late one.
        if (!primed) {
            nextSequence = frames.firstKey();
        }
        // Bound the buffer: a peer that floods faster than it is drained must not grow without end.
        while (frames.size() > capacity) {
            Map.Entry<Long, Frame> oldest = frames.pollFirstEntry();
            if (oldest == null) break;
            if (oldest.getKey() >= nextSequence) nextSequence = oldest.getKey() + 1;
            dropped++;
        }
        return true;
    }

    /**
     * Returns the next frame to play, or null when the buffer should stay silent this tick.
     *
     * <p>It first <em>primes</em>: nothing plays until the buffer has the adaptive target depth or
     * the head has waited past {@link #MAX_WAIT_MS}. The second condition is what keeps a link that
     * delivers in bursts — a depth that never fills — from going silent. Once primed it drains one
     * frame per call, which is the playback clock's job to pace; re-requiring the target depth on
     * every call would stall the stream the moment it dipped below the cushion.
     */
    public byte[] poll(long nowMs) {
        if (!started || frames.isEmpty()) return null;

        if (!primed) {
            Frame head = frames.get(nextSequence);
            long waited = head != null
                    ? nowMs - head.arrivalMs
                    : nowMs - frames.firstEntry().getValue().arrivalMs;
            if (frames.size() < targetDepth && waited < MAX_WAIT_MS) {
                return null;
            }
            primed = true;
        }

        Frame head = frames.get(nextSequence);
        if (head != null) {
            frames.remove(nextSequence);
            nextSequence++;
            return head.payload;
        }

        // The expected sequence never arrived. Skip it once the frames after it have waited long
        // enough, so a lost packet costs one slot and not the rest of the stream.
        Map.Entry<Long, Frame> first = frames.firstEntry();
        if (first == null) return null;
        if (first.getKey() < nextSequence) {
            // Stale leftovers from before a skip; discard them.
            while (!frames.isEmpty() && frames.firstKey() < nextSequence) {
                frames.pollFirstEntry();
                dropped++;
            }
            return null;
        }
        long waited = nowMs - first.getValue().arrivalMs;
        if (waited >= MAX_WAIT_MS) {
            concealed++;
            nextSequence = first.getKey() + 1;
            return frames.pollFirstEntry().getValue().payload;
        }
        return null;
    }

    /**
     * Widens or narrows the target depth from the observed arrival jitter.
     *
     * <p>Jitter is tracked as an exponential moving average of the change in inter-arrival time,
     * the standard cheap estimate. Two frames of depth cover roughly one frame's worth of spread,
     * so the mapping is depth = START + jitter/20ms, clamped. A steady link drifts back down to
     * the minimum, which matters because added latency is the thing players actually notice.
     */
    private void updateJitter(long nowMs) {
        if (lastArrivalMs != 0) {
            long delta = nowMs - lastArrivalMs;
            float deviation = Math.abs(delta - VoiceAudioEngine.FRAME_MS);
            jitterMs = jitterMs * 0.9f + deviation * 0.1f;
            int desired = START_DEPTH + (int) (jitterMs / VoiceAudioEngine.FRAME_MS);
            targetDepth = Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, desired));
        }
        lastArrivalMs = nowMs;
    }

    /**
     * Maps a wrapping sequence number to a monotonically increasing position.
     *
     * <p>Sequence numbers are 32-bit and wrap; comparing them directly would make a stream that
     * crossed the wrap look like it jumped backwards by four billion. The signed difference of two
     * ints is wrap-safe as long as fewer than 2^31 frames separate them, which at 50 frames per
     * second is over a year.
     */
    private long extend(int sequence) {
        if (!haveBase) {
            haveBase = true;
            lastRawSequence = sequence;
            extended = 0;
            return extended;
        }
        extended += (sequence - lastRawSequence);
        lastRawSequence = sequence;
        return extended;
    }

    /** The adaptive buffer depth in frames, for diagnostics. */
    public int targetDepth() {
        return targetDepth;
    }

    /** Frames currently held. */
    public int size() {
        return frames.size();
    }

    /** Frames discarded as late, duplicate, or overflowing. */
    public long droppedCount() {
        return dropped;
    }

    /** Slots skipped because the frame never arrived. */
    public long concealedCount() {
        return concealed;
    }

    /** The smoothed arrival jitter in milliseconds, for diagnostics. */
    public float jitterMs() {
        return jitterMs;
    }

    /** Discards everything; used when a peer leaves or the session restarts. */
    public void reset() {
        frames.clear();
        haveBase = false;
        extended = 0;
        nextSequence = Long.MIN_VALUE;
        lastArrivalMs = 0;
        jitterMs = 0f;
        targetDepth = START_DEPTH;
        started = false;
        primed = false;
    }

    /** Removes the oldest frames, for a caller that needs to drain without playing. */
    public void clear() {
        frames.clear();
    }

    /** Iterates the buffered positions, for tests and diagnostics. */
    Iterator<Map.Entry<Long, Frame>> frameIterator() {
        return frames.entrySet().iterator();
    }
}
