// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.remote.RemoteConfig
import com.vbalex.android.panelo.remote.RemoteResult
import com.vbalex.android.panelo.remote.RemoteState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Remote config panel section: settings, write-only token, status and "Pull now". */
@Composable
internal fun RemoteSection(config: KioskConfig, actions: AdminActions) {
    val status by RemoteState.status.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Section("Remote config") {
        SwitchRow("Enable remote config", "remote", config.remote.enabled) { v -> ConfigStore.update { it.copy(remote = it.remote.copy(enabled = v)) } }
        TextRow("Config URL (https)", "remote", config.remote.url, KeyboardType.Uri) { text ->
            ConfigStore.update { it.copy(remote = it.remote.copy(url = text.trim())) }
        }
        TokenRow()
        TextRow("Poll interval (minutes, min 5)", "remote", config.remote.pollIntervalMin.toString(), KeyboardType.Number) { text ->
            Edit.int(text) { c, v -> c.copy(remote = c.remote.copy(pollIntervalMin = v)) }
        }
        Text(describe(status), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = {
                busy = true
                scope.launch {
                    RemoteConfig.pull(actions.activity, force = true)
                    busy = false
                }
            },
            enabled = config.remote.enabled && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Pulling..." else "Pull now") }
    }
}

@Composable
private fun TokenRow() {
    val state = Control.state("remote")
    val hasToken by RemoteState.hasToken.collectAsState()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    if (hasToken) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Token saved ●●●●", Modifier.weight(1f))
            TextButton(enabled = state.enabled, onClick = { RemoteState.clearToken() }) { Text("Clear") }
        }
        return
    }
    val focus = LocalFocusManager.current

    // Save lives outside the text field (a trailing icon inside it competes with the IME / focus handling and
    // needed a second tap) and also runs from the keyboard's Done key. The token is trimmed on save, not while typing.
    fun save() {
        val token = text.trim()
        if (token.isEmpty()) return
        focus.clearFocus()
        error = RemoteState.saveToken(token).exceptionOrNull()?.let { "Could not store the token securely" }
        if (error == null) text = ""
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text("Access token (optional, write-only)") },
        enabled = state.enabled,
        singleLine = true,
        isError = error != null,
        supportingText = { (error ?: state.note)?.let { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = Modifier.fillMaxWidth(),
    )
    if (text.isNotBlank() && state.enabled) OutlinedButton(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Save token") }
}

/** Multi-line status summary. */
private fun describe(status: RemoteState.Status): String {
    if (status.outcome == RemoteState.Outcome.NEVER) return "Last check: never"
    val result = when (status.outcome) {
        RemoteState.Outcome.APPLIED -> "applied"
        RemoteState.Outcome.NOT_MODIFIED -> "not modified"
        RemoteState.Outcome.INVALID -> "INVALID (kept previous config): ${status.detail}"
        RemoteState.Outcome.FAILED -> "FAILED: ${status.detail}"
        RemoteState.Outcome.NEVER -> ""
    }
    return buildString {
        appendLine("Last check: ${Clock.seconds(status.checkedAtMs)}")
        appendLine("Result: $result")
        if (status.etag.isNotEmpty()) appendLine("ETag: ${status.etag}")
        if (status.changedKeys.isNotEmpty()) append("Keys changed on last apply: ${status.changedKeys.joinToString(", ")}")
    }.trimEnd()
}

/** Time formatting for the panel. */
internal object Clock {
    fun minutes(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    fun dateTime(ms: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ms))
    fun seconds(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
}

/** Shown in the quick-action message after a manual pull. */
internal object RemoteMessage {
    fun of(result: RemoteResult): String = when (result) {
        is RemoteResult.Applied -> if (result.changedKeys.isEmpty()) "Remote config applied (no changes)" else "Remote config applied: ${result.changedKeys.joinToString(", ")}"
        RemoteResult.NotModified -> "Remote config not modified"
        is RemoteResult.Invalid -> "Remote config invalid: ${result.problems.joinToString("; ")}"
        is RemoteResult.Failed -> "Remote pull failed: ${result.reason}"
    }
}
