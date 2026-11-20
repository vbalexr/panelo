// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.Hosts
import com.vbalex.android.panelo.config.StartUrl
import com.vbalex.android.panelo.config.Pin
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Step(val title: String) {
    PIN("Set admin PIN"),
    URL("Start URL"),
    HOSTS("Allowed hosts (optional)"),
    HOME("Default Home app"),
    BATTERY("Battery optimisation"),
}

/** First-run setup. [refresh] changes whenever the app returns from a system dialog. */
@Composable
fun SetupWizard(refresh: Int, onRequestHome: () -> Unit, onRequestBattery: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val steps = remember { Step.entries.filter { it != Step.PIN || ConfigStore.state.value.adminPinHash.isEmpty() } }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]
    var error by remember(step) { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var pinAgain by remember { mutableStateOf("") }
    var url by remember { mutableStateOf(ConfigStore.state.value.startUrl) }
    var hosts by remember { mutableStateOf(ConfigStore.state.value.allowedHosts.joinToString(", ")) }
    var showPrivacy by remember { mutableStateOf(false) }

    val last = index == steps.lastIndex
    fun advance() {
        if (last) {
            Log.i("wizard", "finished")
            onClose()
            return
        }
        index++
    }

    fun next() {
        if (busy) return
        when (step) {
            Step.PIN -> {
                if (!Pin.isValidFormat(pin)) {
                    error = "PIN must be 4 to 8 digits"
                    return
                }
                if (pin != pinAgain) {
                    error = "PINs do not match"
                    return
                }
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { Pin.change(pin) }
                    busy = false
                    error = result.exceptionOrNull()?.message
                    if (result.isSuccess) advance()
                }
            }
            Step.URL -> {
                val value = StartUrl.normalize(url)
                if (value.isEmpty()) {
                    error = "Enter the URL of the web app"
                    return
                }
                val result = ConfigStore.update { it.copy(startUrl = value) }
                error = result.exceptionOrNull()?.message
                if (result.isSuccess) advance()
            }
            Step.HOSTS -> {
                val parsed = Hosts.parse(hosts)
                error = parsed.exceptionOrNull()?.message
                val list = parsed.getOrNull() ?: return
                val result = ConfigStore.update { it.copy(allowedHosts = list) }
                error = result.exceptionOrNull()?.message
                if (result.isSuccess) advance()
            }
            Step.HOME, Step.BATTERY -> advance()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 480.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Setup ${index + 1}/${steps.size}", style = MaterialTheme.typography.labelLarge)
                Text(step.title, style = MaterialTheme.typography.headlineSmall)
                when (step) {
                    Step.PIN -> {
                        Text("The PIN protects the admin menu (swipe from the right edge). 4 to 8 digits.")
                        PinField("PIN", pin) { pin = it }
                        PinField("Repeat PIN", pinAgain) { pinAgain = it }
                    }
                    Step.URL -> {
                        Text("The web app this kiosk shows (http or https). Without a scheme, https:// is assumed.")
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("Start URL") },
                            placeholder = { Text("https://example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Step.HOSTS -> {
                        Text("Hosts the page may navigate to, besides the start URL's own host. Separate with commas. Leave empty for none.")
                        OutlinedTextField(
                            value = hosts,
                            onValueChange = { hosts = it },
                            label = { Text("Allowed hosts") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Step.HOME -> {
                        val held = remember(refresh) { HomeRole.isHeld(context) }
                        Text("Make Panelo the Home app so it starts on boot and when Home is pressed.")
                        Text(if (held) "Status: Panelo is the default Home app." else "Status: not the default Home app yet.")
                        if (!held) OutlinedButton(onClick = onRequestHome) { Text("Set as default Home") }
                    }
                    Step.BATTERY -> {
                        val exempt = remember(refresh) { BatteryOptimization.isExempt(context) }
                        Text("Exempt the app from battery optimisation so the system does not kill it.")
                        Text(if (exempt) "Status: exempt." else "Status: battery optimisation active.")
                        if (!exempt) OutlinedButton(onClick = onRequestBattery) { Text("Allow") }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (index == 0) TextButton(onClick = { showPrivacy = true }) { Text("Privacy policy") }
                if (showPrivacy) Privacy.Show { showPrivacy = false }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClose) { Text("Later") }
                    Row {
                        if (index > 0) TextButton(onClick = { index-- }) { Text("Back") }
                        Button(onClick = { next() }, enabled = !busy) { Text(if (last) "Finish" else "Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
