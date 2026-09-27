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

    private int crystalColor = 0xFF6C8CF5;
    private int crystalLight = 0xFFA9BCFF;
    private int crystalDark = 0xFF3B4FB0;
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
     * than sitting on it.
     *
     * <p>The whole block is now one material, so all three block tones derive from the accent: the
     * body is the accent itself, the lit bevel is the accent blended toward white, and the shaded
     * bevel and the fracture are the accent blended toward black. Deriving them instead of
     * hardcoding greys is what keeps the cube reading as a single cut gem in any theme; the iron
     * and the handle stay neutral so they still read as metal and wood.
     */
    public void setColors(int accent, boolean dark) {
        crystalColor = accent;
        crystalLight = blend(accent, Color.WHITE, 0.42f);
        crystalDark = blend(accent, Color.BLACK, 0.55f);
        // The fracture is a near-black cut in the gem in both themes: the gem itself is the same
        // accent either way, so the crack only needs to read against that, not against the theme.
        crackColor = blend(accent, Color.BLACK, 0.8f);
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
        // Two sprites side by side -- the pickaxe then the block -- plus the gap between them, so
        // the tool sits beside the cube instead of on top of it. The view is wider than it is
        // tall by design; the halves are equal so a square grid in each half stays square.
        float density = getResources().getDisplayMetrics().density;
        int desiredHeight = (int) (72 * density);
        int desiredWidth = (int) (72 * density * OreLoaderLayout.groupWidthFraction());
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        // Positions come from OreLoaderLayout so the "pickaxe outside the block" invariant is
        // unit-tested rather than re-derived (and re-broken) here.
        int cell = OreLoaderLayout.cellSize(width);
        float art = cell * (float) OreCrackSprites.GRID;
        float blockLeft = OreLoaderLayout.blockLeft(width);
        float pickaxeLeft = OreLoaderLayout.pickaxeLeft(width);
        float top = OreLoaderLayout.top(width, height);

        // Block and crack share one rotation about the block's centre, so the fracture turns with
        // the surface it is breaking.
        canvas.save();
        rotation.reset();
        rotation.setRotate(blockRotation, blockLeft + art / 2f, top + art / 2f);
        canvas.concat(rotation);
        drawShadedSprite(canvas, OreCrackSprites.block(), blockLeft, top, cell);
        drawMask(canvas, OreCrackSprites.crack(crackStage), blockLeft, top, cell, crackColor);
        canvas.restore();

        drawPickaxe(canvas, pickaxeLeft, top, cell);
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
            case 'o': return crystalColor;
            case 'O': return crystalLight;
            case 'x': return crystalDark;
            default: return 0;
        }
    }

    /**
     * Draws the pickaxe in its own half, beside the block rather than on top of it.
     *
     * <p>The sprite's own cell origin ({@code left}, {@code top}) is the anchor; the rotation pivot
     * is its handle-side bottom-left corner, so rotating the sprite swings the head down toward the
     * block the way a wrist would, rather than spinning it about the middle of the sprite. The
     * offsets are in view fractions so the completion travel stays proportional at any view size.
     */
    private void drawPickaxe(Canvas canvas, float left, float top, int cell) {
        float art = cell * OreCrackSprites.GRID;
        // Pivot near the handle's lower end, so the head arcs toward the block.
        float pivotX = left + art * 0.45f + pickaxeOffsetX * getWidth();
        float pivotY = top + art * 0.85f + pickaxeOffsetY * getHeight();

        canvas.save();
        canvas.rotate(pickaxeRotation, pivotX, pivotY);

        String[] sprite = OreCrackSprites.pickaxe();
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
