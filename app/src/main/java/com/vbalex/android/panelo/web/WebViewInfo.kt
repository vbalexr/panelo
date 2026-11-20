// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.web

import android.content.Context
import androidx.webkit.WebViewCompat

/** Describes the System WebView provider currently in use. */
object WebViewInfo {
    const val MIN_RECOMMENDED_MAJOR = 100

    class Info(val packageName: String, val version: String) {
        val major: Int? get() = version.substringBefore('.').toIntOrNull()
        val outdated: Boolean get() = (major ?: 0) < MIN_RECOMMENDED_MAJOR
    }

    fun current(context: Context): Info? {
        val pkg = WebViewCompat.getCurrentWebViewPackage(context) ?: return null
        return Info(pkg.packageName, pkg.versionName ?: "unknown")
    }
}
