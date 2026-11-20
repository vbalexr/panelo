// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log

/** Owns the single long-lived WebView: create / destroy / recreate, settings, navigation. */
class WebHost(
    private val activity: ComponentActivity,
    private val container: FrameLayout,
    private val chrome: WebChrome,
    private val events: () -> WebEvents,
) {
    var webView: WebView? = null
        private set

    private var zoom: Zoom? = null
    private var cameraWatch: CameraWatch? = null
    private var cacheMode = WebSettings.LOAD_DEFAULT

    /** Forwards to the (late-bound) events sink, adding WebView-local side effects. */
    private val relay = object : WebEvents {
        override fun onPageStarted(url: String) {
            chrome.resetCamera()
            cameraWatch?.install()
            events().onPageStarted(url)
        }

        override fun onPageFinished(url: String) = events().onPageFinished(url)

        override fun onPageCommitVisible() {
            zoom?.onCommit()
            cameraWatch?.onCommit()
            events().onPageCommitVisible()
        }

        override fun onMainFrameError(url: String, code: Int, description: String) =
            events().onMainFrameError(url, code, description)

        override fun onHttpError(url: String, status: Int) = events().onHttpError(url, status)

        override fun onSslError(url: String) = events().onSslError(url)

        override fun onRenderGone(didCrash: Boolean) = events().onRenderGone(didCrash)
    }
    private val policy = WebPolicy(relay)

    @SuppressLint("SetJavaScriptEnabled")
    fun create() {
        if (webView != null) return
        val view = WebView(activity)
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.displayZoomControls = false
        settings.cacheMode = cacheMode
        view.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        policy.reset()
        view.webViewClient = policy
        view.webChromeClient = chrome
        view.setDownloadListener { url, userAgent, disposition, mime, _ ->
            Downloads.enqueue(activity, url, userAgent, disposition, mime)
        }
        container.addView(view, 0, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        webView = view
        zoom = Zoom(view)
        cameraWatch = CameraWatch(view, chrome::onCameraTracks)
        apply(ConfigStore.state.value)
        Log.i("web", "webview_created")
    }

    fun destroy() {
        val view = webView ?: return
        webView = null
        zoom = null
        cameraWatch = null
        chrome.exitFullscreen()
        container.removeView(view)
        view.stopLoading()
        view.destroy()
        Log.i("web", "webview_destroyed")
    }

    fun recreate() {
        destroy()
        create()
    }

    /** Re-apply all config-driven settings (also on configuration change). */
    @SuppressLint("SetJavaScriptEnabled")
    fun apply(config: KioskConfig) {
        val view = webView ?: return
        val settings = view.settings
        settings.mediaPlaybackRequiresUserGesture = !config.autoplay
        settings.userAgentString = config.userAgent.ifEmpty { null }
        settings.textZoom = config.textZoom
        settings.setSupportZoom(config.pinchZoom)
        settings.builtInZoomControls = config.pinchZoom
        zoom?.apply(config.pageZoom)
        cameraWatch?.install()
    }

    fun load(url: String) {
        create()
        policy.loadingUrl = url
        webView?.loadUrl(url)
    }

    fun reload() {
        webView?.reload()
    }

    /** Applies to subsequent loads: LOAD_DEFAULT or LOAD_CACHE_ELSE_NETWORK. */
    fun setCacheMode(mode: Int) {
        cacheMode = mode
        webView?.settings?.cacheMode = mode
    }

    fun clearCache() {
        webView?.clearCache(true)
        Log.i("web", "cache_cleared")
    }

    /** Cache + web storage + cookies + history/form data (logs the web app out). */
    fun clearData() {
        val view = webView
        view?.clearCache(true)
        view?.clearHistory()
        view?.clearFormData()
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        Log.i("web", "data_cleared")
    }

    fun goBack(): Boolean {
        val view = webView ?: return false
        if (!view.canGoBack()) return false
        view.goBack()
        return true
    }

    fun resume() {
        webView?.onResume()
    }

    fun pause() {
        webView?.onPause()
    }
}
