package org.chimeramc.client.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import org.chimeramc.client.util.PersonalizationManager;

/**
 * A firework burst under the finger, laid over a launcher screen.
 *
 * <p>Attached to the root of {@code BaseActivity}, so every launcher screen gets it without each
 * one opting in. It never covers the game itself: only the launcher's own activity tree hosts it.
 *
 * <p>Everything is drawn in a single {@code onDraw} from pooled particle and ring arrays. No
 * per-particle Views, no allocation during a burst — a touch flourish must not cost frames on the
 * lower-end tablets this runs on. Additive blending keeps overlapping sparks bright rather than
 * muddy.
 *
 * <p>The burst is a real explosion rather than a spray of dots: a bright core flash, an expanding
 * shockwave ring, a spherical shell of sparks thrown outward at varied speeds with comet tails,
 * gravity and drag so they arc down, and a few slow-glowing embers. Sparks carry the accent or blue
 * palette; the core and ring stay white-hot so the centre reads as ignition.
 *
 * <p>It auto-disables under Battery Saver and Android's "remove animations" setting, and follows
 * the personalization animation gate, so an accessibility-minded user never sees it.
 */
public class FireworkTouchLayer extends View {

    /** Blue default palette; the accent palette is derived per burst when enabled. */
    private static final int BLUE_CORE = 0xFF4FA3FF;
    private static final int BLUE_MID = 0xFF8CCBFF;
    private static final int BLUE_PALE = 0xFFBFE0FF;

    /** How long a burst lives. */
    private static final long BURST_MS = 620L;

    /** Hard cap on simultaneous bursts; older ones are recycled to keep the cost bounded. */
    private static final int MAX_BURSTS = 6;

    /** Spark counts per intensity level (Low, Medium, High). */
    private static final int[] SPARKS_BY_INTENSITY = {26, 36, 48};

    /** Minimum gap between trail bursts while dragging. */
    private static final long TRAIL_INTERVAL_MS = 90L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private long lastObservedMs = -1L;
    private int lastObservedAction = -1;

    /** One particle; pooled, so a burst never allocates. */
    private static final class Spark {
        float x, y, vx, vy;
        float prevX, prevY; // for the comet streak
        float life;      // 1..0 remaining
        float decay;     // life lost per second
        float size;
        int color;
        boolean alive;
    }

    /** An expanding shockwave ring at the burst origin. */
    private static final class Ring {
        float x, y;
        float life;
        float decay;
        float maxRadius;
        int color;
        boolean alive;
    }

    private final Spark[] pool = new Spark[320];
    private int poolCursor;
    private final Ring[] rings = new Ring[12];
    private int ringCursor;

    /** Active burst start times, one slot per allowed simultaneous burst. */
    private final long[] burstStart = new long[MAX_BURSTS];
    private int burstCursor;
    private int activeBursts;

    private long lastTrailMs;
    private long lastFrameMs;

    private final ValueAnimator driver;

    private boolean enabled;
    private int intensity = PersonalizationManager.FIREWORK_MEDIUM;
    private boolean followAccent;
    private int accentColor = -1;
    private final float density;
    private final java.util.Random rng = new java.util.Random();

    public FireworkTouchLayer(Context context) {
        this(context, null);
    }

