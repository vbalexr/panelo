// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import com.vbalex.android.panelo.config.ScheduleConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleWindowTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis

    @Test fun parsesAndRejects() {
        assertEquals(22 * 60, ScheduleWindow.parse("22:00"))
        assertEquals(0, ScheduleWindow.parse("00:00"))
        assertNull(ScheduleWindow.parse("24:00"))
        assertNull(ScheduleWindow.parse("7:00"))
        assertNull(ScheduleWindow.parse("12:60"))
        assertNull(ScheduleWindow.parse(""))
        assertNull(ScheduleWindow.parse(null))
    }

    @Test fun sameDayWindow() {
        val off = 9 * 60
        val on = 17 * 60
        assertFalse(ScheduleWindow.isOff(8 * 60 + 59, off, on))
        assertTrue(ScheduleWindow.isOff(9 * 60, off, on))
        assertTrue(ScheduleWindow.isOff(16 * 60 + 59, off, on))
        assertFalse(ScheduleWindow.isOff(17 * 60, off, on))
    }

    @Test fun windowCrossingMidnight() {
        val off = 22 * 60
        val on = 7 * 60
        assertTrue(ScheduleWindow.isOff(23 * 60, off, on))
        assertTrue(ScheduleWindow.isOff(0, off, on))
        assertTrue(ScheduleWindow.isOff(6 * 60 + 59, off, on))
        assertFalse(ScheduleWindow.isOff(7 * 60, off, on))
        assertFalse(ScheduleWindow.isOff(12 * 60, off, on))
        assertTrue(ScheduleWindow.isOff(22 * 60, off, on))
    }

    @Test fun disabledWhenIncompleteInvalidOrEqual() {
        assertNull(ScheduleWindow.of(ScheduleConfig(offAt = "22:00")))
        assertNull(ScheduleWindow.of(ScheduleConfig(offAt = "bad", onAt = "07:00")))
        assertNull(ScheduleWindow.of(ScheduleConfig(offAt = "07:00", onAt = "07:00")))
        assertFalse(ScheduleWindow.isOff(ScheduleConfig(), at(20, 23, 0), utc))
    }

    @Test fun nextTransitionAcrossMidnight() {
        val config = ScheduleConfig(offAt = "22:00", onAt = "07:00")
        val evening = ScheduleWindow.next(config, at(20, 12, 0), utc)!!
        assertTrue(evening.turnOff)
        assertEquals(at(20, 22, 0), evening.atMs)
        val night = ScheduleWindow.next(config, at(20, 23, 30), utc)!!
        assertFalse(night.turnOff)
        assertEquals(at(21, 7, 0), night.atMs)
        val exactly = ScheduleWindow.next(config, at(20, 22, 0), utc)!!
        assertFalse("strictly after now", exactly.turnOff)
    }

    @Test fun isOffUsesWallClock() {
        val config = ScheduleConfig(offAt = "22:00", onAt = "07:00")
        assertTrue(ScheduleWindow.isOff(config, at(20, 3, 0), utc))
        assertFalse(ScheduleWindow.isOff(config, at(20, 8, 0), utc))
    }
}
