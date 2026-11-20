// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import com.vbalex.android.panelo.config.ScheduleConfig
import java.util.Calendar
import java.util.TimeZone

/** Daily off/on window logic (pure functions; minutes are minutes since midnight). */
object ScheduleWindow {
    private val TIME = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")

    class Transition(val atMs: Long, val turnOff: Boolean)

    fun parse(text: String?): Int? {
        val match = TIME.matchEntire(text.orEmpty()) ?: return null
        return match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()
    }

    /** (offAt, onAt) in minutes, or null when the schedule is disabled (missing, invalid or identical times). */
    fun of(config: ScheduleConfig): Pair<Int, Int>? {
        val off = parse(config.offAt) ?: return null
        val on = parse(config.onAt) ?: return null
        if (off == on) return null
        return off to on
    }

    /** True inside the off window [off, on); handles windows that cross midnight (off > on). */
    fun isOff(minute: Int, off: Int, on: Int): Boolean {
        if (off < on) return minute in off until on
        return minute >= off || minute < on
    }

    fun isOff(config: ScheduleConfig, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean {
        val (off, on) = of(config) ?: return false
        return isOff(minuteOfDay(nowMs, zone), off, on)
    }

    fun minuteOfDay(nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val c = Calendar.getInstance(zone).apply { timeInMillis = nowMs }
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    /** The next off or on moment strictly after [nowMs]; null when the schedule is disabled. */
    fun next(config: ScheduleConfig, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): Transition? {
        val (off, on) = of(config) ?: return null
        val offAt = nextOccurrence(off, nowMs, zone)
        val onAt = nextOccurrence(on, nowMs, zone)
        return if (offAt < onAt) Transition(offAt, true) else Transition(onAt, false)
    }

    private fun nextOccurrence(minute: Int, nowMs: Long, zone: TimeZone): Long {
        val c = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, minute / 60)
            set(Calendar.MINUTE, minute % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (c.timeInMillis <= nowMs) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }
}
