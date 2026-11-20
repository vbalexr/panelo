// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.recovery

import android.os.SystemClock
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** autoReloadMin (periodic reload) and idleTimeoutSec (back to start URL after no touch). */
class Watchdog(
    private val scope: CoroutineScope,
    private val onReload: () -> Unit,
    private val onIdle: () -> Unit,
) {
    private var reloadJob: Job? = null
    private var idleJob: Job? = null

    @Volatile
    private var lastTouchMs = SystemClock.elapsedRealtime()

    fun touch() {
        lastTouchMs = SystemClock.elapsedRealtime()
    }

    fun apply(config: KioskConfig) {
        reloadJob?.cancel()
        idleJob?.cancel()
        reloadJob = null
        idleJob = null
        if (config.autoReloadMin > 0) {
            val periodMs = config.autoReloadMin * 60_000L
            reloadJob = scope.launch {
                while (true) {
                    delay(periodMs)
                    Log.i("watchdog", "auto_reload")
                    onReload()
                }
            }
        }
        if (config.idleTimeoutSec > 0) {
            val timeoutMs = config.idleTimeoutSec * 1000L
            touch()
            idleJob = scope.launch {
                while (true) {
                    val remaining = lastTouchMs + timeoutMs - SystemClock.elapsedRealtime()
                    if (remaining > 0) {
                        delay(remaining)
                        continue
                    }
                    Log.i("watchdog", "idle_timeout")
                    touch()
                    onIdle()
                }
            }
        }
    }
}
