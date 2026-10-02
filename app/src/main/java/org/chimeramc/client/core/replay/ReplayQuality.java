package org.chimeramc.client.core.replay;

/**
 * The recorder's quality profiles.
 *
 * <p>The spec targets 1080p60 and falls back to 720p on low-end devices. This decides the
 * profile, so the choice is a unit test rather than a guess buried in the encoder setup — and the
 * fallback profile is a real recorder setting, not a resized 1080p.
 */
public final class ReplayQuality {

    public static final class Profile {
        public final int width;
        public final int height;
        public final int frameRate;
        /** Bits per second handed to the encoder. */
        public final int bitRate;

        Profile(int width, int height, int frameRate, int bitRate) {
            this.width = width;
            this.height = height;
            this.frameRate = frameRate;
            this.bitRate = bitRate;
        }

        public String label() {
            return height + "p" + frameRate;
        }
    }

    public static final Profile HIGH = new Profile(1920, 1080, 60, 12_000_000);
    public static final Profile LOW = new Profile(1280, 720, 30, 6_000_000);

    private ReplayQuality() {
    }

    /**
     * The profile to record with.
     *
     * @param lowEndDevice whether the device is considered low-end; the caller supplies its own
     *                     signal so this stays pure.
     */
    public static Profile select(boolean lowEndDevice) {
        return lowEndDevice ? LOW : HIGH;
    }

    /**
     * A conservative low-end heuristic from the total RAM the platform reports.
     *
     * <p>An encoder at 1080p60 needs roughly half a gigabyte of scratch; under 3 GB the device is
     * treated as low-end and gets 720p30. Unknown RAM (zero) is treated as low-end, because a
     * failed recording on an unknown device is worse than a smaller one.
     */
    public static boolean isLowEnd(long totalMemoryBytes) {
        if (totalMemoryBytes <= 0L) return true;
        return totalMemoryBytes < 3L * 1024L * 1024L * 1024L;
    }
}
