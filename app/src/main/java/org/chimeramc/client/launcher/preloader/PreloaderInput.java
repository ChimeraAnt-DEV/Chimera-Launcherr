package org.chimeramc.client.preloader;

import android.view.MotionEvent;

import java.io.File;

public class PreloaderInput {
    private static final int[] MOUSE_BUTTONS = {
            MotionEvent.BUTTON_PRIMARY,
            MotionEvent.BUTTON_SECONDARY,
            MotionEvent.BUTTON_TERTIARY,
            MotionEvent.BUTTON_BACK,
            MotionEvent.BUTTON_FORWARD,
            MotionEvent.BUTTON_STYLUS_PRIMARY,
            MotionEvent.BUTTON_STYLUS_SECONDARY
    };

    private static int mouseButtonsDown;
    private static int blockedMouseButtons;
    private static int lastConsumedMouseTransitions;
    private static boolean blockedMousePointerStream;

    public static native boolean nativeOnTouch(int action, int pointerId, float x, float y);
    public static native boolean nativeOnKeyEvent(int keyCode, int unicodeChar, boolean isKeyDown);
    public static native boolean nativeOnTextInput(String text);
    public static native boolean nativeOnMouse(int button, boolean isDown);
    public static native void nativeOnDocumentResult(boolean success, String path, String displayName, String error);
    public static native void nativeSetActivity(Object activity);
    public static native void nativeClearActivity();
    public static native boolean nativeIsPauseMenuOpen();
    public static native boolean nativeIsHudScreenOpen();
    public static native boolean nativeIsShowingMenu();
    public static native boolean nativeShouldForceGlobalModMenu();
    public static native boolean nativeIsLocalPlayerAvailable();
    public static native float[] nativeReadLocalPlayerPosition();
    public static native float[] nativeReadLocalPlayerRotation();
    public static native float[] nativeReadLocalPlayerHealth();

    /**
     * Asks the running game to re-read the active resource-pack list.
     *
     * <p>Optional by design: a build that has not resolved the per-version hook for this returns
     * false and the caller falls back to "applies on the next world load". Declared here so the
     * seam exists and the Java side needs no change when the hook lands.
     */
    public static native boolean nativeReloadResourcePacks();

    /**
     * True once the game's own player-model renderer has run this session.
     *
     * <p>This is the native-cosmetics capability probe: the render hook is only useful if the
     * renderer is actually running, so the launcher treats "hook live" as "the native path can
     * drive cape/pet motion this session" and otherwise keeps the resource-pack path.
     */
    public static native boolean nativeIsPlayerRenderHookLive();

    /**
     * The render tick and per-frame call count, as
     * {@code {renderTick, callsThisFrame, totalCalls, msSinceLastRender}}, or null when the
     * renderer has not run. {@code callsThisFrame > 1} means the renderer drew more than one
     * player model this frame, i.e. the hook also covers non-local players.
     */
    public static native int[] nativeReadPlayerRenderStats();

    public static native void nativeConfigureSignatureRules(String rulesPath, String minecraftVersion);

    // --- Bedrock Optifine Mode -------------------------------------------------------------

    /**
     * Applies a flat {@code key=value} optifine configuration blob. The blob is built by
     * {@code OptifineConfigBlob}; the preloader parses it so the two sides share one grammar.
     */
    public static native void nativeConfigureOptifineMode(String blob);

    /**
     * The current state of every optifine item as a flat string array, six entries per item:
     * {@code {id, enabled, tier2, status, detail, needsRestart}}. Null when the native library is
     * absent.
     */
    public static native String[] nativeReadOptifineState();

    /** True when the master switch is on and at least one item is active. */
    public static native boolean nativeIsOptifineModeActive();

    /** Records the refresh rate the launcher resolved, for the refresh-rate item's report. */
    public static native void nativeSetOptifineRefreshTarget(int hz);

    /** Configures the dynamic-render-distance governor bounds. */
    public static native void nativeConfigureRenderDistance(int minDistance, int maxDistance, int fpsThreshold);

    /** The tick count the callback-trimming hook has observed. */
    public static native long nativeOptifineTickCount();

    /** The HUD update count the OreUI-stripping hook has observed. */
    public static native long nativeOptifineHudUpdateCount();

    // --- Native cosmetics registry ---------------------------------------------------------
    // The launcher fills these; the native skin/cape and texture hooks read the same tables. Every
    // call is guarded by the caller via the try/catch wrappers below, so a build without the
    // library degrades to the resource-pack path rather than throwing.

