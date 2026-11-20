// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import com.vbalex.android.panelo.config.KioskConfig

/**
 * One-shot commands carried by nonces. A command is due when its nonce DIFFERS from the last seen value AND a last seen
 * value exists. The first fetch from a source only records the values, so enrolling a device against a file that already
 * has `clearDataNonce: 7` does not wipe it. (Pure functions.)
 */
object Nonces {
    const val RELOAD = "reloadNonce"
    const val CLEAR_CACHE = "clearCacheNonce"
    const val CLEAR_DATA = "clearDataNonce"
    const val REBOOT = "rebootNonce"

    fun of(config: KioskConfig): Map<String, Long> = mapOf(
        RELOAD to config.reloadNonce,
        CLEAR_CACHE to config.clearCacheNonce,
        CLEAR_DATA to config.clearDataNonce,
        REBOOT to config.rebootNonce,
    )

    fun due(lastSeen: Map<String, Long>, now: Map<String, Long>): List<String> =
        now.filter { (name, value) -> lastSeen[name]?.let { it != value } ?: false }.keys.toList()
}
