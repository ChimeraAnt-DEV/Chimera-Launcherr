package org.chimeramc.client.launcher.core.content;

import org.chimeramc.client.core.cosmetics.CapeResourcePackBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Works out, on-device, which link of the cape pipeline is broken.
 *
 * <p>Capes are delivered by a resource pack that overrides the player client entity to draw a cape
 * model on the player's back; the code's own design note says a failure is silent, so this class
 * turns that silence into a set of named checks a person can read off a screen.
 *
 * <p>It is deliberately {@code File}-based and Android-light: the caller resolves the candidate
 * game-data roots and the staging directory, so the logic is unit-testable against a temporary
 * directory tree and never guesses a path itself.
 *
 * <p>Only some of the links can be checked from the launcher. Whether RenderDragon honours the
 * entity override, and whether the cape is visible in third person, cannot be read without the
 * game, so those are reported as manual checks rather than invented.
 */
public final class CosmeticsDiagnostics {

    /** The outcome of one link in the chain. */
    public enum Status {
        /** Working, verified from the filesystem. */
        OK,
        /** Not working, and the cause is knowable from what we read. */
        FAIL,
        /** Cannot be decided without the game; the user has to look. */
        MANUAL,
    }

    /** One named link in the cape chain. */
    public static final class Check {
        public final String label;
        public final Status status;
        public final String detail;

        public Check(String label, Status status, String detail) {
            this.label = label;
            this.status = status;
            this.detail = detail;
        }
    }

    private CosmeticsDiagnostics() {
    }

