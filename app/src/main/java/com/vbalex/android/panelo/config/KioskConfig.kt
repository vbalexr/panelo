// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import android.content.pm.ActivityInfo
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AutoDimConfig(
    val enabled: Boolean = false,
    val min: Float = 0.1f,
    val max: Float = 1.0f,
    val followSystem: Boolean = false,
)

@Serializable
data class PresenceConfig(
    val enabled: Boolean = false,
    val noPresenceTimeoutSec: Int = 120,
    val sensitivity: Float = 0.5f,
)

/** "HH:mm" strings; null = no schedule. */
@Serializable
data class ScheduleConfig(
    val offAt: String? = null,
    val onAt: String? = null,
)

@Serializable
data class RemoteSettings(
    val enabled: Boolean = false,
    val url: String = "",
    val pollIntervalMin: Int = 15,
)

@Serializable
data class HttpCredential(
    val host: String,
    val username: String,
    val password: String,
)

/** Single source of truth for all settings. Unknown JSON keys are ignored. */
@Serializable
data class KioskConfig(
    val schemaVersion: Int = 1,

    // Web
    val startUrl: String = "",
    val allowedHosts: List<String> = emptyList(),
    val blockExternal: Boolean = true,
    val pageZoom: Int = 100,
    val textZoom: Int = 100,
    val pinchZoom: Boolean = false,
    val autoplay: Boolean = true,
    val userAgent: String = "",
    val errorHttpCodes: List<Int> = (500..599).toList(),
    val httpAuth: List<HttpCredential> = emptyList(),
    val downloadsEnabled: Boolean = false,

    // Display
    val orientation: String = Orientation.AUTO,
    val keepScreenOn: Boolean = true,
    val immersive: Boolean = true,
    val edgeHandle: Boolean = false,

    // Refresh / recovery
    val autoReloadMin: Int = 0,
    val idleTimeoutSec: Int = 0,
    val snapshotEnabled: Boolean = true,
    val snapshotIntervalMin: Int = 15,
    val heartbeatIntervalSec: Int = 30,
    val heartbeatTimeoutSec: Int = 10,

    // Power
    val autoDim: AutoDimConfig = AutoDimConfig(),
    val presence: PresenceConfig = PresenceConfig(),
    val screenOffMode: String = SCREEN_OFF_SOFT,
    val schedule: ScheduleConfig = ScheduleConfig(),

    // Lockdown
    val lockdownLevel: Int = 1,
    /** Level 3: allow the power-button menu (lock task GLOBAL_ACTIONS). */
    val allowPowerMenu: Boolean = false,

    // Remote
    val remote: RemoteSettings = RemoteSettings(),
    val lockedKeys: List<String> = emptyList(),
    val reloadNonce: Long = 0,
    val clearCacheNonce: Long = 0,
    val clearDataNonce: Long = 0,
    val rebootNonce: Long = 0,

    // Security (PBKDF2 hash + salt, base64; never the PIN itself)
    val adminPinHash: String = "",
    val adminPinSalt: String = "",
) {
    companion object {
        const val SCREEN_OFF_SOFT = "soft"
        const val SCREEN_OFF_REAL = "real"

        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}

object Orientation {
    const val AUTO = "auto"
    const val PORTRAIT = "portrait"
    const val LANDSCAPE = "landscape"
    const val REVERSE_PORTRAIT = "reversePortrait"
    const val REVERSE_LANDSCAPE = "reverseLandscape"
    const val SENSOR = "sensor"

    val ALL = listOf(AUTO, PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE, SENSOR)

    /** Value for Activity.requestedOrientation. */
    fun requested(name: String): Int = when (name) {
        PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        REVERSE_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
