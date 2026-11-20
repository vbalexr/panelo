// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

/** Cleans up a start URL typed or pasted by the user. */
object StartUrl {
    private val SCHEME = Regex("^(https?)://", RegexOption.IGNORE_CASE)

    /**
     * Trims, collapses a duplicated scheme (a pasted "https://https://a.b" or "https://http://a.b" keeps the last one)
     * and assumes https:// when no scheme is given.
     */
    fun normalize(text: String): String {
        var value = text.trim()
        if (value.isEmpty()) return value
        while (true) {
            val match = SCHEME.find(value) ?: break
            val rest = value.substring(match.range.last + 1)
            if (!SCHEME.containsMatchIn(rest)) break
            value = rest
        }
        if (SCHEME.containsMatchIn(value)) return value
        return "https://$value"
    }
}
