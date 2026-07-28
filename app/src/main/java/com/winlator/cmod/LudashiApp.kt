package com.winlator.cmod

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.winlator.cmod.perf.PerfRevertRegistry
import com.winlator.cmod.perf.PerformanceSettings
import com.winlator.cmod.perf.RootManager
import com.winlator.cmod.perf.TempWatchdog
import java.io.File

/**
 * Initializes the performance-control safety layer before any activity can write a privileged node.
 * Failures are isolated because these controls are optional and must never block app startup.
 */
class LudashiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // XrActivity runs in :vr_process. It must not race the main process over the shared
        // persisted sysfs snapshot or interpret the main game's background state as an exit.
        if (!isMainProcess()) return
        try {
            RootManager.onAppStartup(this)
            TempWatchdog.init(this)
            PerformanceSettings.init(this)
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    try {
                        PerfRevertRegistry.revertAll()
                    } catch (t: Throwable) {
                        Log.w("LudashiApp", "Background performance-state revert failed", t)
                    }
                }
            })
        } catch (t: Throwable) {
            Log.w("LudashiApp", "Performance safety-core initialization failed", t)
        }
    }

    private fun isMainProcess(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val processName = manager?.runningAppProcesses
            ?.firstOrNull { it.pid == Process.myPid() }
            ?.processName
            ?: try {
                File("/proc/self/cmdline").readText().trim('\u0000')
            } catch (_: Throwable) {
                null
            }
        return processName == packageName
    }
}
