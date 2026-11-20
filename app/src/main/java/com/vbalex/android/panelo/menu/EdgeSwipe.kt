// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.app.Activity
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.vbalex.android.panelo.config.KioskConfig
import kotlin.math.abs

/**
 * Right-edge swipe detector. Only touches that start inside the edge strip are consumed (the whole
 * gesture, so the WebView never sees it); everything else is left alone. Also reserves the strip
 * from system back-gestures and can show a faint edge handle. Fallback for devices where the system
 * still steals the edge (gesture navigation honours at most 200 dp of exclusion per edge): a
 * three-finger swipe to the left anywhere on the screen.
 */
class EdgeSwipe(
    private val activity: Activity,
    private val root: FrameLayout,
    private val onSwipe: () -> Unit,
    private val cancelPage: (MotionEvent) -> Unit,
) {
    private companion object {
        const val EDGE_DP = 24f
        const val THRESHOLD_DP = 80f
        const val EXCLUSION_WIDTH_DP = 32f
        const val EXCLUSION_HEIGHT_DP = 200f
        const val FALLBACK_FINGERS = 3
        const val HANDLE_WIDTH_DP = 4f
        const val HANDLE_HEIGHT_DP = 64f
    }

    private val density = activity.resources.displayMetrics.density
    private var tracking = false
    private var fired = false
    private var startX = 0f
    private var startY = 0f
    private var handle: View? = null
    private var multi = false
    private var multiStartX = 0f
    private var multiStartY = 0f

    /** Returns true when the event was consumed by the edge gesture. */
    fun onTouch(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) return begin(event)
        if (multiFinger(event)) return true
        if (!tracking) return false
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            tracking = false
            return true
        }
        if (action == MotionEvent.ACTION_MOVE) check(event)
        return true
    }

    /** Three-finger swipe left: the page is cancelled when the third finger lands, then the gesture is ours. */
    private fun multiFinger(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (!multi && action == MotionEvent.ACTION_POINTER_DOWN && event.pointerCount == FALLBACK_FINGERS) {
            multi = true
            fired = false
            multiStartX = centroidX(event)
            multiStartY = centroidY(event)
            cancelPage(MotionEvent.obtain(event).also { it.action = MotionEvent.ACTION_CANCEL })
            return true
        }
        if (!multi) return false
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) multi = false
        if (action == MotionEvent.ACTION_MOVE && !fired) {
            val dx = multiStartX - centroidX(event)
            if (dx >= THRESHOLD_DP * density && abs(centroidY(event) - multiStartY) < dx) {
                fired = true
                onSwipe()
            }
        }
        return true
    }

    private fun centroidX(event: MotionEvent): Float = (0 until event.pointerCount).sumOf { event.getX(it).toDouble() }.toFloat() / event.pointerCount

    private fun centroidY(event: MotionEvent): Float = (0 until event.pointerCount).sumOf { event.getY(it).toDouble() }.toFloat() / event.pointerCount

    /** Reserve the edge from system gestures (Android 10+); call once, stays updated on layout changes. */
    fun attach() {
        val decor = activity.window.decorView
        decor.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> excludeEdge(view) }
        excludeEdge(decor)
    }

    fun apply(config: KioskConfig) {
        if (!config.edgeHandle) {
            handle?.let { root.removeView(it) }
            handle = null
            return
        }
        if (handle != null) return
        val view = View(activity).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 255, 255, 255))
                cornerRadius = HANDLE_WIDTH_DP * density
            }
        }
        val params = FrameLayout.LayoutParams((HANDLE_WIDTH_DP * density).toInt(), (HANDLE_HEIGHT_DP * density).toInt(), Gravity.END or Gravity.CENTER_VERTICAL)
        root.addView(view, params)
        handle = view
    }

    private fun begin(event: MotionEvent): Boolean {
        val width = activity.window.decorView.width
        tracking = event.x >= width - EDGE_DP * density
        if (!tracking) return false
        fired = false
        startX = event.x
        startY = event.y
        return true
    }

    private fun check(event: MotionEvent) {
        if (fired) return
        val dx = startX - event.x
        if (dx < THRESHOLD_DP * density) return
        if (abs(event.y - startY) >= dx) return
        fired = true
        onSwipe()
    }

    private fun excludeEdge(view: View) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (view.width == 0 || view.height == 0) return
        val height = minOf(view.height, (EXCLUSION_HEIGHT_DP * density).toInt())
        val top = (view.height - height) / 2
        val left = view.width - (EXCLUSION_WIDTH_DP * density).toInt()
        view.systemGestureExclusionRects = listOf(Rect(left, top, view.width, top + height))
    }
}
