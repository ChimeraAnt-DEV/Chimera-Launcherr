package org.chimeramc.client.core.cosmetics;

/**
 * Per-species pet animations and the animation controller that selects between them.
 *
 * <p><b>Why an animation controller.</b> A pet entity's {@code animate} list plays <em>every</em>
 * animation it names at once, so listing walk, run, crouch, fly and swim together would blend them
 * into mush. The supported mechanism is an animation controller: a small state machine that plays
 * exactly one animation per state and switches on the player's own queries
 * ({@code query.is_sneaking}, {@code query.is_swimming}, {@code query.is_on_ground},
 * {@code query.modified_move_speed}). The entity's {@code animate} list then names the controller
 * once.
 *
 * <p><b>Distinct sets per species, not one cycle reused.</b> The motion is generated from a
 * {@link Family} — a quadruped swings its legs and wags its tail, a bird bobs and pecks and flaps,
 * a serpent undulates along its own body, a bug scuttles fast on six legs. A cat and a parrot do
 * not share a walk cycle.
 *
 * <p><b>Contextual bug behaviour.</b> The crawling bugs (spider, ant, beetle — {@link
 * CosmeticCatalog.PetSpecies#isCrawler()}) ride on the player's head and react to what the player
 * does, which is a genuinely different thing from trotting at their feet:
 * <ul>
 *   <li><b>Running</b> — the bug crawls in a small loop on the player's head.</li>
 *   <li><b>Crouching</b> — it stops crawling, holds still and plays a looking-around idle (the head
 *       and its antennae sweep side to side).</li>
 *   <li><b>Flying</b> — a crawler with no flight of its own clings flat and leans back against the
 *       wind (blown-back pose); a crawler that can fly (beetle) flies alongside instead.</li>
 *   <li><b>Swimming</b> — a related but distinct cling: it stays low, bobs with the surface and
 *       paddles, rather than the airborne lean.</li>
 * </ul>
 * A bug that is genuinely a flyer (bee, butterfly, dragonfly) does not cling at all — it flies
 * alongside the player in the flying state.
 *
 * <p>Pure string building, so a unit test can parse the animations and assert every species emits
 * the full gait set, the controller references only animations that exist, and the crawler states
 * are wired to the contextual animations.
 */
public final class PetAnimations {

    /** The motion vocabulary a species animates with. */
    public enum Family {
        /** Four-legged ground animal: leg swing, tail wag, head bob. */
        QUADRUPED,
        /** Bird: head bob/peck, wing flap, tail flick. */
        BIRD,
        /** Six-legged bug: fast scuttle, wing buzz, antenna sway. */
        CRAWLER,
        /** Legless: the whole body undulates. */
        SERPENT
    }

    /** Controller id referenced by the player entity's {@code animate} list. */
    public static final String CONTROLLER_ID = "controller.animation.chimera_pet";

    // The gait animations, shared by every family (their contents differ per family).
    public static final String IDLE = "animation.chimera_pet_idle";
    public static final String WALK = "animation.chimera_pet_walk";
    public static final String RUN = "animation.chimera_pet_run";
    public static final String CROUCH = "animation.chimera_pet_crouch";
    public static final String FLY = "animation.chimera_pet_fly";
    public static final String SWIM = "animation.chimera_pet_swim";

    // Contextual crawler animations.
    public static final String CRAWL_HEAD = "animation.chimera_pet_crawl_head";
    public static final String LOOK_AROUND = "animation.chimera_pet_look_around";
    public static final String BLOWN_BACK = "animation.chimera_pet_blown_back";
    public static final String WATER_CLING = "animation.chimera_pet_water_cling";

    private PetAnimations() {
    }

    /** The motion family a species belongs to. */
    public static Family familyOf(CosmeticCatalog.PetSpecies species) {
        CosmeticCatalog.PetSpecies s = species == null ? CosmeticCatalog.PetSpecies.CAT : species;
        switch (s) {
            case PARROT:
                return Family.BIRD;
            case SNAKE:
                return Family.SERPENT;
            case BEE:
            case BUTTERFLY:
            case DRAGONFLY:
            case BEETLE:
            case SPIDER:
            case ANT:
                return Family.CRAWLER;
            default:
                return Family.QUADRUPED;
        }
    }

    /** True when this pet rides on the player's head and reacts to their state. */
    public static boolean clingsToHead(CosmeticCatalog.PetSpecies species) {
        return species != null && species.isCrawler();
    }

