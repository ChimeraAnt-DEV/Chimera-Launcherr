package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import org.chimeramc.client.core.cosmetics.CapeSimulator;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.cosmetics.PlayerSkinProvider;
import org.chimeramc.client.core.cosmetics.SkinModel;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.ArrayList;
import java.util.List;

/**
 * A real textured 3D render of the player's own Minecraft character, wearing the equipped cape.
 *
 * <p>The model is built from the skin's actual texture: each face of each box samples its own
 * rectangle out of the 64x64 atlas, so the preview shows the player's skin — their hair, their
 * shirt, their face — rather than a generic stand-in. The projection is an orthographic
 * isometric view, which suits a blocky model exactly and costs no matrix stack, and hidden faces
 * are dropped by a normal test that uses the same transform as the projection.
 *
 * <p>Faces are painter-sorted back to front by centroid depth. For a body whose boxes never
 * interpenetrate that is sufficient, and it avoids maintaining a depth buffer for a 30-quad
 * scene.
 *
 * <p>The cape is a {@link CapeSimulator} cloth mesh, drawn behind the body, with the animated
 * Chimera mark crawling across it. The frame callback only runs while there is motion to show,
 * so a static cape under reduced motion costs nothing per frame.
 *
 * <p>Scope note carried by the UI: this previews how the cosmetics <em>look</em>. Bedrock does
 * not expose a custom cape slot to a third-party launcher, so the cape is not injected into the
 * running game — see {@code cosmetics_scope_note}.
 */
public class CapePreviewView extends View {

    /** One crawl cycle of the brand mark. */
    private static final long CRAWL_PERIOD_MS = 3600L;

    /** Idle spin, in degrees per second, so the character keeps showing its depth. */
    private static final float IDLE_SPIN_DEG_PER_SEC = 14f;

    private static final float DEFAULT_PITCH = 18f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pixelPaint = new Paint();
    private final Path path = new Path();
    private final float[] projected = new float[3];
    private final float[] corners = new float[8];

    /** Per-face cropped textures, indexed by box then face. Built once per skin. */
    private Bitmap[][] baseFaceCrops;
    private Bitmap[][] overlayFaceCrops;

    private final PlayerSkinProvider.SkinBitmap skin;
    private CosmeticCatalog.Cape cape;
    private CosmeticCatalog.Accessory accessory;

    private final CapeSimulator capeSim = new CapeSimulator();
    private final List<FaceQuad> drawList = new ArrayList<>();

    private float yawDeg = 28f;
    private float pitchDeg = DEFAULT_PITCH;
    private float spinVelocity;
    private boolean userRotating;
    private long lastFrameNanos;

    private long animStartMs;
    private boolean animating;
    private boolean attached;

    private float lastTouchX;
    private long lastTouchMs;

    /** A projected textured quad, held until it is sorted and drawn. */
    private static final class FaceQuad {
        Bitmap texture;
        final float[] verts = new float[8];
        float depth;
        int tint;

        void set(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
            verts[0] = x0; verts[1] = y0;
            verts[2] = x1; verts[3] = y1;
            verts[4] = x2; verts[5] = y2;
            verts[6] = x3; verts[7] = y3;
        }
    }

