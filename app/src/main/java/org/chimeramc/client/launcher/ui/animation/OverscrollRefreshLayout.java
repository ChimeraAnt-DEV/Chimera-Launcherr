package org.chimeramc.client.ui.animation;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * A minimal pull-to-refresh container that uses the same overshoot physics as the rest of the
 * launcher's motion, instead of a stock spinner.
 *
 * <p>The gesture has no spinner, no indicator ring and no progress bar: the content itself is
 * dragged down against a spring and released. Pulling past {@link #THRESHOLD_DP} triggers the
 * refresh; anything less simply springs back. That keeps the reactive feel consistent with the
 * press-scale and arrival work rather than introducing a second visual language for "waiting".
 *
 * <p>Deliberately hand-rolled rather than pulling in {@code SwipeRefreshLayout}: the only thing
 * needed is a damped drag plus a spring release, which is a few dozen lines against a dependency
 * that would also bring its own spinner, colours and nested-scroll quirks.
 *
 * <p>Only one child is supported, and only a single touch pointer is tracked. A drag that starts
 * while the child is scrolled away from its top is ignored, so the list scrolls normally and the
 * gesture cannot steal a scroll.
 */
public class OverscrollRefreshLayout extends FrameLayout {

    /** How far the content must be pulled past the damping before a refresh fires. */
    public static final float THRESHOLD_DP = 72f;

    /** How far the content follows the finger; below 1 so the pull feels resisted. */
    private static final float DAMPING = 0.5f;

    /** Called on the main thread once a pull passes the threshold and the finger lifts. */
    public interface OnRefreshListener {
        void onRefresh();
    }

    private OnRefreshListener listener;
    private SpringAnimation releaseY;
    private float dragStartY;
    private float currentPull;
    private boolean dragging;
    private int touchSlop;
    private float thresholdPx;

    public OverscrollRefreshLayout(Context context) {
        super(context);
        init(context);
    }

    public OverscrollRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public OverscrollRefreshLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        thresholdPx = THRESHOLD_DP * context.getResources().getDisplayMetrics().density;
        releaseY = new SpringAnimation(this, DynamicAnimation.TRANSLATION_Y, 0f);
        releaseY.setSpring(new SpringForce(0f)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (getChildCount() == 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragStartY = event.getY();
                currentPull = 0f;
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (atTop() && event.getY() - dragStartY > touchSlop) {
                    // Take the gesture only once the pull is clearly downward from the top.
                    dragging = true;
                    dragStartY = event.getY();
                    releaseY.skipToEnd();
                    return true;
                }
                break;
            default:
                break;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (getChildCount() == 0) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    currentPull = Math.max(0f, (event.getY() - dragStartY) * DAMPING);
                    setTranslationY(currentPull);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    boolean refresh = currentPull >= thresholdPx;
                    // Release always springs back; the refresh itself is not visual, so there is
                    // nothing to keep the content displaced for.
                    releaseY.start();
                    if (refresh && listener != null) listener.onRefresh();
                    return true;
                }
                break;
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    /** Whether the single child is scrolled to (or has no) top. */
    private boolean atTop() {
        View child = getChildAt(0);
        if (child instanceof android.widget.ScrollView) {
            return !((android.widget.ScrollView) child).canScrollVertically(-1);
        }
        return !child.canScrollVertically(-1);
    }
}
