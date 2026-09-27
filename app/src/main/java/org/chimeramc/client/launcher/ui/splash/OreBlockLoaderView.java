package org.chimeramc.client.launcher.ui.splash;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * The splash loader: a slowly turning crystal-ore block with a fracture that deepens as load
 * progress advances, and a pickaxe that swings each time a new crack stage appears.
 *
 * <p>Art is data ({@link OreCrackSprites}) and this view only paints it. All the geometry -- which
 * cell is filled, which stage a fraction maps to -- lives in that class so it can be pinned by a
 * JVM test, since no {@code Canvas} exists on the build machine. This view owns only the parts
 * that need a real device: measuring, transforming, and the rotation.
 *
 * <p>Anti-aliasing is deliberately <b>off</b> so the blocks stay crisp, and the block is turned by
 * a single {@link Matrix} for the whole sprite rather than re-rasterised per cell: rotating a
 * 16x16 grid cell-by-cell each frame would burn the UI thread for no visual gain.
 */
public final class OreBlockLoaderView extends View {

    /** The pickaxe's resting rotation, so it starts cocked back rather than perfectly vertical. */
    private static final float REST_ROTATION = 16f;

    private final Paint paint = new Paint();
    private final Matrix rotation = new Matrix();

    private float progress;
    private int crackStage;
    private float blockRotation;
    private float pickaxeRotation = REST_ROTATION;
    private float pickaxeOffsetX;
    private float pickaxeOffsetY;

    private int stoneColor = 0xFF6E7688;
    private int stoneLight = 0xFF98A0B4;
    private int stoneDark = 0xFF4C5264;
    private int crystalColor = 0xFF6C8CF5;
    private int crystalLight = 0xFFA9BCFF;
    private int crackColor = 0xFF232630;
    private int ironColor = 0xFFB9C0CC;
    private int ironLight = 0xFFE6EBF2;
    private int handleColor = 0xFF8A6236;

    public OreBlockLoaderView(Context context) {
        super(context);
        init();
    }

    public OreBlockLoaderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * Sets the glyph colours from the active palette, so the loader belongs to the theme rather
     * than sitting on it. The crystal takes the accent; the stone stays neutral so the accent ore
     * is what the eye lands on.
     */
    public void setColors(int accent, boolean dark) {
        crystalColor = accent;
        crystalLight = blend(accent, Color.WHITE, 0.45f);
        if (dark) {
            stoneColor = 0xFF4A5064;
            stoneLight = 0xFF6B7186;
            stoneDark = 0xFF2E3242;
            crackColor = 0xFFE8ECF5;
        } else {
            stoneColor = 0xFF6E7688;
            stoneLight = 0xFF98A0B4;
            stoneDark = 0xFF4C5264;
            crackColor = 0xFF232630;
        }
        invalidate();
    }

    /** Sets the block's spin, in degrees. Driven by the animation loop, not by draw calls. */
    public void setBlockRotation(float degrees) {
        blockRotation = degrees;
        invalidate();
    }

    /** The pickaxe's swing rotation in degrees, relative to its resting angle. */
    public void setPickaxeRotation(float degrees) {
        pickaxeRotation = REST_ROTATION + degrees;
        invalidate();
    }

    /** Offsets the pickaxe from its anchor, in fractions of the view's size, for the wind-up travel. */
    public void setPickaxeOffset(float dxFraction, float dyFraction) {
        pickaxeOffsetX = dxFraction;
        pickaxeOffsetY = dyFraction;
        invalidate();
    }

    /**
     * Sets the crack stage directly.
     *
     * <p>Separate from {@link #setProgress} so the hold state can freeze the fracture: progress
     * may keep creeping while init is slow, but the visible stage only advances when the owner
     * decides it should.
     */
    public void setCrackStage(int stage) {
        int clamped = Math.max(0, Math.min(stage, OreCrackSprites.CRACK_STAGES - 1));
        if (clamped == crackStage) return;
        crackStage = clamped;
        invalidate();
    }

