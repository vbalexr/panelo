// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import com.vbalex.android.panelo.power.ScreenOff
import com.vbalex.android.panelo.remote.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerValidationTest {
    private fun ok(config: KioskConfig) = ConfigStore.validate(config).isSuccess
    private val base = KioskConfig()

    @Test fun brightnessRange() {
        assertTrue(ok(base.copy(autoDim = AutoDimConfig(min = 0.2f, max = 0.8f))))
        assertTrue(!ok(base.copy(autoDim = AutoDimConfig(min = 0.9f, max = 0.5f))))
        assertTrue(!ok(base.copy(autoDim = AutoDimConfig(min = -0.1f))))
        assertTrue(!ok(base.copy(autoDim = AutoDimConfig(max = 1.5f))))
    }

    @Test fun presenceTimeoutAtLeastTenSeconds() {
        assertTrue(ok(base.copy(presence = PresenceConfig(noPresenceTimeoutSec = 10))))
        assertTrue(!ok(base.copy(presence = PresenceConfig(noPresenceTimeoutSec = 9))))
        assertTrue(!ok(base.copy(presence = PresenceConfig(sensitivity = 1.2f))))
    }

    @Test fun scheduleFormat() {
        assertTrue(ok(base.copy(schedule = ScheduleConfig("22:00", "07:30"))))
        assertTrue(ok(base.copy(schedule = ScheduleConfig(null, null))))
        assertTrue(!ok(base.copy(schedule = ScheduleConfig("25:00", "07:30"))))
        assertTrue(!ok(base.copy(schedule = ScheduleConfig("22:00", "7:30"))))
    }

    @Test fun screenOffModeValues() {
        assertTrue(ok(base.copy(screenOffMode = "real")))
        assertTrue(!ok(base.copy(screenOffMode = "off")))
    }

    @Test fun remoteOverlayAppliesPowerFields() {
        val merged = Overlay.merge(base, """{"schemaVersion":1,"screenOffMode":"real","schedule":{"offAt":"23:15","onAt":"06:00"},"presence":{"enabled":true,"noPresenceTimeoutSec":30},"autoDim":{"enabled":true,"min":0.05,"max":0.9}}""").getOrThrow()
        assertEquals("real", merged.config.screenOffMode)
        assertEquals("23:15", merged.config.schedule.offAt)
        assertEquals(30, merged.config.presence.noPresenceTimeoutSec)
        assertEquals(0.9f, merged.config.autoDim.max)
    }

    @Test fun remoteOverlayRejectsBadPowerFields() {
        listOf(
            """{"schemaVersion":1,"presence":{"noPresenceTimeoutSec":5}}""",
            """{"schemaVersion":1,"schedule":{"offAt":"9pm"}}""",
            """{"schemaVersion":1,"screenOffMode":"hibernate"}""",
            """{"schemaVersion":1,"autoDim":{"min":0.9,"max":0.2}}""",
        ).forEach { assertTrue(it, Overlay.merge(base, it).isFailure) }
    }

    @Test fun realModeDegradesWithoutAdmin() {
        assertEquals("soft", ScreenOff.effectiveMode("real", false))
        assertEquals("real", ScreenOff.effectiveMode("real", true))
        assertEquals("soft", ScreenOff.effectiveMode("soft", true))
    }
}
