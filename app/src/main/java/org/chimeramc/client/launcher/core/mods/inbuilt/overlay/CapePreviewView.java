package org.chimeramc.client.core.mods.inbuilt.overlay;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import org.chimeramc.client.core.cosmetics.CapePatterns;
import org.chimeramc.client.core.cosmetics.CapeSimulator;
import org.chimeramc.client.core.cosmetics.CosmeticCatalog;
import org.chimeramc.client.core.cosmetics.CosmeticLayering;
import org.chimeramc.client.core.cosmetics.AuthoredHatModels;
import org.chimeramc.client.core.cosmetics.AuthoredGeometry;
import org.chimeramc.client.core.cosmetics.AuthoredPetModels;
import org.chimeramc.client.core.cosmetics.PetModel;
import org.chimeramc.client.core.cosmetics.PetPose;
import org.chimeramc.client.core.cosmetics.PlayerSkinProvider;
import org.chimeramc.client.core.cosmetics.PreviewDepthBuffer;
import org.chimeramc.client.core.cosmetics.PreviewLighting;
import org.chimeramc.client.core.cosmetics.QuadDepthSorter;
import org.chimeramc.client.core.cosmetics.SkinModel;
import org.chimeramc.client.core.cosmetics.geometry.BedrockGeometry;
import org.chimeramc.client.core.cosmetics.geometry.BedrockGeometryParser;
import org.chimeramc.client.core.cosmetics.geometry.PreviewMeshModel;
import org.chimeramc.client.ui.animation.DynamicAnim;

import java.util.ArrayList;
import java.util.List;

