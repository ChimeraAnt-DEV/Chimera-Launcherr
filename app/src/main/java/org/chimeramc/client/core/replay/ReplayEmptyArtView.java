package org.chimeramc.client.core.replay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * The Replay empty-state illustration.
 *
 * <p>The spec asks for illustrative art on an empty state rather than a blank screen. This draws a
 * film strip with a play glyph and a soft accent glow behind it, entirely in code so there is no
 * PNG placeholder and it scales to any density. Paints are cached; the view never animates, so it
 * costs one draw.
 */
public class ReplayEmptyArtView extends View {

    private final Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint holePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path triangle = new Path();
    private final RectF rect = new RectF();

    private final ReplayStyle style;

    public ReplayEmptyArtView(Context context) {
        this(context, null);
    }

    public ReplayEmptyArtView(Context context, AttributeSet attrs) {
        super(context, attrs);
        style = new ReplayStyle(context);
        bodyPaint.setColor(style.surfaceElevated());
        holePaint.setColor(ReplayStyle.withAlpha(style.textTertiary(), 90));
        glyphPaint.setColor(style.accent());
        glowPaint.setColor(style.accentFill(38));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float size = Math.min(width, height);
        float cx = width / 2f;
        float cy = height / 2f;
        float stripWidth = size * 0.72f;
        float stripHeight = size * 0.5f;
        float radius = size * 0.08f;

        // Soft glow behind the strip.
        canvas.drawCircle(cx, cy, size * 0.46f, glowPaint);

        rect.set(cx - stripWidth / 2f, cy - stripHeight / 2f,
                cx + stripWidth / 2f, cy + stripHeight / 2f);
        canvas.drawRoundRect(rect, radius, radius, bodyPaint);

        // Sprocket holes down both edges.
        float hole = size * 0.045f;
        float gap = size * 0.11f;
        float inset = size * 0.045f;
        int count = 3;
        for (int i = 0; i < count; i++) {
            float y = cy - ((count - 1) * gap) / 2f + i * gap;
            rect.set(cx - stripWidth / 2f + inset, y - hole / 2f,
                    cx - stripWidth / 2f + inset + hole, y + hole / 2f);
            canvas.drawRoundRect(rect, hole * 0.3f, hole * 0.3f, holePaint);
            rect.set(cx + stripWidth / 2f - inset - hole, y - hole / 2f,
                    cx + stripWidth / 2f - inset, y + hole / 2f);
            canvas.drawRoundRect(rect, hole * 0.3f, hole * 0.3f, holePaint);
        }

        // Play glyph in the middle.
        float glyph = size * 0.16f;
        triangle.reset();
        triangle.moveTo(cx - glyph * 0.5f, cy - glyph * 0.62f);
        triangle.lineTo(cx + glyph * 0.7f, cy);
        triangle.lineTo(cx - glyph * 0.5f, cy + glyph * 0.62f);
        triangle.close();
        canvas.drawPath(triangle, glyphPaint);
    }
}
