// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import android.os.PowerManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.vbalex.android.panelo.admin.DeviceAdmin
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Screen off: soft (black overlay + brightness 0, camera keeps watching) or real (DevicePolicyManager.lockNow()).
 * Triggers: no presence/touch for presence.noPresenceTimeoutSec, the daily schedule, or the panel test button.
 * The schedule wins over presence: inside the off window motion does not wake; a touch wakes for
 * [TEMP_WAKE_MS] and then the screen goes off again.
 */
class ScreenOff(
    private val activity: ComponentActivity,
    private val overlay: View,
    private val dimmer: Dimmer,
    private val presence: Presence,
) {
    enum class Reason { NONE, PRESENCE, SCHEDULE, TEST }

    companion object {
        const val TEMP_WAKE_MS = 2 * 60_000L
        const val TEST_MS = 10_000L
        private const val TICK_MS = 1_000L
        private const val REAL_SETTLE_MS = 3_000L

        private val _soft = MutableStateFlow(false)

        /** True while the soft black overlay is up (snapshot capture is paused meanwhile). */
        val softOff: StateFlow<Boolean> = _soft.asStateFlow()

        /** The activity's controller while it exists (the schedule receiver uses it to avoid double work). */
        @Volatile
        var live: ScreenOff? = null
            private set

        /** Real mode needs Device Admin / Device Owner; otherwise it degrades to soft. */
        fun effectiveMode(configured: String, adminActive: Boolean): String =
            if (configured == KioskConfig.SCREEN_OFF_REAL && adminActive) KioskConfig.SCREEN_OFF_REAL else KioskConfig.SCREEN_OFF_SOFT
    }

    private val _notice = MutableStateFlow<String?>(null)

    /** Panel status line (e.g. real mode fell back to soft). */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private var reason = Reason.NONE
    private var realLocked = false
    private var swallowing = false
    private var offSinceMs = 0L
    private var tempWakeUntilMs = 0L
    private var testUntilMs = 0L
    private var lastTouchMs = SystemClock.elapsedRealtime()
    private var ticker: Job? = null
    private var events: Job? = null

    val isOff: Boolean get() = reason != Reason.NONE

    fun start() {
        live = this
        if (realLocked) wakeFromReal()
        ticker?.cancel()
        ticker = activity.lifecycleScope.launch {
            while (true) {
                tick()
                delay(TICK_MS)
            }
        }
        events?.cancel()
        events = activity.lifecycleScope.launch { Schedule.events.collect { tick() } }
    }

    fun stop() {
        ticker?.cancel()
        events?.cancel()
        if (live === this) live = null
    }

    /** Config change or alarm intent: re-evaluate right away. */
    fun evaluate() = tick()

    /** Power-button / alarm wake from a real lock: the screen is back, restart the idle clock. */
    private fun wakeFromReal() {
        Log.i("screen_off", "woke_from_real")
        realLocked = false
        reason = Reason.NONE
        lastTouchMs = SystemClock.elapsedRealtime()
        if (Schedule.isOff(ConfigStore.state.value)) tempWakeUntilMs = lastTouchMs + TEMP_WAKE_MS
    }

    /** Test button: soft/real off now; ends after [TEST_MS] or on touch. */
    fun test() {
        testUntilMs = SystemClock.elapsedRealtime() + TEST_MS
        goOff(Reason.TEST)
    }

    /** Called for every touch. Returns true when the event must not reach the page (it only woke the screen). */
    fun onTouch(ev: MotionEvent): Boolean {
        lastTouchMs = SystemClock.elapsedRealtime()
        if (swallowing) {
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) swallowing = false
            return true
        }
        if (reason == Reason.NONE) return false
        if (ev.actionMasked != MotionEvent.ACTION_DOWN) return true
        swallowing = true
        if (reason == Reason.SCHEDULE) tempWakeUntilMs = lastTouchMs + TEMP_WAKE_MS
        Log.i("screen_off", "wake_touch", "reason" to reason.name)
        wake()
        return true
    }

    private fun tick() {
        val config = ConfigStore.state.value
        val now = SystemClock.elapsedRealtime()
        if (realLocked) {
            // The lock normally stops the activity; if it never did and the screen is interactive again, resume.
            val interactive = activity.getSystemService(PowerManager::class.java).isInteractive
            if (interactive && now - offSinceMs > REAL_SETTLE_MS) wakeFromReal()
            return
        }
        val inWindow = Schedule.isOff(config)
        when {
            reason == Reason.TEST && now >= testUntilMs -> wake()
            reason == Reason.SCHEDULE && !inWindow -> wake()
            reason == Reason.TEST -> Unit
            inWindow && reason != Reason.SCHEDULE && now >= tempWakeUntilMs -> goOff(Reason.SCHEDULE)
            reason == Reason.PRESENCE && presence.lastMotionElapsed > offSinceMs -> wake()
            reason == Reason.NONE && presenceIdle(config, now) -> goOff(Reason.PRESENCE)
        }
    }

    /** Idle only counts when the camera is really watching (otherwise the screen would go dark unmonitored). */
    private fun presenceIdle(config: KioskConfig, now: Long): Boolean {
        if (!config.presence.enabled || presence.status.value != Presence.Status.ACTIVE) return false
        val lastActivity = maxOf(lastTouchMs, presence.lastMotionElapsed)
        return now - lastActivity >= config.presence.noPresenceTimeoutSec * 1000L
    }

    private fun goOff(next: Reason) {
        if (reason == next) return
        val config = ConfigStore.state.value
        val mode = effectiveMode(config.screenOffMode, DeviceAdmin.isActive(activity))
        _notice.value = if (config.screenOffMode != mode) "Real screen-off needs Device Admin; using soft off" else null
        reason = next
        offSinceMs = SystemClock.elapsedRealtime()
        Log.i("screen_off", "off", "reason" to next.name, "mode" to mode)
        if (mode == KioskConfig.SCREEN_OFF_REAL) {
            val locked = DeviceAdmin.lockNow(activity)
            if (locked.isSuccess) {
                realLocked = true
                return
            }
        }
        _soft.value = true
        overlay.visibility = View.VISIBLE
        dimmer.hold(true)
    }

    private fun wake() {
        Log.i("screen_off", "on", "reason" to reason.name)
        reason = Reason.NONE
        lastTouchMs = SystemClock.elapsedRealtime()
        _soft.value = false
        overlay.visibility = View.GONE
        dimmer.hold(false)
    }
}
