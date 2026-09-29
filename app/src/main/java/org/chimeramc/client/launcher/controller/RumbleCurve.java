package org.chimeramc.client.launcher.controller;

/**
 * Turns a profile's rumble-strength percentage into a device vibration amplitude.
 *
 * <p>Pure so the mapping is unit-testable: the platform's amplitude scale is 1..255, and a
 * strength of 0 must mean "off" rather than "minimum vibration", which is the mistake that
 * makes a rumble slider feel like it does nothing at the low end.
 */
public final class RumbleCurve {

    /** The platform's amplitude range, from {@code Vibrator.vibrate}. */
    public static final int MIN_AMPLITUDE = 1;
    public static final int MAX_AMPLITUDE = 255;

    /** Strength is a percentage; 0 disables rumble entirely. */
    public static final int MIN_STRENGTH = 0;
    public static final int MAX_STRENGTH = 100;
    public static final int DEFAULT_STRENGTH = 70;

    private RumbleCurve() {
    }

    /**
     * The amplitude for a strength percentage, or 0 when rumble is off.
     *
     * <p>Clamped rather than rejected: a profile restored from an older build (or edited by hand)
     * cannot produce an out-of-range amplitude that the platform would throw on.
     */
    public static int amplitude(int strengthPercent) {
        int clamped = clampStrength(strengthPercent);
        if (clamped <= 0) return 0;
        // Scale across the whole 1..255 range so 1% is still a perceptible tick rather than a
        // rounding victim, which a naive strength/100*255 would make it.
        int span = MAX_AMPLITUDE - MIN_AMPLITUDE;
        return MIN_AMPLITUDE + Math.round(span * (clamped / 100f));
    }

    public static int clampStrength(int strengthPercent) {
        return Math.max(MIN_STRENGTH, Math.min(MAX_STRENGTH, strengthPercent));
    }

    /** Whether the strength disables rumble, i.e. the caller should not vibrate at all. */
    public static boolean isOff(int strengthPercent) {
        return clampStrength(strengthPercent) <= 0;
    }
}
