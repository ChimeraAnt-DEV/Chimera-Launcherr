package org.chimeramc.client.launcher.ui.splash;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.Random;

/**
 * The splash backdrop: a live Minecraft-inspired voxel world behind the logo lockup.
 *
 * <p>A flat gradient with a few dots still reads as a "loading" screen. This paints an actual
 * little world so the splash looks like a place warming up: a deep gradient sky with twinkling
 * parallax stars and the occasional shooting star, a blocky terrain silhouette on two parallax
 * layers with lit caps, floating voxel cubes that bob and turn with real per-face shading, two
 * slow accent glows for depth, and a field of rising embers with additive blending.
 *
 * <p>All motion lives in {@link SplashWorld} and {@link SplashParticles}, both pure and unit-tested;
 * this view only projects and paints. One {@link ValueAnimator} drives every frame and self-cancels
 * on detach, so a finishing splash never keeps burning frames. Paints, paths and the background
 * shader are cached and only rebuilt on a size or palette change — rebuilding a {@link LinearGradient}
 * or a {@link Path} every frame is the classic way an animated background quietly costs more than
 * the game it is covering.
 *
 * <p>The floating cubes are drawn with a tiny software 3D projection (rotate, perspective divide,
 * back-face cull, depth sort) so they read as solid voxels rather than flat squares. That is the
 * "Minecraft" signature: an isometric block with a bright top, a mid side and a dark front.
 */
public final class SplashSceneView extends View {

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emberPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint terrainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint capPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shootingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path terrainPath = new Path();
    private final Path blockPath = new Path();

    private final SplashParticles particles = new SplashParticles();
    private final SplashWorld world = new SplashWorld();
    private final Random random = new Random();

    private ValueAnimator driver;
    private long lastFrameMs;

    private int accent = 0xFF6236E8;
    private boolean dark = true;
    private boolean animationsEnabled = true;

    private int cachedWidth;
    private int cachedHeight;
    private int cachedAccent;
    private boolean cachedDark;

    /** Per-block palette, so the floating cubes are grass, stone and amethyst, not one colour. */
    private final int[] blockTop = new int[SplashWorld.BLOCK_COUNT];
    private final int[] blockSide = new int[SplashWorld.BLOCK_COUNT];
    private final int[] blockFront = new int[SplashWorld.BLOCK_COUNT];

    // Reusable projection scratch for one cube, so drawing allocates nothing per frame.
    private final float[] cubeX = new float[8];
    private final float[] cubeY = new float[8];
    private final float[] cubeDepth = new float[8];
    private final float[] faceDepth = new float[6];
    private final int[] faceOrder = {0, 1, 2, 3, 4, 5};

    public SplashSceneView(Context context) {
        super(context);
        init();
    }

