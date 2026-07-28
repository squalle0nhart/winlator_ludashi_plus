package com.winlator.cmod.perf

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File

/**
 * Captures every privileged node's original value before the first write, persists the snapshot,
 * and restores exact values on toggle-off, game exit, background, crash, or the next launch.
 */
object PerfRevertRegistry {
    private const val TAG = "PerfRevert"
    private const val FILE_NAME = "perf_revert_snapshot.json"
    private const val KEY_DIRTY = "dirty"
    private const val KEY_NODES = "nodes"
    private const val PREFS = "perf_prefs"
    private const val KEY_HARNESS = "harness_proven"

    private val originals = LinkedHashMap<String, String>()
    private var snapshotFile: File? = null
    private var appContext: Context? = null
    private var hooksInstalled = false

    private val _harnessProven = MutableStateFlow(true)
    val harnessProven: StateFlow<Boolean> = _harnessProven.asStateFlow()

    @Synchronized
    fun onAppStartup(context: Context) {
        val app = context.applicationContext
        appContext = app
        if (snapshotFile == null) snapshotFile = File(app.filesDir, FILE_NAME)
        _harnessProven.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HARNESS, true)
        installSafetyNets()
        snapshotFile?.takeIf(::fileLooksDirty)?.let {
            Thread(::restoreFromDiskIfDirty, "perf-revert-restore")
                .apply { isDaemon = true }
                .start()
        }
    }

    @Synchronized
    fun applyWrite(path: String, value: String): Boolean {
        captureIfAbsent(path)
        return originals.containsKey(path) && RootManager.writeNode(path, value)
    }

    @Synchronized
    fun captureIfAbsent(path: String) {
        if (originals.containsKey(path)) return
        val current = RootManager.readNode(path) ?: return
        originals[path] = current
        persist()
    }

    @Synchronized
    fun revertAll() {
        if (originals.isEmpty()) {
            clearPersisted()
            return
        }
        Log.d(TAG, "Reverting ${originals.size} node(s)")
        originals.entries.reversed().forEach { (path, value) ->
            RootManager.writeNode(path, value)
        }
        originals.clear()
        clearPersisted()
    }

    @Synchronized
    fun revertNodes(paths: Collection<String>) {
        paths.filter(originals::containsKey).reversed().forEach { path ->
            originals[path]?.let { RootManager.writeNode(path, it) }
            originals.remove(path)
        }
        if (originals.isEmpty()) clearPersisted() else persist()
    }

    @Synchronized
    fun setHarnessProven(proven: Boolean) {
        _harnessProven.value = proven
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()?.putBoolean(KEY_HARNESS, proven)?.apply()
    }

    @Synchronized
    fun restoreFromDiskIfDirty() {
        val file = snapshotFile ?: return
        if (!fileLooksDirty(file)) return
        val loaded = try {
            val json = JSONObject(file.readText())
            if (!json.optBoolean(KEY_DIRTY, false)) return
            val nodes = json.optJSONObject(KEY_NODES) ?: JSONObject()
            LinkedHashMap<String, String>().apply {
                nodes.keys().forEach { key -> put(key, nodes.getString(key)) }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not parse persisted snapshot", t)
            return
        }
        if (loaded.isEmpty()) {
            clearPersisted()
            return
        }
        if (!RootManager.ensureGrantedBlocking()) {
            Log.w(TAG, "Snapshot remains dirty because root is unavailable")
            return
        }
        loaded.entries.reversed().forEach { (path, value) ->
            RootManager.writeNode(path, value)
        }
        originals.clear()
        clearPersisted()
    }

    private fun persist() {
        val file = snapshotFile ?: return
        try {
            val nodes = JSONObject()
            originals.forEach(nodes::put)
            val staging = File(file.parentFile, "$FILE_NAME.staging")
            staging.writeText(JSONObject().put(KEY_DIRTY, true).put(KEY_NODES, nodes).toString())
            if (!staging.renameTo(file)) {
                file.writeText(staging.readText())
                staging.delete()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Snapshot persistence failed", t)
        }
    }

    private fun clearPersisted() {
        try {
            snapshotFile?.takeIf(File::exists)?.delete()
            snapshotFile?.parentFile?.let { File(it, "$FILE_NAME.staging") }
                ?.takeIf(File::exists)?.delete()
        } catch (_: Throwable) {
        }
    }

    private fun fileLooksDirty(file: File): Boolean =
        try {
            file.exists() && JSONObject(file.readText()).optBoolean(KEY_DIRTY, false)
        } catch (_: Throwable) {
            false
        }

    private fun installSafetyNets() {
        if (hooksInstalled) return
        hooksInstalled = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                revertAll()
            } catch (t: Throwable) {
                Log.w(TAG, "Crash revert failed", t)
            }
            previous?.uncaughtException(thread, throwable)
        }
        try {
            Runtime.getRuntime().addShutdownHook(
                Thread({
                    try {
                        revertAll()
                    } catch (_: Throwable) {
                    }
                }, "perf-revert-shutdown"),
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Shutdown hook setup failed", t)
        }
    }
}
