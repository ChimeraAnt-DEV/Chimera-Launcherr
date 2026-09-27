package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * A blocky, pixel-art microphone meter: neutral when idle, filled from the bottom up in green
 * as you speak, with a red pixel-X when the mic is muted.
 *
 * <p>Drawn on a fixed 12x12 logical grid with anti-aliasing off, so it scales up as crisp blocks
 * rather than a smooth vector glyph and belongs beside the rest of the blocky UI. It is driven by
 * real microphone level, not an animation loop: the host feeds it the engine's smoothed 0-1 RMS,
 * so the fill height is a measurement of the audio actually being sent. There is no separate
 * "loud" state -- loud is simply the top of the same meter.
 *
 * <p>The sprite below is the entire artwork. Keeping it as data means the shape is reviewable at
 * a glance; the muted X is drawn from the same grid so it cannot drift out of alignment.
 */
public final class MicIndicatorView extends View {

    /** The logical resolution of the sprite; every coordinate in the grid is within this. */
    public static final int GRID = 12;

    /** The microphone body and stand. {@code #} is a filled cell. */
    private static final String[] MIC_SPRITE = {
            ".....##.....",
            "....####....",
            "....####....",
            "....####....",
            "....####....",
            "....####....",
            ".....##.....",
            "..##....##..",
            "..##....##..",
            "..########..",
            ".....##.....",
            "...######...",
    };

    private static final int COLOR_IDLE = 0xFFC8CED6;
    private static final int COLOR_TALKING = 0xFF6BD68A;
    private static final int COLOR_MUTED = 0xFFE5484D;
    private static final int COLOR_BACKDROP = 0x99000000;

    private final Paint paint = new Paint();
    private float level;
    private boolean muted;

    /** The sprite rows, exposed so a JVM test can pin the shape without a device. */
    static String[] sprite() {
        return MIC_SPRITE.clone();
    }

    public MicIndicatorView(Context context) {
        super(context);
        init();
    }

    public MicIndicatorView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // Blocky, not smooth: the whole point is a pixel-art look.
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setStyle(Paint.Style.FILL);
    }

    /** Sets the muted state; a muted meter ignores the level and shows the red X. */
    public void setMuted(boolean muted) {
        if (this.muted != muted) {
            this.muted = muted;
            invalidate();
        }
    }

    public boolean isMuted() {
        return muted;
    }

    /** Sets the smoothed 0-1 microphone level that drives the fill height. */
    public void setLevel(float level) {
        float clamped = level < 0f ? 0f : (level > 1f ? 1f : level);
        // Skip sub-cell changes: the grid quantises the fill anyway, so repainting for a change
        // that cannot move a row is pure work on the UI thread.
        if (Math.abs(clamped - this.level) < 1f / GRID) return;
        this.level = clamped;
        invalidate();
    }

    public float getLevel() {
        return level;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) (28 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float pad = 2f * density;

        // An integer cell keeps every block the same size and aligned to the pixel grid; a
        // fractional cell would leave one ragged column on the right.
        int cell = Math.max(1, (int) ((Math.min(width, height) - pad * 2f) / GRID));
        float artWidth = cell * GRID;
        float left = (width - artWidth) / 2f;
        float top = (height - artWidth) / 2f;

        paint.setColor(COLOR_BACKDROP);
        canvas.drawRoundRect(0f, 0f, width, height, 6f * density, 6f * density, paint);

        // A cell is green once the rising fill has reached its row, counted from the bottom, so
        // the bar grows upward the way a level meter is read.
        int filledRows = Math.round(level * GRID);

        for (int row = 0; row < GRID; row++) {
            String line = MIC_SPRITE[row];
            boolean inFill = !muted && (GRID - 1 - row) < filledRows;
            for (int col = 0; col < GRID; col++) {
                if (line.charAt(col) != '#') continue;
                paint.setColor(muted ? COLOR_MUTED : (inFill ? COLOR_TALKING : COLOR_IDLE));
                drawCell(canvas, left, top, cell, col, row);
            }
        }

        if (muted) {
            paint.setColor(COLOR_MUTED);
            for (int i = 0; i < GRID; i++) {
                drawCell(canvas, left, top, cell, i, i);
                drawCell(canvas, left, top, cell, i, GRID - 1 - i);
            }
        }
    }

    private void drawCell(Canvas canvas, float left, float top, int cell, int col, int row) {
        float x = left + col * cell;
        float y = top + row * cell;
        canvas.drawRect(x, y, x + cell, y + cell, paint);
    }
}
