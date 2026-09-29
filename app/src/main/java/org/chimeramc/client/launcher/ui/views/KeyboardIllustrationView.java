package org.chimeramc.client.launcher.ui.views;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

import org.chimeramc.client.core.mods.inbuilt.vip.VipKeyLayout;
import org.chimeramc.client.core.mods.inbuilt.vip.VipTheme;

import java.util.HashMap;
import java.util.Map;

/**
 * A premium, drawn keyboard for the VIP Keyboard tab.
 *
 * <p>The geometry lives in {@link VipKeyLayout} (pure and unit-tested); this view only measures
 * and paints it. That split is deliberate — key layout is exactly the kind of arithmetic that
 * looks plausible on screen but is wrong, so it is a JVM test rather than something found on a
 * device.
 *
 * <p>Paints are cached per role and only re-shaded on a size change, so a held key or an animation
 * frame allocates nothing. A key "press" is a short fill flash driven by one {@link ValueAnimator};
 * the highlight colour is the tab accent.
 */
public class KeyboardIllustrationView extends View {

    /** Padding around the board, in key units. */
    private static final float UNIT_PADDING = 0.12f;
    /** Vertical gap between row bands, in key units. */
    private static final float ROW_GAP = 0.16f;
    /** How long a flashed key stays lit, in milliseconds. */
    private static final long FLASH_MS = 280L;

    private final VipKeyLayout layout = new VipKeyLayout();
    private final Map<Integer, Long> pressedUntil = new HashMap<>();

    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capEdgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint legendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF scratch = new RectF();

    private int accent = 0xFF7C5CFF;
    private float density = 1f;
    private int highlightCode = 0;
    private ValueAnimator pressAnimator;

    public KeyboardIllustrationView(Context context) {
        super(context);
        init();
    }

    public KeyboardIllustrationView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KeyboardIllustrationView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        boardPaint.setStyle(Paint.Style.FILL);
        capPaint.setStyle(Paint.Style.FILL);
        capEdgePaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStyle(Paint.Style.FILL);
        legendPaint.setTextAlign(Paint.Align.CENTER);
        legendPaint.setFakeBoldText(true);
    }

    public void setAccentColor(int color) {
        this.accent = color;
        rebuildShaders(getWidth(), getHeight());
        invalidate();
    }

    /** Lights a key permanently, e.g. the control the player is currently remapping. */
    public void setHighlightCode(int keyCode) {
        if (highlightCode == keyCode) return;
        highlightCode = keyCode;
        invalidate();
    }

    /** Flashes a key for one beat, e.g. a captured press. */
    public void flashKey(int keyCode) {
        pressedUntil.put(keyCode, android.os.SystemClock.uptimeMillis() + FLASH_MS);
        if (pressAnimator == null) {
            pressAnimator = ValueAnimator.ofFloat(0f, 1f);
            pressAnimator.setDuration(FLASH_MS);
            pressAnimator.setInterpolator(new DecelerateInterpolator());
            pressAnimator.addUpdateListener(a -> invalidate());
        }
        pressAnimator.cancel();
        pressAnimator.start();
    }

    private void rebuildShaders(int w, int h) {
        if (w <= 0 || h <= 0) return;
        boardPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF17142A, 0xFF0E0C1C}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShaders(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float radius = 18f * density;
        scratch.set(0, 0, w, h);
        canvas.drawRoundRect(scratch, radius, radius, boardPaint);

        float unit = w / (layout.widthUnits() + UNIT_PADDING * 2f);
        float gutter = unit * 0.06f;
        float rowPitch = unit * (1f + ROW_GAP);
        float left = unit * UNIT_PADDING;
        float top = unit * 0.55f;
        float capRadius = Math.max(3f, unit * 0.16f);
        long now = android.os.SystemClock.uptimeMillis();

        for (VipKeyLayout.Key key : layout.keys()) {
            long until = pressedUntil.getOrDefault(key.code, 0L);
            boolean flash = now < until;
            boolean selected = highlightCode != 0 && key.code == highlightCode;

            float kx = left + key.x * unit;
            float ky = top + key.y * rowPitch;
            float kw = key.width * unit - gutter;
            float kh = unit - gutter;

            scratch.set(kx, ky, kx + kw, ky + kh);

            if (flash) {
                highlightPaint.setColor(0xEEFFFFFF);
                canvas.drawRoundRect(scratch, capRadius, capRadius, highlightPaint);
            } else if (selected) {
                highlightPaint.setColor(VipTheme.withAlpha(accent, 210));
                canvas.drawRoundRect(scratch, capRadius, capRadius, highlightPaint);
            } else {
                capPaint.setColor(key.modifier ? 0xFF221E38 : 0xFF2A2542);
                canvas.drawRoundRect(scratch, capRadius, capRadius, capPaint);
                capEdgePaint.setColor(0x22FFFFFF);
                capEdgePaint.setStrokeWidth(1f * density);
                canvas.drawRoundRect(scratch, capRadius, capRadius, capEdgePaint);
            }

            float legendSize = Math.max(7f, unit * (key.width >= 2f ? 0.20f : 0.28f));
            legendPaint.setTextSize(legendSize);
            int textColor;
            if (flash) {
                textColor = 0xFF101018;
            } else if (selected) {
                textColor = 0xFF160F2A;
            } else {
                textColor = key.modifier ? 0xFF9A93B5 : 0xFFD9D4EE;
            }
            legendPaint.setColor(textColor);
            float baseline = ky + kh / 2f - (legendPaint.descent() + legendPaint.ascent()) / 2f;
            canvas.drawText(key.legend, kx + kw / 2f, baseline, legendPaint);
        }

        if (!pressedUntil.isEmpty() && now > newestPress()) {
            pressedUntil.clear();
        }
    }

    private long newestPress() {
        long max = 0;
        for (long v : pressedUntil.values()) {
            max = Math.max(max, v);
        }
        return max;
    }
}