    /**
     * The animations file for a pet.
     *
     * <p>The crawl-on-head offsets are baked from the pet's real body plan, because a Molang
     * expression cannot read the geometry: the pet is authored on the ground in front of the
     * player and the animation has to translate it onto the head.
     */
    public static String animationsJson(CosmeticCatalog.Pet pet) {
        CosmeticCatalog.PetSpecies s = pet == null ? CosmeticCatalog.PetSpecies.CAT : pet.species;
        float scale = pet == null ? 1f : pet.scale;
        Family family = familyOf(s);

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.8.0\",\n  \"animations\": {\n");
        boolean[] first = {true};
        append(sb, first, IDLE, idle(family));
        append(sb, first, WALK, gait(family, false));
        append(sb, first, RUN, gait(family, true));
        append(sb, first, CROUCH, crouch(family));
        append(sb, first, FLY, fly(family));
        append(sb, first, SWIM, swim(family));
        if (clingsToHead(s)) {
            float[] o = headOffset(pet, scale);
            append(sb, first, CRAWL_HEAD, crawlOnHead(o));
            append(sb, first, LOOK_AROUND, lookAround(o));
            if (s.clingsInAir()) {
                append(sb, first, BLOWN_BACK, blownBack(o));
            }
            append(sb, first, WATER_CLING, waterCling(o));
        }
        sb.append("  }\n}\n");
        return sb.toString();
    }

    /**
     * The animation controller that plays one gait at a time.
     *
     * <p>Transitions are ordered most-specific first: crouch, swim and airborne win over the
     * ground-speed tests, so a sneaking player does not also get the run cycle. The crawler states
     * are wired to the contextual animations; a flying bug uses the ordinary fly cycle.
     */
    public static String controllerJson(CosmeticCatalog.PetSpecies species) {
        CosmeticCatalog.PetSpecies s = species == null ? CosmeticCatalog.PetSpecies.CAT : species;
        boolean crawler = clingsToHead(s);
        // A crawler with no flight of its own clings (blown back) in the air; a crawler that can
        // fly (beetle) flies alongside like any other flyer. A non-crawler always flies.
        String flyAnim = s.clingsInAir() ? BLOWN_BACK : FLY;
        String crouchAnim = crawler ? LOOK_AROUND : CROUCH;
        String swimAnim = crawler ? WATER_CLING : SWIM;
        String runAnim = crawler ? CRAWL_HEAD : RUN;
        String walkAnim = crawler ? IDLE : WALK;

        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"format_version\": \"1.8.0\",\n");
        sb.append("  \"animation_controllers\": {\n");
        sb.append("    \"").append(CONTROLLER_ID).append("\": {\n");
        sb.append("      \"initial_state\": \"idle\",\n");
        sb.append("      \"states\": {\n");
        state(sb, "idle", IDLE, true);
        state(sb, "walk", walkAnim, false);
        state(sb, "run", runAnim, false);
        state(sb, "crouch", crouchAnim, false);
        state(sb, "fly", flyAnim, false);
        state(sb, "swim", swimAnim, false);
        sb.append("\n      }\n    }\n  }\n}\n");
        return sb.toString();
    }

    private static void state(StringBuilder sb, String name, String animation, boolean first) {
        if (!first) sb.append(",\n");
        sb.append("        \"").append(name).append("\": {\n");
        sb.append("          \"animations\": [\n");
        sb.append("            \"").append(animation).append("\"\n");
        sb.append("          ],\n");
        sb.append("          \"transitions\": [\n");
        sb.append("            { \"crouch\": \"query.is_sneaking\" },\n");
        sb.append("            { \"swim\": \"query.is_swimming\" },\n");
        sb.append("            { \"fly\": \"!query.is_on_ground && !query.is_swimming && !query.is_sneaking\" },\n");
        sb.append("            { \"run\": \"query.is_on_ground && query.modified_move_speed > 0.15\" },\n");
        sb.append("            { \"walk\": \"query.is_on_ground && query.modified_move_speed > 0.02\" },\n");
        sb.append("            { \"idle\": \"query.is_on_ground && query.modified_move_speed <= 0.02\" }\n");
        sb.append("          ]\n");
        sb.append("        }");
    }

    // ---- Animation bodies ----------------------------------------------------------------

    private static String idle(Family family) {
        switch (family) {
            case BIRD:
                return anim(2.6f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 180) * 0.2\", 0]", null),
                        bone("head", null, "[0, \"math.sin(query.anim_time * 240) * 0.35\", 0]", null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 120) * 6\", 0]", null, null));
            case SERPENT:
                return anim(3.0f,
                        bone("head", "[0, \"math.sin(query.anim_time * 120) * 8\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 120 + 90) * 12\", 0]", null, null));
            case CRAWLER:
                return anim(1.2f,
                        bone("head", "[0, \"math.sin(query.anim_time * 90) * 6\", 0]",
                                "[0, \"math.sin(query.anim_time * 180) * 0.2\", 0]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 200) * 8\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 200 + 180) * 8\"]", null, null));
            default:
                return anim(2.4f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 160) * 0.25\", 0]", null),
                        bone("head", "[0, \"math.sin(query.anim_time * 80) * 7\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 160) * 14\", 0]", null, null));
        }
    }

    /**
     * The trailing offset on the pet root, in blocks, that makes the companion <em>follow</em>
     * rather than sit glued to the player.
     *
     * <p>The pet is drawn on the player's own client entity (never a summoned mob — that is
     * server-side and would flag the account), so it is always anchored to the player. Without a
     * trailing offset it moves in perfect lockstep and reads as attached. This pushes it behind
     * (−z) and slightly to one side as the player's speed rises, so it lags on a run and catches
     * up at a walk — the visual behaviour of a pet following you. It is a position on the pet
     * bone, not a second entity, so it stays within the supported data-driven mechanism.
     */
    private static String followZ() {
        return "\"-math.clamp(query.modified_move_speed, 0.0, 1.0) * 1.6\"";
    }

    private static String gait(Family family, boolean run) {
        float speed = run ? 1.9f : 1.0f;
        float amp = run ? 1.35f : 1.0f;
        float legDeg = (run ? 34f : 22f) * amp;
        float bob = run ? 0.9f : 0.5f;
        String sp = f(speed * 360f);
        switch (family) {
            case BIRD:
                return anim(run ? 0.8f : 1.4f,
                        bone("pet", null, "[0, \"math.abs(math.sin(query.anim_time * " + sp + ")) * " + f(bob * 1.6f) + "\", " + followZ() + "]", null),
                        bone("head", null, "[0, \"math.sin(query.anim_time * " + sp + " + 60) * " + f(amp * 0.6f) + "\", 0]", null),
                        bone("wing_l", "[0, 0, \"math.sin(query.anim_time * " + sp + ") * " + f(legDeg * 0.4f) + "\"]", null, null),
                        bone("wing_r", "[0, 0, \"math.sin(query.anim_time * " + sp + " + 180) * " + f(legDeg * 0.4f) + "\"]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * " + sp + ") * 10\", 0]", null, null));
            case SERPENT:
                return anim(run ? 0.7f : 1.2f,
                        bone("head", "[0, \"math.sin(query.anim_time * " + sp + ") * " + f(legDeg * 0.5f) + "\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * " + sp + " + 90) * " + f(legDeg) + "\", 0]", null, null));
            case CRAWLER:
                return anim(run ? 0.55f : 0.9f,
                        bone("pet", null, "[0, \"math.abs(math.sin(query.anim_time * " + sp + ")) * " + f(bob * 0.5f) + "\", " + followZ() + "]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * " + sp + ") * " + f(legDeg) + "\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * " + sp + " + 180) * " + f(legDeg) + "\"]", null, null),
                        bone("leg_c", "[0, 0, \"math.sin(query.anim_time * " + sp + " + 90) * " + f(legDeg) + "\"]", null, null));
            default:
                return anim(run ? 0.6f : 1.0f,
                        bone("pet", null, "[0, \"math.abs(math.sin(query.anim_time * " + sp + ")) * " + f(bob) + "\", " + followZ() + "]", null),
                        bone("head", null, "[0, \"math.sin(query.anim_time * " + sp + " + 45) * " + f(amp * 0.5f) + "\", 0]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * " + sp + ") * " + f(legDeg) + "\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * " + sp + " + 180) * " + f(legDeg) + "\"]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * " + sp + ") * " + f(legDeg * 0.6f) + "\", 0]", null, null));
        }
    }

    private static String crouch(Family family) {
        switch (family) {
            case BIRD:
                return anim(2.4f,
                        bone("pet", null, "[0, -0.8, 0]", null),
                        bone("head", "[0, \"math.sin(query.anim_time * 120) * 10\", 0]", null, null));
            case SERPENT:
                return anim(2.6f,
                        bone("tail", "[0, \"math.sin(query.anim_time * 90) * 18\", 0]", null, null),
                        bone("head", "[0, \"math.sin(query.anim_time * 60) * 10\", 0]", null, null));
            case CRAWLER:
                return anim(2.2f,
                        bone("pet", null, "[0, -0.4, 0]", null),
                        bone("head", "[0, \"math.sin(query.anim_time * 90) * 12\", 0]", null, null));
            default:
                return anim(2.4f,
                        bone("pet", null, "[0, -1.4, 0]", null),
                        bone("head", "[10, \"math.sin(query.anim_time * 70) * 10\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 80) * 8\", 0]", null, null));
        }
    }

    private static String fly(Family family) {
        switch (family) {
            case BIRD:
                return anim(0.5f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 360) * 0.8\", 0]", null),
                        bone("wing_l", "[0, 0, \"math.sin(query.anim_time * 720) * 55\"]", null, null),
                        bone("wing_r", "[0, 0, \"math.sin(query.anim_time * 720 + 180) * 55\"]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 360) * 8\", 0]", null, null));
            case CRAWLER:
                return anim(0.35f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 720) * 0.6\", 0]", null),
                        bone("wing_l", "[0, 0, \"math.sin(query.anim_time * 1440) * 40\"]", null, null),
                        bone("wing_r", "[0, 0, \"math.sin(query.anim_time * 1440 + 180) * 40\"]", null, null));
            case SERPENT:
                return anim(0.7f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 360) * 0.6\", 0]", null),
                        bone("head", "[0, \"math.sin(query.anim_time * 360) * 16\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 360 + 90) * 22\", 0]", null, null));
            default:
                return anim(1.1f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 300) * 0.7\", 0]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 400) * 30\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 400 + 180) * 30\"]", null, null),
                        bone("head", "[0, \"math.sin(query.anim_time * 200) * 8\", 0]", null, null));
        }
    }

    private static String swim(Family family) {
        switch (family) {
            case BIRD:
                return anim(1.2f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 200) * 0.3\", 0]", null),
                        bone("wing_l", "[0, 0, \"math.sin(query.anim_time * 360) * 30\"]", null, null),
                        bone("wing_r", "[0, 0, \"math.sin(query.anim_time * 360 + 180) * 30\"]", null, null),
                        bone("head", "[0, \"math.sin(query.anim_time * 120) * 6\", 0]", null, null));
            case SERPENT:
                return anim(1.1f,
                        bone("head", "[0, \"math.sin(query.anim_time * 240) * 18\", 0]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 240 + 120) * 28\", 0]", null, null));
            case CRAWLER:
                return anim(1.4f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 200) * 0.3\", 0]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 300) * 22\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 300 + 180) * 22\"]", null, null));
            default:
                return anim(1.3f,
                        bone("pet", null, "[0, \"math.sin(query.anim_time * 200) * 0.3\", 0]", null),
                        bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 300) * 26\"]", null, null),
                        bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 300 + 180) * 26\"]", null, null),
                        bone("tail", "[0, \"math.sin(query.anim_time * 240) * 18\", 0]", null, null));
        }
    }

    // ---- Contextual crawler animations ---------------------------------------------------

    /** Running: a small looping crawl in place on the player's head. */
    private static String crawlOnHead(float[] o) {
        return anim(0.6f,
                bone("pet", null,
                        posExpr(f(o[0] + 0.5f) + " + math.sin(query.anim_time * 360) * 0.5",
                                f(o[1]) + " + math.abs(math.sin(query.anim_time * 720)) * 0.15",
                                f(o[2]) + " + math.cos(query.anim_time * 360) * 0.5"), null),
                bone("head", "[0, \"math.sin(query.anim_time * 360) * 10\", 0]", null, null),
                bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 1080) * 26\"]", null, null),
                bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 1080 + 180) * 26\"]", null, null),
                bone("leg_c", "[0, 0, \"math.sin(query.anim_time * 1080 + 90) * 26\"]", null, null));
    }

    /** Crouching: stop, hold still, and look around (head + antennae sweep). */
    private static String lookAround(float[] o) {
        return anim(4.0f,
                bone("pet", null, posExpr(f(o[0]), f(o[1]), f(o[2])), null),
                bone("head", "[0, \"math.sin(query.anim_time * 120) * 35\", 0]", null, null),
                bone("leg_a", "[0, 0, 0]", null, null),
                bone("leg_b", "[0, 0, 0]", null, null),
                bone("leg_c", "[0, 0, 0]", null, null));
    }

    /** Flying: cling flat and lean back against the wind, wings buzzing. */
    private static String blownBack(float[] o) {
        return anim(0.4f,
                bone("pet", "[-55, 0, 0]",
                        posExpr(f(o[0]), f(o[1]) + " + math.sin(query.anim_time * 720) * 0.08",
                                f(o[2]) + " - 0.3"), null),
                bone("head", "[-20, 0, 0]", null, null),
                bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 1440) * 18\"]", null, null),
                bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 1440 + 180) * 18\"]", null, null),
                bone("leg_c", "[0, 0, \"math.sin(query.anim_time * 1440 + 90) * 18\"]", null, null));
    }

    /** Swimming: a low, surface-bobbing cling that paddles — distinct from the airborne lean. */
    private static String waterCling(float[] o) {
        return anim(1.6f,
                bone("pet", "[-12, 0, 0]",
                        posExpr(f(o[0]), f(o[1]) + " + math.sin(query.anim_time * 200) * 0.25",
                                f(o[2])), null),
                bone("head", "[8, \"math.sin(query.anim_time * 160) * 12\", 0]", null, null),
                bone("leg_a", "[0, 0, \"math.sin(query.anim_time * 420) * 30\"]", null, null),
                bone("leg_b", "[0, 0, \"math.sin(query.anim_time * 420 + 180) * 30\"]", null, null),
                bone("leg_c", "[0, 0, \"math.sin(query.anim_time * 420 + 90) * 30\"]", null, null));
    }

    // ---- JSON helpers --------------------------------------------------------------------

    /**
     * The translation that moves the ground-authored pet onto the player's head, baked from the
     * real body plan. The head's crown is at y=32; the pet is scaled to half size and its centre
     * is placed just above the crown.
     */
    static float[] headOffset(CosmeticCatalog.Pet pet, float scale) {
        PetModel.Body b = PetModel.body(pet == null ? null : pet.species, scale);
        float k = 0.5f;
        float cx = b.centerX() * k;
        float cy = (b.y + b.h * 0.5f) * k;
        float cz = b.centerZ() * k;
        return new float[]{-cx, 31f - cy, -cz};
    }

    /** Builds one named animation from a list of bone blocks. */
    private static String anim(float length, String... bones) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"loop\": true,\n");
        sb.append("      \"animation_length\": ").append(f(length)).append(",\n");
        sb.append("      \"bones\": {\n");
        for (int i = 0; i < bones.length; i++) {
            sb.append(bones[i]);
            sb.append(i < bones.length - 1 ? ",\n" : "\n");
        }
        sb.append("      }\n    }");
        return sb.toString();
    }

    private static void append(StringBuilder sb, boolean[] first, String name, String body) {
        if (!first[0]) sb.append(",\n");
        first[0] = false;
        sb.append("    \"").append(name).append("\": ").append(body);
    }

    /**
     * A bone block. {@code rotation} and {@code position} are each a complete Molang array
     * (e.g. {@code "[0, 0, math.sin(...) * 8]"}) or null to omit that component.
     */
    private static String bone(String name, String rotation, String position, String unused) {
        StringBuilder sb = new StringBuilder();
        sb.append("        \"").append(name).append("\": {");
        java.util.List<String> fields = new java.util.ArrayList<>();
        if (rotation != null) fields.add("\"rotation\": " + rotation);
        if (position != null) fields.add("\"position\": " + position);
        sb.append(String.join(", ", fields));
        sb.append("}");
        return sb.toString();
    }

    /**
     * Builds a position array body from three components, quoting any component that is a Molang
     * expression (contains a letter). Molang strings must be quoted in the JSON, or the file is
     * malformed; a bare numeric literal must not be quoted. This is the one place that rule lives,
     * so the contextual crawler animations cannot drift from the rest.
     */
    private static String posExpr(String x, String y, String z) {
        return "[" + expr(x) + ", " + expr(y) + ", " + expr(z) + "]";
    }

    /**
     * Quotes a Molang component. A bare number that is itself an expression (e.g. {@code -6.75 - 0.3})
     * is not valid JSON, so every component built by {@link #posExpr} is quoted; Molang reads a
     * quoted numeric string the same as a literal.
     */
    private static String expr(String value) {
        return "\"" + value + "\"";
    }

    private static String f(float value) {
        return PetGeometry.f(value);
    }
}
