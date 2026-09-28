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
 * A blue firework burst under the finger, laid over a launcher screen.
 *
 * <p>Attached to the root of {@code BaseActivity}, so every launcher screen gets it without each
 * one opting in. It never covers the game itself: only the launcher's own activity tree hosts it.
 *
 * <p>Everything is drawn in a single {@code onDraw} from one pooled particle array. No
 * per-particle Views, no allocation during a burst — a touch flourish must not cost frames on the
 * lower-end tablets this runs on. Additive blending keeps overlapping sparks bright rather than
 * muddy.
 *
 * <p>It auto-disables under Battery Saver and Android's "remove animations" setting, and follows
 * the personalization animation gate, so an accessibility-minded user never sees it.
 */
public class FireworkTouchLayer extends View {

    /** Blue default palette; the accent palette is derived per burst when enabled. */
    private static final int BLUE_CORE = 0xFF4FA3FF;
    private static final int BLUE_MID = 0xFF8CCBFF;
    private static final int WHITE = 0xFFFFFFFF;

    /** How long a burst lives. */
    private static final long BURST_MS = 450L;

    /** Hard cap on simultaneous bursts; older ones are recycled to keep the cost bounded. */
    private static final int MAX_BURSTS = 6;

    /** Spark counts per intensity level. */
    private static final int[] SPARKS_BY_INTENSITY = {14, 17, 20};

    /** Minimum gap between trail bursts while dragging. */
    private static final long TRAIL_INTERVAL_MS = 90L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** One particle; pooled, so a burst never allocates. */
    private static final class Spark {
        float x, y, vx, vy;
        float life;      // 1..0 remaining
        float decay;     // life lost per second
        float size;
        int color;
        boolean alive;
    }

    private final Spark[] pool = new Spark[256];
    private int poolCursor;

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
    private boolean dark;
    private final float density;
    private final java.util.Random rng = new java.util.Random();

    public FireworkTouchLayer(Context context) {
        this(context, null);
    }

    public FireworkTouchLayer(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < pool.length; i++) pool[i] = new Spark();
        paint.setStyle(Paint.Style.FILL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            paint.setBlendMode(android.graphics.BlendMode.PLUS);
        }
        dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
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

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                spawnBurst(event.getX(), event.getY(), SPARKS_BY_INTENSITY[intensity]);
                lastTrailMs = event.getEventTime();
                return false; // Never consume: the screen below must still receive the gesture.
            case MotionEvent.ACTION_MOVE:
                if (event.getEventTime() - lastTrailMs >= TRAIL_INTERVAL_MS) {
                    lastTrailMs = event.getEventTime();
                    // A smaller trail while dragging, so a swipe sparkles without obscuring.
                    spawnBurst(event.getX(), event.getY(),
                            Math.max(4, SPARKS_BY_INTENSITY[intensity] / 3));
                }
                return false;
            default:
                return false;
        }
    }

    /** Starts a burst, recycling the oldest slot once {@link #MAX_BURSTS} are live. */
    private void spawnBurst(float x, float y, int count) {
        int slot = burstCursor % MAX_BURSTS;
        burstStart[slot] = android.os.SystemClock.uptimeMillis();
        burstCursor++;
        activeBursts = Math.min(MAX_BURSTS, activeBursts + 1);

        for (int i = 0; i < count; i++) {
            Spark s = next();
            double angle = (Math.PI * 2 * i) / count + rng.nextDouble() * 0.25;
            float speed = (2.2f + rng.nextFloat() * 2.4f) * density;
            s.x = x;
            s.y = y;
            s.vx = (float) Math.cos(angle) * speed;
            s.vy = (float) Math.sin(angle) * speed;
            s.life = 1f;
            s.decay = (1f / (BURST_MS / 1000f)) * (0.85f + rng.nextFloat() * 0.4f);
            s.size = (1.6f + rng.nextFloat() * 1.8f) * density;
            s.color = pickColor(i, count);
            s.alive = true;
        }
        // A bright white core flash on the burst centre.
        Spark core = next();
        core.x = x;
        core.y = y;
        core.vx = 0f;
        core.vy = 0f;
        core.life = 1f;
        core.decay = (1f / (BURST_MS / 1000f)) * 2.6f;
        core.size = 6f * density;
        core.color = WHITE;
        core.alive = true;
        postInvalidateOnAnimation();
    }

    /** Blue palette by default, or a wash of the accent when the user opts in. */
    private int pickColor(int index, int count) {
        if (followAccent && accentColor != -1) {
            return (index % 2 == 0) ? accentColor : lighten(accentColor, 0.45f);
        }
        switch (index % 3) {
            case 0: return BLUE_CORE;
            case 1: return BLUE_MID;
            default: return dark ? WHITE : 0xFFDCEBFF;
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
            // Gravity pulls sparks down; drag bleeds speed so they decelerate outward.
            s.vy += 6.5f * density * dt;
            s.vx *= (1f - 1.6f * dt);
            s.vy *= (1f - 1.6f * dt);
            s.x += s.vx * dt * 60f;
            s.y += s.vy * dt * 60f;
            s.life -= s.decay * dt;
            if (s.life <= 0f) {
                s.alive = false;
            } else {
                anyAlive = true;
            }
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
        for (Spark s : pool) {
            if (!s.alive) continue;
            int alpha = (int) (255 * Math.max(0f, Math.min(1f, s.life)));
            int base = s.color;
            paint.setColor((alpha << 24) | (base & 0x00FFFFFF));
            canvas.drawCircle(s.x, s.y, s.size * (0.4f + 0.6f * s.life), paint);
        }
    }
}
