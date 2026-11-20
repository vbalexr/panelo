// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import kotlin.math.abs
import kotlin.math.ln

/** Ambient light -> window brightness (pure functions). */
object Lux {
    /** Illuminance at (and above) which the maximum brightness is used. */
    const val FULL_LUX = 1000f
    const val EMA_ALPHA = 0.25f
    const val HYSTERESIS = 0.03f

    /** Log mapping: 0 lux -> [min], [FULL_LUX] or more -> [max]. */
    fun brightness(lux: Float, min: Float, max: Float): Float {
        val t = (ln(1f + lux.coerceAtLeast(0f)) / ln(1f + FULL_LUX)).coerceIn(0f, 1f)
        return min + (max - min) * t
    }

    /** Exponential moving average; the first sample passes through. */
    fun smooth(previous: Float?, sample: Float, alpha: Float = EMA_ALPHA): Float {
        if (previous == null) return sample
        return previous + alpha * (sample - previous)
    }

    /** Only react to changes bigger than [HYSTERESIS] (avoids flicker around a threshold). */
    fun worthChanging(current: Float?, target: Float): Boolean {
        if (current == null) return true
        return abs(target - current) > HYSTERESIS
    }
}
