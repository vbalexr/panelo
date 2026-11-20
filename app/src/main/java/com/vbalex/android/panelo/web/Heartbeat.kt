// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** JS ping every N seconds; no answer within the timeout means the renderer is hung. */
class Heartbeat(
    private val scope: CoroutineScope,
    private val host: WebHost,
    private val isLive: () -> Boolean,
    private val onTimeout: () -> Unit,
) {
    private var job: Job? = null

    fun start(config: KioskConfig) {
        stop()
        if (config.heartbeatIntervalSec <= 0) return
        val intervalMs = config.heartbeatIntervalSec * 1000L
        val timeoutMs = config.heartbeatTimeoutSec * 1000L
        job = scope.launch {
            while (true) {
                delay(intervalMs)
                if (!isLive()) continue
                if (ping(timeoutMs)) continue
                Log.e("heartbeat", "timeout", "timeoutMs" to timeoutMs)
                onTimeout()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun ping(timeoutMs: Long): Boolean {
        val view = host.webView ?: return true
        val answered = CompletableDeferred<Unit>()
        view.evaluateJavascript("1") { answered.complete(Unit) }
        return withTimeoutOrNull(timeoutMs) { answered.await() } != null
    }
}
