package org.chimeramc.client.launcher.ui.splash;

/**
 * The splash timing rule, as pure arithmetic.
 *
 * <p>The sequence is <b>time-budgeted, not time-fixed</b>: the loader shows real progress, never
 * more than real progress, and paces itself so the fast path still takes a watchable beat rather
 * than flashing past. Two clocks are involved and this class is the only place they meet:
 *
 * <ul>
 *   <li><b>Real progress</b> -- milestones from actual initialization work, so the block reflects
 *       work being done rather than a decorative timer.</li>
 *   <li><b>Pace</b> -- how fast the loader is allowed to <em>look</em> like it is moving.</li>
 * </ul>
 *
 * <p>{@link #displayed} takes the <b>minimum</b> of the two. That single choice gives both
 * required behaviours for free: if init is slow the display stalls at the real value (the hold
 * state), and if init is instant the pace still carries the loader to the end instead of jumping
 * straight to 100. Progress can therefore never overstate how much work is done, which is the
 * property that makes a progress indicator honest.
 */
public final class SplashTimeline {

    /**
     * How long the loader takes to walk 0 to 1 on its own. Reaching full is the end of loading,
     * so the wind-up and wipe that follow land the whole sequence inside the 2.5-3.5s budget.
     */
    public static final long PACE_MS = 2500L;

    /** The idle re-swing period while the load is held waiting on slow initialization. */
    public static final long HOLD_IDLE_MS = 1500L;

    private SplashTimeline() {
    }

    /**
     * The fraction to draw, in {@code [0,1]}.
     *
     * <p>{@code min(real, paced)}: never ahead of real work, never faster than the pace allows.
     */
    public static float displayed(float realProgress, long elapsedMs) {
        return Math.min(clamp01(realProgress), paced(elapsedMs));
    }

    /** How far the display is allowed to have walked purely on the clock. */
    private static float paced(long elapsedMs) {
        return clamp01(elapsedMs <= 0 ? 0f : elapsedMs / (float) PACE_MS);
    }

    /**
     * Whether loading is finished and the completion sequence may begin.
     *
     * <p>Requires both that real work is done and that the display has actually reached full, so a
     * fast init still gets its paced run-up and a slow one is never cut short.
     */
    public static boolean isLoadComplete(float realProgress, long elapsedMs) {
        return realProgress >= 1f && displayed(realProgress, elapsedMs) >= 1f;
    }

    /**
     * Whether the loader is held waiting on initialization that is slower than the pace.
     *
     * <p>Tested against the <em>pace</em>, not the display. The display is {@code min(real, paced)},
     * so requiring {@code displayed >= 1} while also requiring {@code real < 1} is unsatisfiable --
     * the two conditions can never hold at once and the hold state would never fire. What actually
     * defines a hold is "the clock has run out but the work has not", which is a pace comparison.
     *
     * <p>This is a state, not an error: the block freezes at its current crack stage and the
     * pickaxe idles rather than looping, so slow initialization looks deliberate instead of stuck.
     */
    public static boolean isHolding(float realProgress, long elapsedMs) {
        return realProgress < 1f && paced(elapsedMs) >= 1f;
    }

    /** The crack stage to show for the given real progress and elapsed time. */
    public static int crackStage(float realProgress, long elapsedMs) {
        return OreCrackSprites.crackStageFor(displayed(realProgress, elapsedMs));
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) return 0f;
        if (value < 0f) return 0f;
        return value > 1f ? 1f : value;
    }
}
