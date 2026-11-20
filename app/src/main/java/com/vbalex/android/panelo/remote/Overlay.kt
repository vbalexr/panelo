// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import com.vbalex.android.panelo.config.ConfigInvalid
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.util.Base64

/**
 * Remote file = PARTIAL overlay on the current config (pure functions, no Android APIs).
 * Only keys present in the file override; nested objects merge per key; lists and scalars replace.
 */
object Overlay {
    class Merged(val config: KioskConfig, val changedKeys: List<String>, val ignoredKeys: List<String>)

    /** Keys a remote file may never set (nested, dotted): the remote source cannot disable or redirect itself. */
    private val FORBIDDEN = setOf("remote.enabled", "remote.url")
    private val PLAINTEXT_PIN_KEYS = setOf("adminPin", "pin", "adminPassword")
    private const val PIN_HASH_BYTES = 32
    private const val PIN_SALT_BYTES = 16

    fun merge(current: KioskConfig, text: String): Result<Merged> {
        val remote = runCatching { Json.parseToJsonElement(text) }
            .getOrElse { return invalid("not valid JSON: ${it.message?.lineSequence()?.firstOrNull()}") }
        if (remote !is JsonObject) return invalid("top level must be a JSON object")
        val problems = guard(remote)
        if (problems.isNotEmpty()) return Result.failure(ConfigInvalid(problems))

        val base = KioskConfig.JSON.encodeToJsonElement(current).jsonObject
        val ignored = mutableListOf<String>()
        val merged = overlay(base, remote, "", ignored)
        val config = runCatching { KioskConfig.JSON.decodeFromJsonElement<KioskConfig>(merged) }
            .getOrElse { return invalid("wrong field type: ${it.message?.lineSequence()?.firstOrNull()}") }
        val valid = ConfigStore.validate(config, previous = current).getOrElse { return Result.failure(it) }
        val changed = diff(base, KioskConfig.JSON.encodeToJsonElement(valid).jsonObject, "").filter { it != "schemaVersion" }
        return Result.success(Merged(valid, changed, ignored))
    }

    /** Problems that make the whole file unacceptable regardless of field values. */
    private fun guard(remote: JsonObject): List<String> {
        val problems = mutableListOf<String>()
        if ((remote["schemaVersion"] as? JsonPrimitive)?.content != "1") problems += "schemaVersion must be present and 1"
        PLAINTEXT_PIN_KEYS.filter { it in remote }.forEach { problems += "plaintext PIN field '$it' is not accepted; use adminPinHash + adminPinSalt" }
        val hash = remote["adminPinHash"]
        val salt = remote["adminPinSalt"]
        if ((hash == null) != (salt == null)) problems += "adminPinHash and adminPinSalt must be set together"
        if (hash != null && salt != null) {
            if (!isBase64Of(hash, PIN_HASH_BYTES)) problems += "adminPinHash must be base64 of $PIN_HASH_BYTES bytes"
            if (!isBase64Of(salt, PIN_SALT_BYTES)) problems += "adminPinSalt must be base64 of $PIN_SALT_BYTES bytes"
        }
        return problems
    }

    private fun isBase64Of(value: JsonElement, bytes: Int): Boolean {
        val primitive = value as? JsonPrimitive ?: return false
        if (!primitive.isString) return false
        val decoded = runCatching { Base64.getDecoder().decode(primitive.content) }.getOrNull() ?: return false
        return decoded.size == bytes
    }

    private fun overlay(base: JsonObject, remote: JsonObject, prefix: String, ignored: MutableList<String>): JsonObject {
        val out = base.toMutableMap()
        for ((key, value) in remote) {
            val path = prefix + key
            val current = base[key]
            if (current == null || path in FORBIDDEN) {
                ignored += path
                continue
            }
            out[key] = if (current is JsonObject && value is JsonObject) overlay(current, value, "$path.", ignored) else value
        }
        return JsonObject(out)
    }

    private fun diff(before: JsonObject, after: JsonObject, prefix: String): List<String> = after.flatMap { (key, value) ->
        val old = before[key]
        when {
            old == value -> emptyList()
            old is JsonObject && value is JsonObject -> diff(old, value, "$prefix$key.")
            else -> listOf(prefix + key)
        }
    }

    /** Top-level keys that differ between two configs (used to enforce lockedKeys on local edits). */
    fun changedTopLevel(before: KioskConfig, after: KioskConfig): Set<String> {
        val a = KioskConfig.JSON.encodeToJsonElement(before).jsonObject
        val b = KioskConfig.JSON.encodeToJsonElement(after).jsonObject
        return b.filter { (key, value) -> a[key] != value }.keys
    }

    private fun invalid(problem: String): Result<Merged> = Result.failure(ConfigInvalid(listOf(problem)))
}
