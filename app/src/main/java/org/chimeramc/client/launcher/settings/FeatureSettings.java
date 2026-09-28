package org.chimeramc.client.settings;

import android.content.Context;

public class FeatureSettings {
    private static volatile FeatureSettings INSTANCE;
    private static Context appContext;
    private boolean versionIsolationEnabled = false;
    private boolean launcherManagedMcLoginEnabled = false;
    private boolean logcatOverlayEnabled = false;
    private boolean lowInputDelayEnabled = false;
    private Boolean crashUploadEnabled = true;
    private boolean foregroundServiceEnabled = false;
    private Boolean gxcoreEnabled = false;
    private boolean reduceNetworkLatencyEnabled = false;
    private boolean fpsOptimizerEnabled = false;

    public enum StorageType {
        INTERNAL,
        EXTERNAL,
        VERSION_ISOLATION,
        VERSION_ISOLATION_INTERNAL,
        VERSION_ISOLATION_EXTERNAL
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static FeatureSettings getInstance() {
        if (INSTANCE == null) {
            synchronized (FeatureSettings.class) {
                if (INSTANCE == null) {
                    INSTANCE = SettingsStorage.load(appContext);
                    if (INSTANCE == null) {
                        INSTANCE = new FeatureSettings();
                    }
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Re-reads the persisted settings into the singleton.
     *
     * Restoring a backup writes the store directly, but the singleton survives for the life of
     * the process, so without this the current session would keep serving the pre-import values
     * until the next launch. Callers must hold no references to the old instance.
     */
    public static void reload(Context context) {
        if (context != null) {
            appContext = context.getApplicationContext();
        }
        synchronized (FeatureSettings.class) {
            INSTANCE = SettingsStorage.load(appContext);
            if (INSTANCE == null) {
                INSTANCE = new FeatureSettings();
            }
        }
    }

    public boolean isVersionIsolationEnabled() { return versionIsolationEnabled; }
    public void setVersionIsolationEnabled(boolean enabled) { this.versionIsolationEnabled = enabled; autoSave(); }

    public boolean isLauncherManagedMcLoginEnabled() { return launcherManagedMcLoginEnabled; }
    public void setLauncherManagedMcLoginEnabled(boolean enabled) { this.launcherManagedMcLoginEnabled = enabled; autoSave(); }

    public boolean isLogcatOverlayEnabled() { return logcatOverlayEnabled; }
    public void setLogcatOverlayEnabled(boolean enabled) { this.logcatOverlayEnabled = enabled; autoSave(); }

    public boolean isLowInputDelayEnabled() { return lowInputDelayEnabled; }
    public void setLowInputDelayEnabled(boolean enabled) { this.lowInputDelayEnabled = enabled; autoSave(); }

    public boolean isCrashUploadEnabled() { return crashUploadEnabled == null || crashUploadEnabled; }
    public void setCrashUploadEnabled(boolean enabled) { this.crashUploadEnabled = enabled; autoSave(); }

    public boolean isForegroundServiceEnabled() { return foregroundServiceEnabled; }
    public void setForegroundServiceEnabled(boolean enabled) { this.foregroundServiceEnabled = enabled; autoSave(); }

    public boolean isGxCoreEnabled() { return gxcoreEnabled != null && gxcoreEnabled; }
    public void setGxCoreEnabled(boolean enabled) { this.gxcoreEnabled = enabled; autoSave(); }

    /**
     * Whether the FPS optimization module may quiet the host during a session.
     *
     * <p>Off by default, like anti stick drift: enabling it changes how the launcher behaves on
     * every launch, so an existing setup cannot be altered silently. It only ever reduces
     * launcher-side work during a session -- see {@code FpsOptimizer} for what that does and,
     * more importantly, what it deliberately does not do.
     */
    public boolean isFpsOptimizerEnabled() { return fpsOptimizerEnabled; }
    public void setFpsOptimizerEnabled(boolean enabled) { this.fpsOptimizerEnabled = enabled; autoSave(); }

    public boolean isReduceNetworkLatencyEnabled() { return reduceNetworkLatencyEnabled; }
    public void setReduceNetworkLatencyEnabled(boolean enabled) {
        if (this.reduceNetworkLatencyEnabled == enabled) return;
        this.reduceNetworkLatencyEnabled = enabled;
        autoSave();
        if (enabled) {
            LowLatencyNetworkManager.prefetchDnsOnBackground();
        }
    }


    private void autoSave() {
        if (appContext != null) {
            SettingsStorage.save(appContext, this);
        }
    }
}