    /** Registers a cape override (raw RGBA) for a player id. */
    public static native void nativeSetCapeOverride(long playerKey, byte[] rgba, int width, int height);

    /** Clears every cape override. */
    public static native void nativeClearCapeOverrides();

    /** Number of cape overrides currently registered. */
    public static native int nativeCapeOverrideCount();

    /** Registers a texture override (raw RGBA) for a texture id. */
    public static native void nativeSetTextureOverride(long textureId, byte[] rgba, int width, int height);

    /** Clears every texture override. */
    public static native void nativeClearTextureOverrides();

    /** Number of texture overrides currently registered. */
    public static native int nativeTextureOverrideCount();

    /** Publishes the geometry blob the render hook should draw for the local player. */
    public static native void nativeSetRenderGeometry(byte[] data);

    /**
     * Replaces the cape image inside a {@code SerializedSkinRef} at a raw address. The image must
     * be the engine's own 0x30-byte {@code mce::Image} struct (built by its loader), not raw RGBA.
     */
    public static native boolean nativeSwapCapeImage(long skinRefAddress, byte[] imageBytes);

    /** The address of the engine's image loader, or 0 when unresolved on this build. */
    public static native long nativeImageLoaderAddress();

    /**
     * Builds a valid engine {@code mce::Image} (0x30 bytes) from PNG bytes, or null when the loader
     * is unresolved or the engine rejects the bytes.
     */
    public static native byte[] nativeBuildCapeImage(byte[] pngBytes);

    /** True once the native cosmetics skin/cape or texture hook has fired this session. */
    public static native boolean nativeIsCosmeticsHookLive();

    /** Reads {skinCapeCalls (-1 when unavailable), textureCalls, capeOverrides, textureOverrides}. */
    public static native int[] nativeReadCosmeticsStats();

    /**
     * Samples the native cape chain: {@code {lean1, sway1, lean2, sway2, ...}} in degrees for
     * {@code segments} segments, or empty when unavailable. Lets the preview and the native render
     * share one motion curve instead of each carrying its own amplitudes.
     */
    public static native float[] nativeSampleCapeChain(
            double moveSpeed, boolean jumping, double verticalSpeed,
            double distanceMoved, double capeFlap, double bodyYawDegrees, int segments);

