// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vbalex.android.panelo.R
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.Pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Numeric PIN prompt (4-8 digits) with persisted lockout. */
@Composable
fun PinDialog(onVerified: () -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedMs by remember { mutableLongStateOf(PinLockout.remainingMs()) }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    val focus = remember { FocusRequester() }
    val inputEnabled = lockedMs == 0L && !busy

    // Show the keyboard as soon as the field can accept input (also after a lockout or a wrong PIN).
    LaunchedEffect(inputEnabled) {
        if (inputEnabled) focus.requestFocus()
    }

    LaunchedEffect(lockedMs > 0) {
        while (lockedMs > 0) {
            delay(250)
            lockedMs = PinLockout.remainingMs()
        }
    }

    fun submit() {
        if (busy || lockedMs > 0) return
        if (!Pin.isValidFormat(pin)) {
            error = "Enter 4 to 8 digits"
            return
        }
        busy = true
        scope.launch {
            val config = ConfigStore.state.value
            val ok = withContext(Dispatchers.Default) { Pin.verify(pin, config.adminPinHash, config.adminPinSalt) }
            busy = false
            if (ok) {
                PinLockout.reset()
                onVerified()
                return@launch
            }
            pin = ""
            lockedMs = PinLockout.fail().let { if (it > 0) PinLockout.remainingMs() else 0 }
            error = if (lockedMs > 0) null else resources.getQuantityString(R.plurals.pin_attempts_left, PinLockout.attemptsLeft(), PinLockout.attemptsLeft())
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 360.dp).fillMaxWidth()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Admin PIN", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= 8 && value.all { it.isDigit() }) pin = value },
                    label = { Text("PIN") },
                    singleLine = true,
                    enabled = lockedMs == 0L && !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (lockedMs > 0) {
                    Text(
                        "Too many attempts. Try again in ${(lockedMs + 999) / 1000} s.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    Button(onClick = { submit() }, enabled = lockedMs == 0L && !busy) { Text("Unlock") }
                }
            }
        }
    }
}
