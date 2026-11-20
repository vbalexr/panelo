// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Process
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.remote.RemoteState
import kotlin.system.exitProcess

class KioskApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.init(this)
        ConfigStore.init(this)
        RemoteState.init(this)
        Log.i("app", "start", "version" to BuildConfig.VERSION_NAME)
        Crash.install(this)
    }
}

/**
 * Uncaught exception: log, schedule an activity restart, kill the process.
 * Crash-loop guard: 3+ crashes within 2 minutes escalate the restart delay (30 s, 2 min, 5 min,
 * 10 min cap); the escalation resets once a process survives 10 minutes before crashing.
 */
object Crash {
    private const val PREFS = "crash_guard"
    private const val NORMAL_DELAY_MS = 2_000L
    private const val WINDOW_MS = 2 * 60_000L
    private const val LOOP_COUNT = 3
    private const val STABLE_UPTIME_MS = 10 * 60_000L
    private val ESCALATED_DELAYS_MS = longArrayOf(30_000L, 2 * 60_000L, 5 * 60_000L, 10 * 60_000L)

    private val processStartMs = System.currentTimeMillis()

    fun install(context: Context) {
        val app = context.applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val delayMs = recordAndDelay(app, System.currentTimeMillis())
            Log.e("app", "uncaught_exception", "thread" to thread.name, "restartDelayMs" to delayMs, "error" to error.stackTraceToString())
            Log.flush()
            // A failure here must never leave the process half-dead: always kill it.
            runCatching { scheduleRestart(app, delayMs) }
                .onFailure { Log.e("app", "restart_schedule_failed", "error" to it.toString()) }
            Log.flush()
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    /** Records this crash and returns the delay before the restart. */
    private fun recordAndDelay(context: Context, now: Long): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var level = prefs.getInt("level", 0)
        var recent = prefs.getString("recent", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        if (now - processStartMs >= STABLE_UPTIME_MS) {
            level = 0
            recent = emptyList()
        }
        recent = recent.filter { now - it < WINDOW_MS } + now
        if (level > 0 || recent.size >= LOOP_COUNT) level = minOf(level + 1, ESCALATED_DELAYS_MS.size)
        prefs.edit().putInt("level", level).putString("recent", recent.joinToString(",")).commit()
        if (level == 0) return NORMAL_DELAY_MS
        Log.e("app", "crash_loop", "level" to level, "crashesInWindow" to recent.size)
        return ESCALATED_DELAYS_MS[level - 1]
    }

    /**
     * Android 10+ blocks background activity starts (AlarmManager included, even for the Home app), so the
     * restart is handed to [RestartActivity] in a separate process while our window is still visible.
     * The alarm stays as a fallback for crashes that happen while the app is not in the foreground.
     */
    private fun scheduleRestart(context: Context, delayMs: Long) {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT,
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.set(AlarmManager.RTC, System.currentTimeMillis() + delayMs, pending)
        context.startActivity(
            Intent(context, RestartActivity::class.java)
                .putExtra(RestartActivity.EXTRA_DELAY_MS, delayMs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
    }
}
