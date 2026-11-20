// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Auto-dimming from the ambient light sensor. Sets the WINDOW brightness only (no WRITE_SETTINGS).
 * The sensor listener is registered only while the activity is started and auto-dim is enabled.
 */
class Dimmer(private val activity: Activity) : SensorEventListener {
    private companion object {
        const val ANIMATION_MS = 300L
        const val STEP_MS = 400L
        const val NONE = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    }

    private val manager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _lux = MutableStateFlow<Float?>(null)

    /** Latest smoothed illuminance while auto-dim is running. */
    val lux: StateFlow<Float?> = _lux.asStateFlow()

    private var config = KioskConfig()
    private var started = false
    private var registered = false
    private var held = false
    private var smoothed: Float? = null
    private var raw: Float? = null
    private val handler = Handler(Looper.getMainLooper())
    private var shown: Float = NONE
    private var target: Float? = null
    private var lastChangeMs = 0L
    private var animator: ValueAnimator? = null

    fun hasSensor(): Boolean = sensor != null

    fun apply(config: KioskConfig) {
        this.config = config
        refresh(force = true)
    }

    fun start() {
        started = true
        refresh(force = true)
    }

    fun stop() {
        started = false
        refresh(force = false)
    }

    /** Soft screen-off forces brightness 0; releasing it restores the auto-dim (or system) level. */
    fun hold(on: Boolean) {
        held = on
        animator?.cancel()
        if (on) {
            write(0f)
            return
        }
        refresh(force = true)
    }

    private fun running(): Boolean = started && sensor != null && config.autoDim.enabled && !config.autoDim.followSystem

    private fun refresh(force: Boolean) {
        if (!running()) {
            unregister()
            smoothed = null
            target = null
            _lux.value = null
            animator?.cancel()
            if (!held) write(NONE)
            return
        }
        register()
        val value = smoothed ?: return
        retarget(value, force)
    }

    private fun register() {
        if (registered) return
        registered = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed(step, STEP_MS)
        Log.i("dimmer", "registered", "ok" to registered)
    }

    private fun unregister() {
        if (!registered) return
        manager.unregisterListener(this)
        handler.removeCallbacks(step)
        registered = false
        raw = null
        Log.i("dimmer", "unregistered")
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.isEmpty()) return
        val reading = event.values[0]
        raw = reading
        if (smoothed != null) return
        smoothed = reading
        _lux.value = reading
        retarget(reading, force = false)
    }

    /** Light sensors report on change only, so the EMA is stepped on a timer toward the latest reading. */
    private val step = object : Runnable {
        override fun run() {
            if (!registered) return
            val latest = raw
            val current = smoothed
            if (latest != null && current != null) {
                val value = Lux.smooth(current, latest)
                smoothed = value
                _lux.value = value
                retarget(value, force = false)
            }
            handler.postDelayed(this, STEP_MS)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun retarget(lux: Float, force: Boolean) {
        val next = Lux.brightness(lux, config.autoDim.min, config.autoDim.max)
        if (!force) {
            if (!Lux.worthChanging(target, next)) return
            if (SystemClock.elapsedRealtime() - lastChangeMs < ANIMATION_MS) return
        }
        target = next
        lastChangeMs = SystemClock.elapsedRealtime()
        if (held) return
        Log.d("dimmer", "brightness", "lux" to lux, "target" to next)
        animate(next)
    }

    private fun animate(to: Float) {
        animator?.cancel()
        val from = if (shown < 0f) to else shown
        animator = ValueAnimator.ofFloat(from, to).apply {
            duration = ANIMATION_MS
            addUpdateListener { write(it.animatedValue as Float) }
            start()
        }
    }

    private fun write(value: Float) {
        shown = value
        val attributes = activity.window.attributes
        if (attributes.screenBrightness == value) return
        attributes.screenBrightness = value
        activity.window.attributes = attributes
    }
}
