package com.winlator.cmod.store

import android.content.Context
import android.util.Log
import android.net.wifi.WifiManager
import android.os.PowerManager
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Steam depot download engine — uses JavaSteam's built-in DepotDownloader.
 *
 * Replaces the hand-rolled HTTP approach. DepotDownloader handles:
 *   - manifest request codes (CM connection)
 *   - CDN auth tokens (CM connection)
 *   - depot key requests (CM connection)
 *   - chunk downloading via Ktor CIO HTTP
 *   - AES-ECB decryption + VZip/LZMA decompression
 */
object SteamDepotDownloader {

    private const val TAG = "SteamDepot"

    // -------------------------------------------------------------------------
    // Active download tracking — used by UI to detect stale DL_DOWNLOADING rows
    // -------------------------------------------------------------------------

    private val activeDownloads = ConcurrentHashMap<Int, DownloadControl>()

    /** True if a download for this appId is currently running in this process. */
    @JvmStatic fun isDownloading(appId: Int): Boolean = activeDownloads.containsKey(appId)

    fun getControl(appId: Int): DownloadControl? = activeDownloads[appId]
    fun hasActiveDownloads(): Boolean = activeDownloads.isNotEmpty()

    // -------------------------------------------------------------------------
    // Debug log — written to getExternalFilesDir/steam_debug.txt
    // -------------------------------------------------------------------------

    private var debugLogFile: File? = null
    val debugLogPath: String get() = debugLogFile?.absolutePath ?: "(not initialized)"

