// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** CSS-zoom based page zoom (50-200 %) for one WebView. */
class Zoom(private val webView: WebView) {
    private val nativeScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private var handler: ScriptHandler? = null
    private var percent = 100

    /** Apply to the current page and (when supported) to every future document. Safe to call repeatedly. */
    fun apply(percent: Int) {
        this.percent = percent
        handler?.remove()
        handler = null
        webView.evaluateJavascript(script(percent), null)
        if (percent == 100 || !nativeScript) return
        handler = WebViewCompat.addDocumentStartJavaScript(webView, script(percent), setOf("*"))
    }

    /** Fallback for WebViews without document-start scripts: re-inject when content becomes visible. */
    fun onCommit() {
        if (nativeScript || percent == 100) return
        webView.evaluateJavascript(script(percent), null)
    }

    private fun script(percent: Int): String {
        val value = if (percent == 100) "" else "$percent%"
        return "(function(){var z='$value';function a(){if(document.documentElement){document.documentElement.style.zoom=z;return true;}return false;}" +
            "if(!a()){var o=new MutationObserver(function(){if(a())o.disconnect();});o.observe(document,{childList:true});}})();"
    }
}
