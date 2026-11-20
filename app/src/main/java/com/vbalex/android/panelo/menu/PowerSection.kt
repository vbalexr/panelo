// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.vbalex.android.panelo.admin.DeviceAdmin
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.power.Presence
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Power section: auto-dim, presence detection, screen-off mode, schedule, test button. */
@Composable
internal fun PowerSection(config: KioskConfig, actions: AdminActions) {
    val activity = actions.activity
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    val adminActive = remember(tick) { DeviceAdmin.isActive(activity) }
    Section("Power") {
        AutoDim(config, actions)
        PresenceControls(config, actions)
        DropdownRow(
            "Screen-off mode", "screenOffMode", config.screenOffMode,
            listOf(KioskConfig.SCREEN_OFF_SOFT to "Soft (black overlay)", KioskConfig.SCREEN_OFF_REAL to "Real (lock screen)"),
        ) { v ->
            if (v == KioskConfig.SCREEN_OFF_REAL && !adminActive) {
                message = "Real screen-off needs Device Admin or Device Owner: enable it in the Lockdown section first."
                return@DropdownRow
            }
            message = null
            ConfigStore.update { it.copy(screenOffMode = v) }
        }
        if (!adminActive) Note("Real mode is unavailable until Device Admin is enabled (Lockdown section). Presence cannot wake a really-off screen: use the power button or the schedule.")
        Note(message ?: activity.screenOff.notice.collectAsState().value)
        TextRow("Schedule: off at (HH:mm, empty = none)", "schedule", config.schedule.offAt.orEmpty()) { text ->
            ConfigStore.update { it.copy(schedule = it.schedule.copy(offAt = text.trim().ifEmpty { null })) }
        }
        TextRow("Schedule: on at (HH:mm, empty = none)", "schedule", config.schedule.onAt.orEmpty()) { text ->
            ConfigStore.update { it.copy(schedule = it.schedule.copy(onAt = text.trim().ifEmpty { null })) }
        }
        Note("The schedule needs both times. Inside the off window a touch wakes the screen for 2 minutes. Alarm clocks may show a status-bar alarm icon on some devices.")
        OutlinedButton(onClick = { activity.screenOff.test() }, modifier = Modifier.fillMaxWidth()) { Text("Test screen off now (10 s or until touch)") }
    }
}

@Composable
private fun AutoDim(config: KioskConfig, actions: AdminActions) {
    val dimmer = actions.activity.dimmer
    if (!dimmer.hasSensor()) {
        Text("Auto-dim: No light sensor on this device", style = MaterialTheme.typography.bodyMedium)
        return
    }
    val lux by dimmer.lux.collectAsState()
    SwitchRow("Auto-dim (ambient light)", "autoDim", config.autoDim.enabled) { v -> ConfigStore.update { it.copy(autoDim = it.autoDim.copy(enabled = v)) } }
    SwitchRow("Follow system brightness", "autoDim", config.autoDim.followSystem) { v -> ConfigStore.update { it.copy(autoDim = it.autoDim.copy(followSystem = v)) } }
    SliderRow("Minimum brightness", "autoDim", (config.autoDim.min * 100).roundToInt(), 0..100, 5) { v ->
        ConfigStore.update { it.copy(autoDim = it.autoDim.copy(min = v / 100f, max = maxOf(it.autoDim.max, v / 100f))) }
    }
    SliderRow("Maximum brightness", "autoDim", (config.autoDim.max * 100).roundToInt(), 0..100, 5) { v ->
        ConfigStore.update { it.copy(autoDim = it.autoDim.copy(max = v / 100f, min = minOf(it.autoDim.min, v / 100f))) }
    }
    Note(lux?.let { "Current light: ${it.roundToInt()} lux" } ?: "Current light: not measuring (enable auto-dim)")
}

@Composable
private fun PresenceControls(config: KioskConfig, actions: AdminActions) {
    val presence = actions.activity.presence
    val status by presence.status.collectAsState()
    val lastMotion by presence.lastMotionAt.collectAsState()
    SwitchRow("Presence detection (camera)", "presence", config.presence.enabled) { v ->
        if (v) presence.requestPermission()
        ConfigStore.update { it.copy(presence = it.presence.copy(enabled = v)) }
    }
    TextRow("No-presence timeout (seconds, min 10)", "presence", config.presence.noPresenceTimeoutSec.toString(), KeyboardType.Number) { text ->
        Edit.int(text) { c, v -> c.copy(presence = c.presence.copy(noPresenceTimeoutSec = v)) }
    }
    SliderRow("Presence sensitivity", "presence", (config.presence.sensitivity * 100).roundToInt(), 0..100, 5) { v ->
        ConfigStore.update { it.copy(presence = it.presence.copy(sensitivity = v / 100f)) }
    }
    val state = when (status) {
        Presence.Status.OFF -> "off"
        Presence.Status.ACTIVE -> "camera watching"
        Presence.Status.PAUSED_PAGE_CAMERA -> "paused: the page is using the camera"
        Presence.Status.NO_PERMISSION -> "camera permission denied"
        Presence.Status.UNAVAILABLE -> "no usable camera"
    }
    val motion = if (lastMotion == 0L) "none yet" else Clock.seconds(lastMotion)
    Note("Presence: $state. Last motion: $motion. Touches count as presence.")
}
