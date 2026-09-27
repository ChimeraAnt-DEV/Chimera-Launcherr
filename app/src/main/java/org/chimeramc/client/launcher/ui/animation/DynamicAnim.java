package org.chimeramc.client.ui.animation;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;
import androidx.recyclerview.widget.RecyclerView;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import org.chimeramc.client.R;

public final class DynamicAnim {
    private DynamicAnim() {}

    private static final long DIALOG_DISMISS_DURATION_MS = 160L;
    private static float globalSpeedMultiplier = 1.0f;
    private static boolean animationsEnabled = true;

    public static void setGlobalSpeedMultiplier(float multiplier) {
        globalSpeedMultiplier = Math.max(0.1f, multiplier);
    }

    public static float getGlobalSpeedMultiplier() {
        return globalSpeedMultiplier;
    }

    public static void enableAnimations() {
        animationsEnabled = true;
    }

    public static void disableAnimations() {
        animationsEnabled = false;
    }

    public static boolean areAnimationsEnabled() {
        return animationsEnabled;
    }

    public static SpringAnimation springAlphaTo(View view, float target) {
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.ALPHA, target);
        anim.setSpring(new SpringForce(target)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        return anim;
    }

    public static SpringAnimation springTranslationYTo(View view, float target) {
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.TRANSLATION_Y, target);
        anim.setSpring(new SpringForce(target)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        return anim;
    }

    public static SpringAnimation springTranslationXTo(View view, float target) {
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.TRANSLATION_X, target);
        anim.setSpring(new SpringForce(target)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        return anim;
    }

    public static SpringAnimation springScaleXTo(View view, float target) {
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.SCALE_X, target);
        anim.setSpring(new SpringForce(target)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        return anim;
    }