    public static void configureOptifineMode(String blob) {
        try {
            nativeConfigureOptifineMode(blob == null ? "" : blob);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static String[] readOptifineState() {
        try {
            return nativeReadOptifineState();
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    public static boolean isOptifineModeActive() {
        try {
            return nativeIsOptifineModeActive();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static void setOptifineRefreshTarget(int hz) {
        try {
            nativeSetOptifineRefreshTarget(hz);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static void configureRenderDistance(int minDistance, int maxDistance, int fpsThreshold) {
        try {
            nativeConfigureRenderDistance(minDistance, maxDistance, fpsThreshold);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static long optifineTickCount() {
        try {
            return nativeOptifineTickCount();
        } catch (UnsatisfiedLinkError e) {
            return 0L;
        }
    }

    public static long optifineHudUpdateCount() {
        try {
            return nativeOptifineHudUpdateCount();
        } catch (UnsatisfiedLinkError e) {
            return 0L;
        }
    }

    public static void configureSignatureRules(File rulesFile, String minecraftVersion) {
        try {
            nativeConfigureSignatureRules(
                    rulesFile == null ? "" : rulesFile.getAbsolutePath(),
                    minecraftVersion == null ? "" : minecraftVersion
            );
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static boolean isPauseMenuOpen() {
        try {
            return nativeIsPauseMenuOpen();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean isHudScreenOpen() {
        try {
            return nativeIsHudScreenOpen();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean isShowingMenu() {
        try {
            return nativeIsShowingMenu();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean shouldForceGlobalModMenu() {
        try {
            return nativeShouldForceGlobalModMenu();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * True once the game has handed us a live local player this session.
     *
     * <p>Fail-closed: an unavailable native library reads as "no player", so the
     * callers that project in-world icons draw nothing rather than guessing.
     */
    public static boolean isLocalPlayerAvailable() {
        try {
            return nativeIsLocalPlayerAvailable();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /** The local player's world position, or null when no live read is possible. */
    public static float[] readLocalPlayerPosition() {
        try {
            float[] value = nativeReadLocalPlayerPosition();
            return value != null && value.length >= 3 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    /** The local player's view rotation as {yaw, pitch} degrees, or null when unavailable. */
    public static float[] readLocalPlayerRotation() {
        try {
            float[] value = nativeReadLocalPlayerRotation();
            return value != null && value.length >= 2 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    /**
     * The local player's health, or null when no live read is possible.
     *
     * <p>Fail-closed in two ways: the native library being absent reads as "no data", and the
     * native side reports "no data" when the per-version health offset is not configured or the
     * field does not hold a plausible health value. A highlight trigger therefore never fires
     * from an unverified offset.
     */
    public static float[] readLocalPlayerHealth() {
        try {
            float[] value = nativeReadLocalPlayerHealth();
            return value != null && value.length >= 1 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    /**
     * Asks the game to re-read its active resource packs.
     *
     * @return true when the running game refreshed; false when no live hook is installed, so the
     *         caller reports the change will apply on the next world load instead of implying it
     *         took effect now.
     */
    public static boolean reloadResourcePacks() {
        try {
            return nativeReloadResourcePacks();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * True once the game's own player-model renderer has run this session.
     *
     * <p>Fail-closed: an unavailable native library, an unresolved slot, or a build whose
     * renderer never ran all read as false, so the launcher keeps the resource-pack path rather
     * than assuming a native path it does not have.
     */
    public static boolean isPlayerRenderHookLive() {
        try {
            return nativeIsPlayerRenderHookLive();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    /**
     * The render tick and call counts, or null when the renderer has not run.
     *
     * @return {@code {renderTick, callsThisFrame, totalCalls, msSinceLastRender}}, or null.
     */
    public static int[] readPlayerRenderStats() {
        try {
            int[] value = nativeReadPlayerRenderStats();
            return value != null && value.length >= 4 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    /**
     * Samples the native cape chain, or null when the native library is unavailable.
     *
     * <p>The preview and the native render share one motion curve through this call, so the two
     * cannot disagree about how far the cloth leans.
     */
    public static float[] sampleCapeChain(double moveSpeed, boolean jumping, double verticalSpeed,
                                          double distanceMoved, double capeFlap,
                                          double bodyYawDegrees, int segments) {
        try {
            float[] value = nativeSampleCapeChain(moveSpeed, jumping, verticalSpeed, distanceMoved,
                    capeFlap, bodyYawDegrees, segments);
            return value != null && value.length >= 2 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    // --- Guarded cosmetics registry wrappers -----------------------------------------------
    // Each returns a safe default on a build whose native library lacks the symbol, so the
    // cosmetics path degrades to the resource pack rather than throwing into the UI.

    public static void setCapeOverride(long playerKey, byte[] rgba, int width, int height) {
        try {
            nativeSetCapeOverride(playerKey, rgba, width, height);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static void clearCapeOverrides() {
        try {
            nativeClearCapeOverrides();
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static int capeOverrideCount() {
        try {
            return nativeCapeOverrideCount();
        } catch (UnsatisfiedLinkError e) {
            return 0;
        }
    }

    public static void setTextureOverride(long textureId, byte[] rgba, int width, int height) {
        try {
            nativeSetTextureOverride(textureId, rgba, width, height);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static void clearTextureOverrides() {
        try {
            nativeClearTextureOverrides();
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static int textureOverrideCount() {
        try {
            return nativeTextureOverrideCount();
        } catch (UnsatisfiedLinkError e) {
            return 0;
        }
    }

    public static void setRenderGeometry(byte[] data) {
        try {
            nativeSetRenderGeometry(data);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static boolean swapCapeImage(long skinRefAddress, byte[] imageBytes) {
        try {
            return nativeSwapCapeImage(skinRefAddress, imageBytes);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static long imageLoaderAddress() {
        try {
            return nativeImageLoaderAddress();
        } catch (UnsatisfiedLinkError e) {
            return 0L;
        }
    }

    public static byte[] buildCapeImage(byte[] pngBytes) {
        try {
            return nativeBuildCapeImage(pngBytes);
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    public static boolean isCosmeticsHookLive() {
        try {
            return nativeIsCosmeticsHookLive();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static int[] readCosmeticsStats() {
        try {
            int[] value = nativeReadCosmeticsStats();
            return value != null && value.length >= 4 ? value : null;
        } catch (UnsatisfiedLinkError e) {
            return null;
        }
    }

    public static boolean onTouch(int action, int pointerId, float x, float y) {
        try {
            return nativeOnTouch(action, pointerId, x, y);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean onKeyEvent(int keyCode, int unicodeChar, boolean isKeyDown) {
        try {
            return nativeOnKeyEvent(keyCode, unicodeChar, isKeyDown);
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean onTextInput(CharSequence text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        try {
            return nativeOnTextInput(text.toString());
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static void onDocumentResult(boolean success, String path, String displayName, String error) {
        try {
            nativeOnDocumentResult(
                    success,
                    path == null ? "" : path,
                    displayName == null ? "" : displayName,
                    error == null ? "" : error
            );
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static void cancelDocumentRequest(String reason) {
        onDocumentResult(false, "", "", reason == null ? "Import cancelled" : reason);
    }

    private static boolean dispatchMouseTransition(int button, boolean isDown) {
        boolean alreadyDown = (mouseButtonsDown & button) != 0;
        if (alreadyDown == isDown) {
            return (lastConsumedMouseTransitions & button) != 0;
        }

        boolean consumed;
        try {
            consumed = nativeOnMouse(button, isDown);
        } catch (UnsatisfiedLinkError e) {
            consumed = false;
        }

        if (isDown) {
            mouseButtonsDown |= button;
            if (consumed) {
                blockedMouseButtons |= button;
                lastConsumedMouseTransitions |= button;
            } else {
                blockedMouseButtons &= ~button;
                lastConsumedMouseTransitions &= ~button;
            }
            return consumed;
        }

        boolean effectiveConsumed = consumed || (blockedMouseButtons & button) != 0;
        mouseButtonsDown &= ~button;
        blockedMouseButtons &= ~button;
        if (effectiveConsumed) {
            lastConsumedMouseTransitions |= button;
        } else {
            lastConsumedMouseTransitions &= ~button;
        }
        return effectiveConsumed;
    }

    public static synchronized boolean onMouse(int button, boolean isDown) {
        if (button == 0) {
            return false;
        }
        return dispatchMouseTransition(button, isDown);
    }

    public static synchronized boolean onMouseMotion(int action, int actionButton, int buttonState) {
        int normalizedButtonState = buttonState;
        if (action == MotionEvent.ACTION_DOWN && normalizedButtonState == 0 && actionButton == 0) {
            normalizedButtonState = MotionEvent.BUTTON_PRIMARY;
        }
        if (action == MotionEvent.ACTION_BUTTON_PRESS) {
            normalizedButtonState |= actionButton;
        } else if (action == MotionEvent.ACTION_BUTTON_RELEASE) {
            normalizedButtonState &= ~actionButton;
        } else if (action == MotionEvent.ACTION_UP ||
                action == MotionEvent.ACTION_POINTER_UP ||
                action == MotionEvent.ACTION_CANCEL) {
            normalizedButtonState = 0;
        }

        int pressedButtons = normalizedButtonState & ~mouseButtonsDown;
        int releasedButtons = mouseButtonsDown & ~normalizedButtonState;
        int blockedBeforeTransitions = blockedMouseButtons;
        boolean transitionConsumed = false;

        for (int button : MOUSE_BUTTONS) {
            if ((pressedButtons & button) != 0) {
                transitionConsumed |= dispatchMouseTransition(button, true);
            }
        }
        for (int button : MOUSE_BUTTONS) {
            if ((releasedButtons & button) != 0) {
                transitionConsumed |= dispatchMouseTransition(button, false);
            }
        }

        boolean blockedButtonActive = (normalizedButtonState & blockedMouseButtons) != 0;
        boolean duplicateTransitionConsumed = actionButton != 0 &&
                (lastConsumedMouseTransitions & actionButton) != 0;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_BUTTON_PRESS:
                if (transitionConsumed || blockedButtonActive || duplicateTransitionConsumed) {
                    blockedMousePointerStream = true;
                }
                return blockedMousePointerStream;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_HOVER_MOVE:
                return blockedMousePointerStream || blockedButtonActive;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_BUTTON_RELEASE:
            case MotionEvent.ACTION_CANCEL:
                boolean consumeRelease = transitionConsumed ||
                        blockedMousePointerStream ||
                        blockedBeforeTransitions != 0 ||
                        duplicateTransitionConsumed;
                if (mouseButtonsDown == 0 || action == MotionEvent.ACTION_CANCEL) {
                    blockedMousePointerStream = false;
                }
                return consumeRelease;
            default:
                return blockedMousePointerStream || blockedButtonActive;
        }
    }

    public static synchronized void resetMouseState() {
        mouseButtonsDown = 0;
        blockedMouseButtons = 0;
        lastConsumedMouseTransitions = 0;
        blockedMousePointerStream = false;
    }

    public static void setActivity(Object activity) {
        resetMouseState();
        try {
            nativeSetActivity(activity);
        } catch (UnsatisfiedLinkError e) {
        }
    }

    public static void clearActivity() {
        resetMouseState();
        try {
            nativeClearActivity();
        } catch (UnsatisfiedLinkError e) {
        }
    }
}
