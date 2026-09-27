package org.chimeramc.client.launcher.ui.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The wordmark shatter: rasterises a view, slices it with {@link ShatterPlan}, and flies each
 * shard outward with its own random translation, rotation and fade.
 *
 * <p>The classic shatter-transition technique done by hand rather than pulled from a library: one
 * {@link ImageView} per shard, each positioned exactly over the slice it came from, then a
 * per-shard {@link ObjectAnimator}. All shards share the caller's duration; only the small start
 * jitter and the random vectors differ, so the effect does not look mechanically uniform.
 *
 * <p>The source view is hidden while the shards are visible, so the two can never double-draw and
 * leave a ghost of the intact wordmark behind the debris.
 */
public final class WordmarkShatter {

    /** Columns and rows of the shard grid; 6x3 slices a wordmark into readable-sized pieces. */
    public static final int COLUMNS = 6;
    public static final int ROWS = 3;

    private WordmarkShatter() {
    }

    /**
     * Slices {@code source} into shards, adds them to {@code host}, hides the source, and runs the
     * shatter. {@code onComplete} fires once the whole set has finished.
     */
    public static void play(final View source, final FrameLayout host, final long durationMs,
                            final Runnable onComplete) {
        final Bitmap bitmap = rasterise(source);
        if (bitmap == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final List<ShatterPlan.Shard> shards = ShatterPlan.grid(
                source.getWidth(), source.getHeight(), COLUMNS, ROWS);
        if (shards.isEmpty()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final Random random = new Random();
        final List<Animator> animators = new ArrayList<>(shards.size());
        final float density = source.getResources().getDisplayMetrics().density;
        final float halfWidth = source.getWidth() / 2f;
        final float halfHeight = source.getHeight() / 2f;

        // Shards live in the host's coordinate space. Resolving that by walking parents assumes the
        // walk terminates on the host, which it does not when the source sits in a sibling subtree
        // (the wordmark is, the host is not its ancestor); the walk would run to the DecorView and
        // only happen to be right while every extra ancestor sits at the origin. Window locations
        // give the same offset exactly, for any hierarchy.
        int[] sourceLoc = new int[2];
        int[] hostLoc = new int[2];
        source.getLocationInWindow(sourceLoc);
        host.getLocationInWindow(hostLoc);
        int sourceLeft = sourceLoc[0] - hostLoc[0];
        int sourceTop = sourceLoc[1] - hostLoc[1];

        for (final ShatterPlan.Shard shard : shards) {
            final ImageView shardView = new ImageView(source.getContext());
            Bitmap piece = Bitmap.createBitmap(bitmap, shard.left, shard.top,
                    shard.width, shard.height);
            shardView.setImageDrawable(new BitmapDrawable(source.getResources(), piece));

            FrameLayout.LayoutParams params =
                    new FrameLayout.LayoutParams(shard.width, shard.height);
            params.leftMargin = sourceLeft + shard.left;
            params.topMargin = sourceTop + shard.top;
            shardView.setLayoutParams(params);
            host.addView(shardView);

            // Push each shard away from the wordmark's centre so the burst reads as coming apart,
            // not merely scattering.
            float dx = shard.centerX() - halfWidth;
            float dy = shard.centerY() - halfHeight;
            float jitterX = (random.nextFloat() - 0.5f) * 90f * density;
            float travelX = dx * 0.9f + jitterX;
            float travelY = dy * 0.6f + 30f * density + (random.nextFloat() - 0.5f) * 60f * density;

            ObjectAnimator tx = ObjectAnimator.ofFloat(shardView, "translationX", 0f, travelX);
            ObjectAnimator ty = ObjectAnimator.ofFloat(shardView, "translationY", 0f, travelY);
            ObjectAnimator rot = ObjectAnimator.ofFloat(shardView, "rotation",
                    0f, (random.nextFloat() - 0.5f) * 70f);
            ObjectAnimator alpha = ObjectAnimator.ofFloat(shardView, "alpha", 1f, 0f);
            // Hold full opacity for the first half, then fade: fading from the first frame makes
            // the shards vanish before the eye can follow them.
            alpha.setStartDelay(durationMs / 2);

            AnimatorSet one = new AnimatorSet();
            one.playTogether(tx, ty, rot, alpha);
            one.setDuration(durationMs);
            one.setInterpolator(new AccelerateInterpolator(1.4f));
            one.setStartDelay(random.nextInt(40));
            one.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    host.removeView(shardView);
                }
            });
            animators.add(one);
        }

        source.setAlpha(0f);

        AnimatorSet set = new AnimatorSet();
        set.playTogether(animators);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onComplete != null) onComplete.run();
            }
        });
        set.start();
    }

    /** Rasterises a view to a bitmap at its on-screen size, or null when it has no size yet. */
    public static Bitmap rasterise(View view) {
        if (view == null) return null;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0) return null;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }
}
