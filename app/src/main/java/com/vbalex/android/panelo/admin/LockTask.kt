// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.admin

import android.app.Activity
import android.app.ActivityManager
import android.os.SystemClock
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log

/**
 * Lockdown levels (spec section 5). Level 1: Home + immersive only. Level 2: screen pinning (system prompt).
 * Level 3: Device Owner lock task (no prompt). Level 3 without Device Owner falls back to level 1.
 * State is in memory on purpose: a new process (boot, crash restart) re-applies the configured level.
 */
object LockTask {
    private const val DECLINE_BACKOFF_MS = 10 * 60_000L
    private const val PROMPT_SETTLE_MS = 1_500L

    private var suspended = false
    private var startedByUs = false
    private var promptAtMs = 0L
    private var declinedUntilMs = 0L
    private var lastLevel = 0
    private var appliedKey: Pair<Boolean, Boolean>? = null
    private var ownerReleased = false

    /** ActivityManager.LOCK_TASK_MODE_NONE / PINNED / LOCKED. */
    fun state(activity: Activity): Int = activity.getSystemService(ActivityManager::class.java).lockTaskModeState

    fun describe(state: Int): String = when (state) {
        ActivityManager.LOCK_TASK_MODE_PINNED -> "pinned (screen pinning)"
        ActivityManager.LOCK_TASK_MODE_LOCKED -> "locked (lock task)"
        else -> "not locked"
    }

    fun isSuspended(): Boolean = suspended

    /** The level that is really in force: 3 needs Device Owner, otherwise it falls back to 1. */
    fun effectiveLevel(activity: Activity, config: KioskConfig): Int {
        if (config.lockdownLevel == 3 && !DeviceOwner.isOwner(activity)) return 1
        return config.lockdownLevel
    }

    /** Brings the device in line with the configured level. Call on resume / config change / focus regain. */
    fun sync(activity: Activity, config: KioskConfig): Result<Int> {
        val level = effectiveLevel(activity, config)
        if (level != lastLevel) {
            Log.i("lock_task", "level", "configured" to config.lockdownLevel, "effective" to level)
            lastLevel = level
            declinedUntilMs = 0
            promptAtMs = 0
        }
        if (suspended) return Result.success(level)
        return when (level) {
            3 -> enterOwner(activity, config).map { level }
            2 -> { enterPinned(activity); Result.success(level) }
            else -> leave(activity).map { level }
        }
    }

    /** Admin "Exit kiosk temporarily": stops lock task and (Device Owner) lifts the Home preference until the next app start. */
    fun suspend(activity: Activity): Result<Unit> {
        Log.i("lock_task", "suspend")
        suspended = true
        stop(activity)
        startedByUs = false
        if (!DeviceOwner.isOwner(activity)) return Result.success(Unit)
        appliedKey = null
        return DeviceOwner.release(activity, activity)
    }

    /** Ends a [suspend] (new app start or Home press); the next [sync] re-applies the level. */
    fun resume() {
        if (!suspended) return
        Log.i("lock_task", "resume")
        suspended = false
    }

    /** Forget a declined pinning prompt so [sync] asks again. */
    fun retry() {
        declinedUntilMs = 0
        promptAtMs = 0
    }

    /** stopLockTask() when something is locked; safe to call at any time. */
    fun stop(activity: Activity) {
        if (state(activity) == ActivityManager.LOCK_TASK_MODE_NONE) return
        runCatching { activity.stopLockTask() }
            .onSuccess { Log.i("lock_task", "stopped") }
            .onFailure { Log.w("lock_task", "stop_failed", "error" to it.toString()) }
    }

    private fun enterOwner(activity: Activity, config: KioskConfig): Result<Unit> {
        ownerReleased = false
        val key = config.allowPowerMenu to config.keepScreenOn
        if (appliedKey != key) {
            val applied = DeviceOwner.apply(activity, config)
            appliedKey = key
            if (applied.isFailure) Log.w("lock_task", "apply_incomplete", "error" to applied.exceptionOrNull()?.message)
        }
        if (state(activity) != ActivityManager.LOCK_TASK_MODE_NONE) return Result.success(Unit)
        val result = runCatching { activity.startLockTask() }
        result.onSuccess { startedByUs = true; Log.i("lock_task", "started", "level" to 3) }
            .onFailure { Log.e("lock_task", "start_failed", "level" to 3, "error" to it.toString()) }
        return result
    }

    private fun enterPinned(activity: Activity) {
        if (state(activity) != ActivityManager.LOCK_TASK_MODE_NONE) {
            promptAtMs = 0
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (promptAtMs != 0L) {
            // Focus came back and we are still not pinned: the user declined (or pinning is disabled in Settings).
            if (now - promptAtMs < PROMPT_SETTLE_MS) return
            promptAtMs = 0
            declinedUntilMs = now + DECLINE_BACKOFF_MS
            Log.w("lock_task", "pinning_declined", "retryInMin" to DECLINE_BACKOFF_MS / 60_000)
            return
        }
        if (now < declinedUntilMs) return
        promptAtMs = now
        runCatching { activity.startLockTask() }
            .onSuccess { startedByUs = true; Log.i("lock_task", "pin_requested", "level" to 2) }
            .onFailure { Log.e("lock_task", "start_failed", "level" to 2, "error" to it.toString()) }
    }

    /** Level 1: undo anything we started, and undo stale Device Owner policies once per process. */
    private fun leave(activity: Activity): Result<Unit> {
        if (startedByUs) {
            stop(activity)
            startedByUs = false
        }
        if (ownerReleased || !DeviceOwner.isOwner(activity)) return Result.success(Unit)
        ownerReleased = true
        appliedKey = null
        return DeviceOwner.release(activity, activity)
    }
}
