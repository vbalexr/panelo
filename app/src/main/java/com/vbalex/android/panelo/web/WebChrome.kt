// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Browser features WebView lacks by default: file chooser, camera/mic, geolocation,
 * fullscreen video, popups. Must be constructed in Activity.onCreate (registers result launchers).
 */
class WebChrome(
    private val activity: ComponentActivity,
    private val fullscreenContainer: ViewGroup,
    private val loader: (String) -> Unit,
) : WebChromeClient() {

    private companion object {
        const val CAMERA_RELEASE_DELAY_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())

    private val _cameraInUse = MutableStateFlow(false)

    /** True while a page has been granted video capture (presence detection must release the camera). */
    val cameraInUse: StateFlow<Boolean> = _cameraInUse.asStateFlow()

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissions: ((Map<String, Boolean>) -> Unit)? = null
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    private val fileLauncher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = FileChooserParams.parseResult(result.resultCode, result.data)
        fileCallback?.onReceiveValue(uris)
        fileCallback = null
    }

    private val permissionLauncher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val callback = pendingPermissions
        pendingPermissions = null
        callback?.invoke(result)
    }

    val isFullscreen: Boolean get() = customView != null

    /** Called by WebHost on navigation start: the previous page no longer holds the camera. */
    fun resetCamera() {
        handler.removeCallbacks(releaseCamera)
        _cameraInUse.value = false
    }

    /**
     * The page reported how many live video tracks it holds (see [CameraWatch]). Zero releases the camera for presence
     * detection after a short delay that lets the WebView actually close the device; a new track cancels the release.
     */
    fun onCameraTracks(count: Int) {
        Log.i("web", "camera_tracks", "count" to count)
        handler.removeCallbacks(releaseCamera)
        if (count > 0) return
        handler.postDelayed(releaseCamera, CAMERA_RELEASE_DELAY_MS)
    }

    private val releaseCamera = Runnable {
        if (!_cameraInUse.value) return@Runnable
        _cameraInUse.value = false
        Log.i("web", "camera_released_by_page")
    }

    fun exitFullscreen() = onHideCustomView()

    override fun onShowFileChooser(webView: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        val launched = runCatching { fileLauncher.launch(params.createIntent()) }
        if (launched.isFailure) {
            Log.w("web", "file_chooser_failed", "error" to launched.exceptionOrNull().toString())
            fileCallback?.onReceiveValue(null)
            fileCallback = null
        }
        return true
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        activity.runOnUiThread { handlePermission(request) }
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        _cameraInUse.value = false
    }

    private fun handlePermission(request: PermissionRequest) {
        val origin = request.origin.toString()
        if (!WebPolicy.allows(origin)) {
            Log.w("web", "permission_denied_origin", "origin" to origin)
            request.deny()
            return
        }
        val needed = request.resources.mapNotNull { resource -> androidPermission(resource)?.let { resource to it } }
        if (needed.isEmpty()) {
            request.deny()
            return
        }
        askAndroid(needed.map { it.second }) { granted ->
            val resources = needed.filter { it.second in granted }.map { it.first }
            if (resources.isEmpty()) {
                Log.w("web", "permission_denied_user", "origin" to origin)
                request.deny()
                return@askAndroid
            }
            request.grant(resources.toTypedArray())
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources) _cameraInUse.value = true
            Log.i("web", "permission_granted", "origin" to origin, "resources" to resources.joinToString(","))
        }
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        activity.runOnUiThread {
            if (!WebPolicy.allows(origin)) {
                callback.invoke(origin, false, false)
                return@runOnUiThread
            }
            askAndroid(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { granted ->
                callback.invoke(origin, granted.isNotEmpty(), false)
            }
        }
    }

    private fun androidPermission(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        else -> null
    }

    /** Ensure runtime permissions, then report which of [permissions] are granted. One request at a time. */
    private fun askAndroid(permissions: List<String>, done: (Set<String>) -> Unit) {
        val missing = permissions.filter { !isGranted(it) }
        if (missing.isEmpty()) {
            done(permissions.toSet())
            return
        }
        if (pendingPermissions != null) {
            done(emptySet())
            return
        }
        pendingPermissions = { done(permissions.filter { isGranted(it) }.toSet()) }
        permissionLauncher.launch(missing.toTypedArray())
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        if (customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullscreenContainer.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        fullscreenContainer.visibility = View.VISIBLE
    }

    override fun onHideCustomView() {
        val view = customView ?: return
        fullscreenContainer.removeView(view)
        fullscreenContainer.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    /** window.open: load the target in the main WebView if the allowlist permits it. */
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = WebView(activity)
        var handled = false
        fun open(url: String) {
            if (handled) return
            handled = true
            if (WebPolicy.allows(url)) loader(url) else Log.w("web", "popup_blocked", "url" to url)
            popup.stopLoading()
            handler.post { popup.destroy() }
        }
        popup.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                open(request.url.toString())
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (url != "about:blank") open(url)
            }
        }
        transport.webView = popup
        resultMsg.sendToTarget()
        return true
    }
}
