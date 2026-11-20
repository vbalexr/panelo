// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import android.content.Context
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Foreground polling while the activity is started: pull on start, then every remote.pollIntervalMin (+-10 % jitter);
 * restarts (with an immediate pull) when enabled / URL / interval change; also pulls when the network comes back after
 * a failed pull. The WorkManager job ([RemoteWorker]) covers the time the activity is not started.
 */
class RemotePoller(private val context: Context, private val scope: CoroutineScope) {
    private data class Source(val enabled: Boolean, val url: String, val intervalMin: Int)

    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            ConfigStore.state.map { Source(it.remote.enabled, it.remote.url, it.remote.pollIntervalMin) }
                .distinctUntilChanged()
                .collect { source -> restart(source) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        loop?.cancel()
        loop = null
    }

    fun onNetworkAvailable() {
        if (RemoteState.status.value.outcome != RemoteState.Outcome.FAILED) return
        if (!ConfigStore.state.value.remote.enabled) return
        Log.i("remote", "retry_on_network")
        scope.launch { RemoteConfig.pull(context) }
    }

    private var loop: Job? = null

    private fun restart(source: Source) {
        loop?.cancel()
        if (!source.enabled || source.url.isEmpty()) {
            RemoteWorker.cancel(context)
            return
        }
        RemoteWorker.schedule(context, source.intervalMin)
        loop = scope.launch {
            while (true) {
                RemoteConfig.pull(context)
                delay(delayMs(source.intervalMin))
            }
        }
    }

    private fun delayMs(intervalMin: Int): Long {
        val base = intervalMin * 60_000L
        return (base * Random.nextDouble(0.9, 1.1)).toLong()
    }
}