    private fun initDebugLog(ctx: Context) {
        try {
            val dir = ctx.getExternalFilesDir(null)
            if (dir != null) {
                debugLogFile = File(dir, "steam_debug.txt")
                BufferedWriter(FileWriter(debugLogFile!!, false)).use { w ->
                    w.write("=== Steam DepotDownloader Debug Log (JavaSteam native) ===\n")
                    w.write("Engine: JavaSteam DepotDownloader (Ktor CIO)\n")
                    w.write("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n\n")
                }
                dlog("Debug log: ${debugLogFile!!.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create debug log: ${e.message}")
        }
    }

    private fun dlog(msg: String) {
        Log.i(TAG, msg)
        debugLogFile ?: return
        try {
            BufferedWriter(FileWriter(debugLogFile!!, true)).use { w ->
                val ts = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
                w.write("[$ts] $msg\n")
            }
        } catch (_: Exception) {}
    }

    private fun dlogError(msg: String, t: Throwable) {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        dlog("$msg: ${t.message}")
        dlog("Stack: $sw")
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returned by installApp() / resumeApp() — provides independent cancel and pause controls. */
    class DownloadControl(val cancel: Runnable, val pause: Runnable)

    /** Java-compatible singleton accessor. */
    @JvmStatic fun getInstance(): SteamDepotDownloader = this

    /**
     * Start a fresh install. Returns a DownloadControl with cancel + pause Runnables.
     * @param threads number of parallel chunk downloads + decompression workers (4 / 8 / 16)
     */
    fun installApp(appId: Int, ctx: Context, threads: Int = 4): DownloadControl =
        buildControl(appId, ctx, threads, isResume = false)

    /**
     * Resume a previously paused install. Keeps the existing DB row (bytes intact).
     * DepotDownloader will re-verify and skip already-written chunks where possible.
     */
    fun resumeApp(appId: Int, ctx: Context, threads: Int = 4): DownloadControl =
        buildControl(appId, ctx, threads, isResume = true)

    @Synchronized
    private fun buildControl(appId: Int, ctx: Context, threads: Int, isResume: Boolean): DownloadControl {
        activeDownloads[appId]?.let { return it }
        val cancelled = AtomicBoolean(false)
        val paused = AtomicBoolean(false)
        // The worker closes the engine. JavaSteam.close() never completes getCompletion().
        val control = DownloadControl(
            cancel = Runnable { cancelled.set(true) },
            pause = Runnable { paused.set(true) },
        )
        activeDownloads[appId] = control
        CoroutineScope(Dispatchers.IO).launch {
            runInstall(appId, ctx.applicationContext, cancelled, paused, threads.coerceIn(1, 16), isResume)
        }
        return control
    }

    /** Cumulative callbacks are per depot and may arrive out of order. */
    internal class Progress(private val floor: Long, initialTotal: Long) {
        private val bytes = mutableMapOf<Int, Long>()
        private val estimates = mutableMapOf<Int, Long>()
        private val estimateSize = initialTotal <= 1L
        @Volatile var done = floor
            private set
        @Volatile var total = maxOf(initialTotal, floor, 1L)
            private set

        @Synchronized
        fun update(depotId: Int, cumulative: Long, fraction: Float): Pair<Long, Long> {
            bytes[depotId] = maxOf(bytes[depotId] ?: 0L, cumulative)
            done = maxOf(floor, bytes.values.sum())
            if (estimateSize && fraction.isFinite() && fraction > 0.05f) {
                val estimate = (bytes.getValue(depotId) / fraction.toDouble()).toLong()
                estimates[depotId] = maxOf(estimates[depotId] ?: 0L, estimate)
            }
            total = maxOf(total, done, estimates.values.sum())
            return done to total
        }
    }

    internal fun awaitDownload(
        completion: CompletableFuture<Void>, cancelled: AtomicBoolean, paused: AtomicBoolean,
        failure: AtomicReference<Throwable?>,
    ) {
        while (!cancelled.get() && !paused.get()) {
            failure.get()?.let { throw it }
            try {
                completion.get(1L, TimeUnit.SECONDS)
                failure.get()?.let { throw it }
                return
            } catch (_: TimeoutException) {
                // JavaSteam can leave this future pending after either failure or close().
            }
        }
    }

    private fun runInstall(
        appId: Int,
        ctx: Context,
        cancelled: AtomicBoolean,
        paused: AtomicBoolean,
        threads: Int,
        isResume: Boolean,
    ) {
        val repo = SteamRepository.getInstance()
        val db = repo.database
        var downloader: DepotDownloader? = null
        var wakeLock: PowerManager.WakeLock? = null
        var wifiLock: WifiManager.WifiLock? = null
        var installDir: File? = null
        var progress = Progress(0L, 1L)
        var failure: Throwable? = null
        val callbackFailure = AtomicReference<Throwable?>(null)
        val completed = AtomicBoolean(false)
        initDebugLog(ctx)
        try {
            SteamCryptoCompat.ensureBcSha1()
            val row = checkNotNull(db.getGame(appId)) { "Game not found in database" }
            val previous = db.getDownload(appId)
            val safeName = row.name.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
                .takeUnless { it.isEmpty() || it == "." || it == ".." } ?: "app_$appId"
            val directory = when {
                previous?.installDir?.isNotBlank() == true -> File(previous.installDir)
                row.installDir.isNotBlank() -> File(row.installDir)
                else -> File(File(ctx.filesDir, "imagefs/steam_games"), safeName)
            }
            installDir = directory
            val depots = db.getDepotManifests(appId).filter {
                it.manifestId != 0L && !(appId == 993090 && it.depotId == 993092)
            }
            val expected = depots.sumOf { it.sizeBytes }.takeIf { it > 0L } ?: row.sizeBytes
            val floor = if (isResume) previous?.bytesDownloaded ?: 0L else 0L
            progress = Progress(floor, expected)
            if (isResume && previous != null) db.markDownloadResuming(appId)
            else db.queueDownload(appId, progress.total, directory.absolutePath)
            if (cancelled.get() || paused.get()) return

            check(repo.ensureLoggedIn(15_000L)) { "Steam session not ready — sign in again or retry" }
            if (cancelled.get() || paused.get()) return
            val client = checkNotNull(repo.steamClient) { "Not connected to Steam" }
            // Match Bannerlator's background-download locks; release on every terminal path.
            try {
                wakeLock = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                    ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Winlator:steam-download")
                wakeLock?.acquire(6L * 60L * 60L * 1000L)
                @Suppress("DEPRECATION")
                val lock = (ctx.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
                    ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Winlator:steam-download")
                wifiLock = lock
                lock?.acquire()
            } catch (e: Exception) { dlog("Download lock unavailable: ${e.message}") }

            val engine = DepotDownloader(
                steamClient = client,
                licenses = repo.getLicenses(),
                debug = false,
                androidEmulation = true,
                maxDownloads = threads,
                maxDecompress = threads,
            )
            downloader = engine
            engine.addListener(object : IDownloadListener {
                override fun onDownloadStarted(item: DownloadItem) {
                    repo.emit("DownloadProgress:$appId:${progress.done}:${progress.total}")
                }

                override fun onStatusUpdate(message: String) { dlog("Status: $message") }

                @Synchronized
                override fun onChunkCompleted(
                    depotId: Int, depotPercentComplete: Float,
                    compressedBytes: Long, uncompressedBytes: Long,
                ) {
                    if (cancelled.get() || paused.get()) return
                    val (done, total) = progress.update(depotId, uncompressedBytes, depotPercentComplete)
                    db.updateDownloadProgress(appId, done, total)
                    repo.emit("DownloadProgress:$appId:$done:$total")
                }

                override fun onDownloadCompleted(item: DownloadItem) { completed.set(true) }
                override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                    callbackFailure.compareAndSet(null, error)
                }
            })
            // Only force a depot list for the upstream Lossless Scaling duplicate fix.
            // Other apps retain JavaSteam's ownership/shared-depot resolution.
            val explicit = if (appId == 993090) depots else emptyList()
            check(appId != 993090 || explicit.any { it.depotId == 993091 }) {
                "Lossless Scaling depot metadata is missing — refresh the Steam library first"
            }
            engine.add(AppItem(
                appId = appId,
                installDirectory = directory.absolutePath,
                branch = "public",
                os = "windows",
                downloadAllArchs = true,
                depot = explicit.map { it.depotId },
                manifest = explicit.map { it.manifestId },
            ))
            engine.finishAdding()
            awaitDownload(engine.getCompletion(), cancelled, paused, callbackFailure)
            if (!cancelled.get() && !paused.get()) {
                check(completed.get()) { "Steam download ended without completion" }
            }
        } catch (e: Exception) {
            failure = e.cause ?: e
            if (e is InterruptedException) Thread.currentThread().interrupt()
            if (!cancelled.get() && !paused.get()) dlogError("Download failed", failure)
        } finally {
            try { downloader?.close() } catch (e: Exception) { dlog("Close: ${e.message}") }
            try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
            try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
            // Teardown precedes terminal events so reopening/retrying cannot reuse a dead handle.
            synchronized(this) {
                activeDownloads.remove(appId)
                when {
                    cancelled.get() -> {
                        // Preserve partial files for a retry; deleting while engine writes can lose data.
                        db.deleteDownload(appId)
                        repo.emit("DownloadCancelled:$appId")
                    }
                    paused.get() -> {
                        db.markDownloadPaused(appId, progress.done)
                        repo.emit("DownloadPaused:$appId")
                    }
                    failure != null -> emitFailed(appId, failure.message ?: failure.javaClass.simpleName)
                    completed.get() -> {
                        db.markInstalled(appId, installDir!!.absolutePath, progress.total)
                        db.deleteDownload(appId)
                        repo.emit("DownloadProgress:$appId:${progress.total}:${progress.total}")
                        repo.emit("DownloadComplete:$appId")
                    }
                    else -> emitFailed(appId, "Steam download ended without completion")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun emitFailed(appId: Int, reason: String) {
        SteamRepository.getInstance().database.markDownloadFailed(appId, reason)
        SteamRepository.getInstance().emit("DownloadFailed:$appId:$reason")
        Log.e(TAG, "DownloadFailed $appId: $reason")
    }

}