    private final android.view.Choreographer.FrameCallback frameCallback =
            new android.view.Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    if (!animating) return;
                    float dt = lastFrameNanos == 0L ? 0f
                            : (frameTimeNanos - lastFrameNanos) / 1_000_000_000f;
                    lastFrameNanos = frameTimeNanos;
                    // Clamp so a stall cannot fast-forward the cloth.
                    dt = Math.min(dt, 0.1f);
                    advance(dt);
                    invalidate();
                    android.view.Choreographer.getInstance().postFrameCallback(this);
                }
            };

    public CapePreviewView(Context context) {
        super(context);
        setWillNotDraw(false);
        // Skins are pixel art: nearest-neighbour keeps every texel crisp instead of blurring the
        // face into mush, which is the whole reason the texture is legible at this size.
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setAntiAlias(false);
        this.skin = PlayerSkinProvider.resolve(context);
        rebuildFaceCrops();
    }

    public void setCape(CosmeticCatalog.Cape cape) {
        this.cape = cape;
        if (cape != null) capeSim.reset(0f, 0f, 0f);
        syncAnimation();
        invalidate();
    }

    public void setAccessory(CosmeticCatalog.Accessory accessory) {
        this.accessory = accessory;
        syncAnimation();
        invalidate();
    }

    /** True when the displayed skin is the built-in stand-in rather than the player's own. */
    public boolean isShowingFallbackSkin() {
        return skin == null || skin.isFallback;
    }

    public String getSkinSourceName() {
        return skin == null ? "" : skin.sourceName;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        syncAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        stopAnimation();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) syncAnimation();
        else stopAnimation();
    }

    /**
     * Runs the frame callback only while something is actually moving: the idle turn, a finger
     * on the model, or an animated cape. Reduced motion leaves the character static rather than
     * redrawing identical frames forever.
     */
    private void syncAnimation() {
        boolean want = DynamicAnim.areAnimationsEnabled() && getVisibility() == VISIBLE && attached;
        if (want && !animating) {
            animating = true;
            lastFrameNanos = 0L;
            animStartMs = android.os.SystemClock.uptimeMillis();
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback);
        } else if (!want) {
            stopAnimation();
        }
    }

    private void stopAnimation() {
        animating = false;
        lastFrameNanos = 0L;
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback);
    }

    private void advance(float dt) {
        if (!DynamicAnim.areAnimationsEnabled()) return;
        if (!userRotating) {
            // A flick keeps spinning for a moment, then settles back to the slow idle turn.
            if (Math.abs(spinVelocity) > 1f) {
                yawDeg += spinVelocity * dt;
                spinVelocity *= Math.pow(0.12f, dt);
            } else {
                yawDeg += IDLE_SPIN_DEG_PER_SEC * dt;
            }
            if (yawDeg > 360f) yawDeg -= 360f;
        }
        if (cape != null) {
            // The cloth is driven by the residual spin velocity, so a flick sends the cape out
            // and it settles naturally instead of looping forever.
            float speed = Math.abs(spinVelocity) * 0.05f;
            float fx = (float) Math.sin(Math.toRadians(yawDeg));
            float fz = (float) Math.cos(Math.toRadians(yawDeg));
            capeSim.step(dt, 0f, 0f, -0.02f, fx, fz, speed);
        }
    }

    // ---- Touch: drag to spin, flick to set it turning -----------------------------------

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                userRotating = true;
                spinVelocity = 0f;
                lastTouchX = event.getX();
                lastTouchMs = event.getEventTime();
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - lastTouchX;
                long dtMs = Math.max(1L, event.getEventTime() - lastTouchMs);
                yawDeg -= dx * 0.6f;
                spinVelocity = -(dx * 0.6f) / (dtMs / 1000f);
                lastTouchX = event.getX();
                lastTouchMs = event.getEventTime();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                userRotating = false;
                if (Math.abs(spinVelocity) < 40f) spinVelocity = 0f;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    // ---- Rendering ----------------------------------------------------------------------

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // The model is 32 px tall. Fit it, leaving headroom for the cape to swing.
        float scale = Math.min(w / 34f, h / 42f);
        float originX = w / 2f;
        float originY = h * 0.58f + SkinModel.heightPixels() * 0.5f * scale;

        drawGroundShadow(canvas, originX, originY, scale);
        if (cape != null) drawCape(canvas, originX, originY, scale);
        drawModel(canvas, originX, originY, scale);
        if (accessory != null) drawAccessory(canvas, originX, originY, scale);
    }

    private void drawGroundShadow(Canvas canvas, float originX, float originY, float scale) {
        paint.setShader(null);
        paint.setColor(0x33000000);
        float rx = 14f * scale;
        float ry = 3.2f * scale;
        canvas.drawOval(originX - rx, originY - ry, originX + rx, originY + ry, paint);
    }

    private void projectPoint(float x, float y, float z, float scale,
                             float originX, float originY) {
        SkinModel.project(x, y, z, yawDeg, pitchDeg, scale, originX, originY, projected);
    }

    private void drawModel(Canvas canvas, float originX, float originY, float scale) {
        drawList.clear();

        List<SkinModel.Box> boxes = SkinModel.boxes();
        for (int b = 0; b < boxes.size(); b++) {
            SkinModel.Box box = boxes.get(b);
            for (SkinModel.Face face : SkinModel.Face.values()) {
                if (!SkinModel.faceVisible(face, yawDeg, pitchDeg)) continue;

                float[][] modelCorners = box.faceCorners(face);
                FaceQuad quad = new FaceQuad();

                float depthSum = 0f;
                for (int c = 0; c < 4; c++) {
                    projectPoint(modelCorners[c][0], modelCorners[c][1], modelCorners[c][2],
                            scale, originX, originY);
                    quad.verts[c * 2] = projected[0];
                    quad.verts[c * 2 + 1] = projected[1];
                    depthSum += projected[2];
                }
                quad.depth = depthSum / 4f;
                quad.texture = baseFaceCrops == null ? null : baseFaceCrops[b][face.ordinal()];
                quad.tint = shadeFor(face);
                drawList.add(quad);

                Bitmap over = overlayFaceCrops == null ? null : overlayFaceCrops[b][face.ordinal()];
                if (over != null) {
                    FaceQuad overlayQuad = new FaceQuad();
                    System.arraycopy(quad.verts, 0, overlayQuad.verts, 0, 8);
                    overlayQuad.depth = quad.depth + 0.5f;
                    overlayQuad.texture = over;
                    overlayQuad.tint = quad.tint;
                    drawList.add(overlayQuad);
                }
            }
        }

        // Painter's algorithm: farthest centroid first, so a nearer face covers a farther one.
        drawList.sort((a, bq) -> Float.compare(a.depth, bq.depth));

        for (FaceQuad quad : drawList) {
            if (quad.texture != null) {
                canvas.drawBitmapMesh(quad.texture, 1, 1, quad.verts, 0, null, 0, pixelPaint);
            }
            if (Color.alpha(quad.tint) > 0) {
                paint.setShader(null);
                paint.setColor(quad.tint);
                drawQuad(canvas, quad.verts);
            }
        }
    }

    private void drawQuad(Canvas canvas, float[] v) {
        path.reset();
        path.moveTo(v[0], v[1]);
        path.lineTo(v[2], v[3]);
        path.lineTo(v[4], v[5]);
        path.lineTo(v[6], v[7]);
        path.close();
        canvas.drawPath(path, paint);
    }

    /**
     * Per-face shading. The light comes from the upper front-left, so the front is brightest and
     * the back darkest, with the sides in between. Constants rather than a real light model: a
     * fixed ramp reads as crisp pixel-art shading, which is what the game does.
     */
    private static int shadeFor(SkinModel.Face face) {
        switch (face) {
            case FRONT:
                return 0x00000000;
            case TOP:
                return 0x12FFFFFF;
            case LEFT:
                return 0x16000000;
            case RIGHT:
                return 0x1E000000;
            case BACK:
                return 0x33000000;
            case BOTTOM:
                return 0x44000000;
            default:
                return 0x00000000;
        }
    }

    /** Draws the cloth mesh behind the body, with the animated mark printed on it. */
    private void drawCape(Canvas canvas, float originX, float originY, float scale) {
        int cols = CapeSimulator.COLS;
        int rows = CapeSimulator.ROWS;
        // The cloth is anchored at the shoulders and hangs from just below the neck.
        float anchorY = 24f;
        float anchorZ = -2.4f;

        float[] xs = new float[cols * rows];
        float[] ys = new float[cols * rows];
        for (int i = 0; i < cols * rows; i++) {
            float mx = capeSim.x(i) / SkinModel.PIXELS_TO_BLOCKS;
            float my = capeSim.y(i) / SkinModel.PIXELS_TO_BLOCKS;
            float mz = capeSim.z(i) / SkinModel.PIXELS_TO_BLOCKS;
            projectPoint(mx, anchorY + my, anchorZ + mz, scale, originX, originY);
            xs[i] = projected[0];
            ys[i] = projected[1];
        }

        paint.setShader(null);
        for (int r = 0; r + 1 < rows; r++) {
            for (int c = 0; c + 1 < cols; c++) {
                int i00 = r * cols + c, i10 = r * cols + c + 1;
                int i01 = (r + 1) * cols + c, i11 = (r + 1) * cols + c + 1;
                // A vertical gradient over the cloth gives it shading without a texture.
                float v = r / (float) (rows - 1);
                paint.setColor(lerpColor(cape.color, darker(cape.color), v * 0.55f));
                path.reset();
                path.moveTo(xs[i00], ys[i00]);
                path.lineTo(xs[i10], ys[i10]);
                path.lineTo(xs[i11], ys[i11]);
                path.lineTo(xs[i01], ys[i01]);
                path.close();
                canvas.drawPath(path, paint);
            }
        }

        drawCapeTrim(canvas, xs, ys, cols, rows);
        if (cape.branded) drawCrawlingMark(canvas, xs, ys, cols, rows);
    }

    private void drawCapeTrim(Canvas canvas, float[] xs, float[] ys, int cols, int rows) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(cape.trimColor);
        path.reset();
        path.moveTo(xs[cols - 1], ys[cols - 1]);
        for (int c = cols - 2; c >= 0; c--) path.lineTo(xs[c], ys[c]);
        for (int r = 1; r < rows; r++) path.lineTo(xs[r * cols], ys[r * cols]);
        for (int r = rows - 1; r >= 0; r--) {
            path.lineTo(xs[r * cols + cols - 1], ys[r * cols + cols - 1]);
        }
        canvas.drawPath(path, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /**
     * The Chimera mark crawling across the cloth.
     *
     * The mark walks along the mesh's parameter space so it follows the cloth's curve instead of
     * sliding over a flat quad, and it is clipped to the cloth so it reads as printed on the
     * fabric. The phase comes from uptime, so the crawl speed does not depend on frame rate.
     */
    private void drawCrawlingMark(Canvas canvas, float[] xs, float[] ys, int cols, int rows) {
        float phase;
        if (cape.animated && animating && DynamicAnim.areAnimationsEnabled()) {
            long elapsed = android.os.SystemClock.uptimeMillis() - animStartMs;
            phase = (elapsed % CRAWL_PERIOD_MS) / (float) CRAWL_PERIOD_MS;
        } else {
            phase = 0.5f;
        }

        float u = 0.22f + phase * 0.56f;
        float v = 0.46f;
        float fx = u * (cols - 1);
        float fy = v * (rows - 1);
        int c0 = Math.min(cols - 2, (int) fx);
        int r0 = Math.min(rows - 2, (int) fy);
        float tx = fx - c0, ty = fy - r0;
        float x = bilerp(xs, cols, c0, r0, tx, ty);
        float y = bilerp(ys, cols, c0, r0, tx, ty);

        float spacing = Math.abs(xs[1] - xs[0]);
        float markR = Math.max(2f, spacing * 0.55f);

        int save = canvas.save();
        path.reset();
        path.moveTo(xs[0], ys[0]);
        for (int c = 1; c < cols; c++) path.lineTo(xs[c], ys[c]);
        for (int r = 1; r < rows; r++) {
            path.lineTo(xs[r * cols + cols - 1], ys[r * cols + cols - 1]);
        }
        for (int c = cols - 2; c >= 0; c--) {
            path.lineTo(xs[(rows - 1) * cols + c], ys[(rows - 1) * cols + c]);
        }
        for (int r = rows - 2; r >= 0; r--) path.lineTo(xs[r * cols], ys[r * cols]);
        path.close();
        canvas.clipPath(path);

        paint.setShader(null);
        paint.setColor(cape.trimColor);
        // Ant silhouette: three stacked lobes and two antennae, matching the app mark.
        canvas.drawCircle(x, y - markR * 0.78f, markR * 0.46f, paint);
        canvas.drawCircle(x, y, markR * 0.60f, paint);
        canvas.drawCircle(x, y + markR * 0.92f, markR * 0.84f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, markR * 0.20f));
        canvas.drawLine(x - markR * 0.28f, y - markR * 1.12f,
                x - markR * 0.66f, y - markR * 1.72f, paint);
        canvas.drawLine(x + markR * 0.28f, y - markR * 1.12f,
                x + markR * 0.66f, y - markR * 1.72f, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    private static float bilerp(float[] grid, int cols, int c0, int r0, float tx, float ty) {
        float v00 = grid[r0 * cols + c0];
        float v10 = grid[r0 * cols + c0 + 1];
        float v01 = grid[(r0 + 1) * cols + c0];
        float v11 = grid[(r0 + 1) * cols + c0 + 1];
        float top = v00 + (v10 - v00) * tx;
        float bottom = v01 + (v11 - v01) * tx;
        return top + (bottom - top) * ty;
    }

    /**
     * Accessories are drawn as projected model geometry anchored to the character, so they sit
     * in the same 3D space rather than floating as a flat decal.
     */
    private void drawAccessory(Canvas canvas, float originX, float originY, float scale) {
        if (accessory == null || CosmeticCatalog.NONE.equals(accessory.id)) return;
        paint.setShader(null);
        switch (accessory.id) {
            case "headphones": {
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 33.4f, 0f, 10f, 1.6f, 10f, scale, originX, originY);
                paint.setColor(darker(accessory.color));
                drawBox(canvas, -5.2f, 28.5f, 0f, 1.6f, 4f, 4f, scale, originX, originY);
                drawBox(canvas, 5.2f, 28.5f, 0f, 1.6f, 4f, 4f, scale, originX, originY);
                break;
            }
            case "halo": {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, scale * 0.8f));
                paint.setColor(accessory.color);
                path.reset();
                int segments = 24;
                for (int i = 0; i <= segments; i++) {
                    double a = i / (double) segments * Math.PI * 2.0;
                    projectPoint((float) (Math.cos(a) * 5.5f), 35.4f,
                            (float) (Math.sin(a) * 5.5f), scale, originX, originY);
                    if (i == 0) path.moveTo(projected[0], projected[1]);
                    else path.lineTo(projected[0], projected[1]);
                }
                canvas.drawPath(path, paint);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case "wings": {
                paint.setColor(accessory.color);
                paint.setAlpha(235);
                for (int side = -1; side <= 1; side += 2) {
                    path.reset();
                    float[][] pts = {
                            {side * 2.6f, 22f, -2f},
                            {side * 11f, 24.5f, -3.5f},
                            {side * 12.5f, 16f, -4.5f},
                            {side * 7f, 13.5f, -3f},
                            {side * 2.6f, 15f, -2f}
                    };
                    boolean first = true;
                    for (float[] p : pts) {
                        projectPoint(p[0], p[1], p[2], scale, originX, originY);
                        if (first) { path.moveTo(projected[0], projected[1]); first = false; }
                        else path.lineTo(projected[0], projected[1]);
                    }
                    path.close();
                    canvas.drawPath(path, paint);
                }
                paint.setAlpha(255);
                break;
            }
            default:
                break;
        }
    }

    private void drawBox(Canvas canvas, float cx, float cy, float cz,
                         float w, float h, float d, float scale,
                         float originX, float originY) {
        SkinModel.Box box = SkinModel.Box.of("acc", cx, cy, cz, w, h, d);
        for (SkinModel.Face face : SkinModel.Face.values()) {
            if (!SkinModel.faceVisible(face, yawDeg, pitchDeg)) continue;
            float[][] c = box.faceCorners(face);
            for (int i = 0; i < 4; i++) {
                projectPoint(c[i][0], c[i][1], c[i][2], scale, originX, originY);
                corners[i * 2] = projected[0];
                corners[i * 2 + 1] = projected[1];
            }
            drawQuad(canvas, corners);
        }
    }

    // ---- Face texture crops --------------------------------------------------------------

    /**
     * Slices the atlas into one small bitmap per face, once per skin.
     *
     * Cropping up front is what lets {@code drawBitmapMesh} texture a projected quad: it maps a
     * whole bitmap across the mesh and has no source rectangle of its own. A fully transparent
     * region is skipped so the overlay layer costs nothing on a classic skin with no hat.
     */
    private void rebuildFaceCrops() {
        Bitmap atlas = skin == null ? null : skin.bitmap;
        if (atlas == null) {
            baseFaceCrops = null;
            overlayFaceCrops = null;
            return;
        }
        List<SkinModel.Box> boxes = SkinModel.boxes();
        baseFaceCrops = new Bitmap[boxes.size()][];
        overlayFaceCrops = new Bitmap[boxes.size()][];
        for (int b = 0; b < boxes.size(); b++) {
            SkinModel.Box box = boxes.get(b);
            baseFaceCrops[b] = new Bitmap[SkinModel.Face.values().length];
            overlayFaceCrops[b] = new Bitmap[SkinModel.Face.values().length];
            for (SkinModel.Face face : SkinModel.Face.values()) {
                baseFaceCrops[b][face.ordinal()] = crop(atlas, box.baseUv(face));
                overlayFaceCrops[b][face.ordinal()] = crop(atlas, box.overlayUv(face));
            }
        }
    }

    private static Bitmap crop(Bitmap atlas, SkinModel.Uv uv) {
        if (uv == null || !SkinModel.uvWithinAtlas(uv)) return null;
        if (uv.u + uv.w > atlas.getWidth() || uv.v + uv.h > atlas.getHeight()) return null;
        Bitmap out = Bitmap.createBitmap(atlas, uv.u, uv.v, uv.w, uv.h);
        // An entirely transparent region (a classic skin's unused overlay) is dropped, so the
        // overlay pass does not draw invisible quads over every limb.
        return isBlank(out) ? null : out;
    }

    private static boolean isBlank(Bitmap b) {
        for (int y = 0; y < b.getHeight(); y++) {
            for (int x = 0; x < b.getWidth(); x++) {
                if (Color.alpha(b.getPixel(x, y)) > 8) return false;
            }
        }
        return true;
    }

    private static int darker(int color) {
        return Color.argb(255,
                (int) (Color.red(color) * 0.62f),
                (int) (Color.green(color) * 0.62f),
                (int) (Color.blue(color) * 0.62f));
    }

    private static int lerpColor(int from, int to, float t) {
        return Color.argb(255,
                (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * t),
                (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * t),
                (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
    }
}