    /**
     * Runs every filesystem check the launcher can make.
     *
     * @param gameDataDirs every candidate root for the selected instance
     * @param stagingRoot  the app-private directory the pack is built in
     * @param gameVersion  the installed version string, for the min_engine_version comparison
     * @return one {@link Check} per link, in pipeline order
     */
    public static List<Check> run(List<File> gameDataDirs, File stagingRoot, String gameVersion) {
        List<Check> checks = new ArrayList<>();
        String uuid = CapeResourcePackBuilder.PACK_UUID;

        List<File> roots = new ArrayList<>();
        if (gameDataDirs != null) {
            for (File dir : gameDataDirs) if (dir != null) roots.add(dir);
        }

        // 1. Is the pack written into the roots the game reads?
        List<File> writtenRoots = new ArrayList<>();
        File stagedPack = stagingRoot == null ? null : new File(stagingRoot, "cape_pack");
        for (File root : roots) {
            File packDir = new File(new File(root, "resource_packs"), uuid);
            if (new File(packDir, "manifest.json").isFile()) writtenRoots.add(root);
        }
        if (roots.isEmpty()) {
            checks.add(new Check("Pack written to instance storage", Status.MANUAL,
                    "No instance storage resolved; select an installed version first."));
        } else if (writtenRoots.isEmpty()) {
            checks.add(new Check("Pack written to instance storage", Status.FAIL,
                    "No resource_packs/" + uuid + " under any of the " + roots.size()
                            + " candidate root(s). Install a cape to write it."));
        } else {
            checks.add(new Check("Pack written to instance storage", Status.OK,
                    "Found under " + writtenRoots.size() + " of " + roots.size()
                            + " candidate root(s)."));
        }
        if (stagedPack != null && !new File(stagedPack, "manifest.json").isFile()) {
            checks.add(new Check("Staged pack on disk", Status.FAIL,
                    "The launcher's own staging copy is missing; a build failed before applying."));
        }

        // 2. Is the pack listed as active in the file the game reads?
        File activeRoot = null;
        for (File root : roots) {
            if (isActiveIn(root, uuid)) {
                activeRoot = root;
                break;
            }
        }
        if (activeRoot == null) {
            checks.add(new Check("Pack active in global_resource_packs.json", Status.FAIL,
                    "The pack's uuid is not listed, so the game will not load it. Apply the cape."));
        } else {
            checks.add(new Check("Pack active in global_resource_packs.json", Status.OK,
                    "Listed in the root the game reads."));
        }

        // 3. Is the pack's player-entity override present, so the cape actually renders?
        File manifestRoot = activeRoot != null ? activeRoot
                : (writtenRoots.isEmpty() ? null : writtenRoots.get(0));
        File entityRoot = manifestRoot;
        if (entityRoot == null) {
            checks.add(new Check("Player entity override present", Status.MANUAL,
                    "No written pack to read."));
        } else {
            File packDir = new File(new File(entityRoot, "resource_packs"), uuid);
            File entity = new File(packDir, CapeResourcePackBuilder.PLAYER_ENTITY_PATH);
            File controller = new File(packDir, CapeResourcePackBuilder.CAPE_RENDER_CONTROLLER_PATH);
            File animation = new File(packDir, CapeResourcePackBuilder.CAPE_ANIMATION_PATH);
            if (!entity.isFile()) {
                checks.add(new Check("Player entity override present", Status.FAIL,
                        "The pack is active but has no entity/player.entity.json, so no cape model "
                                + "is added. Re-apply the cape to rebuild the pack."));
            } else if (!controller.isFile()) {
                checks.add(new Check("Player entity override present", Status.FAIL,
                        "The entity override exists but its render controller is missing; the cape "
                                + "would not be drawn."));
            } else {
                checks.add(new Check("Player entity override present", Status.OK,
                        "The player entity and its cape render controller are in the pack."));
            }
            // The cape renders without the animation, but as a stiff chain that does not fold;
            // this is the difference between cloth and a plank, so a missing animation is
            // reported rather than ignored.
            if (!animation.isFile()) {
                checks.add(new Check("Cape animation present", Status.FAIL,
                        "The pack has no cape animation, so the cape renders stiff. Re-apply the "
                                + "cape to rebuild the pack."));
            } else if (!entityPlaysAnimation(entity, CapeResourcePackBuilder.CAPE_ANIMATION_ID)) {
                // The file existing is not enough: the vanilla root controller plays the cape key
                // only when query.has_cape is true, which is false for this unconditional cape, so
                // the animation must also be listed in the entity's animate list to run at all.
                checks.add(new Check("Cape animation present", Status.FAIL,
                        "The cape animation is not in the player entity's animate list, so it never "
                                + "plays and the cape renders stiff. Re-apply the cape."));
            } else {
                checks.add(new Check("Cape animation present", Status.OK,
                        "The cape animation is in the pack and played, so the cloth folds as the "
                                + "player moves."));
            }
        }

        // 4. Does the manifest's minimum engine version accept the installed game?
        if (manifestRoot == null) {
            checks.add(new Check("Manifest accepts this game version", Status.MANUAL,
                    "No written pack to read."));
        } else {
            File manifest = new File(new File(new File(manifestRoot, "resource_packs"), uuid),
                    "manifest.json");
            int[] min = readMinEngineVersion(manifest);
            int[] current = parseVersion(gameVersion);
            if (min == null || current == null) {
                checks.add(new Check("Manifest accepts this game version", Status.MANUAL,
                        "Could not compare min_engine_version with \"" + gameVersion + "\"."));
            } else if (compare(min, current) > 0) {
                checks.add(new Check("Manifest accepts this game version", Status.FAIL,
                        "Manifest wants " + join(min) + " but the game is " + join(current)
                                + "; the pack is skipped silently."));
            } else {
                checks.add(new Check("Manifest accepts this game version", Status.OK,
                        "Manifest " + join(min) + " <= game " + join(current) + "."));
            }
        }

        // The link only the game can speak to.
        checks.add(new Check("Renderer draws the cape model", Status.MANUAL,
                "Use \"Test cape\" below: a magenta cape on your character's back in third person "
                        + "(or the dressing-room paperdoll) means the entity override is working. "
                        + "If it never appears, this RenderDragon build ignores entity overrides and "
                        + "no resource-pack route can work."));
        return checks;
    }

