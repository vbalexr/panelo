// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import android.content.Context
import com.vbalex.android.panelo.BuildConfig
import com.vbalex.android.panelo.admin.DeviceOwner
import com.vbalex.android.panelo.config.ConfigInvalid
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.ConfigWarnings
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of one pull. */
sealed interface RemoteResult {
    class Applied(val changedKeys: List<String>) : RemoteResult
    data object NotModified : RemoteResult
    class Invalid(val problems: List<String>) : RemoteResult
    class Failed(val reason: String) : RemoteResult
}

/** Fetch, validate and apply the remote config file. See docs/RemoteConfig.md. */
object RemoteConfig {
    private const val TIMEOUT_MS = 10_000
    private const val MAX_BYTES = 256 * 1024
    private const val MAX_REDIRECTS = 3

    private val lock = Mutex()

    private sealed interface Fetched {
        class Body(val text: String, val etag: String, val lastModified: String) : Fetched
        data object NotModified : Fetched
        class Failed(val reason: String) : Fetched
    }

    /**
     * Fetch + apply + record the status. Serialised (a poll and "Pull now" never overlap).
     * [force] skips the conditional headers so the file is downloaded and re-applied even when unchanged.
     */
    suspend fun pull(context: Context, force: Boolean = false): RemoteResult = lock.withLock {
        withContext(Dispatchers.IO) {
            val result = pullNow(context, force)
            record(result)
            result
        }
    }

    private fun pullNow(context: Context, force: Boolean): RemoteResult {
        val settings = ConfigStore.state.value.remote
        if (!settings.enabled) return RemoteResult.Failed("remote config is disabled")
        if (!RemoteUrl.isAllowed(settings.url, BuildConfig.DEBUG)) {
            ConfigWarnings.raise(ConfigWarnings.Kind.VALUE_REFUSED, "remote.url \"${settings.url}\" is not used: this build only accepts https. Change it in Settings > Remote config.")
            return RemoteResult.Failed("stored remote URL is not allowed in this build (https required); change it in the panel")
        }
        ConfigWarnings.clear(ConfigWarnings.Kind.VALUE_REFUSED)
        val (etag, lastModified) = if (force) "" to "" else RemoteState.validators(settings.url)
        val fetched = fetch(settings.url, etag, lastModified)
        return when (fetched) {
            is Fetched.Failed -> RemoteResult.Failed(fetched.reason)
            Fetched.NotModified -> RemoteResult.NotModified
            is Fetched.Body -> {
                val result = apply(context, settings.url, fetched.text)
                if (result is RemoteResult.Applied) RemoteState.saveValidators(settings.url, fetched.etag, fetched.lastModified)
                result
            }
        }
    }

    private fun fetch(url: String, etag: String, lastModified: String): Fetched {
        val token = RemoteState.token()
        var resolved = RemoteUrl.resolve(url, token != null)
        var useToken = token != null
        repeat(MAX_REDIRECTS + 1) {
            val connection = open(resolved, token.takeIf { useToken }, etag, lastModified)
                .getOrElse { return Fetched.Failed(it.message ?: it.javaClass.simpleName) }
            try {
                val code = connection.responseCode
                if (code in 301..308 && code != 304) {
                    val next = connection.getHeaderField("Location")?.let { runCatching { URL(URL(resolved.url), it).toString() }.getOrNull() }
                        ?: return Fetched.Failed("redirect without Location")
                    if (!RemoteUrl.isAllowed(next, BuildConfig.DEBUG)) return Fetched.Failed("redirect to a disallowed URL")
                    // Never forward the token to another host.
                    if (URL(next).host != URL(resolved.url).host) useToken = false
                    resolved = RemoteUrl.Resolved(next, emptyMap())
                    return@repeat
                }
                return read(connection, code)
            } catch (e: IOException) {
                return Fetched.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                connection.disconnect()
            }
        }
        return Fetched.Failed("too many redirects")
    }

    private fun open(resolved: RemoteUrl.Resolved, token: String?, etag: String, lastModified: String): Result<HttpURLConnection> = runCatching {
        val connection = URL(resolved.url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "Panelo/${BuildConfig.VERSION_NAME}")
        resolved.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        // The token only ever travels over https.
        if (token != null && connection.url.protocol == "https") connection.setRequestProperty("Authorization", "Bearer $token")
        if (etag.isNotEmpty()) connection.setRequestProperty("If-None-Match", etag)
        if (lastModified.isNotEmpty()) connection.setRequestProperty("If-Modified-Since", lastModified)
        connection
    }

