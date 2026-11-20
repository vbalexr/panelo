// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import kotlin.math.abs
import kotlin.math.pow

/** Frame-difference motion detection on a small luma grid (pure functions). */
object Motion {
    const val GRID_W = 32
    const val GRID_H = 24

    private const val THRESHOLD_LOOSE = 8f
    private const val THRESHOLD_TIGHT_RATIO = 0.1f

    /** Sensitivity 0..1 -> mean-abs-diff threshold (0 = 8.0, 0.5 = 2.5, 1 = 0.8 luma levels). */
    fun threshold(sensitivity: Float): Float = THRESHOLD_LOOSE * THRESHOLD_TIGHT_RATIO.pow(sensitivity.coerceIn(0f, 1f))

    fun meanAbsDiff(a: IntArray, b: IntArray): Float {
        if (a.size != b.size || a.isEmpty()) return 0f
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum.toFloat() / a.size
    }

    fun detected(previous: IntArray?, current: IntArray, sensitivity: Float): Boolean {
        if (previous == null) return false
        return meanAbsDiff(previous, current) > threshold(sensitivity)
    }
}
