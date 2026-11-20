// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.log.Log

/** Optional downloads (config.downloadsEnabled, default off) via DownloadManager. */
object Downloads {
    fun enqueue(context: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (!ConfigStore.state.value.downloadsEnabled) {
            Log.w("web", "download_blocked", "url" to url)
            return
        }
        if (!WebPolicy.allows(url)) {
            Log.w("web", "download_blocked_host", "url" to url)
            return
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setMimeType(mimeType)
            .addRequestHeader("User-Agent", userAgent)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                URLUtil.guessFileName(url, contentDisposition, mimeType),
            )
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.enqueue(request)
        Log.i("web", "download_enqueued", "url" to url)
    }
}