    public SplashSceneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setWillNotDraw(false);
        emberPaint.setStyle(Paint.Style.FILL);
        starPaint.setStyle(Paint.Style.FILL);
        terrainPaint.setStyle(Paint.Style.FILL);
        capPaint.setStyle(Paint.Style.FILL);
        blockPaint.setStyle(Paint.Style.FILL);
        shootingPaint.setStyle(Paint.Style.STROKE);
        shootingPaint.setStrokeCap(Paint.Cap.ROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Additive light on the embers and stars is what makes them read as glowing motes
            // rather than flat dots. Below Q the plain fill still looks correct, just less luminous.
            emberPaint.setBlendMode(BlendMode.PLUS);
            starPaint.setBlendMode(BlendMode.PLUS);
        }
        particles.seed(random, 0x5EEDL);
        world.seed(random, 0xC0FFEEL);
        rebuildBlockPalette();
    }

    /** Applies the active palette; the world belongs to the theme rather than sitting on it. */
    public void setPalette(int accent, boolean dark) {
        this.accent = accent;
        this.dark = dark;
        rebuildBlockPalette();
        rebuildShadersIfNeeded();
        invalidate();
    }

    /**
     * Enables or disables the animation. When off the scene paints one static frame, so reduced
     * motion still gets a finished-looking world instead of a blank one.
     */
    public void setAnimationsEnabled(boolean enabled) {
        animationsEnabled = enabled;
        if (enabled) {
            startDriver();
        } else {
            stopDriver();
            invalidate();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animationsEnabled) startDriver();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopDriver();
    }

    private void startDriver() {
        if (driver != null || !isAttachedToWindow()) return;
        lastFrameMs = SystemClock.uptimeMillis();
        driver = ValueAnimator.ofFloat(0f, 1f);
        driver.setDuration(Long.MAX_VALUE);
        driver.setRepeatCount(ValueAnimator.INFINITE);
        driver.setInterpolator(new LinearInterpolator());
        driver.addUpdateListener(a -> advance());
        driver.start();
    }

    private void stopDriver() {
        if (driver != null) {
            driver.cancel();
            driver = null;
        }
    }

    private void advance() {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(64f, now - lastFrameMs);
        lastFrameMs = now;
        particles.update(dt);
        world.update(dt);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rebuildShadersIfNeeded();
    }

    private void rebuildShadersIfNeeded() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        if (w == cachedWidth && h == cachedHeight && accent == cachedAccent && dark == cachedDark) {
            return;
        }
        cachedWidth = w;
        cachedHeight = h;
        cachedAccent = accent;
        cachedDark = dark;

        // A deeper sky than before: near-black at the top, warming toward the horizon, so the
        // stars have contrast and the terrain silhouette has something to sit against.
        int top = dark ? 0xFF07050F : 0xFFF3EFFA;
        int horizon = dark ? 0xFF241A44 : 0xFFDCD0F2;
        backgroundPaint.setShader(new LinearGradient(
                0f, 0f, 0f, h, new int[]{top, horizon},
                new float[]{0f, 0.72f}, Shader.TileMode.CLAMP));

        // One glow high and one low, offset horizontally, so the two drift past each other and the
        // backdrop reads as depth rather than a single pulsing light.
        glowPaint.setShader(new RadialGradient(
                w * 0.32f, h * 0.30f, Math.max(w, h) * 0.62f,
                withAlpha(accent, dark ? 60 : 40), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    private void rebuildBlockPalette() {
        // Grass green top with a dirt side, a grey stone, and an accent-tinted "amethyst" block, so
        // the floating cubes read as Minecraft materials rather than coloured tiles.
        for (int i = 0; i < SplashWorld.BLOCK_COUNT; i++) {
            int kind = i % 3;
            int base;
            int top;
            if (kind == 0) {
                top = 0xFF5FBF46;            // grass
                base = 0xFF7A5230;           // dirt
            } else if (kind == 1) {
                top = 0xFFB9BEC7;            // stone
                base = 0xFF8A8F99;
            } else {
                top = lighten(accent, 0.45f); // amethyst, follows the accent
                base = accent;
            }
            blockTop[i] = top;
            blockSide[i] = shade(base, 0.78f);
            blockFront[i] = shade(base, 0.58f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        float density = getResources().getDisplayMetrics().density;

        canvas.drawRect(0f, 0f, w, h, backgroundPaint);

        float t = world.time();
        float drift = (float) Math.sin(t * 0.18f) * w * 0.06f;
        canvas.save();
        canvas.translate(drift, (float) Math.cos(t * 0.13f) * h * 0.04f);
        canvas.drawRect(-w * 0.2f, -h * 0.2f, w * 1.2f, h * 1.2f, glowPaint);
        canvas.restore();

        drawStars(canvas, w, h, density);
        drawShootingStar(canvas, w, h, density);
        drawTerrain(canvas, w, h, density);
        drawBlocks(canvas, w, h, density);
        drawEmbers(canvas, w, h, density);
    }

    private void drawStars(Canvas canvas, int w, int h, float density) {
        for (int i = 0; i < world.starCount(); i++) {
            int layer = world.starLayer(i);
            float px = wrap01(world.starX(i) + world.starParallax(layer)) * w;
            float py = world.starY(i) * h;
            float radius = world.starRadiusDp(i) * density;
            int alpha = (int) (world.starAlpha(i) * 255f);
            // The far layer is tinted toward the accent; the near layer stays white, so the sky has
            // two temperatures and reads as depth.
            int color = layer == 0 ? withAlpha(accent, alpha) : withAlpha(0xFFFFFFFF, alpha);
            starPaint.setColor(color);
            canvas.drawCircle(px, py, radius, starPaint);
        }
    }

    private void drawShootingStar(Canvas canvas, int w, int h, float density) {
        if (!world.shootingStarActive()) return;
        float alpha = world.shootingStarAlpha();
        float x = world.shootingStarX() * w;
        float y = world.shootingStarY() * h;
        float trail = world.shootingStarTrail() * w;
        double angle = Math.toRadians(20f);
        float dx = (float) Math.cos(angle);
        float dy = (float) Math.sin(angle);
        shootingPaint.setStrokeWidth(Math.max(1.5f, 2f * density));
        shootingPaint.setColor(withAlpha(0xFFFFFFFF, (int) (alpha * 220f)));
        canvas.drawLine(x, y, x - dx * trail, y - dy * trail, shootingPaint);
        starPaint.setColor(withAlpha(0xFFFFFFFF, (int) (alpha * 255f)));
        canvas.drawCircle(x, y, Math.max(1.5f, 2.2f * density), starPaint);
    }

    /**
     * Draws the two parallax terrain ridges as blocky silhouettes.
     *
     * <p>Each ridge is a stepped path rather than a smooth curve — a voxel world has flat tops — and
     * the near ridge is taller and lighter than the far one, which is what makes the two read as
     * separate depths instead of one flat skyline.
     */
    private void drawTerrain(Canvas canvas, int w, int h, float density) {
        float columnWidth = (float) w / SplashWorld.TERRAIN_COLUMNS;
        for (int layer = 0; layer < 2; layer++) {
            boolean near = layer == 1;
            int body = dark
                    ? (near ? 0xFF1E1738 : 0xFF14102A)
                    : (near ? 0xFFD6C9F0 : 0xFFE6DDF6);
            int cap = near
                    ? (dark ? lighten(accent, 0.1f) : shade(accent, 0.85f))
                    : (dark ? shade(accent, 0.62f) : shade(accent, 0.7f));

            float offset = world.terrainOffset(layer) * w;
            terrainPath.reset();
            terrainPath.moveTo(-columnWidth + offset, h);
            for (int c = 0; c <= SplashWorld.TERRAIN_COLUMNS + 1; c++) {
                int col = mod(c, SplashWorld.TERRAIN_COLUMNS);
                float x = c * columnWidth + offset;
                float top = h - world.terrainHeight(layer, col) * h;
                terrainPath.lineTo(x, top);
            }
            terrainPath.lineTo((SplashWorld.TERRAIN_COLUMNS + 2) * columnWidth + offset, h);
            terrainPath.close();
            terrainPaint.setColor(body);
            canvas.drawPath(terrainPath, terrainPaint);

            // A lit cap on each column top, so the ridge catches the "moonlight" like grass does.
            capPaint.setColor(cap);
            float capHeight = Math.max(2f, 3f * density);
            for (int c = 0; c <= SplashWorld.TERRAIN_COLUMNS + 1; c++) {
                int col = mod(c, SplashWorld.TERRAIN_COLUMNS);
                float x = c * columnWidth + offset;
                float top = h - world.terrainHeight(layer, col) * h;
                canvas.drawRect(x, top, x + columnWidth, top + capHeight, capPaint);
            }
        }
    }

    private void drawBlocks(Canvas canvas, int w, int h, float density) {
        for (int i = 0; i < world.blockCount(); i++) {
            float cx = world.blockX(i) * w;
            float cy = world.blockY(i) * h;
            float size = world.blockSizeDp(i) * density;
            drawVoxelCube(canvas, cx, cy, size, world.blockRotation(i),
                    blockTop[i], blockSide[i], blockFront[i]);
        }
    }

    /**
     * Projects and paints one shaded voxel cube.
     *
     * <p>A rotate → perspective-divide → back-face-cull → depth-sort pass over eight corners. The
     * cull and the sort are what make it a solid block: a face turned away is skipped, and the rest
     * are painted far to near. Each visible face is filled with its material's shade for the face's
     * normal, which is the flat-shaded look Minecraft's own blocks have.
     */
    private void drawVoxelCube(Canvas canvas, float cx, float cy, float size, float rotationDeg,
                               int topColor, int sideColor, int frontColor) {
        double ry = Math.toRadians(rotationDeg);
        double rx = Math.toRadians(rotationDeg * 0.45 + 18.0);
        double cosY = Math.cos(ry), sinY = Math.sin(ry);
        double cosX = Math.cos(rx), sinX = Math.sin(rx);

        for (int corner = 0; corner < 8; corner++) {
            float ux = ((corner & 1) == 0 ? -0.5f : 0.5f);
            float uy = ((corner & 2) == 0 ? -0.5f : 0.5f);
            float uz = ((corner & 4) == 0 ? -0.5f : 0.5f);
            // Rotate about Y, then X.
            double x1 = ux * cosY + uz * sinY;
            double z1 = -ux * sinY + uz * cosY;
            double y1 = uy * cosX - z1 * sinX;
            double z2 = uy * sinX + z1 * cosX;
            float persp = (float) (1.0 / (1.0 - z2 * 0.22));
            cubeX[corner] = cx + (float) (x1 * size * persp);
            cubeY[corner] = cy - (float) (y1 * size * persp);
            cubeDepth[corner] = (float) z2;
        }

        for (int face = 0; face < 6; face++) {
            float avg = 0f;
            for (int k = 0; k < 4; k++) {
                avg += cubeDepth[FACE_CORNERS[face][k]] * 0.25f;
            }
            faceDepth[face] = avg;
        }
        // Insertion sort far (small z, further) to near (large z), six items, no allocation.
        for (int i = 1; i < 6; i++) {
            int key = faceOrder[i];
            float keyDepth = faceDepth[key];
            int j = i - 1;
            while (j >= 0 && faceDepth[faceOrder[j]] > keyDepth) {
                faceOrder[j + 1] = faceOrder[j];
                j--;
            }
            faceOrder[j + 1] = key;
        }

        for (int i = 0; i < 6; i++) {
            int face = faceOrder[i];
            // Facing away when the face's corners' average z sits behind the cube centre.
            if (faceDepth[face] <= 0.02f) continue;
            int color = face == FACE_TOP ? topColor : face == FACE_BOTTOM ? sideColor
                    : (face == FACE_FRONT || face == FACE_BACK) ? frontColor : sideColor;
            blockPaint.setColor(color);
            int[] c = FACE_CORNERS[face];
            canvas.drawPath(quadPath(cubeX[c[0]], cubeY[c[0]], cubeX[c[1]], cubeY[c[1]],
                    cubeX[c[2]], cubeY[c[2]], cubeX[c[3]], cubeY[c[3]]), blockPaint);
        }
    }

    private Path quadPath(float ax, float ay, float bx, float by,
                          float dx, float dy, float ex, float ey) {
        blockPath.reset();
        blockPath.moveTo(ax, ay);
        blockPath.lineTo(bx, by);
        blockPath.lineTo(dx, dy);
        blockPath.lineTo(ex, ey);
        blockPath.close();
        return blockPath;
    }

    private void drawEmbers(Canvas canvas, int w, int h, float density) {
        for (int i = 0; i < particles.count(); i++) {
            float px = (particles.x(i) + particles.swayOffset(i)) * w;
            float py = particles.y(i) * h;
            float radius = particles.radiusDp(i) * density;
            int alpha = (int) (particles.alpha(i) * 255f);
            emberPaint.setColor(withAlpha(accent, alpha));
            canvas.drawCircle(px, py, radius, emberPaint);
        }
    }

    // Face corner indices, in corner-bit order (ix | iy<<1 | iz<<2).
    private static final int FACE_TOP = 0;
    private static final int FACE_BOTTOM = 1;
    private static final int FACE_FRONT = 2;
    private static final int FACE_BACK = 3;
    private static final int[][] FACE_CORNERS = {
            {2, 3, 7, 6}, // +y top
            {0, 1, 5, 4}, // -y bottom
            {1, 5, 7, 3}, // +x right (side)
            {0, 4, 6, 2}, // -x left (side)
            {4, 5, 7, 6}, // +z front
            {0, 1, 3, 2}, // -z back
    };

    private static int mod(int value, int modulus) {
        int v = value % modulus;
        return v < 0 ? v + modulus : v;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(
                Math.max(0, Math.min(alpha, 255)),
                Color.red(color),
                Color.green(color),
                Color.blue(color));
    }

    private static int shade(int color, float factor) {
        return Color.rgb(
                clamp255((int) (Color.red(color) * factor)),
                clamp255((int) (Color.green(color) * factor)),
                clamp255((int) (Color.blue(color) * factor)));
    }

    private static int lighten(int color, float amount) {
        return Color.rgb(
                clamp255(Color.red(color) + (int) ((255 - Color.red(color)) * amount)),
                clamp255(Color.green(color) + (int) ((255 - Color.green(color)) * amount)),
                clamp255(Color.blue(color) + (int) ((255 - Color.blue(color)) * amount)));
    }

    private static int clamp255(int value) {
        return value < 0 ? 0 : (value > 255 ? 255 : value);
    }

    private static float wrap01(float value) {
        float v = value % 1f;
        return v < 0f ? v + 1f : v;
    }
}