    /** True when {@code global_resource_packs.json} in {@code gameDataDir} lists the uuid. */
    public static boolean isActiveIn(File gameDataDir, String uuid) {
        if (gameDataDir == null || uuid == null) return false;
        File globalFile = new File(new File(gameDataDir, "minecraftpe"), "global_resource_packs.json");
        if (!globalFile.isFile()) return false;
        String wanted = uuid.toLowerCase(Locale.ROOT);
        try (BufferedReader reader = new BufferedReader(new FileReader(globalFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase(Locale.ROOT).contains(wanted)) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * Reads {@code min_engine_version} from a manifest.
     *
     * A tiny scan rather than a JSON parser: the file is one the launcher itself wrote, and
     * pulling in a parser for three integers is not warranted.
     */
    public static int[] readMinEngineVersion(File manifest) {
        if (manifest == null || !manifest.isFile()) return null;
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(manifest))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        } catch (Exception e) {
            return null;
        }
        String text = builder.toString();
        int key = text.indexOf("min_engine_version");
        if (key < 0) return null;
        int open = text.indexOf('[', key);
        int close = open < 0 ? -1 : text.indexOf(']', open);
        if (open < 0 || close < 0) return null;
        String inner = text.substring(open + 1, close);
        String[] parts = inner.split(",");
        List<Integer> nums = new ArrayList<>();
        for (String part : parts) {
            try {
                nums.add(Integer.parseInt(part.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (nums.isEmpty()) return null;
        int[] out = new int[nums.size()];
        for (int i = 0; i < nums.size(); i++) out[i] = nums.get(i);
        return out;
    }

    /** Parses a dotted version such as {@code 1.26.60.28} or {@code 26.51} into components. */
    public static int[] parseVersion(String version) {
        if (version == null) return null;
        String cleaned = version.trim();
        if (cleaned.isEmpty()) return null;
        String[] parts = cleaned.split("\\.");
        List<Integer> nums = new ArrayList<>();
        for (String part : parts) {
            String digits = part.replaceAll("[^0-9]", "");
            if (digits.isEmpty()) continue;
            try {
                nums.add(Integer.parseInt(digits));
            } catch (NumberFormatException ignored) {
            }
        }
        if (nums.isEmpty()) return null;
        int[] out = new int[nums.size()];
        for (int i = 0; i < nums.size(); i++) out[i] = nums.get(i);
        return out;
    }

    /** Component-wise compare, missing components counting as zero. */
    public static int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int av = i < a.length ? a[i] : 0;
            int bv = i < b.length ? b[i] : 0;
            if (av != bv) return Integer.compare(av, bv);
        }
        return 0;
    }

    private static String join(int[] parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append('.');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    /**
     * Whether the player entity's {@code animate} list names the given animation. A file existing
     * in the pack is not the same as the game playing it: an animation that is only mapped in the
     * {@code animations} table is played by whoever references it, and for the cape that is the
     * vanilla root controller, which gates the cape key on {@code query.has_cape}. Listing it in
     * {@code animate} is what makes it run unconditionally.
     *
     * <p>Reads the raw JSON text rather than parsing it, so this stays Android-light and
     * dependency-free like the rest of the class. The value is always a quoted animation id, so a
     * substring match cannot collide with a different key.
     */
    private static boolean entityPlaysAnimation(File entity, String animationId) {
        String json = readAll(entity);
        if (json == null) return false;
        int animateAt = json.indexOf("\"animate\"");
        if (animateAt < 0) return false;
        int end = json.indexOf(']', animateAt);
        if (end < 0) end = json.length();
        return json.substring(animateAt, end).contains("\"" + animationId + "\"");
    }

    private static String readAll(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                sb.append(buffer, 0, read);
            }
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }

    /** A plain-text report the user can copy and paste back to the team. */
    public static String report(List<Check> checks) {
        StringBuilder sb = new StringBuilder("GlowberryClient cosmetics status\n");
        if (checks != null) {
            for (Check check : checks) {
                sb.append('[').append(symbol(check.status)).append("] ")
                        .append(check.label).append(": ").append(check.detail).append('\n');
            }
        }
        return sb.toString();
    }

    private static String symbol(Status status) {
        switch (status) {
            case OK: return "OK  ";
            case FAIL: return "FAIL";
            default: return "??  ";
        }
    }
}
