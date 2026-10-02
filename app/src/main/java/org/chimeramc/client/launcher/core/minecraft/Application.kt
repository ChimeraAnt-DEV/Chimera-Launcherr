package org.chimeramc.client.core.minecraft

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.chimeramc.client.core.crash.CrashReporter
import org.chimeramc.client.core.news.NewsNotificationHelper
import org.chimeramc.client.settings.FeatureSettings
import org.chimeramc.client.settings.LowLatencyNetworkManager
import org.chimeramc.client.settings.ThermalGovernor
import org.chimeramc.client.ui.dialogs.LogcatOverlayManager

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        FeatureSettings.init(applicationContext)
        LowLatencyNetworkManager.init(applicationContext)
        ThermalGovernor.init(applicationContext)
        org.chimeramc.client.core.minecraft.FpsOptimizationService.init(applicationContext)
        CrashReporter.init(this)
        // Mirror the persisted haptics preference into the static feedback layer so the
        // very first interaction honours it, before Settings is ever opened.
        org.chimeramc.client.ui.animation.UiTouchFeedback.setEnabled(
            org.chimeramc.client.util.PersonalizationManager(applicationContext)
                .isHapticFeedbackEnabled()
        )
        val processName = Application.getProcessName()
        if (processName.endsWith(":crash")) return

        NewsNotificationHelper.initialize(this)
        LogcatOverlayManager.init(this)
        PlaytimeManager.init(applicationContext)
        // The Replay recorder's state lives in a process singleton so a screen opened mid-capture
        // sees the running state; init here so it exists before any Mod Menu screen asks for it.
        org.chimeramc.client.core.replay.ReplayManager.init(applicationContext)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    /**
     * Feeds the OS memory-pressure signal to the FPS optimizer.
     *
     * <p>{@code onTrimMemory} is the only place the platform tells the host it is being squeezed,
     * and the optimizer treats it as one of the reasons to shed background work during a session.
     * The constant is {@code ComponentCallbacks2.TRIM_MEMORY_RUNNING_*} — referencing the int is
     * safe on minSdk 28 because it is a compile-time constant, not a type.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val critical = level >= TRIM_MEMORY_RUNNING_CRITICAL
        org.chimeramc.client.core.minecraft.FpsOptimizationService.setLowMemory(critical)
        if (critical) {
            org.chimeramc.client.core.minecraft.FpsOptimizationService.apply()
        }
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var preferences: SharedPreferences
            private set
    }
}
