// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vbalex.android.panelo.BuildConfig
import com.vbalex.android.panelo.R
import com.vbalex.android.panelo.config.ConfigInvalid
import com.vbalex.android.panelo.admin.DeviceAdmin
import com.vbalex.android.panelo.admin.DeviceOwner
import com.vbalex.android.panelo.admin.LockTask
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.Hosts
import com.vbalex.android.panelo.config.HttpCodes
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.config.Orientation
import com.vbalex.android.panelo.config.Pin
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.log.LogExport
import com.vbalex.android.panelo.remote.RemoteConfig
import com.vbalex.android.panelo.remote.RemoteState
import com.vbalex.android.panelo.web.WebViewInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Slide-in settings panel (right side; full width on phones). Back / scrim / Close dismiss it. */
@Composable
fun AdminPanel(actions: AdminActions, onClose: () -> Unit) {
    val config by ConfigStore.state.collectAsState()
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    CompositionLocalProvider(LocalConfig provides config, LocalPlaceholder provides false) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val panelWidth = if (maxWidth < 600.dp) maxWidth else 440.dp
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
            )
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInHorizontally(initialOffsetX = { it }),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Surface(
                    Modifier.width(panelWidth).fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
                    tonalElevation = 6.dp,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            TextButton(onClick = onClose) { Text("Close") }
                        }
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                            WarningBanner()
                            QuickActions(actions, onClose)
                            WebSection(config)
                            DisplaySection(config)
                            RefreshSection(config)
                            PowerSection(config, actions)
                            LockdownSection(config, actions)
                            RemoteSection(config, actions)
                            SecuritySection()
                            AboutSection(actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActions(actions: AdminActions, onClose: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Section("Quick actions", expandedInitially = true) {
        OutlinedButton(onClick = { actions.reload(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Reload") }
        OutlinedButton(onClick = { actions.goToStart(); onClose() }, modifier = Modifier.fillMaxWidth()) { Text("Go to start URL") }
        OutlinedButton(onClick = { actions.clearCache(); message = "Cache cleared" }, modifier = Modifier.fillMaxWidth()) { Text("Clear cache") }
        OutlinedButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) { Text("Clear all data") }
        OutlinedButton(
            onClick = {
                pulling = true
                message = "Pulling remote config..."
                scope.launch {
                    message = if (!ConfigStore.state.value.remote.enabled) "Remote config is not enabled" else RemoteMessage.of(RemoteConfig.pull(actions.activity, force = true))
                    pulling = false
                }
            },
            enabled = !pulling,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Pull remote config now") }
        OutlinedButton(
            onClick = { message = actions.exitKiosk().exceptionOrNull()?.let { "No Home settings available on this device" } },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Exit kiosk temporarily (Home settings)") }
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        Note(message)
    }
    if (!confirmClear) return
    AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all data?") },
        text = { Text("Removes cache, cookies, web storage and saved snapshots. The web app will be logged out.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; actions.clearData(); onClose() }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

@Composable
private fun WebSection(config: KioskConfig) {
    Section("Web") {
        TextRow("Start URL", "startUrl", config.startUrl, KeyboardType.Uri) { text ->
            ConfigStore.update { it.copy(startUrl = text.trim()) }
        }
        HostsEditor(config)
        SliderRow("Page zoom", "pageZoom", config.pageZoom, 50..200, 5) { value -> ConfigStore.update { it.copy(pageZoom = value) } }
        TextRow("Text zoom (%)", "textZoom", config.textZoom.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(textZoom = v) }
        }
        SwitchRow("Allow pinch zoom", "pinchZoom", config.pinchZoom) { v -> ConfigStore.update { it.copy(pinchZoom = v) } }
        SwitchRow("Autoplay media", "autoplay", config.autoplay) { v -> ConfigStore.update { it.copy(autoplay = v) } }
        TextRow("User agent override (empty = default)", "userAgent", config.userAgent) { text ->
            ConfigStore.update { it.copy(userAgent = text.trim()) }
        }
        TextRow("Error HTTP codes (e.g. 500-599, 404)", "errorHttpCodes", HttpCodes.format(config.errorHttpCodes)) { text ->
            val codes = HttpCodes.parse(text).getOrElse { return@TextRow Result.failure<KioskConfig>(it) }
            ConfigStore.update { it.copy(errorHttpCodes = codes) }
        }
    }
}

@Composable
private fun HostsEditor(config: KioskConfig) {
    val state = Control.state("allowedHosts")
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Allowed hosts (empty = start URL host only)", style = MaterialTheme.typography.labelMedium)
        config.allowedHosts.forEach { host ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host, Modifier.weight(1f))
                TextButton(enabled = state.enabled, onClick = { ConfigStore.update { c -> c.copy(allowedHosts = c.allowedHosts - host) } }) { Text("Remove") }
            }
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add host") },
            enabled = state.enabled,
            singleLine = true,
            isError = error != null,
            supportingText = { (error ?: state.note)?.let { Text(it) } },
            trailingIcon = {
                if (input.isNotBlank() && state.enabled) TextButton(onClick = {
                    val host = Hosts.normalize(input).getOrElse { error = it.message; return@TextButton }
                    val result = ConfigStore.update { c -> c.copy(allowedHosts = (c.allowedHosts + host).distinct()) }
                    error = result.exceptionOrNull()?.message
                    if (result.isSuccess) input = ""
                }) { Text("Add") }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DisplaySection(config: KioskConfig) {
    val labels = listOf(
        Orientation.AUTO to "Auto", Orientation.PORTRAIT to "Portrait", Orientation.LANDSCAPE to "Landscape",
        Orientation.REVERSE_PORTRAIT to "Reverse portrait", Orientation.REVERSE_LANDSCAPE to "Reverse landscape",
        Orientation.SENSOR to "Full sensor",
    )
    Section("Display") {
        DropdownRow("Orientation", "orientation", config.orientation, labels) { v -> ConfigStore.update { it.copy(orientation = v) } }
        SwitchRow("Keep screen on", "keepScreenOn", config.keepScreenOn) { v -> ConfigStore.update { it.copy(keepScreenOn = v) } }
        SwitchRow("Immersive mode", "immersive", config.immersive) { v -> ConfigStore.update { it.copy(immersive = v) } }
        SwitchRow("Show edge handle", "edgeHandle", config.edgeHandle) { v -> ConfigStore.update { it.copy(edgeHandle = v) } }
    }
}

@Composable
private fun RefreshSection(config: KioskConfig) {
    Section("Refresh") {
        TextRow("Auto reload (minutes, 0 = off)", "autoReloadMin", config.autoReloadMin.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(autoReloadMin = v) }
        }
        TextRow("Idle timeout to start URL (seconds, 0 = off)", "idleTimeoutSec", config.idleTimeoutSec.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(idleTimeoutSec = v) }
        }
        SwitchRow("Offline snapshot", "snapshotEnabled", config.snapshotEnabled) { v -> ConfigStore.update { it.copy(snapshotEnabled = v) } }
        TextRow("Snapshot interval (minutes)", "snapshotIntervalMin", config.snapshotIntervalMin.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(snapshotIntervalMin = v) }
        }
        TextRow("Heartbeat interval (seconds, 0 = off)", "heartbeatIntervalSec", config.heartbeatIntervalSec.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(heartbeatIntervalSec = v) }
        }
    }
}

@Composable
private fun LockdownSection(config: KioskConfig, actions: AdminActions) {
    val activity = actions.activity
    var tick by remember { mutableIntStateOf(0) }
    var confirmOwner by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }
    // Re-read the system state on every tick (and after each action).
    val owner = remember(tick) { DeviceOwner.isOwner(activity) }
    val adminActive = remember(tick) { DeviceAdmin.isActive(activity) }
    val homeHeld = remember(tick) { HomeRole.isHeld(activity) }
    val lockState = remember(tick) { LockTask.state(activity) }
    val effective = remember(tick, config.lockdownLevel) { LockTask.effectiveLevel(activity, config) }

    Section("Lockdown") {
        DropdownRow(
            "Lockdown level", "lockdownLevel", config.lockdownLevel.toString(),
            listOf("1" to "1 - Basic (Home + immersive)", "2" to "2 - Screen pinning", "3" to "3 - Device Owner"),
        ) { v ->
            LockTask.retry()
            ConfigStore.update { it.copy(lockdownLevel = v.toInt()) }
        }
        Note("Status")
        Text(
            "Effective level: $effective" + if (effective != config.lockdownLevel) " (level ${config.lockdownLevel} needs Device Owner, using basic)" else "",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("Lock task: ${LockTask.describe(lockState)}" + if (LockTask.isSuspended()) " (kiosk exited temporarily)" else "", style = MaterialTheme.typography.bodyMedium)
        Text("Default Home app: ${if (homeHeld) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
        Text("Device Admin: ${if (adminActive) "active" else "not active"}", style = MaterialTheme.typography.bodyMedium)
        Text("Device Owner: ${if (owner) "yes" else "no"}", style = MaterialTheme.typography.bodyMedium)
        if (config.lockdownLevel == 2) {
            Note("Level 2 asks the system to pin the app. If you decline, the app asks again after 10 minutes, or press the button below.")
            OutlinedButton(onClick = { LockTask.retry(); LockTask.sync(activity, config); tick++ }, modifier = Modifier.fillMaxWidth()) { Text("Ask for screen pinning now") }
        }
        if (config.lockdownLevel == 3) {
            Note("Level 3 needs Device Owner: factory-reset the device (no accounts), then run one adb command. See docs/DeviceOwnerSetup.md.")
            SwitchRow("Allow power-button menu (level 3)", "allowPowerMenu", config.allowPowerMenu) { v -> ConfigStore.update { it.copy(allowPowerMenu = v) } }
        }
        OutlinedButton(
            onClick = { actions.forResult(HomeRole.requestIntent(activity)) },
            enabled = !homeHeld,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (homeHeld) "Already the default Home app" else "Set as default Home") }
        if (!owner) {
            OutlinedButton(
                onClick = {
                    val result = if (adminActive) DeviceAdmin.remove(activity) else DeviceAdmin.requestEnable(activity)
                    message = result.exceptionOrNull()?.message
                    tick++
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (adminActive) "Remove Device Admin" else "Enable Device Admin") }
        }
        if (owner) {
            OutlinedButton(onClick = { confirmOwner = true }, modifier = Modifier.fillMaxWidth()) { Text("Remove Device Owner") }
        }
        Note(message)
    }
    if (!confirmOwner) return
    AlertDialog(
        onDismissRequest = { confirmOwner = false },
        title = { Text("Remove Device Owner?") },
        text = { Text("All kiosk protections (forced Home, locked status bar, lock task) are lost and the device returns to normal. You can set it up again only with adb (see docs/DeviceOwnerSetup.md).") },
        confirmButton = {
            TextButton(onClick = {
                confirmOwner = false
                message = DeviceOwner.clear(activity).fold({ "Device Owner removed" }, { "Failed: ${it.message}" })
                ConfigStore.update { it.copy(lockdownLevel = if (it.lockdownLevel == 3) 1 else it.lockdownLevel) }
                tick++
            }) { Text("Remove") }
        },
        dismissButton = { TextButton(onClick = { confirmOwner = false }) { Text("Cancel") } },
    )
}

@Composable
private fun SecuritySection() {
    val state = Control.state("adminPinHash")
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var snippet by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun change() {
        if (busy) return
        if (!Pin.isValidFormat(next)) {
            message = "New PIN must be 4 to 8 digits"
            return
        }
        if (next != again) {
            message = "New PINs do not match"
            return
        }
        busy = true
        scope.launch {
            val config = ConfigStore.state.value
            val ok = withContext(Dispatchers.Default) { Pin.verify(current, config.adminPinHash, config.adminPinSalt) }
            val result = if (ok) withContext(Dispatchers.Default) { Pin.change(next) } else null
            busy = false
            message = when {
                result == null -> "Current PIN is wrong"
                result.isFailure -> result.exceptionOrNull()?.message
                else -> "PIN changed"
            }
            if (result?.isSuccess == true) {
                Log.i("admin", "pin_changed")
                current = ""; next = ""; again = ""
            }
        }
    }

    Section("Security") {
        PinRow("Current PIN", current, state.enabled) { current = it }
        PinRow("New PIN", next, state.enabled) { next = it }
        PinRow("Repeat new PIN", again, state.enabled) { again = it }
        Button(onClick = { change() }, enabled = state.enabled && !busy, modifier = Modifier.fillMaxWidth()) { Text("Change PIN") }
        Note(message ?: state.note)
        OutlinedButton(
            onClick = {
                snippet = Pin.snippet(ConfigStore.state.value)
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PIN hash", snippet))
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copy PIN hash for remote config") }
        snippet?.let {
            Note("Copied. Paste into the remote JSON (hash and salt, not the PIN itself):")
            Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PinRow(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun AboutSection(actions: AdminActions) {
    val context = LocalContext.current
    val webView = remember { WebViewInfo.current(context) }
    val remote by RemoteState.status.collectAsState()
    Section("About") {
        Text("Panelo ${BuildConfig.VERSION_NAME}")
        if (remote.appliedAtMs > 0) Text("Last remote update: ${Clock.minutes(remote.appliedAtMs)}")
        Text(
            if (webView == null) "WebView: unavailable"
            else "WebView: ${webView.packageName} ${webView.version}",
        )
        if (webView?.outdated == true) {
            Text(
                "Warning: this WebView is older than version ${WebViewInfo.MIN_RECOMMENDED_MAJOR}; some web apps may not work. Update it from the app store.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        var showPrivacy by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { showPrivacy = true }, modifier = Modifier.fillMaxWidth()) { Text("Privacy policy") }
        if (showPrivacy) Privacy.Show { showPrivacy = false }
        LogViewer(actions)
    }
}

@Composable
private fun LogViewer(actions: AdminActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(reloadTick) {
        lines = withContext(Dispatchers.IO) { Log.read(500) }
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(context.resources.getQuantityString(R.plurals.log_last_lines, lines.size, lines.size), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = { reloadTick++ }) { Text("Refresh") }
        TextButton(onClick = {
            scope.launch {
                val intent = withContext(Dispatchers.IO) { LogExport.intent(context) }
                    .getOrElse { status = "Export failed: ${it.message}"; return@launch }
                status = actions.launch(android.content.Intent.createChooser(intent, "Share logs")).exceptionOrNull()?.let { "No app can share logs" }
            }
        }) { Text("Share") }
    }
    Surface(Modifier.fillMaxWidth().height(320.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        LazyColumn(state = listState, modifier = Modifier.padding(4.dp)) {
            items(lines) { line -> Text(line, fontFamily = FontFamily.Monospace, fontSize = 10.sp, lineHeight = 13.sp) }
        }
    }
    Note(status)
}
