// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.vbalex.android.panelo.log.Log

/**
 * Tells the app how many live video tracks the page holds, so presence detection can take the camera back as soon as
 * the page stops using it (WebView has no callback for MediaStreamTrack.stop()).
 *
 * A wrapper around navigator.mediaDevices.getUserMedia is injected at document start (re-injected on page commit when
 * the WebView lacks document-start scripts). It reports the count of live video tracks to the "PaneloBridge" object:
 * - preferred: WebViewCompat.addWebMessageListener restricted to origins built from the allowlist (plus a native
 *   re-check of the source origin and main frame);
 * - fallback: addJavascriptInterface, where the current URL is checked against [WebPolicy] before acting.
 */
class CameraWatch(private val webView: WebView, private val onCount: (Int) -> Unit) {
    companion object {
        const val BRIDGE = "PaneloBridge"

        /** Pure JS, shared by both transports: postMessage on the listener object, cameraTracks on the interface. */
        val SCRIPT = """
            (function(){
              if (window.__paneloCam) return;
              var md = navigator.mediaDevices, bridge = window.$BRIDGE;
              if (!bridge || !md || !md.getUserMedia) return;
              window.__paneloCam = true;
              var live = [], last = -1;
              function report() {
                live = live.filter(function(t){ return t.readyState !== 'ended'; });
                if (live.length === last) return;
                last = live.length;
                try { if (bridge.postMessage) bridge.postMessage(String(last)); else bridge.cameraTracks(last); } catch (e) {}
              }
              function drop(t) { var i = live.indexOf(t); if (i >= 0) live.splice(i, 1); report(); }
              function watch(t) {
                if (live.indexOf(t) >= 0) return;
                live.push(t);
                var stop = t.stop;
                t.stop = function(){ try { return stop.apply(t, arguments); } finally { drop(t); } };
                var clone = t.clone;
                t.clone = function(){ var c = clone.apply(t, arguments); watch(c); report(); return c; };
                t.addEventListener('ended', function(){ drop(t); });
              }
              var original = md.getUserMedia.bind(md);
              md.getUserMedia = function(constraints) {
                return original(constraints).then(function(stream) {
                  stream.getVideoTracks().forEach(watch);
                  var add = stream.addTrack;
                  stream.addTrack = function(t){ var r = add.apply(stream, arguments); if (t.kind === 'video') { watch(t); report(); } return r; };
                  report();
                  return stream;
                });
              };
            })();
        """.trimIndent()

        /** Origins (scheme://host[:port]) the bridge answers to: the allowlist plus the start URL's origin. */
        fun rules(): Set<String> = WebPolicy.originRules()
    }

    private val main = Handler(Looper.getMainLooper())
    private var installedRules: Set<String>? = null
    private var scriptHandler: ScriptHandler? = null
    private var usingListener = false
    private var interfaceAdded = false

    private val nativeScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val nativeListener = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** Safe to call repeatedly: re-installs only when the allowed origins changed. Takes effect on the next navigation. */
    fun install() {
        val wanted = rules()
        if (wanted == installedRules) return
        uninstall()
        installedRules = wanted
        if (wanted.isEmpty()) return
        usingListener = nativeListener && addListener(wanted)
        if (!usingListener) addInterface()
        webView.evaluateJavascript(SCRIPT, null)
        if (nativeScript) scriptHandler = WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, if (usingListener) wanted else setOf("*"))
        Log.i("web", "camera_watch_installed", "transport" to (if (usingListener) "web_message_listener" else "js_interface"), "origins" to wanted.size)
    }

    /** Fallback for WebViews without document-start scripts. */
    fun onCommit() {
        if (nativeScript || installedRules.isNullOrEmpty()) return
        webView.evaluateJavascript(SCRIPT, null)
    }

    private fun uninstall() {
        scriptHandler?.remove()
        scriptHandler = null
        if (usingListener) runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE) }
        usingListener = false
        installedRules = null
    }

    private fun addListener(origins: Set<String>): Boolean {
        val added = runCatching {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, origins) { _, message, sourceOrigin, isMainFrame, _ ->
                if (!isMainFrame || !WebPolicy.allows(sourceOrigin.toString())) {
                    Log.w("web", "camera_bridge_rejected", "origin" to sourceOrigin.toString(), "mainFrame" to isMainFrame)
                    return@addWebMessageListener
                }
                accept(message)
            }
        }
        added.onFailure { Log.w("web", "camera_bridge_listener_failed", "error" to it.toString()) }
        return added.isSuccess
    }

    private fun accept(message: WebMessageCompat) {
        val count = message.data?.toIntOrNull() ?: return
        onCount(count)
    }

    private fun addInterface() {
        if (interfaceAdded) return
        interfaceAdded = true
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun cameraTracks(count: Int) {
                // Called on a WebView background thread; the current URL must be read on the main thread.
                main.post {
                    if (!WebPolicy.allows(webView.url)) {
                        Log.w("web", "camera_bridge_rejected", "origin" to webView.url)
                        return@post
                    }
                    onCount(count)
                }
            }
        }, BRIDGE)
    }
}
