// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted warnings about rejected / fallen-back configuration, shown as a banner at the top of the admin panel.
 * One warning per [Kind]. A warning stays until its cause is resolved ([clear]) or the admin dismisses it; a dismissed
 * warning comes back when the same kind is raised with different details (a NEW problem).
 */
object ConfigWarnings {
    private const val PREFS = "config_warnings"
    private const val SEP = '\u0001'

    enum class Kind(val title: String) {
        REMOTE_REJECTED("Remote config rejected - keeping previous settings"),
        LOAD_RESTORED("Saved settings were invalid - restored last good copy"),
        LOAD_DEFAULTS("Saved settings could not be read - defaults loaded"),
        VALUE_REFUSED("A stored setting is not allowed in this build"),
    }

    class Warning(val kind: Kind, val detail: String, val atMs: Long) {
        val signature: String get() = kind.name + SEP + detail
    }

    private lateinit var prefs: SharedPreferences
    private val _active = MutableStateFlow<List<Warning>>(emptyList())

    /** Warnings that were not dismissed, oldest kind first. */
    val active: StateFlow<List<Warning>> = _active.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        publish()
    }

    fun raise(kind: Kind, detail: String) {
        if (!::prefs.isInitialized) return
        prefs.edit().putString(kind.name, "${System.currentTimeMillis()}$SEP$detail").apply()
        publish()
    }

    /** The cause is gone: remove the warning and any dismissal of it. */
    fun clear(kind: Kind) {
        if (!::prefs.isInitialized || !prefs.contains(kind.name)) return
        prefs.edit().remove(kind.name).putStringSet("dismissed", dismissed().filterNot { it.startsWith(kind.name + SEP) }.toSet()).apply()
        publish()
    }

    fun dismiss(warning: Warning) {
        prefs.edit().putStringSet("dismissed", dismissed() + warning.signature).apply()
        publish()
    }

    private fun dismissed(): Set<String> = prefs.getStringSet("dismissed", emptySet()).orEmpty().toSet()

    private fun publish() {
        val hidden = dismissed()
        _active.value = Kind.entries.mapNotNull { kind ->
            val raw = prefs.getString(kind.name, null) ?: return@mapNotNull null
            val at = raw.substringBefore(SEP).toLongOrNull() ?: return@mapNotNull null
            Warning(kind, raw.substringAfter(SEP), at).takeIf { it.signature !in hidden }
        }
    }
}
