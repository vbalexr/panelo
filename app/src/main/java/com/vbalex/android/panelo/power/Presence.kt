// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Presence detection: CameraX ImageAnalysis on the front camera (back as fallback), tiny luma grid,
 * frame difference. No frame is stored. Bound to the activity lifecycle; pauses while a page uses the camera.
 * Must be constructed in Activity.onCreate (registers the permission launcher).
 */
class Presence(private val activity: ComponentActivity, cameraInUse: StateFlow<Boolean>) {
    enum class Status { OFF, ACTIVE, PAUSED_PAGE_CAMERA, NO_PERMISSION, UNAVAILABLE }

    private companion object {
        const val MIN_FRAME_GAP_MS = 500L
        const val LOG_GAP_MS = 10_000L
    }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _lastMotionAt = MutableStateFlow(0L)

    /** Wall-clock time of the last detected motion (0 = none yet). */
    val lastMotionAt: StateFlow<Long> = _lastMotionAt.asStateFlow()

    /** [SystemClock.elapsedRealtime] of the last motion (0 = none yet). */
    @Volatile
    var lastMotionElapsed = 0L
        private set

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Log.i("presence", "permission_result", "granted" to granted)
        settle()
    }

    private var config = KioskConfig()
    private var started = false
    private var pageCamera = false
    private var provider: ProcessCameraProvider? = null
    private var executor: ExecutorService? = null
    private var analysis: ImageAnalysis? = null

    @Volatile
    private var sensitivity = 0.5f
    private var previous: IntArray? = null
    private var lastFrameMs = 0L
    private var lastLogMs = 0L

    init {
        activity.lifecycleScope.launch {
            cameraInUse.collect {
                pageCamera = it
                settle()
            }
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    fun requestPermission() {
        if (hasPermission()) return
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun apply(config: KioskConfig) {
        this.config = config
        sensitivity = config.presence.sensitivity
        settle()
    }

    fun start() {
        started = true
        settle()
    }

    fun stop() {
        started = false
        settle()
    }

    /** Idempotent: bring the camera binding in line with config, lifecycle, permission and page camera use. */
    private fun settle() {
        val wanted = started && config.presence.enabled
        if (!wanted) {
            release()
            _status.value = Status.OFF
            return
        }
        if (!hasPermission()) {
            release()
            _status.value = Status.NO_PERMISSION
            return
        }
        if (pageCamera) {
            release()
            _status.value = Status.PAUSED_PAGE_CAMERA
            return
        }
        if (analysis != null) return
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            val result = runCatching { future.get() }
            result.onFailure { fail("provider", it) }
            result.onSuccess { provider = it; bind(it) }
        }, ContextCompat.getMainExecutor(activity))
    }

    private fun bind(provider: ProcessCameraProvider) {
        val stillWanted = started && config.presence.enabled && hasPermission() && !pageCamera
        if (!stillWanted || analysis != null) return
        val selector = when {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
            else -> {
                Log.w("presence", "no_camera")
                _status.value = Status.UNAVAILABLE
                return
            }
        }
        val pool = Executors.newSingleThreadExecutor()
        val useCase = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(Size(320, 240), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                    .build(),
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        useCase.setAnalyzer(pool) { analyze(it) }
        val bound = runCatching { provider.bindToLifecycle(activity, selector, useCase) }
        if (bound.isFailure) {
            pool.shutdown()
            fail("bind", bound.exceptionOrNull()!!)
            return
        }
        executor = pool
        analysis = useCase
        previous = null
        _status.value = Status.ACTIVE
        Log.i("presence", "bound", "front" to (selector == CameraSelector.DEFAULT_FRONT_CAMERA))
    }

    private fun release() {
        val use = analysis ?: return
        analysis = null
        runCatching { provider?.unbind(use) }
        executor?.shutdown()
        executor = null
        previous = null
        Log.i("presence", "released")
    }

    private fun fail(stage: String, error: Throwable) {
        Log.e("presence", "camera_failed", "stage" to stage, "error" to error.toString())
        _status.value = Status.UNAVAILABLE
    }

    /** Analyzer thread. Throttled to ~2 fps; closes every image. */
    private fun analyze(image: ImageProxy) {
        image.use {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameMs < MIN_FRAME_GAP_MS) return
            lastFrameMs = now
            val grid = grid(it)
            val moved = Motion.detected(previous, grid, sensitivity)
            previous = grid
            if (!moved) return
            lastMotionElapsed = now
            _lastMotionAt.value = System.currentTimeMillis()
            if (now - lastLogMs < LOG_GAP_MS) return
            lastLogMs = now
            Log.i("presence", "motion")
        }
    }

    /** Luma sampled on a [Motion.GRID_W] x [Motion.GRID_H] grid (2x2 average per cell). */
    private fun grid(image: ImageProxy): IntArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val out = IntArray(Motion.GRID_W * Motion.GRID_H)
        val w = image.width
        val h = image.height
        val limit = buffer.limit()
        for (gy in 0 until Motion.GRID_H) {
            val y = ((gy * h) / Motion.GRID_H + h / (2 * Motion.GRID_H)).coerceAtMost(h - 2)
            for (gx in 0 until Motion.GRID_W) {
                val x = ((gx * w) / Motion.GRID_W + w / (2 * Motion.GRID_W)).coerceAtMost(w - 2)
                val base = y * plane.rowStride + x * plane.pixelStride
                val last = base + plane.rowStride + plane.pixelStride
                if (last >= limit) continue
                val sum = (buffer.get(base).toInt() and 0xFF) + (buffer.get(base + plane.pixelStride).toInt() and 0xFF) +
                    (buffer.get(base + plane.rowStride).toInt() and 0xFF) + (buffer.get(last).toInt() and 0xFF)
                out[gy * Motion.GRID_W + gx] = sum / 4
            }
        }
        return out
    }
}
