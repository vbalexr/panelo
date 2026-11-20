// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.HttpAuthHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log

/** Callbacks from the web layer; implemented by Recovery (and relayed by WebHost). */
interface WebEvents {
    fun onPageStarted(url: String)
    fun onPageFinished(url: String)
    fun onPageCommitVisible()
    fun onMainFrameError(url: String, code: Int, description: String)
    fun onHttpError(url: String, status: Int)
    fun onSslError(url: String)
    fun onRenderGone(didCrash: Boolean)
}

/** Navigation allowlist + WebViewClient. */
class WebPolicy(private val events: WebEvents) : WebViewClient() {

    companion object {
        const val ASSET_PREFIX = "file:///android_asset/"

        /** Hosts learned from redirects of the start URL; valid for [autoStartUrl] only. */
        private val autoHosts = mutableSetOf<String>()
        private var autoStartUrl = ""

        /** True when navigating to [url] (or granting it permissions) is allowed by the current config. */
        fun allows(url: String?): Boolean {
            if (url == null) return false
            if (url.startsWith(ASSET_PREFIX)) return true
            val uri = Uri.parse(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            val config = ConfigStore.state.value
            if (!config.blockExternal) return true
            val host = uri.host?.lowercase() ?: return false
            return effectiveHosts(config).any { matches(it.lowercase(), host) }
        }

        /**
         * Origin patterns for WebViewCompat origin rules built from the allowlist (https and http per host, wildcard
         * subdomains kept) plus the start URL's own origin (with its port); "*" when external navigation is not blocked.
         */
        fun originRules(): Set<String> {
            val config = ConfigStore.state.value
            if (!config.blockExternal) return setOf("*")
            val rules = effectiveHosts(config).filter { it.isNotBlank() }.flatMap { listOf("https://$it", "http://$it") }.toMutableSet()
            val start = Uri.parse(config.startUrl)
            val host = start.host
            if (host != null && (start.scheme == "http" || start.scheme == "https")) {
                rules += "${start.scheme}://$host" + if (start.port != -1) ":${start.port}" else ""
            }
            return rules
        }

        /** Explicit list, or (when empty) the start host without "www." plus all its subdomains; plus learned redirect hosts. */
        private fun effectiveHosts(config: KioskConfig): List<String> {
            val configured = config.allowedHosts.ifEmpty { defaultHosts(config.startUrl) }
            synchronized(autoHosts) {
                if (autoStartUrl != config.startUrl) {
                    autoHosts.clear()
                    autoStartUrl = config.startUrl
                }
                return configured + autoHosts
            }
        }

        private fun defaultHosts(startUrl: String): List<String> {
            val host = Uri.parse(startUrl).host?.lowercase() ?: return emptyList()
            val base = host.removePrefix("www.")
            return listOf(base, "*.$base")
        }

        /** Redirect of the start-URL load landed on a new host: allow it from now on. */
        private fun learn(host: String) {
            synchronized(autoHosts) { autoHosts += host }
            Log.i("web", "redirect_host_allowed", "host" to host)
        }

        private fun matches(pattern: String, host: String): Boolean {
            if (pattern.startsWith("*.")) return host.endsWith(pattern.substring(1))
            return pattern == host
        }
    }

    /** True once any page has been shown, so a blocked navigation can keep it instead of leaving a blank view. */
    private var hasPage = false

    /** URL of the load started by [WebHost.load], used to recognise redirects of the start URL. */
    var loadingUrl: String? = null

    fun reset() {
        hasPage = false
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (allows(url)) return false
        if (request.isForMainFrame && request.isRedirect && loadingUrl == ConfigStore.state.value.startUrl) {
            val host = request.url.host?.lowercase()
            if (host != null && request.url.scheme?.lowercase().let { it == "http" || it == "https" }) {
                learn(host)
                return false
            }
        }
        Log.w("web", "navigation_blocked", "url" to url, "mainFrame" to request.isForMainFrame)
        if (request.isForMainFrame && !hasPage) showBlocked(view, request.url)
        return true
    }

    /** Nothing to keep on screen: show a clear message instead of a blank page (posted: no loads inside callbacks). */
    private fun showBlocked(view: WebView, uri: Uri) {
        val page = ASSET_PREFIX + "blocked.html?host=" + Uri.encode(uri.host ?: uri.toString())
        view.post { view.loadUrl(page) }
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = events.onPageStarted(url)

    override fun onPageFinished(view: WebView, url: String) {
        hasPage = true
        events.onPageFinished(url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) = events.onPageCommitVisible()

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        events.onMainFrameError(request.url.toString(), error.errorCode, error.description.toString())
    }

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
        if (!request.isForMainFrame) return
        val status = errorResponse.statusCode
        if (status !in ConfigStore.state.value.errorHttpCodes) return
        events.onHttpError(request.url.toString(), status)
    }

    /** SSL errors are never ignored. */
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel()
        events.onSslError(error.url ?: "")
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        events.onRenderGone(detail.didCrash())
        return true
    }

    /** HTTP auth credentials come from config (matched by host); otherwise cancel. */
    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        val credential = ConfigStore.state.value.httpAuth.firstOrNull { it.host.equals(host, ignoreCase = true) }
        if (credential == null) {
            handler.cancel()
            return
        }
        handler.proceed(credential.username, credential.password)
    }
}
