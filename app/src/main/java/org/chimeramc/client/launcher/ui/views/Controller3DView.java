package org.chimeramc.client.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import org.chimeramc.client.launcher.controller.Controller3DProjection;
import org.chimeramc.client.launcher.controller.Controller3DProjection.Projected;
import org.chimeramc.client.launcher.controller.ControllerType;
import org.chimeramc.client.ui.views.ControllerLayout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A shallow 3D rendering of a gamepad, built from the same region table as
 * {@link ControllerIllustrationView}.
 *
 * <p>It exists because the flat illustration cannot show the shoulder triggers convincingly and
 * reads as a squashed top-down sketch. Here the body is tilted by a small camera pitch and the
 * bumpers/triggers sit on a raised rear layer, so both are visible at once.
 *
 * <p>The view owns no geometry: {@link Controller3DProjection} decides where every control lands,
 * and this class only paints what it returns. Paints and the body path are cached and only rebuilt
 * on size or type change, so a live input stream allocates nothing per frame.
 */
public class Controller3DView extends View {

    /** Bezier samples per shell segment used for the fit; matches the draw path. */
    private static final int SHELL_SAMPLES = 14;

    private ControllerType type = ControllerType.XBOX;
    private final Set<String> glow = new HashSet<>();
    private final Set<String> confirmed = new HashSet<>();

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bodySidePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint controlEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch = new RectF();
    private final Path bodyPath = new Path();

    private int accent = 0xFF6236E8;
    private int confirmedColor = 0xFF2FBF71;
    private float density = 1f;
    private boolean bodyDirty = true;
    private String pulseRegion;
    private float pulseScale = 1f;

    public Controller3DView(Context context) {
        super(context);
        init();
    }

    public Controller3DView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public Controller3DView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        bodyPaint.setStyle(Paint.Style.FILL);
        bodySidePaint.setStyle(Paint.Style.FILL);
        bodySidePaint.setColor(0xFF14121C);
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(1.4f * density);
        outlinePaint.setColor(0x33FFFFFF);
        controlPaint.setStyle(Paint.Style.FILL);
        controlEdgePaint.setStyle(Paint.Style.STROKE);
        controlEdgePaint.setStrokeWidth(1.2f * density);
        controlEdgePaint.setColor(0x55FFFFFF);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(2.6f * density);
        labelPaint.setColor(0xFFEDEAF6);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setType(ControllerType newType) {
        if (newType == null || newType == type) return;
        type = newType;
        glow.clear();
        confirmed.clear();
        bodyDirty = true;
        invalidate();
    }

    public ControllerType getType() {
        return type;
    }

    public void setAccentColor(int color) {
        accent = color;
        bodyDirty = true;
        invalidate();
    }

    public void setRegionGlow(String id, boolean on) {
        if (id == null) return;
        boolean changed = on ? glow.add(id) : glow.remove(id);
        if (changed) invalidate();
    }

    public void setRegionConfirmed(String id, boolean on) {
        if (id == null) return;
        boolean changed = on ? confirmed.add(id) : confirmed.remove(id);
        if (changed) invalidate();
    }

    public void clearConfirmed() {
        if (confirmed.isEmpty()) return;
        confirmed.clear();
        pulseRegion = null;
        invalidate();
    }

