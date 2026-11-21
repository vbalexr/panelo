// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.Locale

/**
 * Viewport-based page zoom (50-200 %) for one WebView.
 *
 * Forces every page's `<meta name="viewport">` to `initial-scale=s` with no width, so the layout viewport becomes
 * device-width / s in both directions. Unlike CSS `zoom` on `<html>` (which also scales `vh` units and left
 * full-height apps such as Grafana filling only part of the screen), `vh`, `%` heights and fixed elements still
 * cover the whole screen. The page's original viewport content is kept and restored when zoom returns to 100 %.
 */
class Zoom(private val webView: WebView) {
    private val nativeScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private var handler: ScriptHandler? = null
    private var percent = 100

    /** Apply to the current page and (when supported) to every future document. Safe to call repeatedly. */
    fun apply(percent: Int) {
        this.percent = percent
        handler?.remove()
        handler = null
        // Meta viewport is only honoured with wide viewport on; keep the WebView default at 100 %.
        webView.settings.useWideViewPort = percent != 100
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
        val scale = String.format(Locale.US, "%.2f", percent / 100.0)
        val content = if (percent == 100) "" else "initial-scale=$scale,minimum-scale=$scale"
        return "(function(){var c='$content';" +
            "function s(){var ms=document.querySelectorAll('meta[name=viewport]'),own=null,n=0;" +
            "for(var i=0;i<ms.length;i++){var m=ms[i];if(m.hasAttribute('data-panelo')){own=m;continue;}n++;" +
            "if(!m.hasAttribute('data-panelo-orig'))m.setAttribute('data-panelo-orig',m.getAttribute('content')||'');" +
            "var v=c||m.getAttribute('data-panelo-orig');if(m.getAttribute('content')!==v)m.setAttribute('content',v);}" +
            "if(!c){if(own)own.remove();return;}" +
            "if(!own&&n===0&&document.head){own=document.createElement('meta');own.name='viewport';" +
            "own.setAttribute('data-panelo','');own.content=c;document.head.appendChild(own);}else if(own)own.content=c;}" +
            "s();if(document.readyState!=='loading')return;" +
            "var o=new MutationObserver(s);o.observe(document,{childList:true,subtree:true});" +
            "document.addEventListener('DOMContentLoaded',function(){o.disconnect();s();});})();"
    }
}
