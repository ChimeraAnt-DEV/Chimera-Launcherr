package org.chimeramc.client.core.minecraft;

/**
 * The status codes the preloader reports for an optifine item, and the pure mapping from a code
 * to a label and colour.
 *
 * <p>The codes are the wire contract with {@code OptifineItemState} on the native side; the
 * mapping is here so a UI change is a one-file edit and the four states cannot be confused. Pure
 * and Android-resource-id-only, so it is unit-testable.
 */
public final class OptifineStatus {

    /** The item was not attempted (master off, toggle off, or preloader absent). */
    public static final int NOT_ATTEMPTED = 0;
    /** The item was applied and is running. */
    public static final int ACTIVE = 1;
    /** The item could not be applied on this build/device, and said why in its detail. */
    public static final int SKIPPED = 2;
    /** The item was attempted and failed; the detail carries the reason. */
    public static final int FAILED = 3;

    private OptifineStatus() {
    }

    /** The label resource for a status code; {@code optifine_status_off} for an unknown code. */
    public static int labelRes(int status) {
        switch (status) {
            case ACTIVE: return org.chimeramc.client.R.string.optifine_status_active;
            case SKIPPED: return org.chimeramc.client.R.string.optifine_status_skipped;
            case FAILED: return org.chimeramc.client.R.string.optifine_status_failed;
            case NOT_ATTEMPTED:
            default:
                return org.chimeramc.client.R.string.optifine_status_off;
        }
    }

    /** The colour resource for a status code. */
    public static int colorRes(int status) {
        switch (status) {
            case ACTIVE: return org.chimeramc.client.R.color.optifine_item_ok;
            case SKIPPED: return org.chimeramc.client.R.color.optifine_item_skip;
            case FAILED: return org.chimeramc.client.R.color.optifine_item_fail;
            case NOT_ATTEMPTED:
            default:
                return org.chimeramc.client.R.color.text_secondary_light;
        }
    }

    /** True when the status represents an applied, running item. */
    public static boolean isActive(int status) {
        return status == ACTIVE;
    }
}
