// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.recovery

import android.app.Activity
import android.content.res.Configuration
import android.webkit.WebSettings
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.power.ScreenOff
import com.vbalex.android.panelo.web.WebEvents
import com.vbalex.android.panelo.web.WebHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Error recovery state machine.
 *
 * Backoff retries probe the server with a lightweight HTTP request and reload only on success.
 * Failure ladder: network attempt fails -> tier 1 (LOAD_CACHE_ELSE_NETWORK) -> tier 2 (snapshot or
 * "can't reach" screen). While not Live, retries run with exponential backoff (5 s .. 5 min) and
 * immediately when the network becomes available. A successful network load returns to Live.
 */
class Recovery(
    private val activity: Activity,
    private val host: WebHost,
    private val snapshot: Snapshot,
    private val entry: () -> String,
) : WebEvents {

    sealed interface State {
        data object Loading : State
        data object Live : State
        data object RetryingFromCache : State
        class ShowingSnapshot(val shot: Snapshot.Shot) : State
        data object NoSnapshot : State
    }

    private enum class Phase { NETWORK, CACHE }

    private companion object {
        const val BACKOFF_MIN_MS = 5_000L
        const val BACKOFF_MAX_MS = 300_000L
        const val SNAPSHOT_DELAY_MS = 2_000L
    }

    private val scope = CoroutineScope(Dispatchers.Main)
    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    private var phase = Phase.NETWORK
    private var failed = false
    private var currentUrl: String? = null
    private var backoffMs = BACKOFF_MIN_MS
    private var retryJob: Job? = null
    private var snapshotJob: Job? = null

    val isLive: Boolean get() = _state.value is State.Live

    /** Load [url] from scratch (initial load, config URL change, idle -> home). */
    fun start(url: String) {
        Log.i("recovery", "start", "url" to url)
        cancelRetry()
        backoffMs = BACKOFF_MIN_MS
        _state.value = State.Loading
        attempt(Phase.NETWORK, url)
    }

    /** Periodic reload: only when healthy; otherwise retry now. */
    fun reload() {
        if (!isLive) {
            retryNow()
            return
        }
        host.reload()
    }

    /** User/network-triggered immediate retry (Retry button, onAvailable). */
    fun retryNow() {
        if (_state.value is State.Live || _state.value is State.Loading) return
        Log.i("recovery", "retry_now")
        cancelRetry()
        attempt(Phase.NETWORK, currentUrl ?: entry())
    }

    fun onNetworkAvailable() {
        Log.i("recovery", "network_available", "state" to _state.value::class.simpleName)
        retryNow()
    }

    /** Snapshot setting may have changed. */
    fun applyConfig() {
        if (!ConfigStore.state.value.snapshotEnabled) {
            snapshotJob?.cancel()
            snapshot.clear()
            return
        }
        if (isLive) scheduleSnapshots()
    }

    /** Destroy and recreate the WebView, then reload (renderer gone, heartbeat timeout). */
    fun recreate(reason: String) {
        Log.e("recovery", "recreate_webview", "reason" to reason)
        scope.launch {
            cancelRetry()
            snapshotJob?.cancel()
            host.recreate()
            backoffMs = BACKOFF_MIN_MS
            _state.value = State.Loading
            attempt(Phase.NETWORK, currentUrl ?: entry())
        }
    }

    // --- WebEvents ---

    override fun onPageStarted(url: String) {
        failed = false
        currentUrl = url
    }

    override fun onPageFinished(url: String) {
        if (failed) return
        succeeded()
    }

    override fun onPageCommitVisible() = Unit

    override fun onMainFrameError(url: String, code: Int, description: String) {
        Log.w("recovery", "main_frame_error", "url" to url, "code" to code, "description" to description)
        fail(url)
    }

    override fun onHttpError(url: String, status: Int) {
        Log.w("recovery", "http_error", "url" to url, "status" to status)
        fail(url)
    }

    override fun onSslError(url: String) {
        Log.w("recovery", "ssl_error", "url" to url)
        fail(url)
    }

    override fun onRenderGone(didCrash: Boolean) {
        recreate(if (didCrash) "renderer_crashed" else "renderer_killed")
    }

    // --- internals ---

    private fun attempt(phase: Phase, url: String) {
        this.phase = phase
        failed = false
        currentUrl = url
        host.setCacheMode(if (phase == Phase.CACHE) WebSettings.LOAD_CACHE_ELSE_NETWORK else WebSettings.LOAD_DEFAULT)
        host.load(url)
    }

    private fun fail(url: String) {
        failed = true
        currentUrl = url
        // Post: never start a new load from inside a WebViewClient callback.
        scope.launch {
            if (phase == Phase.NETWORK) beginCache(url) else showFallback()
        }
    }

    private fun beginCache(url: String) {
        val current = _state.value
        if (current is State.Loading || current is State.Live) _state.value = State.RetryingFromCache
        scheduleRetry()
        attempt(Phase.CACHE, url)
    }

    private fun succeeded() {
        if (phase == Phase.CACHE) {
            _state.value = State.RetryingFromCache
            scheduleRetry()
            return
        }
        cancelRetry()
        backoffMs = BACKOFF_MIN_MS
        host.setCacheMode(WebSettings.LOAD_DEFAULT)
        _state.value = State.Live
        Log.i("recovery", "live")
        scheduleSnapshots()
    }

    private fun showFallback() {
        scheduleRetry()
        scope.launch {
            if (!ConfigStore.state.value.snapshotEnabled) {
                _state.value = State.NoSnapshot
                return@launch
            }
            val landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val shot = withContext(Dispatchers.IO) { snapshot.load(landscape) }.getOrNull()
            if (_state.value is State.Live || _state.value is State.Loading) return@launch
            _state.value = if (shot == null) State.NoSnapshot else State.ShowingSnapshot(shot)
            Log.w("recovery", "fallback", "snapshot" to (shot != null))
        }
    }

    /**
     * Backoff loop. Each tick only probes reachability off the main thread (the page being shown,
     * cached or snapshot, is never disturbed); the WebView reloads only after a probe succeeds.
     */
    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        retryJob = scope.launch {
            while (true) {
                val wait = backoffMs
                backoffMs = minOf(backoffMs * 2, BACKOFF_MAX_MS)
                delay(wait)
                val target = entry()
                val errorCodes = ConfigStore.state.value.errorHttpCodes.toSet()
                val up = withContext(Dispatchers.IO) { Probe.reachable(target, errorCodes) }
                Log.i("recovery", "backoff_probe", "waitedMs" to wait, "reachable" to up)
                if (!up) continue
                retryJob = null
                attempt(Phase.NETWORK, target)
                return@launch
            }
        }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
    }

    private fun scheduleSnapshots() {
        snapshotJob?.cancel()
        val config = ConfigStore.state.value
        if (!config.snapshotEnabled) return
        val intervalMs = config.snapshotIntervalMin * 60_000L
        snapshotJob = scope.launch {
            delay(SNAPSHOT_DELAY_MS)
            while (true) {
                captureNow()
                delay(intervalMs)
            }
        }
    }

    private suspend fun captureNow() {
        if (!isLive || ScreenOff.softOff.value) return
        val view = host.webView ?: return
        snapshot.capture(activity.window, view)
    }
}
