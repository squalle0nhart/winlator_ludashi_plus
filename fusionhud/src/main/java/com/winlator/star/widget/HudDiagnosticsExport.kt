/*
 * FusionHUD — a lightweight in-game performance HUD for Android / Winlator overlays.
 * Copyright (C) The412Banner  <https://github.com/The412Banner>
 *
 * Licensed under the GNU General Public License v3.0 (see LICENSE), with an
 * ADDITIONAL attribution requirement under GPL-3.0 §7(b) (see ATTRIBUTION.md):
 * any use, fork, or distribution must preserve credit to "The412Banner" and a
 * link to https://github.com/The412Banner/FusionHUD in the project documentation
 * AND in the in-app credits/about screen.
 *
 * Decoupled from Bannerlator for standalone use: the report writes to an app-specific
 * dir by default (no runtime permission) and only builds a share sheet when a
 * FileProvider authority is supplied. The report body comes from the shared
 * HudMetrics.buildDiagnosticsReport, which stays byte-identical to upstream.
 */

package com.winlator.star.widget

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-tap "Export HUD diagnostics" action.
 *
 * Runs [HudMetrics.buildDiagnosticsReport] on a background thread (it primes + samples the sysfs
 * readers, so it must never touch the main thread), writes the plain-text report to [targetDir], runs
 * the MediaScanner over it, and toasts the saved path. When [fileProviderAuthority] is non-null it also
 * fires an `ACTION_SEND` share sheet via [FileProvider] so the user can send the report back to add
 * their device.
 *
 * Standalone-friendly defaults:
 * - [targetDir] defaults to [Context.getExternalFilesDir] (the app-specific external dir) — writable
 *   with **no runtime permission** and no `MANAGE_EXTERNAL_STORAGE`, so it works in the demo out of the box.
 * - [fileProviderAuthority] defaults to `null` → no share sheet (nothing to configure). Pass an
 *   authority (e.g. Bannerlator's `"${applicationId}.tileprovider"`, pointed at a public Downloads dir)
 *   to enable sharing.
 *
 * This is a manual, invoked-only action — nothing here runs on the HUD refresh path, so there is no
 * gameplay impact. Failures are caught and reported via Toast.
 */
fun exportHudDiagnostics(
    context: Context,
    targetDir: File? = context.getExternalFilesDir(null),
    fileProviderAuthority: String? = null,
) {
    Toast.makeText(context, "Collecting HUD diagnostics…", Toast.LENGTH_SHORT).show()
    val main = Handler(Looper.getMainLooper())
    Thread {
        val result = runCatching {
            val report = HudMetrics(context.applicationContext).buildDiagnosticsReport(context.applicationContext)
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val dir = targetDir ?: context.filesDir
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, "fusionhud-diag-$ts.txt")
            out.writeText(report)
            out.setReadable(true, false)
            MediaScannerConnection.scanFile(context, arrayOf(out.absolutePath), null, null)
            out
        }
        main.post {
            result.onSuccess { out ->
                Toast.makeText(context, "Saved ${out.absolutePath}", Toast.LENGTH_LONG).show()
                if (fileProviderAuthority != null) {
                    runCatching {
                        val uri = FileProvider.getUriForFile(context, fileProviderAuthority, out)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            putExtra(Intent.EXTRA_SUBJECT, "FusionHUD diagnostics")
                            putExtra(Intent.EXTRA_TEXT, "FusionHUD sensor diagnostics report")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(
                            Intent.createChooser(send, "Share HUD diagnostics")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }.onFailure {
                Toast.makeText(context, "Couldn't export diagnostics.", Toast.LENGTH_SHORT).show()
            }
        }
    }.start()
}
