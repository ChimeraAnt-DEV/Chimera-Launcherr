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
 * <p>Capes are delivered by a resource pack that overrides {@code textures/entity/cape_invisible.png};
 * the code's own design note says the worst case is an invisible cape, so a failure is silent.
 * This class turns the silent failure into a set of named checks a person can read off a screen.
 *
 * <p>It is deliberately {@code File}-based and Android-light: the caller resolves the candidate
 * game-data roots and the staging directory, so the logic is unit-testable against a temporary
 * directory tree and never guesses a path itself.
 *
 * <p>Only three of the four links can be checked from the launcher. Whether the player has a cape
 * equipped in the game's own dressing room, and whether RenderDragon honours the override, cannot
 * be read without the game, so those are reported as manual checks rather than invented.
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

        // 3. Does the manifest's minimum engine version accept the installed game?
        File manifestRoot = activeRoot != null ? activeRoot
                : (writtenRoots.isEmpty() ? null : writtenRoots.get(0));
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

        // The two links only the game can speak to.
        checks.add(new Check("A cape is equipped in game", Status.MANUAL,
                "The override only shows if the player entity has a cape in the game's dressing "
                        + "room. Equip any vanilla cape once, then check."));
        checks.add(new Check("Renderer honours the override", Status.MANUAL,
                "Use \"Test cape\" below: if no magenta appears, this RenderDragon build ignores "
                        + "the texture override and the resource-pack route cannot work."));
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
