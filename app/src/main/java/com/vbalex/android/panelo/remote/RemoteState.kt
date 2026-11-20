// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import android.content.Context
import android.content.SharedPreferences
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything remote config remembers between runs, in SharedPreferences (never in KioskConfig JSON):
 * the encrypted token, ETag / Last-Modified, last-seen nonces, last result, and pending one-shot commands.
 * ETag and nonces belong to a source URL; they are discarded when the URL changes.
 */
object RemoteState {
    private const val PREFS = "remote_state"

    const val RELOAD = "reload"
    const val CLEAR_CACHE = "clearCache"
    const val CLEAR_DATA = "clearData"

    enum class Outcome { NEVER, APPLIED, NOT_MODIFIED, INVALID, FAILED }

    class Status(
        val outcome: Outcome = Outcome.NEVER,
        val detail: String = "",
        val checkedAtMs: Long = 0,
        val etag: String = "",
        val changedKeys: List<String> = emptyList(),
        val appliedAtMs: Long = 0,
    )

    private lateinit var prefs: SharedPreferences
    private val _status = MutableStateFlow(Status())
    private val _hasToken = MutableStateFlow(false)
    private val _pending = MutableStateFlow<Set<String>>(emptySet())

    val status: StateFlow<Status> = _status.asStateFlow()
    val hasToken: StateFlow<Boolean> = _hasToken.asStateFlow()

    /** Commands waiting for the UI (reload / clear cache / clear data); drained by MainActivity. */
    val pending: StateFlow<Set<String>> = _pending.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _hasToken.value = prefs.getString("token", null) != null
        _pending.value = prefs.getStringSet("pending", emptySet()).orEmpty().toSet()
        _status.value = Status(
            outcome = runCatching { Outcome.valueOf(prefs.getString("outcome", "NEVER").orEmpty()) }.getOrDefault(Outcome.NEVER),
            detail = prefs.getString("detail", "").orEmpty(),
            checkedAtMs = prefs.getLong("checkedAt", 0),
            etag = prefs.getString("etag", "").orEmpty(),
            changedKeys = prefs.getString("changed", "").orEmpty().split(',').filter { it.isNotEmpty() },
            appliedAtMs = prefs.getLong("appliedAt", 0),
        )
    }

    /** Decrypted token, or null when none is set / it cannot be decrypted (then it is dropped). */
    fun token(): String? {
        val sealed = prefs.getString("token", null) ?: return null
        val token = SecretBox.decrypt(sealed).getOrElse {
            Log.e("remote", "token_decrypt_failed", "error" to it.toString())
            clearToken()
            return null
        }
        return token
    }

    fun saveToken(token: String): Result<Unit> {
        val sealed = SecretBox.encrypt(token).getOrElse {
            Log.e("remote", "token_encrypt_failed", "error" to it.toString())
            return Result.failure(it)
        }
        prefs.edit().putString("token", sealed).apply()
        _hasToken.value = true
        Log.i("remote", "token_saved")
        return Result.success(Unit)
    }

    fun clearToken() {
        prefs.edit().remove("token").apply()
        _hasToken.value = false
        Log.i("remote", "token_cleared")
    }

    /** Validators for the conditional request; empty when the source URL changed since they were stored. */
    fun validators(url: String): Pair<String, String> {
        if (prefs.getString("sourceUrl", "") != url) return "" to ""
        return prefs.getString("etag", "").orEmpty() to prefs.getString("lastModified", "").orEmpty()
    }

    fun saveValidators(url: String, etag: String, lastModified: String) {
        resetIfNewSource(url)
        prefs.edit().putString("etag", etag).putString("lastModified", lastModified).apply()
    }

    /** Last-seen nonces for [url]; empty on first sight of that source. */
    fun nonces(url: String): Map<String, Long> {
        if (prefs.getString("sourceUrl", "") != url) return emptyMap()
        return Nonces.of(KioskConfig()).keys.mapNotNull { name ->
            if (prefs.contains("nonce_$name")) name to prefs.getLong("nonce_$name", 0) else null
        }.toMap()
    }

    fun saveNonces(url: String, values: Map<String, Long>) {
        resetIfNewSource(url)
        val edit = prefs.edit()
        values.forEach { (name, value) -> edit.putLong("nonce_$name", value) }
        edit.apply()
    }

    fun record(status: Status) {
        prefs.edit()
            .putString("outcome", status.outcome.name)
            .putString("detail", status.detail)
            .putLong("checkedAt", status.checkedAtMs)
            .putString("changed", status.changedKeys.joinToString(","))
            .putLong("appliedAt", status.appliedAtMs)
            .apply()
        _status.value = status
    }

    fun addPending(commands: Set<String>) {
        if (commands.isEmpty()) return
        val all = _pending.value + commands
        prefs.edit().putStringSet("pending", all).apply()
        _pending.value = all
    }

    fun clearPending(commands: Set<String>) {
        val rest = _pending.value - commands
        prefs.edit().putStringSet("pending", rest).apply()
        _pending.value = rest
    }

    private fun resetIfNewSource(url: String) {
        if (prefs.getString("sourceUrl", "") == url) return
        val edit = prefs.edit().putString("sourceUrl", url).remove("etag").remove("lastModified")
        Nonces.of(KioskConfig()).keys.forEach { edit.remove("nonce_$it") }
        edit.apply()
    }
}