/**
 * A real textured 3D render of the player's own Minecraft character, wearing the equipped cape.
 *
 * <p>The model is built from the skin's actual texture: each face of each box samples its own
 * rectangle out of the 64x64 atlas, so the preview shows the player's skin — their hair, their
 * shirt, their face — rather than a generic stand-in. The projection is a real perspective camera
 * ({@link SkinModel.Camera}): the near side of the body is magnified and the far side recedes, which
 * is what gives the render depth instead of reading as a flat isometric sketch. Hidden faces are
 * dropped by a normal test that uses the same transform as the projection, so a face is drawn
 * exactly when it points at the camera.
 *
 * <p>Faces are painter-sorted back to front. A single centroid-depth key over every face of every
 * box is not enough: two faces of different boxes can tie at some yaw, and a humanoid is full of
 * such pairs (head on torso, arm against torso), so the sort interleaves them and a hidden face
 * paints over a nearer one — the triangular holes the preview used to show. {@link QuadDepthSorter}
 * orders by the owning box first, which is exact for disjoint convex boxes, and uses the face's own
 * depth only to break ties within a box. Textured faces are drawn anti-aliased with bilinear
 * filtering, so the skin reads smoothly rather than as hard pixel blocks.
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
    /** Soft contact-shadow gradient, built once and reused for every frame. */
    private android.graphics.RadialGradient shadowGradient;

    /** Per-face cropped textures, indexed by box then face. Built once per skin. */
    private Bitmap[][] baseFaceCrops;
    private Bitmap[][] overlayFaceCrops;

    private final PlayerSkinProvider.SkinBitmap skin;
    private CosmeticCatalog.Cape cape;
    private CosmeticCatalog.Accessory accessory;
    private CosmeticCatalog.Pet pet;
    private CosmeticCatalog.PetLocomotion petLocomotion = CosmeticCatalog.PetLocomotion.WALK;
    private final PetPose petPose = new PetPose();
    private float petPhase;

    private final CapeSimulator capeSim = new CapeSimulator();
    private final List<FaceQuad> drawList = new ArrayList<>();

    /** Offscreen 2x supersample target; reallocated only when the view size changes. */
    private Bitmap frame;

    /** Body depth, so the cape is clipped where the skin is in front of it. */
    private final PreviewDepthBuffer depthBuffer = new PreviewDepthBuffer();

    /**
     * The authored Blockbench mesh for the equipped accessory, resolved once per selection.
     *
     * <p>When an accessory has a hand-authored {@code .geo.json} (see {@code AuthoredHatModels}),
     * the preview draws its real geometry — its bone pivots, per-cube UVs and mirrored faces — rather
     * than the procedural box stack. The procedural path stays for the many accessories that have no
     * authored mesh, so nothing regresses.
     */
    private List<PreviewMeshModel.Box> accessoryMesh;
    /** The authored mesh's atlas, decoded lazily; null until the first frame that needs it. */
    private Bitmap accessoryAtlas;
    private boolean accessoryAtlasLoaded;
    /** The authored mesh's identifier, used to pick the atlas file. */
    private String accessoryMeshTextureFile;

    /**
     * The authored Blockbench mesh for the equipped pet, resolved once per selection.
     *
     * <p>Same pipeline as the accessory mesh ({@code .geo.json} -> {@code BedrockGeometryParser} ->
     * {@code PreviewMeshModel}), so a pet authored in Blockbench is drawn as its real rig rather than
     * the procedural body plan. A pet with no authored mesh (or an unreadable asset) leaves this null
     * and the procedural {@code PetModel} path draws it as before.
     */
    private List<PreviewMeshModel.Box> petMesh;
    private Bitmap petAtlas;
    private boolean petAtlasLoaded;
    private String petMeshTextureFile;

    /** Reused projection scratch for the authored-mesh pass, so it allocates nothing per frame. */
    private final float[] meshCorners = new float[8];
    private final List<MeshQuad> meshDrawList = new ArrayList<>();

    /** A projected authored-mesh face, held until it is depth-sorted and painted. */
    private static final class MeshQuad {
        final float[] verts = new float[8];
        float groupDepth;
        float depth;
        float[] uv;      // source atlas rect {u, v, w, h} in atlas pixels
        boolean flipH;
        boolean flipV;
        float[] normal = {0f, 0f, 1f};
        float persp = 1f;
    }

    /** Reused cape projections so a swinging cape allocates nothing per frame. */
    private float[] capeXs;
    private float[] capeYs;
    private float[] capeParticleDepth;
    private float[] capeQuadDepth;
    private int[] capeOrder;

    private float yawDeg = 28f;
    private float pitchDeg = DEFAULT_PITCH;
    private float spinVelocity;
    private boolean userRotating;
    private long lastFrameNanos;

    /**
     * The perspective camera for the current frame. Rebuilt once per {@code onDraw} from the live
     * yaw/pitch/scale, and used by every projection and cull test so the two cannot disagree.
     */
    private SkinModel.Camera camera;

    private long animStartMs;
    private boolean animating;
    private boolean attached;

    private float lastTouchX;
    private long lastTouchMs;

    /** A projected textured quad, held until it is sorted and drawn. */
    private static final class FaceQuad {
        Bitmap texture;
        final float[] verts = new float[8];
        /** The owning box's centroid depth; the primary painter's key (see QuadDepthSorter). */
        float groupDepth;
        /** This face's own centroid depth; the tie-breaker within one box. */
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
        // Textured faces are sampled with bilinear filtering and anti-aliasing set per draw in
        // drawModel; flat accessory quads use `paint`, which is anti-aliased but unfiltered, so a
        // solid colour stays hard-edged.
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
        rebuildAccessoryMesh();
        syncAnimation();
        invalidate();
    }

    /**
     * Resolves the equipped accessory's authored Blockbench mesh, if it has one.
     *
     * <p>Runs once per selection, never per frame. The mesh is parsed from the pack assets and the
     * bone hierarchy is flattened to world-space corners here; the renderer then only projects.
     * An accessory with no authored model (or an unreadable asset) leaves {@link #accessoryMesh}
     * null and the procedural path draws it as before, so a broken file cannot blank the preview.
     */
    private void rebuildAccessoryMesh() {
        accessoryMesh = null;
        accessoryMeshTextureFile = null;
        if (accessory == null || accessory.modelId == null) return;
        if (!AuthoredHatModels.hasAuthoredModel(accessory.modelId)) return;

        String json = AuthoredHatModels.loadFromAssets(this::openAsset, accessory.modelId);
        if (json == null) return;
        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        if (geometry == null) return;
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        if (boxes.isEmpty()) return;

        accessoryMesh = boxes;
        accessoryMeshTextureFile = AuthoredHatModels.textureFor(accessory.modelId);
        // A new accessory means a new atlas; drop the cached decode so the next frame reloads it.
        if (accessoryAtlas != null && !accessoryAtlas.isRecycled()) accessoryAtlas.recycle();
        accessoryAtlas = null;
        accessoryAtlasLoaded = false;
    }

    /** Opens a pack asset, matching the {@link AuthoredGeometry.AssetOpener} contract. */
    private java.io.InputStream openAsset(String path) throws java.io.IOException {
        return getContext().getAssets().open(path);
    }

    public void setPet(CosmeticCatalog.Pet pet) {
        this.pet = pet;
        rebuildPetMesh();
        syncAnimation();
        invalidate();
    }

    /**
     * Resolves the equipped pet's authored Blockbench mesh, if it has one.
     *
     * <p>Runs once per selection, never per frame, mirroring {@link #rebuildAccessoryMesh}. A pet
     * without an authored model keeps the procedural body plan, so the shipped set of authored pets
     * is a strict upgrade with no regression for the rest.
     */
    private void rebuildPetMesh() {
        petMesh = null;
        petMeshTextureFile = null;
        if (pet == null) return;
        String petId = pet.modelId != null ? pet.modelId : pet.id;
        if (!AuthoredPetModels.hasAuthoredModel(petId)) return;

        String json = AuthoredPetModels.loadFromAssets(this::openAsset, petId);
        if (json == null) return;
        BedrockGeometry geometry = BedrockGeometryParser.parse(json);
        if (geometry == null) return;
        List<PreviewMeshModel.Box> boxes = PreviewMeshModel.build(geometry);
        if (boxes.isEmpty()) return;

        petMesh = boxes;
        petMeshTextureFile = AuthoredPetModels.textureFor(petId);
        if (petAtlas != null && !petAtlas.isRecycled()) petAtlas.recycle();
        petAtlas = null;
        petAtlasLoaded = false;
    }

    /** The gait the equipped pet is currently animating; the panel offers the ones it supports. */
    public void setPetLocomotion(CosmeticCatalog.PetLocomotion locomotion) {
        this.petLocomotion = PetPose.resolveGait(pet == null ? null : pet.species, locomotion);
        syncAnimation();
        invalidate();
    }

    public CosmeticCatalog.PetLocomotion getPetLocomotion() {
        return petLocomotion;
    }

    /** True when the displayed skin is the grey last-resort placeholder, not a real skin. */
    public boolean isShowingFallbackSkin() {
        return skin == null || skin.isFallback;
    }

    /** True when the preview is showing the built-in Steve character (no skin imported yet). */
    public boolean isShowingDefaultSteve() {
        return skin != null && skin.isDefaultSteve;
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
        boolean want = DynamicAnim.areAnimationsEnabled() && getVisibility() == VISIBLE && attached
                && (pet != null || cape != null);
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
            // and it settles naturally instead of looping forever. The drive is clamped: an
            // unclamped flick could reach hundreds of blocks/s of "wind", which is what made the
            // cape flap wildly and look broken. A hard cap keeps even the sharpest flick to a
            // believable billow, and the cape's own damping does the rest.
            float speed = Math.min(Math.abs(spinVelocity) * 0.03f, 2.6f);
            float fx = (float) Math.sin(Math.toRadians(yawDeg));
            float fz = (float) Math.cos(Math.toRadians(yawDeg));
            capeSim.step(dt, 0f, 0f, -0.02f, fx, fz, speed);
        }
        if (pet != null) {
            petPhase += PetPose.cyclesPerSecond(petLocomotion) * dt;
            if (petPhase > 1f) petPhase -= (float) Math.floor(petPhase);
            petPose.compute(pet.species, petLocomotion, petPhase);
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

        // Supersample: draw everything into an offscreen bitmap at 2x and scale it down, so the
        // thin cloth edges and the model's silhouette are anti-aliased instead of jagged. The
        // preview is small, so 2x costs little and is the single biggest quality win.
        int ss = 2;
        Bitmap target = ensureFrame(w * ss, h * ss);
        if (target != null) {
            Canvas scaled = new Canvas(target);
            scaled.scale(ss, ss);
            drawScene(scaled, w, h);
            canvas.drawBitmap(target, new android.graphics.Rect(0, 0, w * ss, h * ss),
                    new android.graphics.Rect(0, 0, w, h), pixelPaint);
            return;
        }
        drawScene(canvas, w, h);
    }

    /** The offscreen supersample target, or null when it cannot be allocated. */
    private Bitmap ensureFrame(int width, int height) {
        if (frame != null && frame.getWidth() == width && frame.getHeight() == height) {
            frame.eraseColor(0);
            return frame;
        }
        try {
            frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable t) {
            frame = null;
        }
        return frame;
    }

    private void drawScene(Canvas canvas, int w, int h) {
        // The depth buffer covers the logical frame the quads project into (not the supersampled
        // canvas), so the occlusion test compares like coordinates.
        depthBuffer.prepare(w, h);

        // The model is 32 px tall. Fit it, leaving headroom for the cape to swing and room to the
        // right for the pet to trot beside the character.
        float scale = Math.min(w / 52f, h / 42f);
        float originX = w * 0.40f;
        float originY = h * 0.58f + SkinModel.heightPixels() * 0.5f * scale;

        // One perspective camera for the whole frame, orbiting the body's centre so the character
        // sits still while the near side grows and the far side recedes. Every draw call and cull
        // test below goes through it, so they cannot drift apart.
        camera = new SkinModel.Camera(yawDeg, pitchDeg, SkinModel.CAMERA_DISTANCE, 0f, 18f, 0f);

        drawGroundShadow(canvas, originX, originY, scale);
        if (pet != null) {
            // The pet is drawn first so the character always reads as the subject; it sits to the
            // character's right, on the same ground line, so it looks like it is walking with them.
            drawPet(canvas, originX, originY, scale);
        }
        // Wings sit behind the character, so they must be painted before the cape and the body;
        // drawing them last (as the accessories used to be) made them cover the torso and read as
        // a flat sheet stuck to the front of the model. The over-head and beside-head pieces stay
        // in the foreground pass, where they belong.
        drawAccessoryBehind(canvas, originX, originY, scale);

        // The cape is drawn through a per-quad depth test against the body, not a whole-cape
        // before/after choice. The cape hangs just before the back plane (z around -2.4) while the
        // body occupies z in [-2, 2], so a whole-cape decision is wrong for any camera that is not
        // squarely behind or in front: whichever side calls it, part of the cloth is nearer than
        // the skin and part is behind it. The visible defect was the cape's far/edge quads being
        // painted over the torso — the cape "showing through the skin". The body is rasterised
        // first into a depth buffer, then each cape quad is drawn only where it is actually in
        // front of the skin, so the cape can never appear through the character.
        if (cape != null) projectCape(originX, originY, scale);
        drawModel(canvas, originX, originY, scale);
        if (cape != null) paintCape(canvas);
        if (accessory != null) drawAccessoryFront(canvas, originX, originY, scale);
    }

    // ---- Pet -----------------------------------------------------------------------------

    /**
     * Draws the equipped pet beside the character.
     *
     * <p>The pet's mesh comes from {@link PetModel}, the same vanilla-grounded body plan the in-game
     * geometry uses, so the preview and the game cannot drift. The pose comes from {@link PetPose},
     * which now animates per-bone: legs swing for a walk or run, a bird flaps and bobs, a serpent
     * undulates, a swimmer paddles. When the player is running, crouching, flying or swimming, the
     * crawling bugs switch to their contextual behaviour (riding the head, looking around, clinging
     * against the wind or the water) exactly as the in-game controller does.
     */
    private void drawPet(Canvas canvas, float originX, float originY, float scale) {
        if (pet == null) return;

        // An authored Blockbench pet is drawn as its real rig, placed beside the player at the same
        // ground line. The procedural body plan below is the fallback for pets with no authored mesh.
        if (petMesh != null && !petMesh.isEmpty()) {
            drawAuthoredPet(canvas, originX, originY, scale);
            return;
        }

        float ps = scale * pet.scale;

        // The ground line is the projected model origin, since the camera orbits above the feet.
        camera.project(0f, 0f, 0f, scale, originX, originY, projected);
        float groundY = projected[1];

        // Crawling bugs ride the player's head once the player is moving, crouching, flying or
        // swimming; otherwise they trot at the feet like any other pet. The state comes from the
        // pose, which derives it from the same rules the in-game controller uses.
        if (petPose.ridesHead) {
            camera.project(0f, 32f, 0f, scale, originX, originY, projected);
            drawPetModel(canvas, projected[0] + petPose.crawlLoopX * ps,
                    projected[1] + petPose.crawlLoopZ * ps * 0.2f, ps * 0.5f,
                    originX, originY, petPose.leanX, 0f, 0f, true);
        } else {
            float px = originX + 20f * scale;
            float py = groundY + 0.5f * scale;
            float lift = 0f;
            if (petLocomotion == CosmeticCatalog.PetLocomotion.FLY) {
                lift = 5f + petPose.bodyBob * 0.6f;
            } else if (petLocomotion == CosmeticCatalog.PetLocomotion.SWIM) {
                lift = 1.5f;
            }
            // The pet is a separate body, so it needs its own contact shadow; without one it reads
            // as pasted over the scene. The shadow fades and shrinks with the lift, so a flying or
            // swimming pet is visibly off the ground instead of glued to it.
            drawPetShadow(canvas, px, groundY + 0.5f * scale, scale, lift);
            drawPetModel(canvas, px, py + lift, ps, originX, originY,
                    0f, -petPose.crouchDrop, 0f, false);
        }
    }

    /**
     * Draws an authored Blockbench pet beside the player.
     *
     * <p>The mesh is centred on its own X/Z (authored pets are symmetric about the origin) and
     * placed at the same ground line the procedural pet uses, so the two paths look like the same
     * companion rather than two different toys. A soft contact shadow grounds it, and the gait lift
     * matches the procedural path so a flying or swimming pet is visibly off the ground.
     */
    private void drawAuthoredPet(Canvas canvas, float originX, float originY, float scale) {
        float ps = scale * pet.scale;
        camera.project(0f, 0f, 0f, scale, originX, originY, projected);
        float groundY = projected[1];

        float lift = 0f;
        if (petLocomotion == CosmeticCatalog.PetLocomotion.FLY) {
            lift = 5f + petPose.bodyBob * 0.6f;
        } else if (petLocomotion == CosmeticCatalog.PetLocomotion.SWIM) {
            lift = 1.5f;
        }

        // Beside the player, matching the procedural offset, with the mesh centred on its own X/Z.
        float px = originX + 20f * scale;
        float offsetX = 20f;
        float offsetZ = 0f;
        float offsetY = lift;

        drawPetShadow(canvas, px, groundY + 0.5f * scale, scale, lift);
        drawAuthoredMeshAt(canvas, petMesh, ensurePetAtlas(),
                offsetX, offsetY, offsetZ, pet.color, scale, originX, originY);
    }

    /** The atlas for the current authored pet, decoded once and cached. */
    private Bitmap ensurePetAtlas() {
        if (petAtlasLoaded) return petAtlas;
        petAtlasLoaded = true;
        petAtlas = null;
        if (petMeshTextureFile == null) return null;
        try (java.io.InputStream input = getContext().getAssets()
                .open(AuthoredPetModels.DIR + "/" + petMeshTextureFile)) {
            petAtlas = android.graphics.BitmapFactory.decodeStream(input);
        } catch (java.io.IOException | RuntimeException e) {
            petAtlas = null;
        }
        return petAtlas;
    }

    /** A soft contact shadow under the pet, faded by how far it is off the ground. */
    private void drawPetShadow(Canvas canvas, float px, float groundY, float scale, float lift) {
        float fade = 1f / (1f + lift * 0.35f);
        paint.setShader(shadowGradient());
        paint.setAlpha((int) (255 * fade));
        float rx = 6.5f * scale * fade;
        float ry = 1.7f * scale * fade;
        canvas.save();
        canvas.translate(px, groundY);
        canvas.scale(rx, ry);
        canvas.drawCircle(0f, 0f, 1f, paint);
        canvas.restore();
        paint.setAlpha(255);
        paint.setShader(null);
    }

    /**
     * Paints every box of the pet's {@link PetModel} plan, grouped by bone so the pose can move the
     * head, tail, wings and legs independently.
     *
     * <p>Each bone's transform is a rotation about its own pivot (the joint), which is what makes a
     * leg swing from the hip and a tail wag from the rump rather than the whole animal tilting.
     */
    private void drawPetModel(Canvas canvas, float px, float py, float scale,
                              float originX, float originY,
                              float extraRotX, float extraDY, float extraDZ, boolean riding) {
        List<PetModel.Part> parts = PetModel.parts(pet.species, pet.scale);
        PetModel.Body body = PetModel.body(pet.species, pet.scale);

        int bodyColor = pet.color;
        int accent = pet.accentColor;
        int dark = darker(bodyColor);

        // A stable bone order so the painter's algorithm sees a consistent grouping.
        String[] bones = {PetModel.BONE_ROOT, PetModel.BONE_TAIL, PetModel.BONE_WING_L,
                PetModel.BONE_WING_R, PetModel.BONE_LEG_A, PetModel.BONE_LEG_B,
                PetModel.BONE_LEG_C, PetModel.BONE_HEAD};

        for (String bone : bones) {
            float[] pivot = PetModel.pivot(bone, body);
            // Per-bone rotation, in degrees, from the pose. The riding bugs use the contextual
            // rotations the in-game controller plays, so the two views agree.
            float rotX = extraRotX;
            float rotY = 0f;
            float rotZ = 0f;
            float legPhase = 0f;
            if (PetModel.BONE_HEAD.equals(bone)) {
                rotY = petPose.headYaw;
                rotX += petPose.headPitch;
            } else if (PetModel.BONE_TAIL.equals(bone)) {
                rotY = petPose.tailWag * 16f;
            } else if (bone.startsWith("leg_")) {
                int idx = bone.equals(PetModel.BONE_LEG_A) ? 0
                        : bone.equals(PetModel.BONE_LEG_B) ? 1 : 2;
                legPhase = petPose.legSwing * (idx == 1 ? -1f : 1f);
                rotZ = legPhase * 30f;
            } else if (bone.startsWith("wing_")) {
                rotZ = petPose.wingFlap * 55f * (bone.equals(PetModel.BONE_WING_R) ? -1f : 1f);
            }

            for (PetModel.Part part : parts) {
                if (!part.bone.equals(bone)) continue;
                int color = part.accent ? accent : bodyColor;
                // Legs are darker so they read as under the body; a shadowed variant, not a
                // separate material.
                if (part.role == PetModel.Role.LEG) color = dark;

                // Rotate the cube about the bone pivot.
                float lx = part.x - pivot[0];
                float ly = part.y - pivot[1];
                float lz = part.z - pivot[2];
                float[] r = rotate(lx, ly, lz, rotX, rotY, rotZ);
                float cx = pivot[0] + r[0];
                float cy = pivot[1] + r[1] + extraDY;
                float cz = pivot[2] + r[2] + extraDZ;
                drawPetBox(canvas, px + cx, py + cy, cz,
                        part.sx, part.sy, part.sz, scale, originX, originY, color,
                        rotX, rotY, rotZ);
            }
        }
    }

    /** Rotates a point by the bone's X/Y/Z degrees (Z, then Y, then X), in model space. */
    private static float[] rotate(float x, float y, float z, float rotX, float rotY, float rotZ) {
        double rx = Math.toRadians(rotX);
        double ry = Math.toRadians(rotY);
        double rz = Math.toRadians(rotZ);
        // Z
        double x1 = x * Math.cos(rz) - y * Math.sin(rz);
        double y1 = x * Math.sin(rz) + y * Math.cos(rz);
        // Y
        double x2 = x1 * Math.cos(ry) + z * Math.sin(ry);
        double z2 = -x1 * Math.sin(ry) + z * Math.cos(ry);
        // X
        double y3 = y1 * Math.cos(rx) - z2 * Math.sin(rx);
        double z3 = y1 * Math.sin(rx) + z2 * Math.cos(rx);
        return new float[]{(float) x2, (float) y3, (float) z3};
    }

    /** A single shaded pet box, projected with the same camera as the character. */
    private void drawPetBox(Canvas canvas, float cx, float cy, float cz,
                            float w, float h, float d, float scale,
                            float originX, float originY, int color) {
        drawPetBox(canvas, cx, cy, cz, w, h, d, scale, originX, originY, color, 0f, 0f, 0f);
    }

    /**
     * A single shaded pet box whose shading follows the bone's rotation.
     *
     * <p>Shading the face from its <em>rotated</em> normal is what makes an animated limb read as
     * three-dimensional: a leg swinging through its arc brightens and darkens with the swing, the
     * way a lit surface does, instead of staying at a constant shade while the geometry moves. The
     * rotation is the same one the box's centre was rotated by, so the light and the geometry
     * cannot disagree.
     */
    private void drawPetBox(Canvas canvas, float cx, float cy, float cz,
                            float w, float h, float d, float scale,
                            float originX, float originY, int color,
                            float rotX, float rotY, float rotZ) {
        SkinModel.Box box = SkinModel.Box.of("pet", cx, cy, cz, w, h, d);
        paint.setShader(null);
        float persp = camera.perspectiveAt(cx, cy, cz);
        for (SkinModel.Face face : SkinModel.Face.values()) {
            if (!camera.faceVisible(face)) continue;
            float[][] c = box.faceCorners(face);
            for (int i = 0; i < 4; i++) {
                projectPoint(c[i][0], c[i][1], c[i][2], scale, originX, originY);
                corners[i * 2] = projected[0];
                corners[i * 2 + 1] = projected[1];
            }
            paint.setColor(shadeRotated(color, face, persp, rotX, rotY, rotZ));
            drawQuad(canvas, corners);
        }
    }

    private int shadeFace(int color, SkinModel.Face face, float perspective) {
        return PreviewLighting.shadeColor(color, face, perspective);
    }

    /** Shades a face using its normal rotated by the bone's own rotation. */
    private static int shadeRotated(int color, SkinModel.Face face, float perspective,
                                    float rotX, float rotY, float rotZ) {
        if (rotX == 0f && rotY == 0f && rotZ == 0f) {
            return PreviewLighting.shadeColor(color, face, perspective);
        }
        float[] n = rotate(face.nx, face.ny, face.nz, rotX, rotY, rotZ);
        return PreviewLighting.shadeColorForNormal(color, n[0], n[1], n[2]);
    }

    private void drawGroundShadow(Canvas canvas, float originX, float originY, float scale) {
        // A soft radial contact shadow rather than a flat disc: the gradient fades to nothing at
        // the rim so the character reads as standing on the surface instead of over a dark oval.
        // The shader is built once at unit radius and the canvas is scaled, so an ellipse costs no
        // per-frame allocation and the softness scales with the character.
        paint.setShader(shadowGradient());
        camera.project(0f, 0f, 0f, scale, originX, originY, projected);
        float groundY = projected[1];
        float rx = 15f * scale;
        float ry = 3.4f * scale;
        canvas.save();
        canvas.translate(originX, groundY);
        canvas.scale(rx, ry);
        canvas.drawCircle(0f, 0f, 1f, paint);
        canvas.restore();
        paint.setShader(null);
    }

    private android.graphics.RadialGradient shadowGradient() {
        if (shadowGradient == null) {
            shadowGradient = new android.graphics.RadialGradient(0f, 0f, 1f,
                    new int[]{0x48000000, 0x20000000, 0x00000000},
                    new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP);
        }
        return shadowGradient;
    }

    private void projectPoint(float x, float y, float z, float scale,
                             float originX, float originY) {
        camera.project(x, y, z, scale, originX, originY, projected);
    }

    private void drawModel(Canvas canvas, float originX, float originY, float scale) {
        drawList.clear();
        // The depth buffer was sized in drawScene to the logical (unscaled) frame, matching the
        // coordinates the quads are projected into. This method only records into it.

        List<SkinModel.Box> boxes = SkinModel.boxes();
        for (int b = 0; b < boxes.size(); b++) {
            SkinModel.Box box = boxes.get(b);
            for (SkinModel.Face face : SkinModel.Face.values()) {
                if (!camera.faceVisible(face)) continue;

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
                // Record this opaque face into the depth buffer too, so the cape test below sees
                // exactly the body silhouette at this camera angle.
                depthBuffer.accept(quad.verts, quad.depth);
                // The box's own centroid depth orders whole boxes before their faces, so two
                // boxes' faces can never interleave — that interleave is what tore holes at the
                // head/torso and arm/torso seams.
                projectPoint(box.cx, box.cy, box.cz, scale, originX, originY);
                quad.groupDepth = projected[2];
                quad.texture = baseFaceCrops == null ? null : baseFaceCrops[b][face.ordinal()];
                float persp = camera.perspectiveAt(box.cx, box.cy, box.cz);
                // Black overlay at an alpha the light model derives from the face normal, so the
                // brightness runs continuously around the model instead of flipping at an edge.
                quad.tint = PreviewLighting.overlayAlphaFor(face, persp) << 24;
                drawList.add(quad);

                Bitmap over = overlayFaceCrops == null ? null : overlayFaceCrops[b][face.ordinal()];
                if (over != null) {
                    FaceQuad overlayQuad = new FaceQuad();
                    System.arraycopy(quad.verts, 0, overlayQuad.verts, 0, 8);
                    // Nudged a hair toward the viewer so the overlay layer paints after its base
                    // face without ever changing which box is in front.
                    overlayQuad.groupDepth = quad.groupDepth + 0.5f;
                    overlayQuad.depth = quad.depth + 0.5f;
                    overlayQuad.texture = over;
                    // Hats, hair and eyes live on this layer; shading it as hard as the base would
                    // dim the very detail it exists to show.
                    overlayQuad.tint =
                            PreviewLighting.overlayAlphaForOverlayLayer(face, persp) << 24;
                    drawList.add(overlayQuad);
                }
            }
        }

        // Painter's algorithm: farthest box first, then farthest face within it.
        drawList.sort((a, bq) -> QuadDepthSorter.compare(
                a.groupDepth, a.depth, bq.groupDepth, bq.depth));

        // Textured faces get a smooth, anti-aliased bilinear sample — the difference between a
        // blocky proof-of-concept and a render. The pixel paint is reserved for flat accessory
        // quads, which must stay hard-edged.
        pixelPaint.setAntiAlias(true);
        pixelPaint.setFilterBitmap(true);
        pixelPaint.setDither(true);
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

    /**
     * Fills the quad described by {@code v}, which is in {@link SkinModel.Box#faceCorners} grid
     * order: {@code [TL, TR, BL, BR]}.
     *
     * <p>Traversing the array in order would draw a bow-tie, so the perimeter is walked
     * TL → TR → BR → BL instead. That order is correct for both the flat accessory fills and the
     * character's shading overlay; the textured pass uses {@code drawBitmapMesh}, which wants the
     * grid order directly.
     */
    private void drawQuad(Canvas canvas, float[] v) {
        path.reset();
        path.moveTo(v[0], v[1]);
        path.lineTo(v[2], v[3]);
        path.lineTo(v[6], v[7]);
        path.lineTo(v[4], v[5]);
        path.close();
        canvas.drawPath(path, paint);
    }

    /**
     * Per-face shading for the textured character, via the shared {@link PreviewLighting} model.
     *
     * <p>Returns a black overlay whose alpha rises as the face turns away from the light, so the
     * brightness runs continuously around the model rather than snapping between a hand-written
     * ramp's steps. The perspective factor keeps the near side a touch brighter, the cheap depth
     * cue that makes a perspective render read as volumetric.
     */
    private static int shadeFor(SkinModel.Face face, float perspective) {
        return PreviewLighting.overlayAlphaFor(face, perspective) << 24;
    }

    /** Average camera depth of the projected cloth, set by {@link #projectCape}. */
    private float capeAverageDepth;

    /**
     * Projects the cloth mesh once for this frame and records the average depth.
     *
     * <p>Split out of the paint so the caller can decide whether the cape is in front of or behind
     * the body before choosing a draw order. Without this the cape was always painted first and so
     * vanished whenever the camera swung around to the player's back.
     */
    private void projectCape(float originX, float originY, float scale) {
        int cols = CapeSimulator.COLS;
        int rows = CapeSimulator.ROWS;
        float anchorY = 24f;
        float anchorZ = CosmeticLayering.CAPE_Z;

        int particleCount = cols * rows;
        int quadCount = (cols - 1) * (rows - 1);
        if (capeXs == null || capeXs.length != particleCount) {
            capeXs = new float[particleCount];
            capeYs = new float[particleCount];
            capeParticleDepth = new float[particleCount];
            capeQuadDepth = new float[quadCount];
            capeOrder = new int[quadCount];
        }

        float depthSum = 0f;
        for (int i = 0; i < particleCount; i++) {
            float mx = capeSim.x(i) / SkinModel.PIXELS_TO_BLOCKS;
            float my = capeSim.y(i) / SkinModel.PIXELS_TO_BLOCKS;
            float mz = capeSim.z(i) / SkinModel.PIXELS_TO_BLOCKS;
            projectPoint(mx, anchorY + my, anchorZ + mz, scale, originX, originY);
            capeXs[i] = projected[0];
            capeYs[i] = projected[1];
            capeParticleDepth[i] = projected[2];
            depthSum += projected[2];
        }
        capeAverageDepth = particleCount == 0 ? 0f : depthSum / particleCount;
    }

    /**
     * Paints the cloth mesh with the animated mark printed on it.
     *
     * <p>Requires {@link #projectCape} to have run for this frame; the draw order in {@code onDraw}
     * guarantees it. Kept separate from the projection so the depth sort and the paint order can
     * disagree about nothing.
     */
    private void paintCape(Canvas canvas) {
        int cols = CapeSimulator.COLS;
        int rows = CapeSimulator.ROWS;
        int quadCount = (cols - 1) * (rows - 1);

        for (int r = 0; r + 1 < rows; r++) {
            for (int c = 0; c + 1 < cols; c++) {
                int q = r * (cols - 1) + c;
                int i00 = r * cols + c, i10 = r * cols + c + 1;
                int i01 = (r + 1) * cols + c, i11 = (r + 1) * cols + c + 1;
                capeQuadDepth[q] = (capeParticleDepth[i00] + capeParticleDepth[i10]
                        + capeParticleDepth[i01] + capeParticleDepth[i11]) * 0.25f;
                capeOrder[q] = q;
            }
        }
        sortQuadsByDepth(capeOrder, capeQuadDepth, quadCount);

        paint.setShader(null);
        for (int k = 0; k < quadCount; k++) {
            int q = capeOrder[k];
            int r = q / (cols - 1);
            int c = q % (cols - 1);
            int i00 = r * cols + c, i10 = r * cols + c + 1;
            int i01 = (r + 1) * cols + c, i11 = (r + 1) * cols + c + 1;

            // Real surface shading from the cloth's own geometry. The normal comes from the two
            // edges of the quad, so a fold that curls toward the light brightens and one curling
            // away darkens — the same continuous light the character's faces use, which is what
            // makes the cloth read as a three-dimensional surface rather than a painted sheet.
            // The sign of the screen-space cross product flips the normal for the back of a fold.
            float e1x = capeXs[i10] - capeXs[i00];
            float e1y = capeYs[i10] - capeYs[i00];
            float e2x = capeXs[i01] - capeXs[i00];
            float e2y = capeYs[i01] - capeYs[i00];
            float nzScreen = e1x * e2y - e1y * e2x;
            // A screen-space cross product of two projected edges gives the surface's tilt.
            double nx3 = nzScreen == 0f ? 0.0 : (double) -e1y * 0.02;
            double ny3 = nzScreen == 0f ? 0.0 : (double) -e1x * 0.02;
            double nz3 = nzScreen >= 0f ? 1.0 : -1.0;
            // The cloth's own weave, evaluated at the quad's centre in normalised cloth space.
            // Sharing CapePatterns with the in-game texture painter is what keeps the menu preview
            // and the pack the player wears showing the same pattern.
            float u = (c + 0.5f) / Math.max(1f, cols - 1f);
            float wv = (r + 0.5f) / Math.max(1f, rows - 1f);
            int cloth = CapePatterns.colorAt(cape.pattern, u, wv, cape.color, cape.accentColor);
            // The Optifine "OF" monogram is lettering, not a weave, so it is applied on top of the
            // cloth colour from the shared glyph rule. The in-game texture painter draws the same
            // glyph, so the preview and the worn cape cannot show a different mark.
            if (cape.pattern == CosmeticCatalog.CapePattern.OPTIFINE
                    && CapePatterns.optifineMonogramAt(u, wv)) {
                cloth = cape.accentColor;
            }
            int color = PreviewLighting.shadeColorForNormal(cloth, nx3, ny3, nz3);

            // Per-quad depth test against the body. The whole quad is drawn only when it is in
            // front of the skin at all four corners; otherwise it is skipped, so the cape can never
            // paint over the character it hangs behind. This is what fixes the cape showing through
            // the skin — a whole-cape before/after decision cannot, because at most camera angles
            // part of the cloth is nearer than the body and part is farther.
            if (!depthBuffer.test(capeXs[i00], capeYs[i00], capeQuadDepth[q])
                    || !depthBuffer.test(capeXs[i10], capeYs[i10], capeQuadDepth[q])
                    || !depthBuffer.test(capeXs[i11], capeYs[i11], capeQuadDepth[q])
                    || !depthBuffer.test(capeXs[i01], capeYs[i01], capeQuadDepth[q])) {
                continue;
            }
            paint.setColor(color);
            path.reset();
            path.moveTo(capeXs[i00], capeYs[i00]);
            path.lineTo(capeXs[i10], capeYs[i10]);
            path.lineTo(capeXs[i11], capeYs[i11]);
            path.lineTo(capeXs[i01], capeYs[i01]);
            path.close();
            canvas.drawPath(path, paint);
        }

        drawCapeTrim(canvas);
        if (cape.branded) drawCrawlingMark(canvas);
    }

    /** Farthest-first insertion sort; small fixed quad count, allocation-free. */
    private static void sortQuadsByDepth(int[] order, float[] depth, int count) {
        for (int i = 1; i < count; i++) {
            int key = order[i];
            float keyDepth = depth[key];
            int j = i - 1;
            while (j >= 0 && depth[order[j]] > keyDepth) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
    }

    private void drawCapeTrim(Canvas canvas) {
        int cols = CapeSimulator.COLS;
        float[] xs = capeXs;
        float[] ys = capeYs;
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(cape.trimColor);
        path.reset();
        path.moveTo(xs[cols - 1], ys[cols - 1]);
        for (int c = cols - 2; c >= 0; c--) path.lineTo(xs[c], ys[c]);
        for (int r = 1; r < CapeSimulator.ROWS; r++) path.lineTo(xs[r * cols], ys[r * cols]);
        for (int r = CapeSimulator.ROWS - 1; r >= 0; r--) {
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
    private void drawCrawlingMark(Canvas canvas) {
        int cols = CapeSimulator.COLS;
        int rows = CapeSimulator.ROWS;
        float[] xs = capeXs;
        float[] ys = capeYs;
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
     * Accessories behind the character: anything worn on the back.
     *
     * <p>Wings are geometry anchored to the shoulder blades, so they are painted before the cape
     * and the body. A back-worn piece drawn in the foreground pass covers the torso and looks
     * pasted onto the chest instead of worn.
     */
    private void drawAccessoryBehind(Canvas canvas, float originX, float originY, float scale) {
        if (accessory == null || accessory.kind == CosmeticCatalog.AccessoryKind.NONE) return;
        switch (accessory.kind) {
            case WINGS:
                drawWingsAccessory(canvas, originX, originY, scale);
                break;
            case BACKPACK:
                drawBackpack(canvas, originX, originY, scale);
                break;
            case SCARF:
                // A scarf's trailing tail hangs behind the shoulders, so its back half goes here.
                paint.setShader(null);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(darker(accessory.color));
                drawBox(canvas, -2.2f, 20f, CosmeticLayering.CAPE_Z + 0.5f,
                        1.6f, 6f, 1.2f, scale, originX, originY);
                break;
            default:
                break;
        }
    }

    private void drawWingsAccessory(Canvas canvas, float originX, float originY, float scale) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accessory.color);
        paint.setAlpha(235);
        for (int side = -1; side <= 1; side += 2) {
            float[][] pts = {
                    {side * 2.6f, 22f, CosmeticLayering.WINGS_Z + 0.4f},
                    {side * 11f, 24.5f, CosmeticLayering.WINGS_Z + 1.9f},
                    {side * 12.5f, 16f, CosmeticLayering.WINGS_Z + 2.9f},
                    {side * 7f, 13.5f, CosmeticLayering.WINGS_Z + 1.4f},
                    {side * 2.6f, 15f, CosmeticLayering.WINGS_Z + 0.4f}
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
        // Feather ribs so the wings read as layered feathers rather than two flat triangles.
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, scale * 0.5f));
        paint.setColor(darker(accessory.color));
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                float t = 0.28f + f * 0.26f;
                projectPoint(side * (2.6f + t * 8.4f), 22f - t * 7.5f,
                        CosmeticLayering.WINGS_Z + 0.4f - t * 1.8f, scale, originX, originY);
                float sx = projected[0];
                float sy = projected[1];
                projectPoint(side * (2.6f + t * 9.9f), 21.6f - t * 9.0f,
                        CosmeticLayering.WINGS_Z + 0.4f - t * 2.4f, scale, originX, originY);
                canvas.drawLine(sx, sy, projected[0], projected[1], paint);
            }
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
    }

    /** A box worn on the back, with a strap over each shoulder and a pocket flap. */
    private void drawBackpack(Canvas canvas, float originX, float originY, float scale) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(accessory.color);
        drawBox(canvas, 0f, 18f, CosmeticLayering.CAPE_Z - 0.2f,
                7f, 8f, 3f, scale, originX, originY);
        // Pocket flap in the accent colour, so the pack is not a plain cube.
        paint.setColor(accessory.accentColor);
        drawBox(canvas, 0f, 15.6f, CosmeticLayering.CAPE_Z - 1.2f,
                5f, 2.2f, 1.4f, scale, originX, originY);
        // Shoulder straps come around to the front and are finished in the foreground pass.
    }

    /**
     * Accessories in front of the character: worn on or around the head, face and torso.
     *
     * <p>Drawn after the model so they sit over the head instead of being hidden inside it.
     */
    private void drawAccessoryFront(Canvas canvas, float originX, float originY, float scale) {
        if (accessory == null || accessory.kind == CosmeticCatalog.AccessoryKind.NONE) return;
        // An authored Blockbench mesh is the exact model the in-game pack draws, so prefer it over
        // the procedural box stack whenever the accessory has one.
        if (accessoryMesh != null) {
            drawAuthoredMesh(canvas, originX, originY, scale);
            return;
        }
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        switch (accessory.kind) {
            case HEADPHONES: {
                // Band arcing over the crown, then one cup on each side, in the same 3D space as
                // the head box (which spans y=24..32, x=-4..4, z=-4..4). Anchoring to those actual
                // bounds is what stops the headphones floating above the head or sinking into it.
                canvas.save();
                path.reset();
                int segments = 16;
                for (int i = 0; i <= segments; i++) {
                    double a = Math.PI * (i / (double) segments);
                    float x = (float) (Math.cos(a) * 4.6f);
                    float y = 32f + (float) (Math.sin(a) * 1.4f);
                    projectPoint(x, y, 0f, scale, originX, originY);
                    if (i == 0) path.moveTo(projected[0], projected[1]);
                    else path.lineTo(projected[0], projected[1]);
                }
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(2f, scale * 1.0f));
                paint.setColor(accessory.color);
                canvas.drawPath(path, paint);
                canvas.restore();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(darker(accessory.color));
                // Cups: slightly proud of the head's x bounds so they read as worn over the ears.
                drawBox(canvas, -4.6f, 27.6f, 0f, 1.6f, 4.4f, 4.4f, scale, originX, originY);
                drawBox(canvas, 4.6f, 27.6f, 0f, 1.6f, 4.4f, 4.4f, scale, originX, originY);
                break;
            }
            case HALO: {
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(accessory.color);
                paint.setStrokeWidth(Math.max(2f, scale * 0.9f));
                path.reset();
                int segments = 24;
                for (int i = 0; i <= segments; i++) {
                    double a = i / (double) segments * Math.PI * 2.0;
                    projectPoint((float) (Math.cos(a) * 5.2f), 35.0f,
                            (float) (Math.sin(a) * 5.2f), scale, originX, originY);
                    if (i == 0) path.moveTo(projected[0], projected[1]);
                    else path.lineTo(projected[0], projected[1]);
                }
                canvas.drawPath(path, paint);
                // A soft inner glow ring, so the halo reads as lit rather than as a drawn circle.
                paint.setStrokeWidth(Math.max(1f, scale * 0.4f));
                paint.setAlpha(140);
                canvas.drawPath(path, paint);
                paint.setAlpha(255);
                paint.setStyle(Paint.Style.FILL);
                break;
            }
            case CAP: {
                // A flat crown box with a forward brim, in the palette colour.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 33.4f, 0f, 8.6f, 2.4f, 8.6f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 32.6f, 4.8f, 6.2f, 1.0f, 3.0f, scale, originX, originY);
                break;
            }
            case BEANIE: {
                // A rounded crown with a folded band and a pom-pom on top.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 33.8f, 0f, 8.8f, 3.0f, 8.8f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 32.6f, 0f, 9.0f, 1.2f, 9.0f, scale, originX, originY);
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 35.8f, 0f, 2.0f, 2.0f, 2.0f, scale, originX, originY);
                break;
            }
            case CROWN: {
                // A band with five points, the middle one taller.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 33.0f, 0f, 8.6f, 1.6f, 8.6f, scale, originX, originY);
                for (int i = -2; i <= 2; i++) {
                    float h = (i == 0) ? 2.6f : 1.8f;
                    drawBox(canvas, i * 1.7f, 34.0f + h / 2f, 0f,
                            1.0f, h, 8.6f, scale, originX, originY);
                }
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 33.8f, 0f, 1.2f, 1.2f, 9.0f, scale, originX, originY);
                break;
            }
            case GLASSES: {
                // Two lenses joined by a bridge, sitting proud of the face.
                paint.setColor(accessory.color);
                drawBox(canvas, -1.6f, 29.6f, 4.4f, 3.2f, 2.0f, 0.6f, scale, originX, originY);
                drawBox(canvas, 1.6f, 29.6f, 4.4f, 3.2f, 2.0f, 0.6f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 29.6f, 4.4f, 1.0f, 0.6f, 0.6f, scale, originX, originY);
                break;
            }
            case MASK: {
                // A bandana covering the lower face, with a knot at one side.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 27.6f, 4.2f, 8.0f, 4.4f, 0.8f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 4.4f, 28.4f, 2.4f, 1.4f, 1.4f, 1.4f, scale, originX, originY);
                break;
            }
            case SCARF: {
                // A wrap around the neck plus the trailing tail that started in the back pass.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 22.8f, 0f, 8.4f, 2.0f, 4.4f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 21.6f, 0f, 8.6f, 0.8f, 4.6f, scale, originX, originY);
                break;
            }
            case BACKPACK: {
                // The straps come around the shoulders into the foreground.
                paint.setColor(accessory.accentColor);
                drawBox(canvas, -3.2f, 19f, 2.4f, 1.2f, 7.0f, 0.8f, scale, originX, originY);
                drawBox(canvas, 3.2f, 19f, 2.4f, 1.2f, 7.0f, 0.8f, scale, originX, originY);
                break;
            }
            case HORNS: {
                // Two tapered horns rising from the crown.
                paint.setColor(accessory.color);
                drawBox(canvas, -2.6f, 35.2f, 0f, 1.6f, 4.0f, 1.6f, scale, originX, originY);
                drawBox(canvas, 2.6f, 35.2f, 0f, 1.6f, 4.0f, 1.6f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, -3.2f, 37.2f, 0f, 1.0f, 1.6f, 1.0f, scale, originX, originY);
                drawBox(canvas, 3.2f, 37.2f, 0f, 1.0f, 1.6f, 1.0f, scale, originX, originY);
                break;
            }
            case TOPHAT: {
                // A wide brim under a tall narrow crown, with a contrasting band.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.5f, 0f, 11.0f, 1.0f, 11.0f, scale, originX, originY);
                drawBox(canvas, 0f, 36.0f, 0f, 8.0f, 6.0f, 8.0f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 33.5f, 0f, 8.2f, 1.0f, 8.2f, scale, originX, originY);
                break;
            }
            case WIZARD_HAT: {
                // A cone of graduated boxes on a wide brim, tapering to a tilted tip.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.5f, 0f, 12.0f, 1.0f, 12.0f, scale, originX, originY);
                drawBox(canvas, 0f, 34.0f, 0f, 8.0f, 2.0f, 8.0f, scale, originX, originY);
                drawBox(canvas, 0f, 36.0f, 0f, 6.0f, 2.0f, 6.0f, scale, originX, originY);
                drawBox(canvas, 0f, 38.0f, 0f, 4.0f, 2.0f, 4.0f, scale, originX, originY);
                drawBox(canvas, 0f, 40.0f, 0f, 2.0f, 2.0f, 2.0f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 41.5f, 0f, 0.8f, 1.0f, 0.8f, scale, originX, originY);
                break;
            }
            case TIARA: {
                // A low band with a rising centre stone, in the accent colour.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.5f, 0f, 8.4f, 1.0f, 8.4f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 34.0f, 3.6f, 1.6f, 2.0f, 0.8f, scale, originX, originY);
                drawBox(canvas, -2.8f, 33.7f, 3.6f, 1.2f, 1.4f, 0.8f, scale, originX, originY);
                drawBox(canvas, 2.8f, 33.7f, 3.6f, 1.2f, 1.4f, 0.8f, scale, originX, originY);
                break;
            }
            case BEARD: {
                // Graduated rows narrowing under the chin, so it reads as a beard not a slab.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 24f, 3.4f, 7.2f, 2.0f, 1.2f, scale, originX, originY);
                drawBox(canvas, 0f, 22.4f, 3.4f, 6.0f, 2.0f, 1.2f, scale, originX, originY);
                drawBox(canvas, 0f, 20.8f, 3.4f, 4.0f, 2.0f, 1.2f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 19.4f, 3.4f, 2.0f, 1.8f, 1.2f, scale, originX, originY);
                break;
            }
            case FLOWER: {
                // A small bloom tucked at one side of the head.
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 4.4f, 33.4f, 1.6f, 1.4f, 1.4f, 1.4f, scale, originX, originY);
                paint.setColor(accessory.color);
                drawBox(canvas, 4.4f, 34.6f, 1.6f, 2.4f, 1.2f, 2.4f, scale, originX, originY);
                drawBox(canvas, 3.6f, 33.4f, 1.6f, 1.2f, 1.2f, 1.2f, scale, originX, originY);
                drawBox(canvas, 5.2f, 33.4f, 1.6f, 1.2f, 1.2f, 1.2f, scale, originX, originY);
                break;
            }
            case BOWTIE: {
                // Two triangles at the collar with a centre knot.
                paint.setColor(accessory.color);
                drawBox(canvas, -1.4f, 22.4f, 2.6f, 2.4f, 2.0f, 1.2f, scale, originX, originY);
                drawBox(canvas, 1.4f, 22.4f, 2.6f, 2.4f, 2.0f, 1.2f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 22.4f, 2.8f, 1.0f, 1.4f, 1.0f, scale, originX, originY);
                break;
            }
            case EAR: {
                // Animal ears on top of the head.
                paint.setColor(accessory.color);
                drawBox(canvas, -2.4f, 34.4f, 0f, 2.0f, 3.4f, 2.0f, scale, originX, originY);
                drawBox(canvas, 2.4f, 34.4f, 0f, 2.0f, 3.4f, 2.0f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, -2.4f, 34.8f, 0.9f, 1.2f, 2.2f, 0.6f, scale, originX, originY);
                drawBox(canvas, 2.4f, 34.8f, 0.9f, 1.2f, 2.2f, 0.6f, scale, originX, originY);
                break;
            }
            case VEIL: {
                // A hood crown with a soft shroud falling behind the head and neck.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 33.2f, 0f, 8.8f, 2.4f, 8.8f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 31.5f, -4.6f, 8.4f, 2.2f, 1.2f, scale, originX, originY);
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 28.6f, -4.6f, 7.6f, 4.0f, 1.0f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 25.4f, -4.6f, 6.4f, 2.8f, 0.9f, scale, originX, originY);
                break;
            }
            case MONOCLE: {
                // One rimmed lens over the right eye, with a chain to the cheek.
                paint.setColor(accessory.color);
                drawBox(canvas, 2.5f, 29.9f, 4.2f, 3.0f, 3.0f, 0.6f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 2.5f, 29.9f, 4.4f, 3.8f, 3.8f, 0.3f, scale, originX, originY);
                drawBox(canvas, 2.5f, 27.4f, 4.2f, 0.5f, 2.0f, 0.5f, scale, originX, originY);
                break;
            }
            case ANTLERS: {
                // Branching antlers: a stem then two tines each.
                paint.setColor(accessory.color);
                drawBox(canvas, -2.6f, 34.2f, 0f, 1.2f, 4.5f, 1.2f, scale, originX, originY);
                drawBox(canvas, 2.6f, 34.2f, 0f, 1.2f, 4.5f, 1.2f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, -3.8f, 36.4f, 0f, 1.2f, 1.2f, 1.2f, scale, originX, originY);
                drawBox(canvas, 3.8f, 36.4f, 0f, 1.2f, 1.2f, 1.2f, scale, originX, originY);
                paint.setColor(accessory.color);
                drawBox(canvas, -3.1f, 38.4f, 0f, 1.0f, 2.0f, 1.0f, scale, originX, originY);
                drawBox(canvas, 3.1f, 38.4f, 0f, 1.0f, 2.0f, 1.0f, scale, originX, originY);
                break;
            }
            case PLUME: {
                // A feathered crest fanning up from a headband.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.6f, 0f, 8.4f, 1.2f, 8.4f, scale, originX, originY);
                for (int i = -2; i <= 2; i++) {
                    paint.setColor(i % 2 == 0 ? accessory.accentColor : accessory.color);
                    float h = 3.4f - Math.abs(i) * 0.4f;
                    drawBox(canvas, i * 0.7f, 33.2f + h / 2f, 0f, 0.8f, h, 0.8f,
                            scale, originX, originY);
                }
                break;
            }
            case TRICORN: {
                // A broad brim turned up at three points, over a low crown.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.5f, 0f, 12.0f, 0.9f, 12.0f, scale, originX, originY);
                drawBox(canvas, 0f, 34.2f, 0f, 8.4f, 2.6f, 8.4f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 33.6f, 5.3f, 10.8f, 2.4f, 1.4f, scale, originX, originY);
                drawBox(canvas, 0f, 33.6f, -5.3f, 10.8f, 2.4f, 1.4f, scale, originX, originY);
                drawBox(canvas, 5.3f, 33.6f, 0f, 1.4f, 2.4f, 10.8f, scale, originX, originY);
                drawBox(canvas, -5.3f, 33.6f, 0f, 1.4f, 2.4f, 10.8f, scale, originX, originY);
                break;
            }
            case MORTARBOARD: {
                // A flat board on a skullcap, with a tassel hanging at one corner.
                paint.setColor(accessory.color);
                drawBox(canvas, 0f, 32.4f, 0f, 8.4f, 1.6f, 8.4f, scale, originX, originY);
                drawBox(canvas, 0f, 33.6f, 0f, 12.8f, 0.8f, 12.8f, scale, originX, originY);
                paint.setColor(accessory.accentColor);
                drawBox(canvas, 0f, 33.6f, 0f, 0.8f, 0.8f, 0.8f, scale, originX, originY);
                drawBox(canvas, 6f, 32.8f, 0f, 0.6f, 2.4f, 0.6f, scale, originX, originY);
                break;
            }
            default:
                break;
        }
    }

    // ---- Authored Blockbench mesh ---------------------------------------------------------

    /**
     * Draws the equipped accessory's authored {@code .geo.json} mesh, if it has one.
     *
     * <p>This is the payoff of the preview pipeline: instead of a procedural box stack the preview
     * shows the exact authored model — every bone pivot, every cube, every mirrored face UV. The
     * mesh was resolved to world-space corners in {@link #rebuildAccessoryMesh}; here it is only
     * projected, depth-sorted and painted.
     *
     * <p>Colour comes from the accessory's palette when no atlas is available, and from the decoded
     * atlas when it is, so a hat authored with a real texture shows that texture and a hat without
     * one still shows its silhouette.
     */
    private void drawAuthoredMesh(Canvas canvas, float originX, float originY, float scale) {
        if (accessoryMesh == null || accessoryMesh.isEmpty()) return;
        int fallback = accessory == null ? 0xFFB0B0B0 : accessory.color;
        drawAuthoredMeshAt(canvas, accessoryMesh, ensureAccessoryAtlas(),
                0f, 0f, 0f, fallback, scale, originX, originY);
    }

    /**
     * Draws an authored mesh at a model-space offset.
     *
     * <p>Shared by the accessory (offset zero) and the pet (offset to the player's side), so the two
     * cannot drift on projection, depth sorting, lighting or UV mirroring. The offset is applied to
     * each corner before projection, which is the same as translating the mesh in the world without
     * rebuilding it.
     */
    private void drawAuthoredMeshAt(Canvas canvas, List<PreviewMeshModel.Box> mesh, Bitmap atlas,
                                    float offsetX, float offsetY, float offsetZ, int fallbackColor,
                                    float scale, float originX, float originY) {
        if (mesh == null || mesh.isEmpty()) return;

        meshDrawList.clear();
        for (PreviewMeshModel.Box box : mesh) {
            // A single centroid depth orders whole cubes; the face's own depth breaks ties within a
            // cube, exactly the two-level key QuadDepthSorter uses for the character.
            float groupDepth = centroidDepth(box, offsetX, offsetY, offsetZ, scale, originX, originY);
            for (int face = 0; face < 6; face++) {
                float[] normal = faceNormal(box, face);
                if (!camera.faceVisibleNormal(normal[0], normal[1], normal[2])) continue;
                int[] indices = PreviewMeshModel.faceCornerIndices(face);
                MeshQuad quad = new MeshQuad();
                float depthSum = 0f;
                for (int i = 0; i < 4; i++) {
                    float[] corner = box.corners[indices[i]];
                    projectPoint(corner[0] + offsetX, corner[1] + offsetY, corner[2] + offsetZ,
                            scale, originX, originY);
                    quad.verts[i * 2] = projected[0];
                    quad.verts[i * 2 + 1] = projected[1];
                    depthSum += projected[2];
                }
                quad.groupDepth = groupDepth;
                quad.depth = depthSum / 4f;
                PreviewMeshModel.FaceUv uv = box.faceUv[face];
                if (uv != null) {
                    quad.uv = new float[]{uv.u, uv.v, uv.w, uv.h};
                    quad.flipH = uv.flipH;
                    quad.flipV = uv.flipV;
                }
                quad.normal = normal;
                quad.persp = camera.perspectiveAt(box.corners[indices[0]][0] + offsetX,
                        box.corners[indices[0]][1] + offsetY,
                        box.corners[indices[0]][2] + offsetZ);
                meshDrawList.add(quad);
            }
        }

        meshDrawList.sort((a, b) -> QuadDepthSorter.compare(
                a.groupDepth, a.depth, b.groupDepth, b.depth));

        boolean textured = atlas != null;
        pixelPaint.setAntiAlias(true);
        pixelPaint.setFilterBitmap(true);
        pixelPaint.setDither(true);
        paint.setShader(null);
        for (MeshQuad quad : meshDrawList) {
            if (textured && quad.uv != null && quad.uv[2] > 0.5f && quad.uv[3] > 0.5f) {
                drawMeshQuadTextured(canvas, atlas, quad);
            } else {
                paint.setColor(PreviewLighting.shadeColorForNormal(
                        fallbackColor, quad.normal[0], quad.normal[1], quad.normal[2]));
                paint.setAlpha(255);
                drawQuad(canvas, quad.verts);
            }
        }
    }

    /** Paints one authored-mesh face from the atlas, honouring the face's mirror flags. */
    private void drawMeshQuadTextured(Canvas canvas, Bitmap atlas, MeshQuad quad) {
        int u = (int) quad.uv[0];
        int v = (int) quad.uv[1];
        int w = (int) quad.uv[2];
        int h = (int) quad.uv[3];
        if (u < 0) u = 0;
        if (v < 0) v = 0;
        if (u + w > atlas.getWidth()) w = atlas.getWidth() - u;
        if (v + h > atlas.getHeight()) h = atlas.getHeight() - v;
        if (w <= 0 || h <= 0) return;

        // Source corners in grid order [TL, TR, BL, BR]. A negative authored uv_size mirrors the
        // sample, so the left/right or top/bottom source corners swap to match the mesh.
        float su0 = quad.flipH ? u + w : u;
        float su1 = quad.flipH ? u : u + w;
        float sv0 = quad.flipV ? v + h : v;
        float sv1 = quad.flipV ? v : v + h;
        float[] src = {su0, sv0, su1, sv0, su0, sv1, su1, sv1};

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setPolyToPoly(src, 0, quad.verts, 0, 4);
        canvas.save();
        canvas.clipPath(quadPath(quad.verts));
        canvas.drawBitmap(atlas, matrix, pixelPaint);
        canvas.restore();
    }

    private final Path meshClip = new Path();

    private Path quadPath(float[] v) {
        meshClip.reset();
        meshClip.moveTo(v[0], v[1]);
        meshClip.lineTo(v[2], v[3]);
        meshClip.lineTo(v[6], v[7]);
        meshClip.lineTo(v[4], v[5]);
        meshClip.close();
        return meshClip;
    }

    /** The atlas for the current authored mesh, decoded once and cached. */
    private Bitmap ensureAccessoryAtlas() {
        if (accessoryAtlasLoaded) return accessoryAtlas;
        accessoryAtlasLoaded = true;
        accessoryAtlas = null;
        if (accessoryMeshTextureFile == null) return null;
        try (java.io.InputStream input = getContext().getAssets()
                .open(AuthoredHatModels.DIR + "/" + accessoryMeshTextureFile)) {
            accessoryAtlas = android.graphics.BitmapFactory.decodeStream(input);
        } catch (java.io.IOException | RuntimeException e) {
            accessoryAtlas = null;
        }
        return accessoryAtlas;
    }

    /** A resolved box's centroid depth, the primary painter's key for its faces. */
    private float centroidDepth(PreviewMeshModel.Box box, float offsetX, float offsetY, float offsetZ,
                                float scale, float originX, float originY) {
        float cx = 0f, cy = 0f, cz = 0f;
        for (float[] corner : box.corners) {
            cx += corner[0] / 8f;
            cy += corner[1] / 8f;
            cz += corner[2] / 8f;
        }
        camera.project(cx + offsetX, cy + offsetY, cz + offsetZ, scale, originX, originY, projected);
        return projected[2];
    }

    /** A face's outward normal, rotated by the bone chain so the cull and the light agree. */
    private static float[] faceNormal(PreviewMeshModel.Box box, int face) {
        float[] base;
        switch (face) {
            case 0: base = new float[]{0f, 1f, 0f}; break;   // top
            case 1: base = new float[]{0f, -1f, 0f}; break;  // bottom
            case 2: base = new float[]{-1f, 0f, 0f}; break;  // left / west
            case 3: base = new float[]{1f, 0f, 0f}; break;   // right / east
            case 4: base = new float[]{0f, 0f, 1f}; break;   // front / south
            default: base = new float[]{0f, 0f, -1f}; break; // back / north
        }
        return PreviewMeshModel.rotateNormal(base, box.rotations);
    }

    private void drawBox(Canvas canvas, float cx, float cy, float cz,
                         float w, float h, float d, float scale,
                         float originX, float originY) {
        SkinModel.Box box = SkinModel.Box.of("acc", cx, cy, cz, w, h, d);
        // The fill is shaded per face from the shared light, so an accessory box reads as a solid
        // object with a lit side and a shaded side instead of a flat silhouette. The colour is
        // taken from the paint the caller set, so a piece keeps its own palette.
        int base = paint.getColor();
        float persp = camera.perspectiveAt(cx, cy, cz);
        for (SkinModel.Face face : SkinModel.Face.values()) {
            if (!camera.faceVisible(face)) continue;
            float[][] c = box.faceCorners(face);
            for (int i = 0; i < 4; i++) {
                projectPoint(c[i][0], c[i][1], c[i][2], scale, originX, originY);
                corners[i * 2] = projected[0];
                corners[i * 2 + 1] = projected[1];
            }
            paint.setColor(PreviewLighting.shadeColor(base, face, persp));
            drawQuad(canvas, corners);
        }
        paint.setColor(base);
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
}
