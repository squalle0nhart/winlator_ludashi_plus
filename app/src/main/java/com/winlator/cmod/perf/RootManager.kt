package com.winlator.cmod.perf

import android.content.Context
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** The only class allowed to create a root shell or perform a privileged write. */
object RootManager {
    private const val TAG = "RootManager"
    private const val PREFS = "perf_prefs"
    private const val KEY_GRANTED_ONCE = "root_granted_once"

    enum class RootState {
        UNKNOWN,
        UNAVAILABLE,
        AVAILABLE_NOT_GRANTED,
        GRANTED,
        DENIED,
    }

    private val SU_CANDIDATES = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/su/bin/su",
        "/system/sbin/su",
        "/vendor/bin/su",
        "/debug_ramdisk/su",
    )

    private var appContext: Context? = null
    private val _state = MutableStateFlow(RootState.UNKNOWN)
    val state: StateFlow<RootState> = _state.asStateFlow()
    val isGranted: Boolean get() = _state.value == RootState.GRANTED

    init {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
    }

    private fun hasSuBinary(): Boolean =
        SU_CANDIDATES.any { File(it).exists() } ||
            (System.getenv("PATH")?.split(":")?.any { File(it, "su").exists() } ?: false)

    private fun grantedOnce(): Boolean =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(KEY_GRANTED_ONCE, false) ?: false

    private fun setGrantedOnce(value: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_GRANTED_ONCE, value)?.apply()
    }

    fun probe() {
        val granted = Shell.isAppGrantedRoot()
        _state.value = when {
            granted == true -> RootState.GRANTED
            !hasSuBinary() -> RootState.UNAVAILABLE
            else -> RootState.AVAILABLE_NOT_GRANTED
        }
        Log.d(TAG, "probe -> ${_state.value}")
    }

    suspend fun requestGrant(): Boolean = withContext(Dispatchers.IO) {
        ensureGrantedBlocking()
    }

    fun ensureGrantedBlocking(): Boolean {
        if (isGranted) return true
        if (!hasSuBinary()) {
            _state.value = RootState.UNAVAILABLE
            return false
        }
        val granted = try {
            Shell.getShell().isRoot
        } catch (t: Throwable) {
            Log.w(TAG, "Root grant failed", t)
            false
        }
        _state.value = if (granted) RootState.GRANTED else RootState.DENIED
        setGrantedOnce(granted)
        return granted
    }

    fun readNode(path: String): String? {
        if (!isGranted) return null
        return try {
            val result = Shell.cmd(PerfCmd.readCmd(path)).exec()
            if (result.isSuccess) PerfCmd.normalizeRead(result.out.joinToString("\n")) else null
        } catch (t: Throwable) {
            Log.w(TAG, "readNode($path) failed", t)
            null
        }
    }

    fun writeNode(path: String, value: String): Boolean {
        if (!isGranted) return false
        return try {
            val result = Shell.cmd(PerfCmd.writeCmd(path, value)).exec()
            if (!result.isSuccess) {
                Log.w(TAG, "writeNode($path=$value) rc=${result.code}: ${result.err}")
            }
            result.isSuccess
        } catch (t: Throwable) {
            Log.w(TAG, "writeNode($path=$value) failed", t)
            false
        }
    }

    fun onAppStartup(context: Context) {
        try {
            appContext = context.applicationContext
            probe()
            PerfRevertRegistry.onAppStartup(context.applicationContext)
            if (!isGranted && grantedOnce() && hasSuBinary()) {
                Thread({
                    try {
                        if (!ensureGrantedBlocking()) setGrantedOnce(false)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Silent root re-acquire failed", t)
                    }
                }, "root-silent-reacquire").apply { isDaemon = true }.start()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Startup root probe failed", t)
        }
    }
}
