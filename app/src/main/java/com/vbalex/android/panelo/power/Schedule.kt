// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import com.vbalex.android.panelo.MainActivity
import com.vbalex.android.panelo.admin.DeviceAdmin
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Daily screen off/on alarms. Uses [AlarmManager.setAlarmClock] when [AlarmManager.canScheduleExactAlarms]
 * (USE_EXACT_ALARM on API 33+, SCHEDULE_EXACT_ALARM up to API 32): exact and fires in Doze. Side effect: some launchers show an "alarm" icon in the status bar.
 * When exact alarms are denied it falls back to the inexact
 * setAndAllowWhileIdle (may be late by minutes in Doze); a live activity also checks the schedule every second.
 * Exactly one alarm is armed at a time (the next transition); it is re-armed after firing, on config
 * change, after boot, and when the clock or time zone changes.
 */
object Schedule {
    const val ACTION_FIRE = "com.vbalex.android.panelo.SCHEDULE_FIRE"
    const val EXTRA_AT = "at"
    const val EXTRA_EVAL = "power_eval"
    private const val WAKE_LOCK_MS = 5_000L
    private const val SLACK_MS = 1_000L

    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emitted when an alarm fired; the live [ScreenOff] re-evaluates the schedule. */
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun isOff(config: KioskConfig, nowMs: Long = System.currentTimeMillis()): Boolean = ScheduleWindow.isOff(config.schedule, nowMs)

    /** Arms the next transition (or cancels the alarm when the schedule is disabled). */
    fun arm(context: Context, config: KioskConfig = ConfigStore.state.value) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val operation = fireIntent(context, 0L)
        val next = ScheduleWindow.next(config.schedule, System.currentTimeMillis())
        if (next == null) {
            alarms.cancel(operation)
            Log.d("schedule", "disarmed")
            return
        }
        val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val operationAt = fireIntent(context, next.atMs)
        val armed = runCatching {
            if (!canScheduleExact(alarms)) throw SecurityException("exact alarms not allowed")
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(next.atMs, show), operationAt)
        }
            .recoverCatching { error ->
                // Android 14+ may refuse exact alarms without the (user-granted) exact-alarm special access.
                if (error !is SecurityException) throw error
                Log.w("schedule", "exact_denied_using_inexact")
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.atMs, operationAt)
            }
        armed.onSuccess { Log.i("schedule", "armed", "atMs" to next.atMs, "turnOff" to next.turnOff) }
        armed.onFailure { Log.e("schedule", "arm_failed", "error" to it.toString()) }
    }

    private fun canScheduleExact(alarms: AlarmManager): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Alarm fired. Real-off locks directly when no activity is alive; everything else goes through [ScreenOff]. */
    fun fire(context: Context, atMs: Long) {
        val config = ConfigStore.state.value
        val off = isOff(config, maxOf(atMs, System.currentTimeMillis()) + SLACK_MS)
        val real = ScreenOff.effectiveMode(config.screenOffMode, DeviceAdmin.isActive(context)) == KioskConfig.SCREEN_OFF_REAL
        Log.i("schedule", "fired", "off" to off, "real" to real, "live" to (ScreenOff.live != null))
        _events.tryEmit(Unit)
        if (off && real) {
            if (ScreenOff.live == null) DeviceAdmin.lockNow(context)
            return
        }
        if (off && !context.getSystemService(PowerManager::class.java).isInteractive) return
        if (!off) wake(context)
        val start = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_EVAL, true)
        runCatching { context.startActivity(start) }
            .onFailure { Log.w("schedule", "activity_start_failed", "error" to it.toString()) }
    }

    /** Turns the screen on: a short full wake lock that acquires a wakeup (deprecated but still functional). */
    @Suppress("DEPRECATION")
    private fun wake(context: Context) {
        val flags = PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE
        val lock = context.getSystemService(PowerManager::class.java).newWakeLock(flags, "panelo:schedule")
        runCatching { lock.acquire(WAKE_LOCK_MS) }
            .onFailure { Log.w("schedule", "wake_lock_failed", "error" to it.toString()) }
    }

    private fun fireIntent(context: Context, atMs: Long): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_AT, atMs)
        return PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
