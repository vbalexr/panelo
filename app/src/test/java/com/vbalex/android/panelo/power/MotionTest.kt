// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test fun thresholdFallsAsSensitivityRises() {
        assertEquals(8f, Motion.threshold(0f), 0.01f)
        assertEquals(0.8f, Motion.threshold(1f), 0.01f)
        assertTrue(Motion.threshold(0.7f) < Motion.threshold(0.3f))
    }

    @Test fun staticSceneAndFirstFrameAreNoMotion() {
        val frame = IntArray(10) { 100 }
        assertFalse(Motion.detected(null, frame, 0.5f))
        assertFalse(Motion.detected(frame, frame.copyOf(), 0.5f))
    }

    @Test fun changeAboveThresholdIsMotion() {
        val a = IntArray(10) { 100 }
        val b = IntArray(10) { 105 }
        assertTrue(Motion.detected(a, b, 0.5f))
        assertFalse(Motion.detected(a, b, 0f))
    }
}
