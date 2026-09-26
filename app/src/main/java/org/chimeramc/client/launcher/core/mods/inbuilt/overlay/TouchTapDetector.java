package org.chimeramc.client.core.mods.inbuilt.overlay;

/**
 * Detects the touch gesture that Bedrock treats as an attack: a short tap that does not drag.
 *
 * The other attack paths (a mouse button, a controller trigger, the on-screen mouse button) all
 * funnel through a single "send mouse button" call, so they are trivial to observe. A vanilla
 * touchscreen tap does not: the game consumes the raw {@code MotionEvent} itself and decides
 * whether the gesture was an attack or a camera drag. Watching the same events and classifying
 * them here is the only way to feed the Select Hit metronome the same input the game acted on.
 *
 * The rule is the one the game uses: contact that lifts quickly and without travelling further
 * than touch slop is a tap; anything that drags is a look gesture and must not reset the
 * timing window, or every camera movement would forge a phantom swing.
 *
 * Pure and clock-injected so the classification is unit-testable without a device.
 */
public final class TouchTapDetector {

    /** A press held longer than this is a hold (mining/using), not an attack tap. */
    public static final long MAX_TAP_MS = 220L;

    private int activePointerId = -1;
    private float downX, downY;
    private long downAtMs;
    private boolean tracking;
    private boolean movedBeyondSlop;

    /**
     * Begin tracking a gesture.
     *
     * @param pointerId the pointer that went down
     */
    public void onDown(int pointerId, float x, float y, long nowMs) {
        activePointerId = pointerId;
        downX = x;
        downY = y;
        downAtMs = nowMs;
        tracking = true;
        movedBeyondSlop = false;
    }

    /**
     * Track movement so a drag can be ruled out.
     *
     * @param slop the platform's scaled touch slop
     */
    public void onMove(int pointerId, float x, float y, float slop) {
        if (!tracking || pointerId != activePointerId || movedBeyondSlop) return;
        float dx = x - downX;
        float dy = y - downY;
        if (dx * dx + dy * dy > slop * slop) {
            movedBeyondSlop = true;
        }
    }

    /**
     * Finish the gesture and report whether it was an attack tap.
     *
     * A pointer id that is not the tracked one means some other finger lifted; the gesture is
     * still in progress, so tracking continues. Ending it there would drop the real tap the
     * moment a second finger — a jump or sneak button — was released.
     */
    public boolean onUp(int pointerId, long nowMs) {
        if (!tracking) return false;
        if (pointerId != activePointerId) return false;
        boolean tap = !movedBeyondSlop && (nowMs - downAtMs) <= MAX_TAP_MS;
        tracking = false;
        activePointerId = -1;
        movedBeyondSlop = false;
        return tap;
    }

    /** A cancelled gesture (a second finger, a system intercept) is never an attack. */
    public void onCancel() {
        tracking = false;
        activePointerId = -1;
        movedBeyondSlop = false;
    }

    public boolean isTracking() {
        return tracking;
    }
}
