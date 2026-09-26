package org.chimeramc.client.ui.views;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import org.chimeramc.client.launcher.controller.ControllerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.chimeramc.client.ui.views.ControllerLayout.Shape;

/**
 * Shaded top-down illustration of the three supported gamepads, with every button and
 * stick addressable by id so a physical press can light it up.
 *
 * Region coordinates are normalised: 0.5 is the view centre and the span 0.0..1.0 maps to
 * twice {@link #scale}. The shell paths are expressed against the same scale, so a button
 * sits inside the body by construction instead of by eyeballed pixel offsets.
 */
public class ControllerIllustrationView extends View {
    private ControllerType type = ControllerType.XBOX;
    private final List<ControllerLayout.Spec> regions = new ArrayList<>();
    private final Map<String, Float> glow = new HashMap<>();

    private final Paint shellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shellDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seamPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint wellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonDarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint detailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knurlPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint letterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF scratch = new RectF();
    private final Path shellPath = new Path();
    private final Path chassisPath = new Path();

    private int bodyLight;
    private int bodyDark;
    private int wellColor;
    private int accentColor = -1;
    private boolean dark;
    private float cx;
    private float cy;
    private float scale = 1f;

    public ControllerIllustrationView(Context context) {
        super(context);
        init();
    }

    public ControllerIllustrationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dark = isDarkMode();
        applyThemeColors();
        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(2.5f);
        symbolPaint.setStyle(Paint.Style.STROKE);
        symbolPaint.setStrokeCap(Paint.Cap.ROUND);
        symbolPaint.setStrokeJoin(Paint.Join.ROUND);
        textPaint.setTextAlign(Paint.Align.CENTER);
        seamPaint.setStyle(Paint.Style.STROKE);
        rebuild();
    }

    private boolean isDarkMode() {
        int flags = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void applyThemeColors() {
        if (dark) {
            // Near-black shell so the pad reads as a modern controller rather than grey putty.
            bodyLight = 0xFF454B56;
            bodyDark = 0xFF171A20;
            wellColor = 0xFF0A0C10;
            outlinePaint.setColor(0xFF6E7683);
            seamPaint.setColor(0x33FFFFFF);
            buttonPaint.setColor(Color.rgb(52, 58, 67));
            buttonDarkPaint.setColor(Color.rgb(30, 34, 41));
            capPaint.setColor(Color.rgb(66, 73, 84));
            detailPaint.setColor(Color.rgb(20, 23, 28));
            textPaint.setColor(Color.rgb(222, 228, 236));
        } else {
            bodyLight = 0xFFFFFFFF;
            bodyDark = 0xFFD3D9E0;
            wellColor = 0xFF9BA3AD;
            outlinePaint.setColor(Color.rgb(126, 133, 143));
            seamPaint.setColor(0x4DFFFFFF);
            buttonPaint.setColor(Color.rgb(226, 230, 235));
            buttonDarkPaint.setColor(Color.rgb(186, 192, 200));
            capPaint.setColor(Color.rgb(244, 246, 248));
            detailPaint.setColor(Color.rgb(150, 157, 166));
            textPaint.setColor(Color.rgb(44, 48, 56));
        }
    }

    /**
     * The view must be redrawn as well as rebuilt. Rebuilding only the region list leaves
     * the previous controller's shell on screen, which made the manual "Next" button look
     * like it had done nothing after the first press.
     */
    public void setType(ControllerType type) {
        if (type == null) return;
        this.type = type;
        rebuild();
        invalidate();
    }

    public ControllerType getType() {
        return type;
    }

    public void setAccentColor(int accentColor) {
        this.accentColor = accentColor;
        invalidate();
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dark = isDarkMode();
        applyThemeColors();
        buildShaders();
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scale = Math.min(w, h) * 0.55f;
        cx = w / 2f;
        cy = h / 2f;
        buildShaders();
    }

    /**
     * Shell gradients are built once per size/theme change rather than per frame; a
     * {@code LinearGradient} allocated in {@code onDraw} would churn every frame for a
     * view that sits on a scrolling screen.
     */
    private void buildShaders() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        shellPaint.setShader(new LinearGradient(0f, h * 0.10f, 0f, h * 0.92f,
                bodyLight, bodyDark, Shader.TileMode.CLAMP));
        shellDarkPaint.setShader(new LinearGradient(0f, h * 0.30f, 0f, h * 0.95f,
                dark ? 0xFF262B34 : 0xFFC6CCD5,
                dark ? 0xFF12151A : 0xFF97A0AB,
                Shader.TileMode.CLAMP));
        shadowPaint.setShader(new RadialGradient(w / 2f, h * 0.90f, w * 0.52f,
                new int[]{0x3C000000, 0x00000000}, null, Shader.TileMode.CLAMP));
    }

    public void setRegionGlow(String id, boolean on) {
        glow.put(id, on ? 1f : 0f);
        invalidate();
    }

    private void rebuild() {
        regions.clear();
        regions.addAll(ControllerLayout.regions(type));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(w, h);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;
        if (shadowPaint.getShader() == null) buildShaders();
        drawShadow(canvas);
        if (type == ControllerType.XBOX) {
            drawBodyXbox(canvas);
        } else {
            drawBodyPlayStation(canvas, type == ControllerType.DUAL_SENSE);
        }
        for (ControllerLayout.Spec r : regions) drawRegion(canvas, r);
    }

    private void drawShadow(Canvas canvas) {
        scratch.set(cx - scale * 0.98f, cy + scale * 0.62f, cx + scale * 0.98f, cy + scale * 0.94f);
        canvas.drawOval(scratch, shadowPaint);
    }

    /**
     * A thin dark falloff hugging the silhouette.
     *
     * This is what gives the shell a solid edge — the eye reads a body outline from the
     * contrast at the rim. It replaces a large translucent oval "sheen" that washed across
     * the top of the shell; at this scale that read as a smeared highlight rather than a
     * surface, which is the "spilled water" look the illustration had.
     */
    private void drawEdgeFalloff(Canvas canvas) {
        float stroke = scale * 0.030f;
        edgePaint.setShader(null);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(stroke);
        edgePaint.setColor(dark ? 0x66000000 : 0x30000000);
        canvas.drawPath(shellPath, edgePaint);
        edgePaint.setStyle(Paint.Style.FILL);
    }

    /**
     * Xbox silhouette: a wide arched shell whose shoulders drop into two splayed grips,
     * pinched at a waist between them.
     */
    private void drawBodyXbox(Canvas canvas) {
        float w = scale * 0.60f;
        float top = cy - scale * 0.72f;
        float shellH = scale * 1.46f;

        Path p = shellPath;
        p.reset();
        p.moveTo(cx - w * 0.86f, top);
        p.cubicTo(cx - w * 1.12f, top, cx - w * 1.26f, top + shellH * 0.10f, cx - w * 1.24f, top + shellH * 0.30f);
        p.cubicTo(cx - w * 1.22f, top + shellH * 0.52f, cx - w * 1.18f, top + shellH * 0.72f, cx - w * 1.02f, top + shellH * 0.92f);
        p.cubicTo(cx - w * 0.92f, top + shellH * 1.02f, cx - w * 0.74f, top + shellH * 1.03f, cx - w * 0.60f, top + shellH * 0.96f);
        p.cubicTo(cx - w * 0.50f, top + shellH * 0.90f, cx - w * 0.34f, top + shellH * 0.78f, cx - w * 0.26f, top + shellH * 0.70f);
        p.cubicTo(cx - w * 0.14f, top + shellH * 0.62f, cx + w * 0.14f, top + shellH * 0.62f, cx + w * 0.26f, top + shellH * 0.70f);
        p.cubicTo(cx + w * 0.34f, top + shellH * 0.78f, cx + w * 0.50f, top + shellH * 0.90f, cx + w * 0.60f, top + shellH * 0.96f);
        p.cubicTo(cx + w * 0.74f, top + shellH * 1.03f, cx + w * 0.92f, top + shellH * 1.02f, cx + w * 1.02f, top + shellH * 0.92f);
        p.cubicTo(cx + w * 1.18f, top + shellH * 0.72f, cx + w * 1.22f, top + shellH * 0.52f, cx + w * 1.24f, top + shellH * 0.30f);
        p.cubicTo(cx + w * 1.26f, top + shellH * 0.10f, cx + w * 1.12f, top, cx + w * 0.86f, top);
        p.cubicTo(cx + w * 0.40f, top - shellH * 0.045f, cx - w * 0.40f, top - shellH * 0.045f, cx - w * 0.86f, top);
        p.close();

        canvas.drawPath(p, shellPaint);

        // Crisp top-face highlight: a defined band high on the shell with a hard-ish edge,
        // then a dark falloff at the rim. Both are clipped to the silhouette.
        canvas.save();
        canvas.clipPath(p);
        rimPaint.setShader(null);
        rimPaint.setColor(dark ? 0x14FFFFFF : 0x66FFFFFF);
        scratch.set(cx - w * 1.30f, top - shellH * 0.02f, cx + w * 1.30f, top + shellH * 0.22f);
        canvas.drawRoundRect(scratch, shellH * 0.14f, shellH * 0.14f, rimPaint);
        canvas.restore();

        drawEdgeFalloff(canvas);

        canvas.save();
        canvas.clipPath(p);
        seamPaint.setStrokeWidth(1.6f);
        for (int side = -1; side <= 1; side += 2) {
            Path seam = new Path();
            float sx = cx + side * w * 0.30f;
            seam.moveTo(sx, top + shellH * 0.62f);
            seam.cubicTo(sx + side * w * 0.12f, top + shellH * 0.74f,
                    sx + side * w * 0.30f, top + shellH * 0.86f,
                    sx + side * w * 0.44f, top + shellH * 0.95f);
            canvas.drawPath(seam, seamPaint);
        }
        canvas.restore();

        canvas.drawPath(p, outlinePaint);
    }

    /**
     * DualShock 4 / DualSense silhouette: a symmetric centre shell with a touchpad set
     * into the top face and two grips curving down and out. The DualSense adds the
     * two-tone inner chassis, the flanking light bars and the mute bar under the PS
     * button, which is what makes the two read as different pads at a glance.
     */
    private void drawBodyPlayStation(Canvas canvas, boolean dualSense) {
        float w = scale * 0.58f;
        float top = cy - scale * 0.70f;
        float shellH = scale * 1.42f;

        Path p = shellPath;
        p.reset();
        p.moveTo(cx - w * 0.62f, top);
        p.cubicTo(cx - w * 0.95f, top, cx - w * 1.18f, top + shellH * 0.16f, cx - w * 1.20f, top + shellH * 0.42f);
        p.cubicTo(cx - w * 1.22f, top + shellH * 0.62f, cx - w * 1.14f, top + shellH * 0.84f, cx - w * 0.92f, top + shellH * 0.98f);
        p.cubicTo(cx - w * 0.74f, top + shellH * 1.04f, cx - w * 0.56f, top + shellH * 1.04f, cx - w * 0.42f, top + shellH * 0.96f);
        p.cubicTo(cx - w * 0.32f, top + shellH * 0.90f, cx - w * 0.24f, top + shellH * 0.78f, cx - w * 0.16f, top + shellH * 0.66f);
        p.cubicTo(cx - w * 0.07f, top + shellH * 0.58f, cx + w * 0.07f, top + shellH * 0.58f, cx + w * 0.16f, top + shellH * 0.66f);
        p.cubicTo(cx + w * 0.24f, top + shellH * 0.78f, cx + w * 0.32f, top + shellH * 0.90f, cx + w * 0.42f, top + shellH * 0.96f);
        p.cubicTo(cx + w * 0.56f, top + shellH * 1.04f, cx + w * 0.74f, top + shellH * 1.04f, cx + w * 0.92f, top + shellH * 0.98f);
        p.cubicTo(cx + w * 1.14f, top + shellH * 0.84f, cx + w * 1.22f, top + shellH * 0.62f, cx + w * 1.20f, top + shellH * 0.42f);
        p.cubicTo(cx + w * 1.18f, top + shellH * 0.16f, cx + w * 0.95f, top, cx + w * 0.62f, top);
        p.cubicTo(cx + w * 0.30f, top - shellH * 0.05f, cx - w * 0.30f, top - shellH * 0.05f, cx - w * 0.62f, top);
        p.close();

        canvas.drawPath(p, dualSense ? shellPaint : shellDarkPaint);

        canvas.save();
        canvas.clipPath(p);

        if (dualSense) {
            // Dark inner chassis wrapping the sticks; the light outer shell stays visible
            // around it, which is the DualSense's two-tone look.
            chassisPath.reset();
            chassisPath.addRoundRect(new RectF(cx - w * 0.88f, top + shellH * 0.52f,
                    cx + w * 0.88f, top + shellH * 1.08f), shellH * 0.22f, shellH * 0.22f,
                    Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(chassisPath);
            canvas.drawRect(cx - w * 1.4f, top + shellH * 0.40f,
                    cx + w * 1.4f, top + shellH * 1.10f, shellDarkPaint);
            canvas.restoreToCount(save);
        }

        rimPaint.setShader(null);
        rimPaint.setColor(dark ? 0x14FFFFFF : 0x60FFFFFF);
        scratch.set(cx - w * 1.25f, top - shellH * 0.02f, cx + w * 1.25f, top + shellH * 0.20f);
        canvas.drawRoundRect(scratch, shellH * 0.13f, shellH * 0.13f, rimPaint);
        canvas.restore();

        drawEdgeFalloff(canvas);

        // Light bar. DualShock 4 carries a single bar above the touchpad; the DualSense
        // splits it into two strips flanking the pad.
        glowPaint.setShader(null);
        glowPaint.setColor(accentColor != -1 ? accentColor : 0xFF3B82F6);
        glowPaint.setAlpha(dark ? 205 : 235);
        if (dualSense) {
            float tx = w * 0.62f;
            scratch.set(cx - tx - w * 0.10f, top + shellH * 0.16f, cx - tx + w * 0.02f, top + shellH * 0.44f);
            canvas.drawRoundRect(scratch, w * 0.05f, w * 0.05f, glowPaint);
            scratch.set(cx + tx - w * 0.02f, top + shellH * 0.16f, cx + tx + w * 0.10f, top + shellH * 0.44f);
            canvas.drawRoundRect(scratch, w * 0.05f, w * 0.05f, glowPaint);
        } else {
            scratch.set(cx - w * 0.44f, top + shellH * 0.075f, cx + w * 0.44f, top + shellH * 0.115f);
            canvas.drawRoundRect(scratch, shellH * 0.02f, shellH * 0.02f, glowPaint);
        }

        canvas.drawPath(p, outlinePaint);

        // Grip seam where the outer shell meets the grip mouldings.
        seamPaint.setStrokeWidth(2f);
        for (int side = -1; side <= 1; side += 2) {
            Path seam = new Path();
            float sx = cx + side * w * 0.34f;
            seam.moveTo(sx, top + shellH * 0.60f);
            seam.cubicTo(sx + side * w * 0.20f, top + shellH * 0.74f,
                    sx + side * w * 0.42f, top + shellH * 0.86f,
                    sx + side * w * 0.52f, top + shellH * 0.98f);
            canvas.drawPath(seam, seamPaint);
        }
    }

    private void drawRegion(Canvas canvas, ControllerLayout.Spec r) {
        // The normalised-to-centre conversion lives in ControllerLayout so the tests and
        // the drawing cannot disagree about it.
        float px = cx + ControllerLayout.regionDx(r) * scale;
        float py = cy + ControllerLayout.regionDy(r) * scale;
        float pr = r.radius * 2f * scale;
        Float strength = glow.get(r.id);
        float g = strength == null ? 0f : strength;
        if (g > 0.05f) {
            glowPaint.setColor(accentColor != -1 ? accentColor : 0xFF6236E8);
            glowPaint.setAlpha((int) (150 * g));
            drawShape(canvas, r, px, py, pr * (1f + 0.3f * g), true);
        }
        drawShape(canvas, r, px, py, pr, false);
        if (r.label != null && !r.label.isEmpty()) {
            textPaint.setTextSize(pr * 0.42f);
            textPaint.setAlpha(170);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            canvas.drawText(r.label, px, py - (fm.ascent + fm.descent) / 2f, textPaint);
            textPaint.setAlpha(255);
        }
    }

    private void drawShape(Canvas canvas, ControllerLayout.Spec r, float px, float py, float pr, boolean glowMode) {
        // The glow pass writes to glowPaint, which share shape code paths with the normal
        // pass. Its shader is cleared on every entry so a shader set by the shell cannot
        // bleed into a button glow.
        if (glowMode) glowPaint.setShader(null);
        switch (r.shape) {
            case STICK -> drawStick(canvas, px, py, pr, glowMode);
            case STICK_RING -> drawRing(canvas, px, py, pr, glowMode);
            case DPAD -> drawDPad(canvas, px, py, pr, glowMode);
            case FACE_XBOX -> drawFaceXbox(canvas, r, px, py, pr, glowMode);
            case FACE_DUAL -> drawFaceDual(canvas, r, px, py, pr, glowMode);
            case BUMPER -> drawBumper(canvas, px, py, pr, glowMode);
            case TRIGGER -> drawTrigger(canvas, px, py, pr, glowMode);
            case CENTER_BUTTON -> drawCenterButton(canvas, px, py, pr, glowMode);
            case TOUCHPAD -> drawTouchpad(canvas, px, py, pr, glowMode);
            case PS_LOGO -> drawPsLogo(canvas, px, py, pr, glowMode);
            case GUIDE -> drawGuide(canvas, px, py, pr, glowMode);
            case MUTE -> drawMute(canvas, px, py, pr, glowMode);
        }
    }

    /**
     * A stick is a recessed well containing a concave knurled cap. The well's inner
     * shadow and the cap's off-centre highlight are what stop it reading as a flat disc.
     */
    private void drawStick(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        float well = pr * 1.30f;
        wellPaint.setShader(null);
        wellPaint.setColor(wellColor);
        canvas.drawCircle(px, py, well, wellPaint);
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        outlinePaint.setStrokeWidth(1.8f);
        canvas.drawCircle(px, py, well, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);

        // Inner shadow: only the upper inside wall is in shade, because the opening faces the
        // viewer and the light comes from above. Drawing the whole rim dark is what made the
        // well read as a smeared oval instead of a recess.
        shadePaint.setStyle(Paint.Style.STROKE);
        shadePaint.setStrokeWidth(pr * 0.26f);
        shadePaint.setColor(dark ? 0x77000000 : 0x2E000000);
        canvas.drawArc(new RectF(px - well * 0.92f, py - well * 0.92f,
                px + well * 0.92f, py + well * 0.92f), 195f, 150f, false, shadePaint);
        // A thin light arc on the lower inside wall completes the concave read.
        shadePaint.setColor(dark ? 0x18FFFFFF : 0x45FFFFFF);
        canvas.drawArc(new RectF(px - well * 0.92f, py - well * 0.92f,
                px + well * 0.92f, py + well * 0.92f), 15f, 150f, false, shadePaint);

        // Cap: a gently domed disc. The dome is two stacked circles of decreasing radius,
        // which gives a hard edge between them that reads as a moulded surface.
        capPaint.setShader(null);
        capPaint.setColor(dark ? 0xFF525A66 : 0xFFE7EAEE);
        canvas.drawCircle(px, py, pr, capPaint);
        platePaint.setColor(dark ? 0xFF3E454F : 0xFFD2D7DE);
        canvas.drawCircle(px, py, pr * 0.90f, platePaint);
        platePaint.setColor(dark ? 0xFF4A525D : 0xFFDFE3E8);
        canvas.drawCircle(px, py, pr * 0.78f, platePaint);

        // Rubber grip ring with a fine knurl, drawn as ticks so it stays crisp at any size.
        knurlPaint.setStyle(Paint.Style.STROKE);
        knurlPaint.setStrokeWidth(pr * 0.07f);
        knurlPaint.setColor(dark ? 0x33FFFFFF : 0x4DFFFFFF);
        for (int i = 0; i < 28; i++) {
            double a = Math.toRadians(i * (360.0 / 28.0));
            canvas.drawLine(px + (float) Math.cos(a) * pr * 0.83f, py + (float) Math.sin(a) * pr * 0.83f,
                    px + (float) Math.cos(a) * pr * 0.99f, py + (float) Math.sin(a) * pr * 0.99f, knurlPaint);
        }

        // Concave dish in the middle of the cap.
        groovePaint.setStyle(Paint.Style.STROKE);
        groovePaint.setStrokeWidth(pr * 0.10f);
        groovePaint.setColor(dark ? 0x5A000000 : 0x33000000);
        canvas.drawCircle(px, py, pr * 0.62f, groovePaint);

        // A single specular highlight, tight and offset, not a broad wash.
        highlightPaint.setShader(null);
        highlightPaint.setColor(dark ? 0x40FFFFFF : 0x99FFFFFF);
        canvas.drawCircle(px - pr * 0.30f, py - pr * 0.34f, pr * 0.26f, highlightPaint);
    }

    private void drawRing(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        if (!glowMode) return;
        glowPaint.setShader(null);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(pr * 0.16f);
        canvas.drawCircle(px, py, pr, glowPaint);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    /** D-pad as two crossing bars so the centre reads as a pivot rather than a blob. */
    private void drawDPad(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Path cross = new Path();
        float arm = pr * 0.94f;
        float half = pr * 0.30f;
        float rad = pr * 0.11f;
        cross.addRoundRect(new RectF(px - half, py - arm, px + half, py + arm), rad, rad, Path.Direction.CW);
        cross.addRoundRect(new RectF(px - arm, py - half, px + arm, py + half), rad, rad, Path.Direction.CW);

        if (glowMode) {
            canvas.drawPath(cross, glowPaint);
            return;
        }
        buttonPaint.setColor(dark ? 0xFF40464F : 0xFFC4CAD2);
        canvas.drawPath(cross, buttonPaint);
        outlinePaint.setStrokeWidth(1.8f);
        canvas.drawPath(cross, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);

        detailPaint.setColor(dark ? 0xFF20242B : 0xFFA6ACB5);
        canvas.drawCircle(px, py, pr * 0.17f, detailPaint);
        // Directional dimples: small dished circles on each arm, with a crisp dark edge and a
        // tight highlight, which is what a moulded d-pad actually looks like.
        float d = pr * 0.58f;
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            float dx = px + (float) Math.cos(a) * d;
            float dy = py + (float) Math.sin(a) * d;
            groovePaint.setStyle(Paint.Style.STROKE);
            groovePaint.setStrokeWidth(pr * 0.06f);
            groovePaint.setColor(dark ? 0x66000000 : 0x33000000);
            canvas.drawCircle(dx, dy, pr * 0.13f, groovePaint);
            highlightPaint.setShader(null);
            highlightPaint.setColor(dark ? 0x2EFFFFFF : 0x66FFFFFF);
            canvas.drawCircle(dx - pr * 0.03f, dy - pr * 0.03f, pr * 0.07f, highlightPaint);
        }
    }

    private void drawFaceXbox(Canvas canvas, ControllerLayout.Spec r, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        // Recessed socket the button sits in, then the domed cap on top of it.
        shadePaint.setStyle(Paint.Style.STROKE);
        shadePaint.setStrokeWidth(pr * 0.16f);
        shadePaint.setColor(dark ? 0x66000000 : 0x30000000);
        canvas.drawCircle(px, py, pr * 1.06f, shadePaint);

        buttonPaint.setShader(null);
        buttonPaint.setColor(dark ? 0xFF23272F : 0xFFE4E7EB);
        canvas.drawCircle(px, py, pr, buttonPaint);
        // A smaller concentric disc gives the cap its dome; the hard step between them reads
        // as a moulded shoulder rather than a blur.
        platePaint.setColor(dark ? 0xFF2C313A : 0xFFD6DAE0);
        canvas.drawCircle(px, py, pr * 0.86f, platePaint);
        outlinePaint.setStrokeWidth(1.6f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);

        letterPaint.setTextAlign(Paint.Align.CENTER);
        letterPaint.setFakeBoldText(true);
        letterPaint.setTextSize(pr * 1.24f);
        letterPaint.setColor(faceColor(r.id));
        Paint.FontMetrics fm = letterPaint.getFontMetrics();
        canvas.drawText(r.label, px, py - (fm.ascent + fm.descent) / 2f, letterPaint);

        // Gloss: a tight crescent across the top-left of the cap.
        highlightPaint.setShader(null);
        highlightPaint.setColor(dark ? 0x2AFFFFFF : 0x80FFFFFF);
        canvas.drawCircle(px - pr * 0.30f, py - pr * 0.32f, pr * 0.22f, highlightPaint);
    }

    private void drawFaceDual(Canvas canvas, ControllerLayout.Spec r, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        buttonDarkPaint.setColor(dark ? 0xFF2A2F37 : 0xFFE9ECEF);
        canvas.drawCircle(px, py, pr, buttonDarkPaint);
        outlinePaint.setStrokeWidth(1.8f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);

        symbolPaint.setColor(faceColor(r.id));
        symbolPaint.setStrokeWidth(pr * 0.22f);
        drawDualSymbol(canvas, r.id, px, py, pr * 0.50f);
    }

    private int faceColor(String id) {
        if (id == null) return 0xFF7D8590;
        switch (id) {
            case "y": return 0xFF2FBF71;   // triangle - green
            case "b": return 0xFFE0455A;   // circle - red
            case "a": return 0xFF3FA9F5;   // cross - blue
            case "x": return 0xFFE45FA8;   // square - pink
            default: return 0xFF7D8590;
        }
    }

    private void drawDualSymbol(Canvas canvas, String id, float px, float py, float pr) {
        Paint p = symbolPaint;
        if ("a".equals(id)) {
            canvas.drawLine(px - pr, py - pr, px + pr, py + pr, p);
            canvas.drawLine(px - pr, py + pr, px + pr, py - pr, p);
        } else if ("b".equals(id)) {
            canvas.drawCircle(px, py, pr, p);
        } else if ("x".equals(id)) {
            canvas.drawRect(px - pr * 0.88f, py - pr * 0.88f, px + pr * 0.88f, py + pr * 0.88f, p);
        } else if ("y".equals(id)) {
            Path t = new Path();
            t.moveTo(px, py - pr);
            t.lineTo(px + pr * 0.94f, py + pr * 0.72f);
            t.lineTo(px - pr * 0.94f, py + pr * 0.72f);
            t.close();
            canvas.drawPath(t, p);
        }
    }

    private void drawBumper(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        scratch.set(px - pr * 2.3f, py - pr * 0.52f, px + pr * 2.3f, py + pr * 0.52f);
        if (glowMode) {
            canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, glowPaint);
            return;
        }
        buttonPaint.setColor(dark ? 0xFF3E444F : 0xFFCCD2D9);
        canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, buttonPaint);
        outlinePaint.setStrokeWidth(1.8f);
        canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);
        detailPaint.setColor(dark ? 0xFF252A32 : 0xFFAAB0B9);
        scratch.set(px - pr * 1.75f, py + pr * 0.24f, px + pr * 1.75f, py + pr * 0.34f);
        canvas.drawRoundRect(scratch, pr * 0.05f, pr * 0.05f, detailPaint);
    }

    /**
     * Triggers sit behind the bumpers and curve back over the shoulder.
     *
     * The arc deliberately extends past the shell and is clipped to it, so the trigger reads
     * as emerging from behind the body the way a real one does. A trigger fully inside the
     * silhouette looks like a flat pill stuck to the face, and on the Xbox, whose shoulders
     * angle steeply, it cannot fit inside at all without shrinking past legibility.
     */
    private void drawTrigger(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        Path t = new Path();
        t.moveTo(px - pr * 1.9f, py + pr * 0.62f);
        t.cubicTo(px - pr * 1.9f, py - pr * 0.85f,
                px + pr * 1.9f, py - pr * 0.85f,
                px + pr * 1.9f, py + pr * 0.62f);
        t.close();
        int save = canvas.save();
        canvas.clipPath(shellPath);
        if (glowMode) {
            canvas.drawPath(t, glowPaint);
        } else {
            buttonDarkPaint.setColor(dark ? 0xFF333944 : 0xFFBFC5CD);
            canvas.drawPath(t, buttonDarkPaint);
            outlinePaint.setStrokeWidth(1.8f);
            canvas.drawPath(t, outlinePaint);
            outlinePaint.setStrokeWidth(2.5f);
        }
        canvas.restoreToCount(save);
    }

    private void drawCenterButton(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        capPaint.setColor(dark ? 0xFF4A505C : 0xFFD2D7DD);
        canvas.drawCircle(px, py, pr, capPaint);
        outlinePaint.setStrokeWidth(1.6f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);
    }

    /** Touchpad: a raised glass panel with a hairline border and a top-edge catchlight. */
    private void drawTouchpad(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        scratch.set(px - pr * 2.15f, py - pr * 0.78f, px + pr * 2.15f, py + pr * 0.78f);
        if (glowMode) {
            canvas.drawRoundRect(scratch, pr * 0.26f, pr * 0.26f, glowPaint);
            return;
        }
        detailPaint.setColor(dark ? 0xFF1B1F26 : 0xFFD6DBE1);
        canvas.drawRoundRect(scratch, pr * 0.26f, pr * 0.26f, detailPaint);
        outlinePaint.setStrokeWidth(1.6f);
        canvas.drawRoundRect(scratch, pr * 0.26f, pr * 0.26f, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);
        highlightPaint.setColor(dark ? 0x18FFFFFF : 0x55FFFFFF);
        scratch.set(px - pr * 1.95f, py - pr * 0.62f, px + pr * 1.95f, py - pr * 0.10f);
        canvas.drawRoundRect(scratch, pr * 0.12f, pr * 0.12f, highlightPaint);
    }

    private void drawPsLogo(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        capPaint.setColor(dark ? 0xFF23272F : 0xFFE4E7EB);
        canvas.drawCircle(px, py, pr, capPaint);
        symbolPaint.setColor(accentColor != -1 ? accentColor : 0xFF4A90E2);
        symbolPaint.setStrokeWidth(pr * 0.20f);
        canvas.drawLine(px - pr * 0.24f, py + pr * 0.58f, px + pr * 0.34f, py - pr * 0.58f, symbolPaint);
        canvas.drawLine(px - pr * 0.52f, py + pr * 0.02f, px + pr * 0.58f, py + pr * 0.02f, symbolPaint);
        canvas.drawLine(px + pr * 0.30f, py - pr * 0.52f, px + pr * 0.56f, py - pr * 0.16f, symbolPaint);
        outlinePaint.setStrokeWidth(1.6f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);
    }

    private void drawGuide(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        if (glowMode) {
            canvas.drawCircle(px, py, pr, glowPaint);
            return;
        }
        capPaint.setColor(dark ? 0xFF23272F : 0xFFE4E7EB);
        canvas.drawCircle(px, py, pr, capPaint);
        outlinePaint.setStrokeWidth(1.6f);
        canvas.drawCircle(px, py, pr, outlinePaint);
        outlinePaint.setStrokeWidth(2.5f);

        // Guide glyph: two opposed arcs, which is how the Xbox mark reads at this size.
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setStrokeWidth(pr * 0.20f);
        g.setColor(accentColor != -1 ? accentColor : 0xFF57C84D);
        RectF arc = new RectF(px - pr * 0.60f, py - pr * 0.60f, px + pr * 0.60f, py + pr * 0.60f);
        canvas.drawArc(arc, 130f, 130f, false, g);
        canvas.drawArc(arc, 310f, 130f, false, g);
    }

    private void drawMute(Canvas canvas, float px, float py, float pr, boolean glowMode) {
        scratch.set(px - pr * 2.4f, py - pr * 0.55f, px + pr * 2.4f, py + pr * 0.55f);
        if (glowMode) {
            canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, glowPaint);
            return;
        }
        detailPaint.setColor(dark ? 0xFF262A32 : 0xFFC3C9D1);
        canvas.drawRoundRect(scratch, pr * 0.5f, pr * 0.5f, detailPaint);
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

        // Motion events stream continuously, so every axis is reported lit *or* unlit on
        // each event. Only ever setting the glow left sticks and the d-pad highlighted
        // forever once they had been touched.
        setRegionGlow("ls", Math.abs(lx) > 0.35f || Math.abs(ly) > 0.35f);
        setRegionGlow("rs", Math.abs(rx) > 0.35f || Math.abs(rz) > 0.35f);
        setRegionGlow("dp", Math.abs(hatX) > 0.4f || Math.abs(hatY) > 0.4f);
        setRegionGlow("lt", event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.25f);
        setRegionGlow("rt", event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.25f);
    }

    /**
     * Map a physical key to a region id. Select and Mode resolve differently per pad
     * because the same key code means View/Share and Guide/PS respectively.
     */
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

}