    public int getCrackStage() {
        return crackStage;
    }

    /** Sets load progress in {@code [0,1]}; the owner converts real milestones into this. */
    public void setProgress(float value) {
        float clamped = value < 0f ? 0f : (value > 1f ? 1f : value);
        if (Math.abs(clamped - progress) < 0.0001f) return;
        progress = clamped;
        invalidate();
    }

    public float getProgress() {
        return progress;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desired = (int) (84 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(desired, widthMeasureSpec),
                resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        int cell = Math.max(1, (int) (Math.min(width, height) / OreCrackSprites.GRID));
        float art = cell * OreCrackSprites.GRID;
        float left = (width - art) / 2f;
        float top = (height - art) / 2f;

        // Block and crack share one rotation about the block's centre, so the fracture turns with
        // the surface it is breaking.
        canvas.save();
        rotation.reset();
        rotation.setRotate(blockRotation, left + art / 2f, top + art / 2f);
        canvas.concat(rotation);
        drawShadedSprite(canvas, OreCrackSprites.block(), left, top, cell);
        drawMask(canvas, OreCrackSprites.crack(crackStage), left, top, cell, crackColor);
        canvas.restore();

        drawPickaxe(canvas, width, height, cell);
    }

    /** Paints a sprite whose characters select from the block palette. */
    private void drawShadedSprite(Canvas canvas, String[] sprite, float left, float top, int cell) {
        for (int row = 0; row < sprite.length; row++) {
            String line = sprite[row];
            for (int col = 0; col < line.length(); col++) {
                int color = shadeFor(line.charAt(col));
                if (color == 0) continue;
                paint.setColor(color);
                drawCell(canvas, left, top, cell, col, row);
            }
        }
    }

    /** Paints every {@code #} cell of a mask in one colour. */
    private void drawMask(Canvas canvas, String[] mask, float left, float top, int cell, int color) {
        paint.setColor(color);
        for (int row = 0; row < mask.length; row++) {
            String line = mask[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                drawCell(canvas, left, top, cell, col, row);
            }
        }
    }

    private int shadeFor(char c) {
        switch (c) {
            case '#': return stoneColor;
            case '+': return stoneLight;
            case '-': return stoneDark;
            case 'o': return crystalColor;
            case 'O': return crystalLight;
            default: return 0;
        }
    }

    private void drawPickaxe(Canvas canvas, int width, int height, int cell) {
        float art = cell * OreCrackSprites.GRID;
        float baseLeft = width - art;
        float pivotX = baseLeft + art / 2f + pickaxeOffsetX * width;
        float pivotY = art / 2f + pickaxeOffsetY * height;

        canvas.save();
        canvas.rotate(pickaxeRotation, pivotX, pivotY);

        String[] sprite = OreCrackSprites.pickaxe();
        float left = pivotX - art / 2f;
        float top = pivotY - art / 2f;
        for (int row = 0; row < sprite.length; row++) {
            String line = sprite[row];
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                int color;
                if (c == '#') color = ironColor;
                else if (c == '/') color = ironLight;
                else if (c == 'h') color = handleColor;
                else continue;
                paint.setColor(color);
                drawCell(canvas, left, top, cell, col, row);
            }
        }
        canvas.restore();
    }

    private void drawCell(Canvas canvas, float left, float top, int cell, int col, int row) {
        float x = left + col * cell;
        float y = top + row * cell;
        canvas.drawRect(x, y, x + cell, y + cell, paint);
    }

    private static int blend(int from, int to, float ratio) {
        float r = Math.max(0f, Math.min(ratio, 1f));
        float inv = 1f - r;
        return Color.rgb(
                (int) (Color.red(from) * inv + Color.red(to) * r),
                (int) (Color.green(from) * inv + Color.green(to) * r),
                (int) (Color.blue(from) * inv + Color.blue(to) * r));
    }
}
