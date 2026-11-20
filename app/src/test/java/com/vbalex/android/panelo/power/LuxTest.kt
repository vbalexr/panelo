// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxTest {
    @Test fun zeroLuxIsMinAndBrightIsMax() {
        assertEquals(0.1f, Lux.brightness(0f, 0.1f, 1f), 0.001f)
        assertEquals(1f, Lux.brightness(1000f, 0.1f, 1f), 0.001f)
        assertEquals(1f, Lux.brightness(50_000f, 0.1f, 1f), 0.001f)
    }

    @Test fun mappingIsLogarithmicAndMonotonic() {
        val mid = Lux.brightness(30f, 0f, 1f)
        assertTrue("30 lux is already about half bright on a log scale", mid in 0.4f..0.6f)
        assertTrue(Lux.brightness(10f, 0f, 1f) < Lux.brightness(100f, 0f, 1f))
    }

    @Test fun negativeLuxClampsToMin() = assertEquals(0.2f, Lux.brightness(-5f, 0.2f, 0.8f), 0.001f)

    @Test fun smoothingMovesPartWayAndFirstSamplePasses() {
        assertEquals(100f, Lux.smooth(null, 100f), 0f)
        assertEquals(25f, Lux.smooth(0f, 100f, 0.25f), 0.001f)
    }

    @Test fun hysteresisIgnoresSmallChanges() {
        assertTrue(Lux.worthChanging(null, 0.5f))
        assertFalse(Lux.worthChanging(0.50f, 0.52f))
        assertTrue(Lux.worthChanging(0.50f, 0.54f))
    }
}
