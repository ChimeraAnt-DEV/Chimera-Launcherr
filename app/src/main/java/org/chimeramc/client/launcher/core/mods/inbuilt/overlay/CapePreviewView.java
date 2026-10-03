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
import org.chimeramc.client.core.cosmetics.PetPose;
import org.chimeramc.client.core.cosmetics.PlayerSkinProvider;
import org.chimeramc.client.core.cosmetics.PreviewLighting;
import org.chimeramc.client.core.cosmetics.SkinModel;
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
 * <p>Faces are painter-sorted back to front by centroid depth. For a body whose boxes never
 * interpenetrate that is sufficient, and it avoids maintaining a depth buffer for a 30-quad
 * scene. Textured faces are drawn anti-aliased with bilinear filtering, so the skin reads smoothly
 * rather than as hard pixel blocks.
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
    private CosmeticCatalog.Pet pet;
    private CosmeticCatalog.PetLocomotion petLocomotion = CosmeticCatalog.PetLocomotion.WALK;
    private final PetPose petPose = new PetPose();
    private float petPhase;

    private final CapeSimulator capeSim = new CapeSimulator();
    private final List<FaceQuad> drawList = new ArrayList<>();

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
        syncAnimation();
        invalidate();
    }

    public void setPet(CosmeticCatalog.Pet pet) {
        this.pet = pet;
        syncAnimation();
        invalidate();
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
        if (cape != null) drawCape(canvas, originX, originY, scale);
        drawModel(canvas, originX, originY, scale);
        if (accessory != null) drawAccessoryFront(canvas, originX, originY, scale);
    }

    // ---- Pet -----------------------------------------------------------------------------

    /**
     * Draws the equipped pet beside the character.
     *
     * <p>The pet is a blocky Minecraft-style animal built from the same {@link SkinModel.Box}
     * primitive the player model uses, so it belongs to the same visual language. Its pose comes
     * from {@link PetPose}, which decides the gait: legs swing for a walk or run, wings beat for a
     * flyer (crawlers get their own elytra), a swimmer paddles with a dedicated set, and a crouch
     * sinks the body. The pet stands on the character's right, scaled by its own size trait.
     */
    private void drawPet(Canvas canvas, float originX, float originY, float scale) {
        if (pet == null) return;
        float ps = scale * pet.scale;
        // Ground the pet on the same baseline as the character, a little to the right. The ground
        // line is the projected model origin, since the camera orbits above the feet.
        camera.project(0f, 0f, 0f, scale, originX, originY, projected);
        float groundY = projected[1];
        float px = originX + 20f * scale;
        float py = groundY + 0.5f * scale;

        CosmeticCatalog.PetSpecies species = pet.species;
        int body = pet.color;
        int accent = pet.accentColor;
        int dark = darker(body);

        // Body dimensions per species, in model pixels, so a bee is small and a dragon is long.
        float bodyW, bodyH, bodyD, legLen;
        switch (species) {
            case BEE:
            case BUTTERFLY:
            case DRAGONFLY:
                bodyW = 4f; bodyH = 3f; bodyD = 6f; legLen = 1.5f;
                break;
            case SPIDER:
            case ANT:
                bodyW = 5f; bodyH = 2.5f; bodyD = 7f; legLen = 2.5f;
                break;
            case FROG:
            case AXOLOTL:
            case LIZARD:
                bodyW = 5f; bodyH = 3f; bodyD = 7f; legLen = 2f;
                break;
            case SNAKE:
                bodyW = 3.5f; bodyH = 3f; bodyD = 10f; legLen = 0.5f;
                break;
            case DRAGON:
                bodyW = 7f; bodyH = 5f; bodyD = 11f; legLen = 3.5f;
                break;
            case PARROT:
                bodyW = 4f; bodyH = 5f; bodyD = 5f; legLen = 2.5f;
                break;
            case TURTLE:
                bodyW = 7f; bodyH = 3.5f; bodyD = 9f; legLen = 1.5f;
                break;
            case RABBIT:
                bodyW = 4f; bodyH = 4f; bodyD = 6f; legLen = 2f;
                break;
            default:
                bodyW = 5f; bodyH = 4f; bodyD = 8f; legLen = 3f;
                break;
        }

        // Crouch sinks the whole body; flying lifts it and adds a hover bob.
        float lift = 0f;
        if (petLocomotion == CosmeticCatalog.PetLocomotion.FLY) {
            lift = 5f + petPose.bodyBob * 0.6f;
        } else if (petLocomotion == CosmeticCatalog.PetLocomotion.SWIM) {
            lift = 1.5f;
        }
        float bodyCenterY = legLen + bodyH / 2f + lift - petPose.crouchDrop;

        // Legs first (behind the body), swinging opposite each other so the gait reads.
        if (species != CosmeticCatalog.PetSpecies.SNAKE) {
            int legs = (species == CosmeticCatalog.PetSpecies.SPIDER
                    || species == CosmeticCatalog.PetSpecies.ANT) ? 3 : 2;
            for (int i = 0; i < legs; i++) {
                float along = legs == 1 ? 0f : (i / (float) (legs - 1) - 0.5f) * bodyD * 0.7f;
                float swing = petPose.legSwing * ((i % 2 == 0) ? 1f : -1f);
                float lx = px + swing * 1.4f;
                float lz = along + swing * 0.8f;
                int legColor = darker(body);
                drawPetBox(canvas, lx, py + legLen / 2f, lz,
                        1.6f, legLen, 1.6f, ps, originX, originY, legColor);
            }
        } else {
            // A snake gets a segmented tail that ripples with the swim/walk stroke.
            for (int seg = 1; seg <= 4; seg++) {
                float segZ = -bodyD / 2f - seg * 1.6f;
                float wobble = petPose.swimStroke * seg * 0.5f;
                drawPetBox(canvas, px + wobble, py + legLen + bodyH / 2f, segZ,
                        2.4f - seg * 0.3f, 2.4f - seg * 0.3f, 1.6f, ps, originX, originY,
                        seg % 2 == 0 ? body : dark);
            }
        }

        // Tail behind the body, wagging.
        if (species != CosmeticCatalog.PetSpecies.SNAKE
                && species != CosmeticCatalog.PetSpecies.BEE
                && species != CosmeticCatalog.PetSpecies.BUTTERFLY
                && species != CosmeticCatalog.PetSpecies.DRAGONFLY
                && species != CosmeticCatalog.PetSpecies.ANT
                && species != CosmeticCatalog.PetSpecies.SPIDER) {
            float tailZ = -bodyD / 2f - 1.2f;
            float wag = petPose.tailWag * 1.6f;
            drawPetBox(canvas, px + wag, py + legLen + bodyH * 0.7f, tailZ,
                    1.4f, 1.4f, 3.2f, ps, originX, originY, body);
        }

        // The body itself.
        drawPetBox(canvas, px, py + legLen + bodyH / 2f - petPose.crouchDrop,
                petPose.bodyBob * 0.2f, bodyW, bodyH, bodyD, ps, originX, originY, body);

        // Head at the front of the body, bobbing.
        float headSize = Math.min(bodyH, 5f) + 1f;
        float headZ = bodyD / 2f + headSize * 0.3f;
        drawPetBox(canvas, px, py + legLen + bodyH + headSize * 0.3f - petPose.crouchDrop
                        + petPose.headBob, headZ, headSize, headSize, headSize,
                ps, originX, originY, body);

        // Ears / antennae per species.
        drawPetHeadgear(canvas, species, px, py + legLen + bodyH + headSize * 0.3f
                - petPose.crouchDrop, headZ, headSize, ps, originX, originY, accent);

        // Wings: flyers beat them; crawlers get a smaller elytra shell (their own flight set).
        if (petLocomotion == CosmeticCatalog.PetLocomotion.FLY) {
            if (species.isCrawler()) {
                // Crawlers cannot have feathered wings, so they carry a hard elytra shell.
                drawElytra(canvas, px, py + legLen + bodyH, ps, originX, originY,
                        accent, petPose.wingFlap);
            } else {
                drawWings(canvas, px, py + legLen + bodyH, ps, originX, originY,
                        accent, petPose.wingFlap, bodyW, bodyD);
            }
        }

        // A swimmer gets a dedicated swim set: a snorkel mask and fins, so swimming looks
        // intentional rather than the pet just floating.
        if (petLocomotion == CosmeticCatalog.PetLocomotion.SWIM && species.isSwimmer()) {
            drawSwimSet(canvas, species, px, py + legLen + bodyH + headSize * 0.3f - petPose.crouchDrop,
                    headZ, headSize, ps, originX, originY, accent);
        }
    }

    /** A single shaded pet box, projected with the same camera as the character. */
    private void drawPetBox(Canvas canvas, float cx, float cy, float cz,
                            float w, float h, float d, float scale,
                            float originX, float originY, int color) {
        SkinModel.Box box = SkinModel.Box.of("pet", cx, cy, cz, w, h, d);
        paint.setShader(null);
        for (SkinModel.Face face : SkinModel.Face.values()) {
            if (!camera.faceVisible(face)) continue;
            float[][] c = box.faceCorners(face);
            for (int i = 0; i < 4; i++) {
                projectPoint(c[i][0], c[i][1], c[i][2], scale, originX, originY);
                corners[i * 2] = projected[0];
                corners[i * 2 + 1] = projected[1];
            }
            paint.setColor(shadeFace(color, face));
            drawQuad(canvas, corners);
        }
    }

    private void drawPetHeadgear(Canvas canvas, CosmeticCatalog.PetSpecies species,
                                 float px, float headBaseY, float headZ, float headSize,
                                 float scale, float originX, float originY, int accent) {
        float topY = headBaseY + headSize * 0.4f;
        switch (species) {
            case CAT:
            case DOG:
            case FOX:
            case WOLF:
                // Pointed ears: two small boxes angled by a per-species amount.
                for (int side = -1; side <= 1; side += 2) {
                    drawPetBox(canvas, px + side * headSize * 0.28f, topY + 1.2f, headZ,
                            1.2f, 1.6f, 1.2f, scale, originX, originY, accent);
                }
                break;
            case RABBIT:
                for (int side = -1; side <= 1; side += 2) {
                    drawPetBox(canvas, px + side * headSize * 0.2f, topY + 2.4f, headZ,
                            1.0f, 4.0f, 1.0f, scale, originX, originY, accent);
                }
                break;
            case BEE:
            case BUTTERFLY:
            case DRAGONFLY:
                // Antennae: thin stalks with a lit tip.
                for (int side = -1; side <= 1; side += 2) {
                    drawPetBox(canvas, px + side * 1.0f, topY + 1.8f, headZ + 0.5f,
                            0.6f, 2.6f, 0.6f, scale, originX, originY, accent);
                }
                break;
            case DRAGON:
                for (int side = -1; side <= 1; side += 2) {
                    drawPetBox(canvas, px + side * headSize * 0.3f, topY + 1.6f, headZ - 0.4f,
                            1.0f, 2.2f, 1.0f, scale, originX, originY, accent);
                }
                break;
            default:
                break;
        }
    }

    private void drawWings(Canvas canvas, float px, float bodyTopY, float scale,
                           float originX, float originY, int accent, float flap,
                           float bodyW, float bodyD) {
        // A wing is a fan of three feather quads per side; the flap raises them about the shoulder.
        float rise = flap * 3.2f;
        float spread = 3.0f + flap * 1.5f;
        for (int side = -1; side <= 1; side += 2) {
            for (int f = 0; f < 3; f++) {
                float t = 0.3f + f * 0.3f;
                float[][] pts = {
                        {px + side * bodyW * 0.3f, bodyTopY, 0f},
                        {px + side * (bodyW * 0.3f + spread * t), bodyTopY + rise - f * 0.6f, -t * bodyD * 0.4f},
                        {px + side * (bodyW * 0.3f + spread * (t + 0.3f)), bodyTopY + rise * 0.4f, -bodyD * 0.2f},
                };
                paint.setShader(null);
                paint.setColor(shadeFace(accent, SkinModel.Face.FRONT));
                path.reset();
                boolean first = true;
                for (float[] p : pts) {
                    projectPoint(p[0], p[1], p[2], scale, originX, originY);
                    if (first) { path.moveTo(projected[0], projected[1]); first = false; }
                    else path.lineTo(projected[0], projected[1]);
                }
                path.close();
                canvas.drawPath(path, paint);
            }
        }
    }

    /** A hard elytra shell for crawling flyers: no feathers, just two domed wing cases. */
    private void drawElytra(Canvas canvas, float px, float bodyTopY, float scale,
                            float originX, float originY, int accent, float flap) {
        float lift = 1.5f + flap * 2.5f;
        paint.setShader(null);
        paint.setColor(darker(accent));
        for (int side = -1; side <= 1; side += 2) {
            drawPetBox(canvas, px + side * 2.2f, bodyTopY + lift, -1.5f,
                    3.4f, 1.4f, 5.0f, scale, originX, originY, darker(accent));
        }
    }

    /** A swim set: a snorkel mask over the eyes and a fin on each leg. */
    private void drawSwimSet(Canvas canvas, CosmeticCatalog.PetSpecies species,
                             float px, float headBaseY, float headZ, float headSize,
                             float scale, float originX, float originY, int accent) {
        // Mask band across the front of the head.
        drawPetBox(canvas, px, headBaseY + headSize * 0.1f, headZ + headSize * 0.5f,
                headSize * 1.05f, 1.0f, 0.6f, scale, originX, originY, accent);
        // Snorkel tube rising from one side of the mask.
        drawPetBox(canvas, px + headSize * 0.5f, headBaseY + headSize * 0.6f, headZ,
                0.7f, 2.4f, 0.7f, scale, originX, originY, accent);
        // Fins on the front legs.
        for (int side = -1; side <= 1; side += 2) {
            drawPetBox(canvas, px + side * 1.4f, 0.9f, 2.6f,
                    1.4f, 1.2f, 2.4f, scale, originX, originY, accent);
        }
    }

    /**
     * Per-face shading for a flat-coloured pet/accessory box, via {@link PreviewLighting}.
     *
     * <p>A colour multiply rather than the character's alpha overlay, because these quads are solid
     * fills, not bitmaps. Sharing the light model is what keeps a pet's shading consistent with the
     * character it stands beside.
     */
    private int shadeFace(int color, SkinModel.Face face) {
        return PreviewLighting.shadeColor(color, face, 1f);
    }

    private void drawGroundShadow(Canvas canvas, float originX, float originY, float scale) {
        paint.setShader(null);
        paint.setColor(0x33000000);
        // The camera orbits the body's centre, so the feet no longer sit at originY; project the
        // model origin to find the real ground line, or the shadow floats at the character's waist.
        camera.project(0f, 0f, 0f, scale, originX, originY, projected);
        float groundY = projected[1];
        float rx = 14f * scale;
        float ry = 3.2f * scale;
        canvas.drawOval(originX - rx, groundY - ry, originX + rx, groundY + ry, paint);
    }

    private void projectPoint(float x, float y, float z, float scale,
                             float originX, float originY) {
        camera.project(x, y, z, scale, originX, originY, projected);
    }

    private void drawModel(Canvas canvas, float originX, float originY, float scale) {
        drawList.clear();

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

        // Painter's algorithm: farthest centroid first, so a nearer face covers a farther one.
        drawList.sort((a, bq) -> Float.compare(a.depth, bq.depth));

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

    /** Draws the cloth mesh behind the body, with the animated mark printed on it. */
    private void drawCape(Canvas canvas, float originX, float originY, float scale) {
        int cols = CapeSimulator.COLS;
        int rows = CapeSimulator.ROWS;
        // The cloth is anchored at the shoulders and hangs from just below the neck, on the same
        // back plane the wings reference so the two never disagree about where "behind" is.
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

        // Project every particle once, keeping its depth, so the quads can be depth-sorted.
        // Without the sort the mesh is painted row-major and, the moment the cape swings, the
        // far side of a fold paints over the near side — the "glitched paper cape" where the
        // cloth appears to fold through itself and reads as a flat sheet.
        for (int i = 0; i < particleCount; i++) {
            float mx = capeSim.x(i) / SkinModel.PIXELS_TO_BLOCKS;
            float my = capeSim.y(i) / SkinModel.PIXELS_TO_BLOCKS;
            float mz = capeSim.z(i) / SkinModel.PIXELS_TO_BLOCKS;
            projectPoint(mx, anchorY + my, anchorZ + mz, scale, originX, originY);
            capeXs[i] = projected[0];
            capeYs[i] = projected[1];
            capeParticleDepth[i] = projected[2];
        }

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
            int color = PreviewLighting.shadeColorForNormal(cloth, nx3, ny3, nz3);
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
            default:
                break;
        }
    }

    private void drawBox(Canvas canvas, float cx, float cy, float cz,
                         float w, float h, float d, float scale,
                         float originX, float originY) {
        SkinModel.Box box = SkinModel.Box.of("acc", cx, cy, cz, w, h, d);
        for (SkinModel.Face face : SkinModel.Face.values()) {
            if (!camera.faceVisible(face)) continue;
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
}