    public static SpringAnimation springScaleYTo(View view, float target) {
        SpringAnimation anim = new SpringAnimation(view, DynamicAnimation.SCALE_Y, target);
        anim.setSpring(new SpringForce(target)
                .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW));
        return anim;
    }

    /** View tag marking a view that already carries the press listener. */
    private static final int KEY_PRESS_STATE = R.id.dynamic_anim_press_state;

    private static final float PRESS_SCALE = 0.955f;
    private static final float PRESS_Z_DP = 6f;

    /**
     * Per-view press state. The two springs are built once and re-targeted on every
     * touch, so scrolling a long list does not allocate a SpringAnimation per event.
     */
    private static final class PressState {
        final SpringAnimation scaleX;
        final SpringAnimation scaleY;
        final View.OnTouchListener delegate;
        final int touchSlop;
        float downX;
        float downY;
        boolean dragging;
        boolean pressed;

        PressState(View view, View.OnTouchListener delegate) {
            this.delegate = delegate;
            this.touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
            scaleX = new SpringAnimation(view, DynamicAnimation.SCALE_X, 1f);
            scaleY = new SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f);
            SpringForce force = new SpringForce(1f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_HIGH);
            scaleX.setSpring(force);
            // Separate SpringForce instances: a SpringForce cannot be shared between
            // two animations, it holds the animation's own state.
            scaleY.setSpring(new SpringForce(1f)
                    .setDampingRatio(SpringForce.DAMPING_RATIO_NO_BOUNCY)
                    .setStiffness(SpringForce.STIFFNESS_HIGH));
        }

        void press() {
            pressed = true;
            dragging = false;
            // Velocity must be set before re-targeting: animateToFinalPosition starts
            // the animation immediately, so a velocity applied afterwards is dropped.
            scaleX.setStartVelocity(-1.5f);
            scaleY.setStartVelocity(-1.5f);
            scaleX.animateToFinalPosition(PRESS_SCALE);
            scaleY.animateToFinalPosition(PRESS_SCALE);
        }

        void release() {
            if (!pressed) return;
            pressed = false;
            scaleX.setStartVelocity(1.5f);
            scaleY.setStartVelocity(1.5f);
            scaleX.animateToFinalPosition(1f);
            scaleY.animateToFinalPosition(1f);
        }

        /** Drop the pressed look immediately, e.g. once the gesture became a scroll. */
        void snapBack(View view) {
            if (!pressed) return;
            pressed = false;
            scaleX.cancel();
            scaleY.cancel();
            view.setScaleX(1f);
            view.setScaleY(1f);
        }
    }

    /**
     * Apply press-scale + elevation + haptic feedback to a view.
     *
     * The listener never consumes the event, so click handling still runs. Pass
     * {@code delegate} when the view needs its own touch listener; it is invoked after
     * the press effect and its return value is honoured. Installing press feedback with
     * {@code setOnTouchListener} would otherwise silently replace that listener — for a
     * RecyclerView drag handle that means drag-to-reorder stops working with no error.
     */
    public static void applyPressScale(View view, @Nullable View.OnTouchListener delegate) {
        if (view == null) return;
        if (view.getTag(KEY_PRESS_STATE) != null) return;

        view.setClickable(true);
        final PressState state = new PressState(view, delegate);
        view.setTag(KEY_PRESS_STATE, state);

        view.setOnTouchListener((v, event) -> {
            if (animationsEnabled) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        state.downX = event.getX();
                        state.downY = event.getY();
                        state.press();
                        animateElevation(v, PRESS_Z_DP, 8f);
                        UiTouchFeedback.pressView(v);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        // Once the finger travels past touch slop the gesture belongs to
                        // an enclosing scroll view; un-shrink so rows do not stay dimmed.
                        if (!state.dragging
                                && Math.hypot(event.getX() - state.downX,
                                event.getY() - state.downY) > state.touchSlop) {
                            state.dragging = true;
                            state.snapBack(v);
                            animateElevation(v, 0f, 8f);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        state.release();
                        animateElevation(v, 0f, 8f);
                        break;
                    default:
                        break;
                }
            }
            return state.delegate != null && state.delegate.onTouch(v, event);
        });
    }

    /**
     * Apply press feedback to a view that manages its own click, not its own touch.
     * Callers that need a touch listener must use the two-argument overload.
     */
    public static void applyPressScale(View view) {
        applyPressScale(view, null);
    }

    private static void animateElevation(View view, float target, float durationMs) {
        try {
            view.animate().cancel();
            view.animate()
                    .translationZ(dp(view.getContext(), target))
                    .setDuration((long) durationMs)
                    .setInterpolator(getDefaultInterpolator())
                    .start();
        } catch (Throwable ignored) {
            // Elevation is purely cosmetic; never break touch handling.
        }
    }

    /**
     * Dialog enter: alpha 0->1 and scale 0.94->1.
     */
    public static void animateDialogShow(View root) {
        if (root == null) return;
        root.setAlpha(0f);
        root.setScaleX(0.94f);
        root.setScaleY(0.94f);
        springAlphaTo(root, 1f).start();
        springScaleXTo(root, 1f).start();
        springScaleYTo(root, 1f).start();
    }

    /**
     * Dialog dismiss with spring; call onEnd when finished.
     */
    public static void animateDialogDismiss(View root, @Nullable Runnable onEnd) {
        if (root == null) {
            if (onEnd != null) onEnd.run();
            return;
        }
        root.animate().cancel();
        root.animate()
                .alpha(0f)
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(DIALOG_DISMISS_DURATION_MS)
                .setInterpolator(new AccelerateInterpolator())
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        root.animate().setListener(null);
                        if (onEnd != null) onEnd.run();
                    }
                })
                .start();
    }

    public static void applyPressScaleRecursively(View root) {
        if (root == null) return;
        if (root.isClickable()) applyPressScale(root);
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                applyPressScaleRecursively(vg.getChildAt(i));
            }
        }
    }

    // 对 RecyclerView 可见子项做阶梯式入场（淡入 + 上滑）
    public static void staggerRecyclerChildren(RecyclerView rv) {
        if (rv == null) return;
        rv.post(() -> {
            int count = rv.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = rv.getChildAt(i);
                if (child == null) continue;
                float dy = dp(rv.getContext(), 12f);
                child.setAlpha(0f);
                child.setTranslationY(dy);
                final int delay = i * 40; // 40ms 阶梯
                rv.postDelayed(() -> {
                    springAlphaTo(child, 1f).start();
                    springTranslationYTo(child, 0f).start();
                }, delay);
            }
        });
    }

    private static float dp(Context ctx, float value) {
        return value * ctx.getResources().getDisplayMetrics().density;
    }

    public static android.view.animation.Interpolator getDefaultInterpolator() {
        return new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    }

    // ------------------------------------------------------------------ arrival motion system
    //
    // One shared language for "something arrived on this screen": scale in from a little under
    // full with an overshoot, staggered across a row. Keeping it here rather than per screen is
    // what makes the nav bar, the home cards and any later surface read as one motion system
    // instead of each screen inventing its own feel.

    /** Where an arriving view starts; small enough to read as a pop, not a zoom. */
    public static final float ARRIVAL_FROM_SCALE = 0.7f;

    /** Gap between successive views in a staggered arrival. */
    public static final long ARRIVAL_STEP_MS = 50L;

    /**
     * Scales a view in from {@link #ARRIVAL_FROM_SCALE} with a slight overshoot.
     *
     * <p>Honours the global animation switch and the speed multiplier, so reduced motion skips it
     * and the accessibility "animation speed" setting still applies.
     */
    public static void overshootScaleIn(View view, long startDelayMs) {
        if (view == null) return;
        if (!animationsEnabled) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        view.setAlpha(0f);
        view.setScaleX(ARRIVAL_FROM_SCALE);
        view.setScaleY(ARRIVAL_FROM_SCALE);
        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(scaled(startDelayMs))
                .setDuration(scaled(260L))
                .setInterpolator(new OvershootInterpolator(1.6f))
                .start();
    }

    /**
     * Staggers {@link #overshootScaleIn} across a row of views.
     *
     * <p>Order is the array order, so a caller controls the beat by controlling the order it passes.
     */
    public static void staggerArrival(View[] views, long baseDelayMs) {
        if (views == null) return;
        for (int i = 0; i < views.length; i++) {
            overshootScaleIn(views[i], baseDelayMs + i * ARRIVAL_STEP_MS);
        }
    }

    /**
     * Runs a numeric count-up and hands each intermediate value to {@code onValue}.
     *
     * <p>The caller formats, so a plain count and a duration that reads as "3h 12m" both animate
     * without this class knowing about either. On the reduced-motion path it jumps straight to the
     * target instead of tweening.
     */
    public static void countUp(long target, long durationMs,
                               final java.util.function.LongConsumer onValue) {
        if (onValue == null) return;
        if (!animationsEnabled) {
            onValue.accept(target);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(scaled(durationMs));
        animator.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            onValue.accept(Math.round(target * t));
        });
        animator.start();
    }

    /** Applies the global speed multiplier to a duration, never returning zero. */
    private static long scaled(long durationMs) {
        return Math.max(1L, (long) (durationMs / globalSpeedMultiplier));
    }
}
