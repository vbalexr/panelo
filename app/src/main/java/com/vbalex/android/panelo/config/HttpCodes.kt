// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

/** Text form of the error HTTP code list, e.g. "500-599, 404". */
object HttpCodes {
    fun parse(text: String): Result<List<Int>> {
        val codes = sortedSetOf<Int>()
        for (part in text.split(',', ' ').filter { it.isNotBlank() }) {
            val bounds = part.split('-')
            val from = bounds.first().toIntOrNull()
            val to = bounds.last().toIntOrNull()
            if (bounds.size > 2 || from == null || to == null || from > to) {
                return Result.failure(ConfigInvalid(listOf("Invalid code or range: $part")))
            }
            if (from < 100 || to > 599) return Result.failure(ConfigInvalid(listOf("Codes must be 100..599")))
            codes.addAll(from..to)
        }
        return Result.success(codes.toList())
    }

    fun format(codes: List<Int>): String {
        val sorted = codes.sorted()
        val parts = mutableListOf<String>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j + 1 < sorted.size && sorted[j + 1] == sorted[j] + 1) j++
            parts += if (j - i >= 2) "${sorted[i]}-${sorted[j]}" else sorted.subList(i, j + 1).joinToString(", ")
            i = j + 1
        }
        return parts.joinToString(", ")
    }
}
