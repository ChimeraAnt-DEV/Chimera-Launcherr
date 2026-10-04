package org.chimeramc.client.core.minecraft;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The stable ids and display metadata for Bedrock Optifine Mode items.
 *
 * <p>The id strings are the wire contract with the preloader's {@code OptifineConfig.cpp}; never
 * renumber or rename one without changing both sides and the golden blob test. The order here is
 * the order the Settings screen renders.
 */
public final class OptifineItemIds {

    private OptifineItemIds() {
    }

    public static final String ALLOCATOR = "allocator";
    public static final String RENDER_PRIORITY = "render_priority";
    public static final String CPU_AFFINITY = "cpu_affinity";
    public static final String REFRESH_RATE = "refresh_rate";
    public static final String ENTITY_CULLING = "entity_culling";
    public static final String PARTICLE_CULLING = "particle_culling";
    public static final String DYNAMIC_RENDER_DISTANCE = "dynamic_render_distance";
    public static final String CALLBACK_TRIMMING = "callback_trimming";
    public static final String OREUI_STRIPPING = "oreui_stripping";

    /** All item ids, in display order. */
    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            ALLOCATOR, RENDER_PRIORITY, CPU_AFFINITY, REFRESH_RATE,
            ENTITY_CULLING, PARTICLE_CULLING, DYNAMIC_RENDER_DISTANCE,
            CALLBACK_TRIMMING, OREUI_STRIPPING));

    /**
     * True for an item that installs a game hook (Tier 2). These default off and carry the
     * "advanced" warning; Tier-1 items only touch the host process and default on.
     */
    public static boolean isTier2(String itemId) {
        if (itemId == null) {
            return false;
        }
        switch (itemId) {
            case ENTITY_CULLING:
            case PARTICLE_CULLING:
            case DYNAMIC_RENDER_DISTANCE:
            case CALLBACK_TRIMMING:
            case OREUI_STRIPPING:
                return true;
            default:
                return false;
        }
    }

    /**
     * The resource id for an item's title string.
     *
     * <p>An explicit switch, not a name lookup, so a renamed key cannot silently bind to an
     * unrelated string.
     */
    public static int titleRes(String itemId) {
        if (itemId == null) {
            return 0;
        }
        switch (itemId) {
            case ALLOCATOR: return org.chimeramc.client.R.string.optifine_item_allocator;
            case RENDER_PRIORITY: return org.chimeramc.client.R.string.optifine_item_render_priority;
            case CPU_AFFINITY: return org.chimeramc.client.R.string.optifine_item_cpu_affinity;
            case REFRESH_RATE: return org.chimeramc.client.R.string.optifine_item_refresh_rate;
            case ENTITY_CULLING: return org.chimeramc.client.R.string.optifine_item_entity_culling;
            case PARTICLE_CULLING: return org.chimeramc.client.R.string.optifine_item_particle_culling;
            case DYNAMIC_RENDER_DISTANCE: return org.chimeramc.client.R.string.optifine_item_render_distance;
            case CALLBACK_TRIMMING: return org.chimeramc.client.R.string.optifine_item_callback_trimming;
            case OREUI_STRIPPING: return org.chimeramc.client.R.string.optifine_item_oreui_stripping;
            default: return 0;
        }
    }

    /** The resource id for an item's one-line description. */
    public static int descriptionRes(String itemId) {
        if (itemId == null) {
            return 0;
        }
        switch (itemId) {
            case ALLOCATOR: return org.chimeramc.client.R.string.optifine_item_allocator_desc;
            case RENDER_PRIORITY: return org.chimeramc.client.R.string.optifine_item_render_priority_desc;
            case CPU_AFFINITY: return org.chimeramc.client.R.string.optifine_item_cpu_affinity_desc;
            case REFRESH_RATE: return org.chimeramc.client.R.string.optifine_item_refresh_rate_desc;
            case ENTITY_CULLING: return org.chimeramc.client.R.string.optifine_item_entity_culling_desc;
            case PARTICLE_CULLING: return org.chimeramc.client.R.string.optifine_item_particle_culling_desc;
            case DYNAMIC_RENDER_DISTANCE: return org.chimeramc.client.R.string.optifine_item_render_distance_desc;
            case CALLBACK_TRIMMING: return org.chimeramc.client.R.string.optifine_item_callback_trimming_desc;
            case OREUI_STRIPPING: return org.chimeramc.client.R.string.optifine_item_oreui_stripping_desc;
            default: return 0;
        }
    }
}
