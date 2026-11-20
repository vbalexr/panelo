// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

/** Allowed-host list parsing/normalisation. */
object Hosts {
    private val VALID = Regex("^[a-z0-9*.:-]+$")

    /** "https://Example.com/path" -> "example.com". */
    fun normalize(input: String): Result<String> {
        val host = input.trim().lowercase().substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.isEmpty()) return Result.failure(ConfigInvalid(listOf("Host is empty")))
        if (!VALID.matches(host)) return Result.failure(ConfigInvalid(listOf("Invalid host: $input")))
        return Result.success(host)
    }

    /** Comma / whitespace separated list; blank text gives an empty list. */
    fun parse(text: String): Result<List<String>> {
        val hosts = mutableListOf<String>()
        for (part in text.split(',', ' ', '\n', '\t').filter { it.isNotBlank() }) {
            val host = normalize(part).getOrElse { return Result.failure(it) }
            if (host !in hosts) hosts += host
        }
        return Result.success(hosts)
    }
}
