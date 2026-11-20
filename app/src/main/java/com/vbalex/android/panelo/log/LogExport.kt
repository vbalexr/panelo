// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.log

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares the rolling log files through FileProvider. */
object LogExport {
    /** Blocking IO (<= ~1 MB): call off the main thread. */
    fun intent(context: Context): Result<Intent> = runCatching {
        Log.flush()
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val out = File(dir, "kiosk-logs.txt")
        out.outputStream().use { sink -> Log.files().forEach { file -> file.inputStream().use { it.copyTo(sink) } } }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Panelo logs")
            clipData = ClipData.newRawUri("logs", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
