// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import android.content.Context
import com.vbalex.android.panelo.BuildConfig
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.power.ScheduleWindow
import com.vbalex.android.panelo.remote.Overlay
import com.vbalex.android.panelo.remote.RemoteUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.URI

/** Carried inside Result.failure when a config does not validate. */
class ConfigInvalid(val problems: List<String>) : Exception(problems.joinToString("; "))

/**
 * Loads, validates, persists the config and exposes it as a StateFlow.
 *
 * Policy: an invalid update (local edit or remote file) is rejected entirely and the previous config stays in force.
 * Loading the saved config never silently resets to defaults: main file, then the last-known-good copy; rules that
 * depend on the build (http remote URL in release) do not block loading, the consumer refuses the value instead.
 */
object ConfigStore {
    private lateinit var dir: File
    private val _state = MutableStateFlow(KioskConfig())
    val state: StateFlow<KioskConfig> = _state.asStateFlow()

    fun init(context: Context) {
        dir = context.filesDir
        ConfigWarnings.init(context)
        when (val loaded = ConfigFiles.load(dir, ::parseStored)) {
            ConfigLoad.Absent -> Log.i("config", "no_config_file")
            is ConfigLoad.Main -> adopt(loaded.config, "loaded")
            is ConfigLoad.LastKnownGood -> {
                Log.w("config", "main_unusable_using_lkg", "problem" to loaded.mainProblem)
                ConfigWarnings.raise(ConfigWarnings.Kind.LOAD_RESTORED, loaded.mainProblem)
                adopt(loaded.config, "loaded_from_lkg")
            }
            is ConfigLoad.Unusable -> {
                Log.e("config", "load_failed_using_defaults", "problems" to loaded.problems.joinToString(" | "))
                ConfigWarnings.raise(ConfigWarnings.Kind.LOAD_DEFAULTS, loaded.problems.joinToString("; "))
            }
        }
    }

    private fun adopt(config: KioskConfig, event: String) {
        _state.value = config
        Log.i("config", event)
        validate(config).onFailure { Log.w("config", "build_rule_violation", "problems" to it.message) }
    }

    /** Parse JSON text and apply the build-independent rules only (what a stored config must satisfy to load). */
    fun parseStored(text: String): Result<KioskConfig> {
        val config = runCatching { KioskConfig.JSON.decodeFromString<KioskConfig>(text) }
            .getOrElse { return Result.failure(it) }
        return validate(config, previous = config)
    }

    /** Apply a local edit: transform current config, validate, persist, publish. */
    fun update(change: (KioskConfig) -> KioskConfig): Result<KioskConfig> {
        val current = _state.value
        val next = change(current)
        val locked = lockedChanges(current, next)
        if (locked.isNotEmpty()) {
            Log.w("config", "locked_rejected", "keys" to locked.joinToString(","))
            return Result.failure(ConfigInvalid(listOf("Managed remotely: ${locked.joinToString(", ")}")))
        }
        return replace(next)
    }

    /** Keys of [current].lockedKeys that [next] would change; empty unless remote config is enabled. */
    private fun lockedChanges(current: KioskConfig, next: KioskConfig): List<String> {
        if (!current.remote.enabled || current.lockedKeys.isEmpty()) return emptyList()
        val locked = if ("adminPinHash" in current.lockedKeys) current.lockedKeys + "adminPinSalt" else current.lockedKeys
        return Overlay.changedTopLevel(current, next).filter { it in locked }
    }

    @Synchronized
    fun replace(config: KioskConfig): Result<KioskConfig> {
        val valid = validate(config, previous = _state.value).getOrElse {
            Log.w("config", "rejected", "problems" to it.message)
            return Result.failure(it)
        }
        ConfigFiles.save(dir, KioskConfig.JSON.encodeToString(KioskConfig.serializer(), valid)).onFailure {
            Log.e("config", "save_failed", "error" to it.toString())
            return Result.failure(it)
        }
        _state.value = valid
        Log.i("config", "applied")
        return Result.success(valid)
    }

    /**
     * Structural rules always; build-dependent rules (remote.url scheme) only when the value differs from [previous],
     * so a value that was stored by another build can stay without blocking every later edit.
     */
    fun validate(config: KioskConfig, previous: KioskConfig? = null, allowHttpRemote: Boolean = BuildConfig.DEBUG): Result<KioskConfig> {
        val problems = mutableListOf<String>()
        if (config.schemaVersion != 1) problems += "schemaVersion must be 1"
        if (!isUrlOrEmpty(config.startUrl)) problems += "startUrl must be http(s)"
        if (config.allowedHosts.any { it.isBlank() }) problems += "allowedHosts has blank entry"
        if (config.pageZoom !in 50..200) problems += "pageZoom must be 50..200"
        if (config.textZoom !in 50..300) problems += "textZoom must be 50..300"
        if (config.errorHttpCodes.any { it !in 100..599 }) problems += "errorHttpCodes must be 100..599"
        if (config.orientation !in Orientation.ALL) problems += "unknown orientation"
        if (config.autoReloadMin < 0) problems += "autoReloadMin must be >= 0"
        if (config.idleTimeoutSec < 0) problems += "idleTimeoutSec must be >= 0"
        if (config.snapshotIntervalMin !in 1..1440) problems += "snapshotIntervalMin must be 1..1440"
        if (config.heartbeatIntervalSec != 0 && config.heartbeatIntervalSec !in 5..3600) problems += "heartbeatIntervalSec must be 0 or 5..3600"
        if (config.heartbeatTimeoutSec !in 2..300) problems += "heartbeatTimeoutSec must be 2..300"
        if (config.autoDim.min !in 0f..1f || config.autoDim.max !in 0f..1f || config.autoDim.min > config.autoDim.max) problems += "autoDim min/max invalid"
        if (config.presence.noPresenceTimeoutSec < 10) problems += "presence.noPresenceTimeoutSec must be >= 10"
        if (config.presence.sensitivity !in 0f..1f) problems += "presence.sensitivity must be 0..1"
        if (config.screenOffMode != KioskConfig.SCREEN_OFF_SOFT && config.screenOffMode != KioskConfig.SCREEN_OFF_REAL) problems += "unknown screenOffMode"
        if (!isTimeOrNull(config.schedule.offAt) || !isTimeOrNull(config.schedule.onAt)) problems += "schedule times must be HH:mm"
        if (config.lockdownLevel !in 1..3) problems += "lockdownLevel must be 1..3"
        if (config.remote.pollIntervalMin < 5) problems += "remote.pollIntervalMin must be >= 5"
        val urlChanged = previous == null || previous.remote.url != config.remote.url
        if (urlChanged && config.remote.url.isNotEmpty() && !RemoteUrl.isAllowed(config.remote.url, allowHttpRemote)) problems += "remote.url must be https"

        if (problems.isNotEmpty()) return Result.failure(ConfigInvalid(problems))
        return Result.success(config)
    }

    private fun isUrlOrEmpty(url: String): Boolean {
        if (url.isEmpty()) return true
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "http" && uri.scheme != "https") return false
        return !uri.host.isNullOrEmpty()
    }

    private fun isTimeOrNull(value: String?): Boolean = value == null || ScheduleWindow.parse(value) != null
}