    public FireworkTouchLayer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < pool.length; i++) pool[i] = new Spark();
        for (int i = 0; i < rings.length; i++) rings[i] = new Ring();
        paint.setStyle(Paint.Style.FILL);
        glowPaint.setStyle(Paint.Style.STROKE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.setBlendMode(android.graphics.BlendMode.PLUS);
            glowPaint.setBlendMode(android.graphics.BlendMode.PLUS);
        }
        driver = ValueAnimator.ofFloat(0f, 1f);
        driver.setDuration(1000);
        driver.setRepeatCount(ValueAnimator.INFINITE);
        driver.setInterpolator(new DecelerateInterpolator());
        driver.addUpdateListener(a -> step());
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setVisibility(GONE);
    }

    /**
     * Reads the personalization settings and the platform gates, then starts or stops the driver.
     *
     * Called by {@code BaseActivity} when a screen becomes visible and after a settings change.
     * A disabled layer is fully transparent to touch, so it can never eat a tap.
     */
    public void refresh(Context context) {
        PersonalizationManager manager = new PersonalizationManager(context);
        boolean wanted = manager.isFireworkTouchEnabled()
                && manager.isShowAnimations()
                && !isBatterySaverOn(context)
                && animationsEnabled(context);
        intensity = manager.getFireworkIntensity();
        followAccent = manager.isFireworkFollowAccent();
        accentColor = manager.getAccentColor();
        enabled = wanted;
        if (wanted) {
            setVisibility(VISIBLE);
            if (!driver.isStarted()) {
                lastFrameMs = 0L;
                driver.start();
            }
        } else {
            driver.cancel();
            clearAll();
            setVisibility(GONE);
        }
    }

    /** Android's "remove animations" accessibility setting. */
    private static boolean animationsEnabled(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f;
        } catch (Exception e) {
            return true;
        }
    }

    /** True while the system is in Battery Saver; the effect is skipped rather than degraded. */
    private static boolean isBatterySaverOn(Context context) {
        try {
            android.os.PowerManager pm =
                    (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isPowerSaveMode();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reacts to a touch observed by a parent, without consuming it.
     *
     * <p>This view is non-clickable, so it never receives its own MOVE stream; the screen that
     * hosts it forwards the events here. Nothing is consumed at any point, which is what lets the
     * gesture continue to the content underneath.
     */
    public void observe(MotionEvent event) {
        // A ViewGroup's onInterceptTouchEvent and onTouchEvent can both be invoked for the same
        // DOWN when no child claims it, so the same event would spawn two bursts. Ignore an event
        // we have already seen; a later event always has a strictly greater uptime.
        long stamp = event.getEventTime();
        if (stamp == lastObservedMs && event.getActionMasked() == lastObservedAction) {
            return;
        }
        lastObservedMs = stamp;
        lastObservedAction = event.getActionMasked();
        onTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                spawnBurst(event.getX(), event.getY(), SPARKS_BY_INTENSITY[intensity], true);
                lastTrailMs = event.getEventTime();
                return false; // Never consume: the screen below must still receive the gesture.
            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - lastTrailMs >= TRAIL_INTERVAL_MS) {
                    lastTrailMs = event.getEventTime();
                    // A smaller trail while dragging, so a swipe sparkles without obscuring.
                    spawnBurst(event.getX(), event.getY(),
                            Math.max(8, SPARKS_BY_INTENSITY[intensity] / 3), false);
                }
                return false;
            default:
                return false;
        }
    }

    /**
     * Starts a burst, recycling the oldest slot once {@link #MAX_BURSTS} are live.
     *
     * @param full true for a tap's full explosion (core + ring), false for a drag trail's sparks
     */
    private void spawnBurst(float x, float y, int count, boolean full) {
        int slot = burstCursor % MAX_BURSTS;
        burstStart[slot] = android.os.SystemClock.uptimeMillis();
        burstCursor++;
        activeBursts = Math.min(MAX_BURSTS, activeBursts + 1);

        // A spherical shell: sparks are evenly spread in angle but thrown at varied speeds, so the
        // shell has thickness and some sparks outrun others. A constant speed makes a thin, dull
        // ring; varied speeds are what read as an explosion.
        for (int i = 0; i < count; i++) {
            Spark s = next();
            double angle = (Math.PI * 2 * i) / count + rng.nextDouble() * 0.30;
            float speed = (4.2f + rng.nextFloat() * 5.4f) * density;
            s.x = x;
            s.y = y;
            s.prevX = x;
            s.prevY = y;
            s.vx = (float) Math.cos(angle) * speed;
            s.vy = (float) Math.sin(angle) * speed - rng.nextFloat() * 1.2f * density;
            s.life = 1f;
            s.decay = (1f / (BURST_MS / 1000f)) * (0.75f + rng.nextFloat() * 0.5f);
            s.size = (2.0f + rng.nextFloat() * 2.8f) * density;
            s.color = pickColor(i, count);
            s.alive = true;
        }

        if (full) {
            // A few slow-glowing embers that linger and sink, so the burst has depth after the
            // fast sparks have gone.
            int embers = Math.max(4, count / 5);
            for (int i = 0; i < embers; i++) {
                Spark s = next();
                double angle = rng.nextDouble() * Math.PI * 2;
                float speed = (1.0f + rng.nextFloat() * 1.6f) * density;
                s.x = x;
                s.y = y;
                s.prevX = x;
                s.prevY = y;
                s.vx = (float) Math.cos(angle) * speed;
                s.vy = (float) Math.sin(angle) * speed;
                s.life = 1f;
                s.decay = (1f / (BURST_MS / 1000f)) * (0.35f + rng.nextFloat() * 0.25f);
                s.size = (2.6f + rng.nextFloat() * 2.2f) * density;
                s.color = lighten(pickColor(i, embers), 0.35f);
                s.alive = true;
            }

            // The white-hot core flash, short-lived.
            Spark core = next();
            core.x = x;
            core.y = y;
            core.prevX = x;
            core.prevY = y;
            core.vx = 0f;
            core.vy = 0f;
            core.life = 1f;
            core.decay = (1f / (BURST_MS / 1000f)) * 5.5f;
            core.size = 7f * density;
            core.color = 0xFFFFFFFF;
            core.alive = true;

            // The shockwave ring.
            Ring ring = rings[ringCursor % rings.length];
            ringCursor++;
            ring.x = x;
            ring.y = y;
            ring.life = 1f;
            ring.decay = (1f / (BURST_MS / 1000f)) * 1.7f;
            ring.maxRadius = (26f + rng.nextFloat() * 14f) * density;
            ring.color = followAccent && accentColor != -1 ? lighten(accentColor, 0.55f) : BLUE_PALE;
            ring.alive = true;
        }

        postInvalidateOnAnimation();
    }

    /**
     * Blue palette by default, or a wash of the accent when the user opts in.
     *
     * <p>Every spark keeps blue in it. The palette used to include plain white, and because the
     * blend is additive a wash of white sparks saturated to flat white dots that read as clutter
     * rather than a firework. White is now only the brief core flash.
     */
    private int pickColor(int index, int count) {
        if (followAccent && accentColor != -1) {
            return (index % 2 == 0) ? accentColor : lighten(accentColor, 0.45f);
        }
        switch (index % 3) {
            case 0: return BLUE_CORE;
            case 1: return BLUE_MID;
            default: return BLUE_PALE;
        }
    }

    private static int lighten(int color, float amount) {
        int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
        r = (int) (r + (255 - r) * amount);
        g = (int) (g + (255 - g) * amount);
        b = (int) (b + (255 - b) * amount);
        return Color.argb(255, r, g, b);
    }

    /** Grabs the next pool slot, overwriting the oldest particle when the pool is exhausted. */
    private Spark next() {
        Spark s = pool[poolCursor % pool.length];
        poolCursor++;
        return s;
    }

    private void step() {
        long now = android.os.SystemClock.uptimeMillis();
        if (lastFrameMs == 0L) lastFrameMs = now;
        float dt = Math.min(0.05f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        boolean anyAlive = false;
        for (Spark s : pool) {
            if (!s.alive) continue;
            s.prevX = s.x;
            s.prevY = s.y;
            // Gravity pulls sparks down; drag bleeds speed so they decelerate outward and then arc.
            s.vy += 7.5f * density * dt;
            float drag = (1f - 1.9f * dt);
            s.vx *= drag;
            s.vy *= drag;
            s.x += s.vx * dt * 60f;
            s.y += s.vy * dt * 60f;
            s.life -= s.decay * dt;
            if (s.life <= 0f) {
                s.alive = false;
            } else {
                anyAlive = true;
            }
        }
        for (Ring r : rings) {
            if (!r.alive) continue;
            r.life -= r.decay * dt;
            if (r.life <= 0f) r.alive = false;
            else anyAlive = true;
        }
        if (activeBursts > 0) {
            activeBursts = 0;
            for (long t : burstStart) {
                if (t != 0L && now - t < BURST_MS) activeBursts++;
            }
        }
        if (!anyAlive && activeBursts == 0) {
            // Nothing on screen: idle rather than burning frames.
            driver.cancel();
            lastFrameMs = 0L;
            return;
        }
        postInvalidateOnAnimation();
    }

    private void clearAll() {
        for (Spark s : pool) s.alive = false;
        for (Ring r : rings) r.alive = false;
        for (int i = 0; i < burstStart.length; i++) burstStart[i] = 0L;
        activeBursts = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        driver.cancel();
        clearAll();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!enabled) return;

        // Shockwave rings first, so sparks paint over them.
        for (Ring r : rings) {
            if (!r.alive) continue;
            float progress = 1f - r.life;
            float radius = r.maxRadius * (0.25f + 0.75f * progress);
            int alpha = (int) (150 * r.life * r.life);
            glowPaint.setColor((alpha << 24) | (r.color & 0x00FFFFFF));
            glowPaint.setStrokeWidth(Math.max(1.5f, (3.5f * r.life) * density));
            canvas.drawCircle(r.x, r.y, radius, glowPaint);
        }

        // Sparks as short comet streaks: a line from the previous position to the current one,
        // capped with a dot. A plain dot reads as a static speck; the streak reads as motion.
        for (Spark s : pool) {
            if (!s.alive) continue;
            int alpha = (int) (255 * Math.max(0f, Math.min(1f, s.life)));
            int base = s.color;
            paint.setColor((alpha << 24) | (base & 0x00FFFFFF));
            float r = s.size * (0.35f + 0.65f * s.life);
            canvas.drawCircle(s.x, s.y, r, paint);
            // Only fast sparks get a visible tail; a slow ember's tail would just be a smudge.
            float dx = s.x - s.prevX;
            float dy = s.y - s.prevY;
            if (dx * dx + dy * dy > 1.0f) {
                paint.setStrokeWidth(Math.max(1f, r * 0.8f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                canvas.drawLine(s.prevX, s.prevY, s.x, s.y, paint);
            }
        }
    }
}
