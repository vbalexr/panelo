// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.recovery

import java.net.HttpURLConnection
import java.net.URL

/** Lightweight reachability probe so recovery never has to reload the WebView just to test the network. */
object Probe {
    private const val TIMEOUT_MS = 4_000

    /**
     * True when [url] answers with a status that is not in [errorCodes]. HEAD first; GET with
     * "Range: bytes=0-0" when HEAD is rejected. Blocking: call off the main thread.
     */
    fun reachable(url: String, errorCodes: Set<Int>): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return true
        val head = request(url, "HEAD") ?: return false
        if (head != 405 && head != 501 && head !in errorCodes) return true
        val get = request(url, "GET") ?: return false
        return get !in errorCodes
    }

    private fun request(url: String, method: String): Int? {
        val connection = runCatching { URL(url).openConnection() as HttpURLConnection }.getOrNull() ?: return null
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            if (method == "GET") connection.setRequestProperty("Range", "bytes=0-0")
            val code = connection.responseCode
            if (method == "GET") runCatching { connection.inputStream.use { it.read(ByteArray(1024)) } }
            code
        } catch (e: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
