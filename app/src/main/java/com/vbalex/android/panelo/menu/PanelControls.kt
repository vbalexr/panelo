// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vbalex.android.panelo.config.ConfigInvalid
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig

internal val LocalConfig = compositionLocalOf { KioskConfig() }
internal val LocalPlaceholder = compositionLocalOf { false }

/** Parsing + saving helpers for text fields. */
internal object Edit {
    fun int(text: String, change: (KioskConfig, Int) -> KioskConfig): Result<KioskConfig> {
        val value = text.trim().toIntOrNull() ?: return Result.failure(ConfigInvalid(listOf("Enter a whole number")))
        return ConfigStore.update { change(it, value) }
    }

    fun float(text: String, change: (KioskConfig, Float) -> KioskConfig): Result<KioskConfig> {
        val value = text.trim().toFloatOrNull() ?: return Result.failure(ConfigInvalid(listOf("Enter a number")))
        return ConfigStore.update { change(it, value) }
    }
}

/** Whether a control is editable and the note to show under it. */
internal class ControlState(val enabled: Boolean, val note: String?)

internal object Control {
    @Composable
    fun state(key: String): ControlState {
        val locked = key in LocalConfig.current.lockedKeys
        val later = LocalPlaceholder.current
        val note = when {
            locked -> "Managed remotely"
            later -> "Available in a later update"
            else -> null
        }
        return ControlState(enabled = !locked && !later, note = note)
    }
}

@Composable
internal fun Note(text: String?) {
    if (text == null) return
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Collapsible section; [later] renders its controls disabled as placeholders. */
@Composable
internal fun Section(title: String, expandedInitially: Boolean = false, later: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(expandedInitially) }
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (later) Text("later  ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (expanded) "-" else "+", style = MaterialTheme.typography.titleMedium)
        }
        if (!expanded) return@Column
        CompositionLocalProvider(LocalPlaceholder provides later) {
            Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
        }
    }
}

@Composable
internal fun SwitchRow(label: String, key: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val state = Control.state(key)
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange, enabled = state.enabled)
        }
        Note(state.note)
    }
}

/** Text field with an explicit Save (also IME Done); validation errors are shown inline. */
@Composable
internal fun TextRow(
    label: String,
    key: String,
    stored: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSave: (String) -> Result<*>,
) {
    val state = Control.state(key)
    var text by remember(stored) { mutableStateOf(stored) }
    var error by remember(stored) { mutableStateOf<String?>(null) }
    val dirty = text != stored

    fun save() {
        if (!dirty) return
        error = onSave(text).exceptionOrNull()?.message
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        enabled = state.enabled,
        singleLine = true,
        isError = error != null,
        supportingText = { (error ?: state.note)?.let { Text(it) } },
        trailingIcon = { if (dirty && state.enabled) TextButton(onClick = { save() }) { Text("Save") } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { save() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun DropdownRow(label: String, key: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val state = Control.state(key)
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Column {
            OutlinedButton(onClick = { open = true }, enabled = state.enabled) {
                Text(options.firstOrNull { it.first == value }?.second ?: value)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (option, title) ->
                    DropdownMenuItem(text = { Text(title) }, onClick = { open = false; onSelect(option) })
                }
            }
        }
        Note(state.note)
    }
}

@Composable
internal fun SliderRow(label: String, key: String, value: Int, range: IntRange, step: Int, onCommit: (Int) -> Unit) {
    val state = Control.state(key)
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column {
        Text("$label: ${drag.toInt()}%", style = MaterialTheme.typography.labelMedium)
        Slider(
            value = drag,
            onValueChange = { drag = it },
            onValueChangeFinished = { onCommit(drag.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
            enabled = state.enabled,
        )
        Note(state.note)
    }
}
