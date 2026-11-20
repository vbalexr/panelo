// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.content.Context
import android.content.SharedPreferences
import com.vbalex.android.panelo.log.Log

/** Persisted PIN brute-force protection: 5 failures lock entry for 60 s (survives restarts). */
object PinLockout {
    const val MAX_FAILURES = 5
    const val LOCK_MS = 60_000L

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("pin_lockout", Context.MODE_PRIVATE)
    }

    fun remainingMs(): Long {
        val until = prefs.getLong("until", 0)
        val now = System.currentTimeMillis()
        if (until <= now) return 0
        return minOf(until - now, LOCK_MS) // clamp against clock changes
    }

    fun attemptsLeft(): Int = MAX_FAILURES - prefs.getInt("fails", 0)

    /** Records a failed attempt; returns the lock duration when this one triggered a lock, else 0. */
    fun fail(): Long {
        val fails = prefs.getInt("fails", 0) + 1
        if (fails < MAX_FAILURES) {
            prefs.edit().putInt("fails", fails).commit()
            Log.w("pin", "failed_attempt", "fails" to fails)
            return 0
        }
        prefs.edit().putInt("fails", 0).putLong("until", System.currentTimeMillis() + LOCK_MS).commit()
        Log.w("pin", "locked", "ms" to LOCK_MS)
        return LOCK_MS
    }

    fun reset() {
        prefs.edit().putInt("fails", 0).putLong("until", 0).commit()
    }
}
