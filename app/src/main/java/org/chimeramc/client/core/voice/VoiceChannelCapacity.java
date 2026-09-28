package org.chimeramc.client.core.voice;

/**
 * The capacity rule for public voice channels, as pure integer maths.
 *
 * <p><b>Capacity is advisory, not enforced.</b> This is a launcher-side peer link over multicast:
 * there is no server and no membership protocol, so a host cannot actually refuse a datagram it
 * receives. What capacity can honestly do is advertise a limit and let a would-be joiner decline
 * on itself: the directory shows "3/8" and a full public room reports that it is full, and the
 * join path respects that. Saying "host picks max member count" without this note would imply a
 * server-side kick that does not exist.
 *
 * <p>Private channels have no capacity setting by design: they are join-by-code only, so the set
 * of people who can join is already exactly the set the host gave the code to. Adding a number
 * would be a second, redundant cap.
 *
 * <p>Pure and Android-free, so "a room advertising 2 members and a cap of 2 is full while one
 * advertising 1 is not" is a unit test rather than a device test.
 */
public final class VoiceChannelCapacity {

    /** The smallest cap a host may advertise; a smaller room is pointless. */
    public static final int MIN_HOST_CAPACITY = 2;
    /** The largest cap the settings UI offers; the wire allows up to {@link VoiceProtocol#MAX_CAPACITY}. */
    public static final int MAX_HOST_CAPACITY = 50;

    private VoiceChannelCapacity() {
    }

    /** Whether {@code memberCount} has reached {@code capacity}; a non-positive cap means never. */
    public static boolean isFull(int memberCount, int capacity) {
        if (capacity <= VoiceProtocol.CAPACITY_NONE) return false;
        return memberCount >= capacity;
    }

    /**
     * Whether a join should be allowed.
     *
     * <p>A private channel is always allowed here (the code is the cap). A public channel is
     * refused only when its advertised count has reached its advertised cap.
     */
    public static boolean canJoin(int memberCount, int capacity, boolean isPrivate) {
        if (isPrivate) return true;
        return !isFull(memberCount, capacity);
    }

    /** Clamps a host-chosen capacity into the settings range; a non-positive value means "no cap". */
    public static int clampHostCapacity(int value) {
        if (value <= VoiceProtocol.CAPACITY_NONE) return VoiceProtocol.CAPACITY_NONE;
        return Math.max(MIN_HOST_CAPACITY, Math.min(MAX_HOST_CAPACITY, value));
    }

    /** A compact "3/8", or just "3" when there is no cap, for a directory row. */
    public static String describe(int memberCount, int capacity) {
        if (capacity <= VoiceProtocol.CAPACITY_NONE) return String.valueOf(memberCount);
        return memberCount + "/" + capacity;
    }
}