    /**
     * Flashes a confirmed region green with a short overshoot, then settles.
     *
     * <p>This is the "got it" moment when a bind is captured. The animator only touches two
     * floats and invalidates, so it runs for a few hundred milliseconds on the dialog and
     * cannot disturb the game's frame budget, which is why the picker uses this rather than a
     * live animated render.
     */
    public void animateConfirm(final String id) {
        if (id == null) return;
        pulseRegion = id;
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(420);
        animator.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // One overshoot then settle: 0 -> 1.18 -> 1.0.
            float eased = (float) Math.sin(t * Math.PI);
            pulseScale = 1f + 0.20f * eased;
            invalidate();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                pulseScale = 1f;
                invalidate();
            }
        });
        animator.start();
    }

    public String regionIdForKey(int keyCode) {
        return regionForKey(keyCode);
    }

    public void handleKeyEvent(int keyCode, boolean down) {
        String id = regionForKey(keyCode);
        if (id != null) setRegionGlow(id, down);
    }

    public void handleMotionEvent(MotionEvent event) {
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);
        float rx = event.getAxisValue(MotionEvent.AXIS_Z);
        float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
        float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        setRegionGlow("ls", Math.abs(lx) > 0.35f || Math.abs(ly) > 0.35f);
        setRegionGlow("rs", Math.abs(rx) > 0.35f || Math.abs(rz) > 0.35f);
        setRegionGlow("dp", Math.abs(hatX) > 0.4f || Math.abs(hatY) > 0.4f);
        setRegionGlow("lt", event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.25f);
        setRegionGlow("rt", event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.25f);
    }

    private String regionForKey(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "a";
            case KeyEvent.KEYCODE_BUTTON_B: return "b";
            case KeyEvent.KEYCODE_BUTTON_X: return "x";
            case KeyEvent.KEYCODE_BUTTON_Y: return "y";
            case KeyEvent.KEYCODE_BUTTON_L1: return "lb";
            case KeyEvent.KEYCODE_BUTTON_R1: return "rb";
            case KeyEvent.KEYCODE_BUTTON_L2: return "lt";
            case KeyEvent.KEYCODE_BUTTON_R2: return "rt";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return "ls";
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return "rs";
            case KeyEvent.KEYCODE_BUTTON_START: return "options";
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                return type == ControllerType.XBOX ? "view" : "share";
            case KeyEvent.KEYCODE_BUTTON_MODE:
                return type == ControllerType.XBOX ? "guide" : "ps";
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT: return "dp";
            default: return null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        bodyDirty = true;
    }

    private float centerX() {
        return getWidth() / 2f;
    }

    private float centerY() {
        return getHeight() / 2f;
    }

    /**
     * Uniform scale so the whole pad, shoulder controls included, fits with a margin.
     *
     * <p>Fitted from {@link Controller3DProjection#unitBounds} rather than a fixed divisor: the
     * triggers and bumpers sit above the shell and are drawn larger by perspective, so the drawn
     * shape is taller than the body. A fixed {@code height / 2.1} left them off the top edge,
     * which is why the back triggers never showed.
     */
    private float scale() {
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return 1f;
        float[] b = Controller3DProjection.unitBounds(type, SHELL_SAMPLES);
        float spanX = Math.max(0.001f, b[2] - b[0]);
        float spanY = Math.max(0.001f, b[3] - b[1]);
        return Math.min(w * 0.92f / spanX, h * 0.92f / spanY);
    }

    /**
     * Screen centre for the fitted pad.
     *
     * <p>The illustration is not symmetric about the face centre (the triggers extend upward), so
     * the centre is placed at the middle of the measured bounds rather than at the view's centre.
     */
    private void fittedCenter(float scale, float[] out) {
        float[] b = Controller3DProjection.unitBounds(type, SHELL_SAMPLES);
        float midX = (b[0] + b[2]) / 2f;
        float midY = (b[1] + b[3]) / 2f;
        out[0] = centerX() - midX * scale;
        out[1] = centerY() - midY * scale;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = scale();
        float[] centre = new float[2];
        fittedCenter(scale, centre);
        float cx = centre[0];
        float cy = centre[1];

        List<Projected> regions = Controller3DProjection.project(type, scale, cx, cy);

        drawShoulders(canvas, regions);
        drawBody(canvas, scale, cx, cy);
        drawControls(canvas, regions);
    }

    /** Shoulder controls paint first so the body overlaps their lower half. */
    private void drawShoulders(Canvas canvas, List<Projected> regions) {
        for (Projected p : regions) {
            if (p.shoulder) drawControl(canvas, p);
        }
    }

    private void drawBody(Canvas canvas, float scale, float cx, float cy) {
        if (bodyDirty) rebuildBody(scale, cx, cy);
        // A dark offset copy underneath gives the slab an edge without a second geometry pass.
        canvas.save();
        canvas.translate(0, 6f * density);
        canvas.drawPath(bodyPath, bodySidePaint);
        canvas.restore();
        canvas.drawPath(bodyPath, bodyPaint);
        canvas.drawPath(bodyPath, outlinePaint);
    }

    private void rebuildBody(float scale, float cx, float cy) {
        float[] poly = Controller3DProjection.projectShell(type, 14, scale, cx, cy);
        bodyPath.reset();
        if (poly.length >= 2) {
            bodyPath.moveTo(poly[0], poly[1]);
            for (int i = 2; i < poly.length; i += 2) {
                bodyPath.lineTo(poly[i], poly[i + 1]);
            }
            bodyPath.close();
        }
        RectF bounds = new RectF();
        bodyPath.computeBounds(bounds, true);
        int top = 0xFF3A3350;
        int bottom = 0xFF17141F;
        bodyPaint.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, top, bottom,
                Shader.TileMode.CLAMP));
        bodyDirty = false;
    }

    private void drawControls(Canvas canvas, List<Projected> regions) {
        for (Projected p : regions) {
            if (!p.shoulder) drawControl(canvas, p);
        }
    }

    private void drawControl(Canvas canvas, Projected p) {
        boolean isGlow = glow.contains(p.spec.id);
        boolean isConfirmed = confirmed.contains(p.spec.id);
        float r = Math.max(p.radius, 3f * density);
        if (p.spec.id.equals(pulseRegion)) r *= pulseScale;

        int fill;
        int edge;
        if (isConfirmed) {
            fill = blend(confirmedColor, 0xFF10231A, 0.45f);
            edge = confirmedColor;
        } else if (isGlow) {
            fill = blend(accent, 0xFF1A1430, 0.45f);
            edge = accent;
        } else {
            // Shoulder controls sit in shadow, so the depth reads without an explicit fog.
            fill = p.shoulder ? 0xFF221D30 : 0xFF2A2438;
            edge = 0x44FFFFFF;
        }
        controlPaint.setColor(fill);
        controlEdgePaint.setColor(edge);

        if (isRound(p.spec.shape)) {
            canvas.drawCircle(p.x, p.y, r, controlPaint);
            canvas.drawCircle(p.x, p.y, r, controlEdgePaint);
            if (isGlow || isConfirmed) {
                glowPaint.setColor(isConfirmed ? confirmedColor : accent);
                canvas.drawCircle(p.x, p.y, r + 2.5f * density, glowPaint);
            }
        } else {
            float halfW = r * aspectX(p.spec.shape);
            float halfH = r * aspectY(p.spec.shape);
            scratch.set(p.x - halfW, p.y - halfH, p.x + halfW, p.y + halfH);
            float radius = Math.min(halfH, 6f * density);
            canvas.drawRoundRect(scratch, radius, radius, controlPaint);
            canvas.drawRoundRect(scratch, radius, radius, controlEdgePaint);
            if (isGlow || isConfirmed) {
                glowPaint.setColor(isConfirmed ? confirmedColor : accent);
                scratch.inset(-2.5f * density, -2.5f * density);
                canvas.drawRoundRect(scratch, radius, radius, glowPaint);
            }
        }
        drawLabel(canvas, p, r);
    }

    private void drawLabel(Canvas canvas, Projected p, float r) {
        String label = p.spec.label;
        if (label == null || label.isEmpty()) return;
        if (r < 7f * density) return;
        labelPaint.setTextSize(Math.min(r * 0.95f, 13f * density));
        Paint.FontMetrics fm = labelPaint.getFontMetrics();
        float baseline = p.y - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, p.x, baseline, labelPaint);
    }

    private static boolean isRound(ControllerLayout.Shape shape) {
        switch (shape) {
            case BUMPER:
            case TRIGGER:
            case TOUCHPAD:
            case MUTE:
                return false;
            default:
                return true;
        }
    }

    private static float aspectX(ControllerLayout.Shape shape) {
        switch (shape) {
            case BUMPER: return 2.3f;
            case TRIGGER: return 1.9f;
            case TOUCHPAD: return 2.15f;
            case MUTE: return 2.4f;
            default: return 1f;
        }
    }

    private static float aspectY(ControllerLayout.Shape shape) {
        switch (shape) {
            case BUMPER: return 0.52f;
            case TRIGGER: return 0.95f;
            case TOUCHPAD: return 0.78f;
            case MUTE: return 0.55f;
            default: return 1f;
        }
    }

    private static int blend(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg = Color.green(b), bb = Color.blue(b);
        int r = (int) (ar * t + br * (1 - t));
        int g = (int) (ag * t + bg * (1 - t));
        int bl = (int) (ab * t + bb * (1 - t));
        return Color.argb(255, r, g, bl);
    }
}
