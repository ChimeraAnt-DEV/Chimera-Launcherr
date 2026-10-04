package org.chimeramc.client.core.minecraft;

import java.util.List;

/**
 * Builds the flat {@code key=value} configuration blob the preloader's Optifine mode parses.
 *
 * <p>Pure and Android-free so the blob grammar is unit-testable: the two sides cannot drift
 * field-by-field because the format is pinned here and in {@code OptifineConfig.cpp}. An unknown
 * item id is still written (a newer launcher may send an id an older preloader ignores), but the
 * preloader treats unknown keys as no-ops.
 */
public final class OptifineConfigBlob {

    private OptifineConfigBlob() {
    }

    /**
     * @param masterEnabled the master switch.
     * @param itemIds       the stable item ids in order.
     * @param itemEnabled   per-item toggle, same order as {@code itemIds}.
     * @param crashCounts   consecutive-crash count per item, same order as {@code itemIds}.
     * @return a newline-separated blob, or {@code master=0} when the master switch is off.
     */
    public static String build(boolean masterEnabled, List<String> itemIds,
                               List<Boolean> itemEnabled, List<Integer> crashCounts) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("master=").append(masterEnabled ? 1 : 0).append('\n');
        if (itemIds == null) {
            return builder.toString();
        }
        for (int i = 0; i < itemIds.size(); i++) {
            String id = itemIds.get(i);
            if (id == null || id.isEmpty()) {
                continue;
            }
            boolean enabled = itemEnabled != null && i < itemEnabled.size()
                    && Boolean.TRUE.equals(itemEnabled.get(i));
            int crashes = crashCounts != null && i < crashCounts.size()
                    && crashCounts.get(i) != null ? crashCounts.get(i) : 0;
            builder.append("enabled.").append(id).append('=').append(enabled ? 1 : 0).append('\n');
            builder.append("crash.").append(id).append('=').append(Math.max(0, crashes)).append('\n');
        }
        return builder.toString();
    }

    /**
     * Builds the blob with every item toggle forced off.
     *
     * <p>Used when the master switch is turned off but the sub-toggles must be preserved for the
     * next time it is on: the preloader sees a fully-off configuration, so nothing keeps running.
     */
    public static String buildDisabled(List<String> itemIds) {
        StringBuilder builder = new StringBuilder(256);
        builder.append("master=0\n");
        if (itemIds == null) {
            return builder.toString();
        }
        for (String id : itemIds) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            builder.append("enabled.").append(id).append("=0\n");
            builder.append("crash.").append(id).append("=0\n");
        }
        return builder.toString();
    }
}
