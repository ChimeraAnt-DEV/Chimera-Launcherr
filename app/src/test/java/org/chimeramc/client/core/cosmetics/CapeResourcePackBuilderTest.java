package org.chimeramc.client.core.cosmetics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.InflaterInputStream;

/**
 * Pins the generated cape resource pack: a pack that is subtly malformed imports but renders
 * nothing, which on a device is indistinguishable from "capes do not work".
 */
public class CapeResourcePackBuilderTest {

    @Test
    public void pngHeaderIsValidAndDimensionsMatch() {
        byte[] png = CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true);

        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        for (int i = 0; i < signature.length; i++) {
            assertEquals("png signature byte " + i, signature[i], png[i]);
        }

        // IHDR data starts after the 8-byte signature, a 4-byte length and "IHDR".
        int width = readInt(png, 16);
        int height = readInt(png, 20);
        assertEquals(CapeTexturePainter.TEXTURE_WIDTH, width);
        assertEquals(CapeTexturePainter.TEXTURE_HEIGHT, height);
        assertEquals("bit depth", 8, png[24]);
        assertEquals("colour type (truecolour+alpha)", 6, png[25]);
    }

    @Test
    public void pngPixelsRoundTripThroughInflate() throws Exception {
        int[] source = new int[CapeTexturePainter.TEXTURE_WIDTH * CapeTexturePainter.TEXTURE_HEIGHT];
        for (int i = 0; i < source.length; i++) {
            source[i] = 0xFF000000 | (int) ((i * 2654435761L) % 0xFFFFFF);
        }
        byte[] png = PngWriter.encode(64, 32, source);

        int idatStart = findChunk(png, "IDAT");
        int length = readInt(png, idatStart - 8);
        byte[] deflated = new byte[length];
        System.arraycopy(png, idatStart, deflated, 0, length);

        byte[] raw;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            raw = inflater.readAllBytes();
        }
        assertEquals(32 * (1 + 64 * 4), raw.length);

        // Each scanline starts with filter type 0, then RGBA in the order we wrote.
        for (int y = 0; y < 32; y++) {
            int rowStart = y * (1 + 64 * 4);
            assertEquals("filter byte on row " + y, 0, raw[rowStart]);
            for (int x = 0; x < 64; x++) {
                int pixel = source[y * 64 + x];
                int at = rowStart + 1 + x * 4;
                assertEquals("R at " + x + "," + y, (pixel >>> 16) & 0xFF, raw[at] & 0xFF);
                assertEquals("G at " + x + "," + y, (pixel >>> 8) & 0xFF, raw[at + 1] & 0xFF);
                assertEquals("B at " + x + "," + y, pixel & 0xFF, raw[at + 2] & 0xFF);
                assertEquals("A at " + x + "," + y, (pixel >>> 24) & 0xFF, raw[at + 3] & 0xFF);
            }
        }
    }

    @Test
    public void packWritesTheFilesTheRendererNeeds() throws Exception {
        File dir = Files.createTempDirectory("cape-pack").toFile();
        try {
            CapeResourcePackBuilder.BuiltPack built =
                    CapeResourcePackBuilder.build(dir, CosmeticCatalog.cape("chimera"));

            assertTrue("manifest", new File(dir, "manifest.json").isFile());
            assertTrue("player client entity",
                    new File(dir, CapeResourcePackBuilder.PLAYER_ENTITY_PATH).isFile());
            assertTrue("cape geometry",
                    new File(dir, CapeResourcePackBuilder.CAPE_MODEL_PATH).isFile());
            assertTrue("cape render controller",
                    new File(dir, CapeResourcePackBuilder.CAPE_RENDER_CONTROLLER_PATH).isFile());
            assertTrue("cape texture",
                    new File(dir, CapeResourcePackBuilder.CAPE_TEXTURE_PATH).isFile());
            assertTrue("pack icon", new File(dir, CapeResourcePackBuilder.PACK_ICON_PATH).isFile());
            // Written even with no pet, so the entity's reference to it always resolves.
            assertTrue("pet animation",
                    new File(dir, CapeResourcePackBuilder.PET_ANIMATION_PATH).isFile());

            assertNotNull(built);
            assertEquals(CapeResourcePackBuilder.PACK_UUID, built.uuid);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The pack must draw the cape through the player client entity, not by overriding the
     * persona-fetched {@code cape_invisible} texture. A future edit that quietly reverts to the
     * texture override fails here rather than silently rendering nothing on a device.
     */
    @Test
    public void theCapeIsDrawnByARenderControllerNotByOverridingTheVanillaTexture() {
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("player identifier", entity.contains("\"identifier\": \"minecraft:player\""));
        assertTrue("cape geometry shortname", entity.contains("\"chimera_cape\": \"geometry.chimera_cape\""));
        assertTrue("cape texture shortname",
                entity.contains("\"chimera_cape\": \"textures/entity/chimera_cape.png\""));
        assertTrue("cape controller wired", entity.contains(CapeResourcePackBuilder.CAPE_CONTROLLER_ID));
        // Persona skins keep working only below this threshold.
        assertTrue("min_engine_version must stay <= 1.13.0",
                entity.contains("\"min_engine_version\": \"1.8.0\""));

        String controller = CapeResourcePackBuilder.capeRenderControllerJson();
        assertTrue("controller renders the cape geometry",
                controller.contains("\"geometry\": \"Geometry.chimera_cape\""));
        assertTrue("controller samples the cape texture",
                controller.contains("\"Texture.chimera_cape\""));
        assertTrue("controller uses the cape material",
                controller.contains("\"Material.chimera_cape\""));
        // Every segment bone is gated by the same visibility condition, not just the first.
        assertTrue("first segment is gated by visibility",
                controller.contains("\"cape_1\": \"" + CapeResourcePackBuilder.capeVisibilityCondition() + "\""));
        assertTrue("last segment is gated too",
                controller.contains("\"cape_" + CapeGeometry.SEGMENT_COUNT + "\": \""
                        + CapeResourcePackBuilder.capeVisibilityCondition() + "\""));
    }

    /** The cape geometry is now a chain of thin bones spanning the vanilla cape box. */
    @Test
    public void theCapeGeometryIsASegmentedChainAcrossTheStandardCapeBox() {
        String model = CapeResourcePackBuilder.capeModelJson();
        assertTrue("geometry id", model.contains("\"" + CapeResourcePackBuilder.CAPE_GEOMETRY_ID + "\""));
        assertTrue("64x32 texture", model.contains("\"texture_width\": 64")
                && model.contains("\"texture_height\": 32"));
        // The first segment hangs from the body; the chain continues from there.
        assertTrue("first segment parented to the body",
                model.contains("\"name\": \"cape_1\"") && model.contains("\"parent\": \"body\""));
        assertTrue("a later segment parented to the one above",
                model.contains("\"name\": \"cape_2\"") && model.contains("\"parent\": \"cape_1\""));
        // The single 10x16x1 box is gone: the chain spans that box in 10x1 slices.
        assertFalse("no single rigid 16px box", model.contains("\"size\": [10, 16, 1]"));
        assertTrue("thin segments", model.contains("\"size\": [10, 1.0, 1]"));
        // The 180-degree turn is what puts each segment's face outward.
        assertTrue("segments are turned to face outward", model.contains("\"rotation\": [0.0, 180.0, 0.0]"));
    }

    @Test
    public void manifestDeclaresAResourcesModuleAndMatchingVersion() {
        String manifest = CapeResourcePackBuilder.manifestJson();

        assertTrue("pack uuid", manifest.contains(CapeResourcePackBuilder.PACK_UUID));
        assertTrue("module uuid", manifest.contains(CapeResourcePackBuilder.moduleUuid()));
        // A pack with no resources module imports but applies nothing.
        assertTrue("resources module", manifest.contains("\"type\": \"resources\""));
        // Derived from the constant so a version bump does not need this test edited, while a
        // version that stopped matching the code would still fail here.
        assertTrue("header version",
                manifest.contains("\"version\": [" + CapeResourcePackBuilder.PACK_VERSION
                        .replace(".", ", ") + "]"));
        assertTrue("uuid differs from the pack uuid",
                !CapeResourcePackBuilder.PACK_UUID.equals(CapeResourcePackBuilder.moduleUuid()));
    }

    /**
     * Every generated file must be well-formed JSON. A malformed client-entity or render
     * controller file is skipped by the game with no visible error, so the cape would silently not
     * appear; parsing here catches a broken string edit before it ships.
     */
    @Test
    public void everyGeneratedFileIsValidJson() {
        JsonObject manifest = JsonParser.parseString(
                CapeResourcePackBuilder.manifestJson()).getAsJsonObject();
        assertTrue("manifest has a header", manifest.has("header"));
        assertTrue("manifest has modules", manifest.getAsJsonArray("modules").size() == 1);

        JsonObject entity = JsonParser.parseString(CapeResourcePackBuilder.playerEntityJson())
                .getAsJsonObject();
        JsonObject description = entity.getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description");
        assertEquals("minecraft:player", description.get("identifier").getAsString());
        assertTrue("entity declares the cape controller",
                description.getAsJsonArray("render_controllers").toString()
                        .contains(CapeResourcePackBuilder.CAPE_CONTROLLER_ID));

        JsonObject controller = JsonParser.parseString(
                        CapeResourcePackBuilder.capeRenderControllerJson())
                .getAsJsonObject()
                .getAsJsonObject("render_controllers")
                .getAsJsonObject(CapeResourcePackBuilder.CAPE_CONTROLLER_ID);
        assertEquals("Geometry.chimera_cape", controller.get("geometry").getAsString());

        JsonObject model = JsonParser.parseString(CapeResourcePackBuilder.capeModelJson())
                .getAsJsonObject();
        JsonObject geo = model.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
        assertEquals(CapeResourcePackBuilder.CAPE_GEOMETRY_ID,
                geo.getAsJsonObject("description").get("identifier").getAsString());

        // The cape animation must parse too: a malformed expression file is skipped silently and
        // the cape reverts to the stiff box the animation exists to fix.
        JsonObject animation = JsonParser.parseString(CapeResourcePackBuilder.capeAnimationJson())
                .getAsJsonObject()
                .getAsJsonObject("animations")
                .getAsJsonObject(CapeResourcePackBuilder.CAPE_ANIMATION_ID);
        JsonObject capeBones = animation.getAsJsonObject("bones");
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            assertTrue("animation drives " + CapeGeometry.boneName(i),
                    capeBones.has(CapeGeometry.boneName(i)));
        }

        // The hat and pet controllers and the pet animation must parse too, for the same reason.
        JsonObject hatController = JsonParser.parseString(
                        CapeResourcePackBuilder.hatRenderControllerJson())
                .getAsJsonObject()
                .getAsJsonObject("render_controllers")
                .getAsJsonObject(CapeResourcePackBuilder.HAT_CONTROLLER_ID);
        assertEquals("Geometry.chimera_hat", hatController.get("geometry").getAsString());

        JsonObject petController = JsonParser.parseString(
                        CapeResourcePackBuilder.petRenderControllerJson())
                .getAsJsonObject()
                .getAsJsonObject("render_controllers")
                .getAsJsonObject(CapeResourcePackBuilder.PET_CONTROLLER_ID);
        assertEquals("Geometry.chimera_pet", petController.get("geometry").getAsString());

        JsonParser.parseString(CapeResourcePackBuilder.petAnimationsJson()).getAsJsonObject();
        JsonParser.parseString(CapeResourcePackBuilder.petAnimationControllerJson())
                .getAsJsonObject();
    }

    /**
     * The vanilla {@code cape} animation key must stay pointed at {@code animation.player.cape}.
     *
     * <p>It is played by {@code controller.animation.player.root} for <em>every</em> player, and
     * drives the vanilla cape bone on a player with a vanilla or Persona cape equipped. The pack
     * previously redirected this key to its own animation, which replaced the vanilla swing
     * globally: a player wearing a plain vanilla cape — with no Chimera cosmetic at all — got this
     * pack's animation and their cape froze. This pins that the vanilla key is untouched.
     */
    @Test
    public void theVanillaCapeKeyIsNotRedirected() {
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("vanilla cape animation must be left alone",
                entity.contains("\"cape\": \"animation.player.cape\""));
        assertFalse("the cape key must not be redirected to this pack's animation",
                entity.contains("\"cape\": \"" + CapeResourcePackBuilder.CAPE_ANIMATION_ID + "\""));
    }

    /**
     * The pack's cape animation is played from the entity's own {@code animate} list instead.
     *
     * <p>That is what scopes it: it only writes the {@code cape_1..N} bones, which exist only in
     * this pack's geometry, so the vanilla cape bone and its animation are never touched.
     */
    @Test
    public void theCapeAnimationIsPlayedFromTheEntityAnimateList() {
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("cape animation is in the animate list",
                entity.contains("\"" + CapeResourcePackBuilder.CAPE_ANIMATION_ID + "\""));
        // And the animation file it names actually exists under that id.
        assertTrue("the animation file is written under the same id",
                CapeResourcePackBuilder.capeAnimationJson()
                        .contains("\"" + CapeResourcePackBuilder.CAPE_ANIMATION_ID + "\""));
        assertTrue("the cape animation loops like vanilla's",
                CapeResourcePackBuilder.capeAnimationJson().contains("\"loop\": true"));
    }

    /**
     * The cape animation writes only the pack's own {@code cape_N} bones.
     *
     * <p>If it named the vanilla {@code cape} bone it would fight vanilla's own animation for that
     * bone. Scoping to the pack's bones is the whole reason the vanilla swing survives.
     */
    @Test
    public void theCapeAnimationOnlyTouchesItsOwnBones() {
        String animation = CapeResourcePackBuilder.capeAnimationJson();
        assertFalse("must not write the vanilla cape bone",
                animation.contains("\"cape\": {"));
        for (int i = 1; i <= CapeGeometry.SEGMENT_COUNT; i++) {
            assertTrue("writes cape_" + i, animation.contains("\"" + CapeGeometry.boneName(i) + "\""));
        }
    }

    /**
     * The worn headwear follows the head, not just the body.
     *
     * <p>An accessory is its own geometry in entity space, so it follows position and body facing
     * already; without this animation it would stay bolt-upright while the player looks around.
     * The animation must apply the head's look rotation and be played from the entity.
     */
    @Test
    public void theHeadwearFollowsTheHeadLook() {
        String animation = CapeResourcePackBuilder.hatAnimationJson();
        assertTrue("head pitch", animation.contains("query.target_x_rotation"));
        assertTrue("head yaw", animation.contains("query.target_y_rotation"));
        assertTrue("drives the acc bone", animation.contains("\"acc\""));
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("hat animation is in the animate list",
                entity.contains("\"" + CapeResourcePackBuilder.HAT_ANIMATION_ID + "\""));
    }

    /**
     * The vanilla root controller only plays the {@code cape} key when {@code query.has_cape} is
     * true, which is false for this unconditional cape, so the animation must also be listed
     * directly in the entity's {@code animate} list. Without that the chain sat at its bind pose
     * and the cape rendered stiff on device.
     */
    @Test
    public void theEntityPlaysTheCapeAnimationUnconditionally() {
        JsonObject entity = JsonParser.parseString(CapeResourcePackBuilder.playerEntityJson())
                .getAsJsonObject()
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description")
                .getAsJsonObject("scripts");
        JsonArray animate = entity.getAsJsonArray("animate");
        boolean listed = false;
        for (int i = 0; i < animate.size(); i++) {
            if (CapeResourcePackBuilder.CAPE_ANIMATION_ID.equals(animate.get(i).getAsString())) {
                listed = true;
            }
        }
        assertTrue("cape animation is in the animate list, not only the has_cape-gated key", listed);
    }

    @Test
    public void packUuidIsStableSoReapplyingReplacesInPlace() {
        // A random uuid per build would accumulate one pack per cape change in the instance.
        assertEquals("b7c1a5e2-3d4f-4a6b-9c8d-1e2f3a4b5c6d", CapeResourcePackBuilder.PACK_UUID);
        assertNotNull(CapeResourcePackBuilder.moduleUuid());
    }

    @Test
    public void aBlankPackIsFullyTransparent() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0x00000000, 0x00000000, false));
        for (int pixel : pixels) {
            assertEquals("blank cape must be transparent", 0, pixel >>> 24);
        }
    }

    @Test
    public void theClothRectIsPaintedOpaque() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true));

        // The visible cloth is the 10x16 rect at (12,1); every pixel of it must carry colour,
        // or the cape shows holes where the base texture shows through.
        for (int y = CapeTexturePainter.CLOTH_Y; y < CapeTexturePainter.CLOTH_Y + CapeTexturePainter.CLOTH_HEIGHT; y++) {
            for (int x = CapeTexturePainter.CLOTH_X; x < CapeTexturePainter.CLOTH_X + CapeTexturePainter.CLOTH_WIDTH; x++) {
                int pixel = pixels[y * CapeTexturePainter.TEXTURE_WIDTH + x];
                assertEquals("alpha at " + x + "," + y, 0xFF, pixel >>> 24);
            }
        }
    }

    /**
     * Every face of the cape box the game can sample must be opaque.
     *
     * <p>The cape is a thin box, not a single quad: the front panel (1,1), the side columns at
     * x=0 and x=11, and the top/bottom edge row at y=0 are all sampled when the cape swings and
     * folds. Leaving them transparent made the cape show holes as it moved — the "glitched paper
     * cape" the player sees — so this pins the whole box, not just the visible back panel.
     */
    @Test
    public void everyCapeBoxFaceIsOpaque() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true));

        // Front panel against the player's back.
        for (int y = 1; y < 17; y++) {
            for (int x = 1; x < 11; x++) {
                assertEquals("front panel alpha at " + x + "," + y,
                        0xFF, pixels[y * 64 + x] >>> 24);
            }
        }
        // Side strips and the top edge.
        for (int y = 0; y < 17; y++) {
            assertEquals("left strip at y=" + y, 0xFF, pixels[y * 64 + 0] >>> 24);
            assertEquals("right strip at y=" + y, 0xFF, pixels[y * 64 + 11] >>> 24);
        }
        for (int x = 0; x < 22; x++) {
            assertEquals("top edge at x=" + x, 0xFF, pixels[x] >>> 24);
        }
    }

    /**
     * The outward-facing panel must carry the bright artwork and the panel against the player's
     * back the darker shade. With the cape bone's {@code rotation:[0,180,0]} the outward face is
     * the box "front" at {@link CapeTexturePainter#VISIBLE_X} (x=1), not the box back at x=12.
     * Painting the artwork at x=12 was the "cape texture on the wrong side" bug.
     */
    @Test
    public void theOutwardPanelIsBrighterThanTheInnerPanel() {
        int[] pixels = decodeToPixels(CapeTexturePainter.paint(0xFF6236E8, 0xFFA88CFF, true));
        // Sample away from the inset trim band and the brand mark so both points are panel fill.
        int outward = pixels[8 * 64 + 6];
        int inner = pixels[14 * 64 + 14];
        assertTrue("outward and inner panels must not be identical", outward != inner);
        assertTrue("the outward panel must be the brighter one",
                ((outward >>> 16) & 0xFF) > ((inner >>> 16) & 0xFF));
    }

    /**
     * The artwork must sit on the face the player actually sees.
     *
     * <p>Pinned to the concrete x ranges so a future edit cannot silently move the bright panel
     * back onto the hidden side: the visible face is the box "front" at x=1..11.
     */
    @Test
    public void theArtworkSitsOnTheVisibleFace() {
        assertEquals(1, CapeTexturePainter.VISIBLE_X);
        assertEquals(12, CapeTexturePainter.INNER_X);
        assertEquals(CapeTexturePainter.VISIBLE_X, CapeTexturePainter.CLOTH_X);
    }

    @Test
    public void shadeDarkensWithoutTouchingAlpha() {
        int shaded = CapeTexturePainter.shade(0xFF808080, 0.5f);
        assertEquals(0xFF, shaded >>> 24);
        assertEquals(0x40, (shaded >>> 16) & 0xFF);
        assertEquals(0x40, (shaded >>> 8) & 0xFF);
        assertEquals(0x40, shaded & 0xFF);
    }

    @Test
    public void shadeClampsRatherThanWrapping() {
        // A factor above 1 must not overflow a channel into the next one.
        int bright = CapeTexturePainter.shade(0xFFF0F0F0, 4f);
        assertEquals(0xFF, (bright >>> 16) & 0xFF);
        assertEquals(0xFF, (bright >>> 8) & 0xFF);
        assertEquals(0xFF, bright & 0xFF);
    }

    @Test(expected = IllegalArgumentException.class)
    public void encodeRejectsAMismatchedBuffer() {
        PngWriter.encode(64, 32, new int[10]);
    }

    private static int[] decodeToPixels(byte[] png) {
        int idatStart = findChunk(png, "IDAT");
        int length = readInt(png, idatStart - 8);
        byte[] deflated = new byte[length];
        System.arraycopy(png, idatStart, deflated, 0, length);

        byte[] raw;
        try (InflaterInputStream inflater = new InflaterInputStream(new ByteArrayInputStream(deflated))) {
            raw = inflater.readAllBytes();
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        int[] pixels = new int[CapeTexturePainter.TEXTURE_WIDTH * CapeTexturePainter.TEXTURE_HEIGHT];
        for (int y = 0; y < CapeTexturePainter.TEXTURE_HEIGHT; y++) {
            int rowStart = y * (1 + CapeTexturePainter.TEXTURE_WIDTH * 4);
            for (int x = 0; x < CapeTexturePainter.TEXTURE_WIDTH; x++) {
                int at = rowStart + 1 + x * 4;
                pixels[y * CapeTexturePainter.TEXTURE_WIDTH + x] =
                        ((raw[at + 3] & 0xFF) << 24) | ((raw[at] & 0xFF) << 16)
                                | ((raw[at + 1] & 0xFF) << 8) | (raw[at + 2] & 0xFF);
            }
        }
        return pixels;
    }

    /** Returns the offset just past the chunk type, i.e. where its data begins. */
    private static int findChunk(byte[] png, String type) {
        byte[] needle = type.getBytes(StandardCharsets.US_ASCII);
        for (int i = 8; i < png.length - 4; i++) {
            if (png[i] == needle[0] && png[i + 1] == needle[1]
                    && png[i + 2] == needle[2] && png[i + 3] == needle[3]) {
                return i + 4;
            }
        }
        throw new AssertionError("chunk not found: " + type);
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }

    /**
     * A worn accessory must reach the game through the same first-party route as the cape: a
     * render controller on the player, not a texture override. An accessory-less pack writes no
     * hat model, but the entity still names the controller so the two cases share one entity file.
     */
    @Test
    public void theWornAccessoryIsDrawnByARenderController() {
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("hat geometry shortname",
                entity.contains("\"chimera_hat\": \"" + AccessoryGeometry.GEOMETRY_ID + "\""));
        assertTrue("hat texture shortname",
                entity.contains("\"chimera_hat\": \"" + CapeResourcePackBuilder.HAT_TEXTURE_PATH + "\""));
        assertTrue("hat controller wired",
                entity.contains(CapeResourcePackBuilder.HAT_CONTROLLER_ID));

        JsonObject controller = JsonParser.parseString(
                        CapeResourcePackBuilder.hatRenderControllerJson())
                .getAsJsonObject()
                .getAsJsonObject("render_controllers")
                .getAsJsonObject(CapeResourcePackBuilder.HAT_CONTROLLER_ID);
        assertEquals("Geometry.chimera_hat", controller.get("geometry").getAsString());
        assertTrue("controller samples the hat texture",
                controller.toString().contains("Texture.chimera_hat"));
        assertTrue("controller uses the hat material",
                controller.toString().contains("Material.chimera_hat"));
    }

    /** A pack built with an accessory writes the hat model, controller and texture. */
    @Test
    public void packWritesTheAccessoryFilesWhenOneIsEquipped() throws Exception {
        File dir = Files.createTempDirectory("hat-pack").toFile();
        try {
            CosmeticCatalog.Accessory accessory = CosmeticCatalog.accessory("amethyst_crown");
            CapeResourcePackBuilder.build(dir, null, accessory);

            assertTrue("hat geometry",
                    new File(dir, CapeResourcePackBuilder.HAT_MODEL_PATH).isFile());
            assertTrue("hat render controller",
                    new File(dir, CapeResourcePackBuilder.HAT_RENDER_CONTROLLER_PATH).isFile());
            assertTrue("hat texture",
                    new File(dir, CapeResourcePackBuilder.HAT_TEXTURE_PATH).isFile());
            assertTrue("hat head-tilt animation",
                    new File(dir, CapeResourcePackBuilder.HAT_ANIMATION_PATH).isFile());
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The hat is a separate render-controller geometry, so it cannot be parented to the player's
     * head bone and would sit bolt upright however the player looks. The entity must play an
     * animation that drives its bone from the head queries, and that animation must be listed in
     * the animate list (not only the animations map, which nothing plays by itself).
     */
    @Test
    public void theHatFollowsThePlayersHeadLook() {
        JsonArray rotation = JsonParser.parseString(CapeResourcePackBuilder.hatAnimationJson())
                .getAsJsonObject()
                .getAsJsonObject("animations")
                .getAsJsonObject(CapeResourcePackBuilder.HAT_ANIMATION_ID)
                .getAsJsonObject("bones")
                .getAsJsonObject("acc")
                .getAsJsonArray("rotation");
        assertEquals("hat tilt has one rotation triple", 3, rotation.size());
        assertTrue("hat animation turns with the head yaw",
                rotation.toString().contains("query.target_y_rotation"));
        assertTrue("hat animation pitches with the head pitch",
                rotation.toString().contains("query.target_x_rotation"));

        JsonObject scripts = JsonParser.parseString(CapeResourcePackBuilder.playerEntityJson())
                .getAsJsonObject()
                .getAsJsonObject("minecraft:client_entity")
                .getAsJsonObject("description")
                .getAsJsonObject("scripts");
        JsonArray animate = scripts.getAsJsonArray("animate");
        boolean listed = false;
        for (int i = 0; i < animate.size(); i++) {
            if (CapeResourcePackBuilder.HAT_ANIMATION_ID.equals(animate.get(i).getAsString())) {
                listed = true;
            }
        }
        assertTrue("hat tilt is in the animate list so it actually plays", listed);
    }

    /**
     * With no accessory the pack still writes a hat geometry, but an empty one: the player entity
     * always names the geometry, and a reference that does not resolve can fail the whole client
     * entity (taking the cape with it). An empty bone draws nothing.
     */
    @Test
    public void packWritesAResolvingButEmptyAccessoryGeometryWithoutOne() throws Exception {
        File dir = Files.createTempDirectory("no-hat-pack").toFile();
        try {
            CapeResourcePackBuilder.build(dir, CosmeticCatalog.cape("chimera"), null);
            File hat = new File(dir, CapeResourcePackBuilder.HAT_MODEL_PATH);
            assertTrue("hat geometry is always written so the reference resolves", hat.isFile());

            JsonObject geometry = JsonParser.parseString(
                            new String(Files.readAllBytes(hat.toPath()), StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("minecraft:geometry")
                    .get(0)
                    .getAsJsonObject();
            int cubes = geometry.getAsJsonArray("bones").get(0).getAsJsonObject()
                    .getAsJsonArray("cubes").size();
            assertEquals("empty geometry draws nothing", 0, cubes);
        } finally {
            deleteRecursively(dir);
        }
    }

    /**
     * The pet reaches the game the same first-party way as the cape and hat: a render controller
     * on the player, with the pet animation always present so the entity's reference resolves.
     */
    @Test
    public void thePetIsDrawnByARenderController() {
        String entity = CapeResourcePackBuilder.playerEntityJson();
        assertTrue("pet geometry shortname",
                entity.contains("\"chimera_pet\": \"" + PetGeometry.GEOMETRY_ID + "\""));
        assertTrue("pet texture shortname",
                entity.contains("\"chimera_pet\": \"" + CapeResourcePackBuilder.PET_TEXTURE_PATH + "\""));
        assertTrue("pet controller wired",
                entity.contains(CapeResourcePackBuilder.PET_CONTROLLER_ID));
        assertTrue("pet animation always referenced",
                entity.contains("\"" + CapeResourcePackBuilder.PET_ANIMATION_ID + "\""));

        JsonObject controller = JsonParser.parseString(
                        CapeResourcePackBuilder.petRenderControllerJson())
                .getAsJsonObject()
                .getAsJsonObject("render_controllers")
                .getAsJsonObject(CapeResourcePackBuilder.PET_CONTROLLER_ID);
        assertEquals("Geometry.chimera_pet", controller.get("geometry").getAsString());
        assertTrue("controller samples the pet texture",
                controller.toString().contains("Texture.chimera_pet"));
    }

    /** A pack built with a pet writes the pet model, controller, texture and animation. */
    @Test
    public void packWritesThePetFilesWhenOneIsEquipped() throws Exception {
        File dir = Files.createTempDirectory("pet-pack").toFile();
        try {
            CapeResourcePackBuilder.build(dir, null, null,
                    new CosmeticCatalog.Pet("test", "Test",
                            CosmeticCatalog.PetSpecies.CAT, 0xFF6236E8, 0xFFA88CFF, 1f));

            assertTrue("pet geometry",
                    new File(dir, CapeResourcePackBuilder.PET_MODEL_PATH).isFile());
            assertTrue("pet render controller",
                    new File(dir, CapeResourcePackBuilder.PET_RENDER_CONTROLLER_PATH).isFile());
            assertTrue("pet texture",
                    new File(dir, CapeResourcePackBuilder.PET_TEXTURE_PATH).isFile());
            assertTrue("pet animation",
                    new File(dir, CapeResourcePackBuilder.PET_ANIMATION_PATH).isFile());
        } finally {
            deleteRecursively(dir);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