    private fun read(connection: HttpURLConnection, code: Int): Fetched {
        if (code == HttpURLConnection.HTTP_NOT_MODIFIED) return Fetched.NotModified
        if (code != HttpURLConnection.HTTP_OK) return Fetched.Failed("HTTP $code ${connection.responseMessage.orEmpty()}".trim())
        if (connection.contentLengthLong > MAX_BYTES) return Fetched.Failed("file larger than ${MAX_BYTES / 1024} KB")
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        connection.inputStream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) return Fetched.Failed("file larger than ${MAX_BYTES / 1024} KB")
            }
        }
        return Fetched.Body(
            out.toString(Charsets.UTF_8.name()),
            connection.getHeaderField("ETag").orEmpty(),
            connection.getHeaderField("Last-Modified").orEmpty(),
        )
    }

    /** Merge [text] into the current config atomically (last-known-good is kept on any problem), then run due nonces. */
    fun apply(context: Context, url: String, text: String): RemoteResult {
        val merged = Overlay.merge(ConfigStore.state.value, text).getOrElse { error ->
            val problems = (error as? ConfigInvalid)?.problems ?: listOf(error.message ?: error.toString())
            Log.w("remote", "invalid", "problems" to problems.joinToString("; "))
            return RemoteResult.Invalid(problems)
        }
        if (merged.ignoredKeys.isNotEmpty()) Log.i("remote", "ignored_keys", "keys" to merged.ignoredKeys.joinToString(","))
        if (merged.changedKeys.isNotEmpty()) {
            ConfigStore.replace(merged.config).onFailure {
                val problems = (it as? ConfigInvalid)?.problems ?: return RemoteResult.Failed("could not save config: ${it.message}")
                return RemoteResult.Invalid(problems)
            }
        }
        Log.i("remote", "applied", "changed" to merged.changedKeys.joinToString(","))
        runNonces(context, url, Nonces.of(merged.config))
        return RemoteResult.Applied(merged.changedKeys)
    }

    private fun runNonces(context: Context, url: String, now: Map<String, Long>) {
        val due = Nonces.due(RemoteState.nonces(url), now)
        RemoteState.saveNonces(url, now)
        if (due.isEmpty()) return
        Log.i("remote", "commands_due", "nonces" to due.joinToString(","))
        RemoteState.addPending(
            buildSet {
                if (Nonces.RELOAD in due) add(RemoteState.RELOAD)
                if (Nonces.CLEAR_CACHE in due) add(RemoteState.CLEAR_CACHE)
                if (Nonces.CLEAR_DATA in due) add(RemoteState.CLEAR_DATA)
            },
        )
        if (Nonces.REBOOT !in due) return
        if (!DeviceOwner.isOwner(context)) {
            Log.w("remote", "reboot_skipped", "reason" to "not device owner")
            return
        }
        DeviceOwner.reboot(context).onFailure { Log.e("remote", "reboot_failed", "error" to it.toString()) }
    }

    private fun record(result: RemoteResult) {
        val previous = RemoteState.status.value
        val now = System.currentTimeMillis()
        val etag = RemoteState.validators(ConfigStore.state.value.remote.url).first
        val status = when (result) {
            is RemoteResult.Applied -> RemoteState.Status(RemoteState.Outcome.APPLIED, "", now, etag, result.changedKeys, now)
            RemoteResult.NotModified -> RemoteState.Status(RemoteState.Outcome.NOT_MODIFIED, "", now, etag, previous.changedKeys, previous.appliedAtMs)
            is RemoteResult.Invalid -> RemoteState.Status(RemoteState.Outcome.INVALID, result.problems.joinToString("; "), now, etag, previous.changedKeys, previous.appliedAtMs)
            is RemoteResult.Failed -> RemoteState.Status(RemoteState.Outcome.FAILED, result.reason, now, etag, previous.changedKeys, previous.appliedAtMs)
        }
        if (result is RemoteResult.Applied) ConfigWarnings.clear(ConfigWarnings.Kind.REMOTE_REJECTED)
        if (result is RemoteResult.Invalid) {
            val source = ConfigStore.state.value.remote.url
            ConfigWarnings.raise(ConfigWarnings.Kind.REMOTE_REJECTED, "${result.problems.joinToString("; ")}\nSource: $source")
        }
        if (result is RemoteResult.Failed) Log.w("remote", "failed", "reason" to result.reason)
        if (result is RemoteResult.NotModified) Log.i("remote", "not_modified")
        RemoteState.record(status)
    }
}
