// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coordinates swipe -> PIN -> panel / setup wizard. The Compose view is created lazily when a screen
 * opens and removed + disposed on close, so nothing Compose-related exists while the menu is hidden.
 */
class AdminMenu(
    private val activity: ComponentActivity,
    private val container: FrameLayout,
    private val actions: AdminActions,
) {
    private enum class Screen { NONE, PIN, WIZARD, PANEL }

    private companion object {
        const val IDLE_CLOSE_MS = 2 * 60_000L
    }

    private var screen = Screen.NONE
    private var idleJob: Job? = null
    private val refresh = mutableIntStateOf(0)
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh.intValue++ }

    val isOpen: Boolean get() = screen != Screen.NONE

    init {
        actions.forResult = { launcher.launch(it) }
        PinLockout.init(activity)
    }

    /** Swipe entry point: wizard when there is no PIN, otherwise PIN first. */
    fun open() {
        if (isOpen) return
        Log.i("menu", "open")
        val config = ConfigStore.state.value
        if (config.adminPinHash.isEmpty()) {
            showWizard()
            return
        }
        showPin(if (needsSetup(config)) Screen.WIZARD else Screen.PANEL)
    }

    /** First run: open the wizard / PIN prompt when setup is incomplete. */
    fun openIfSetupNeeded() {
        if (needsSetup(ConfigStore.state.value)) open()
    }

    fun close() {
        if (!isOpen) return
        Log.i("menu", "close", "screen" to screen.name)
        idleJob?.cancel()
        screen = Screen.NONE
        for (i in 0 until container.childCount) (container.getChildAt(i) as? ComposeView)?.disposeComposition()
        container.removeAllViews()
    }

    /** Any user interaction: restarts the 2-minute auto-close timer. */
    fun touch() {
        if (screen != Screen.PIN && screen != Screen.PANEL) return
        idleJob?.cancel()
        idleJob = activity.lifecycleScope.launch {
            delay(IDLE_CLOSE_MS)
            Log.i("menu", "idle_close")
            close()
        }
    }

    fun onResume() {
        refresh.intValue++
    }

    /** Leaving the app closes PIN/panel; the wizard survives system dialogs (role / battery prompts). */
    fun onStop() {
        if (screen == Screen.WIZARD) return
        close()
    }

    private fun needsSetup(config: KioskConfig) = config.adminPinHash.isEmpty() || config.startUrl.isEmpty()

    private fun showPin(next: Screen) {
        mount(Screen.PIN) {
            PinDialog(
                onVerified = { if (next == Screen.WIZARD) showWizard() else showPanel() },
                onCancel = { close() },
            )
        }
    }

    private fun showWizard() {
        mount(Screen.WIZARD) {
            SetupWizard(
                refresh = refresh.intValue,
                onRequestHome = { launcher.launch(HomeRole.requestIntent(activity)) },
                onRequestBattery = { launcher.launch(BatteryOptimization.requestIntent(activity)) },
                onClose = { close() },
            )
        }
    }

    private fun showPanel() {
        mount(Screen.PANEL) { AdminPanel(actions = actions, onClose = { close() }) }
    }

    private fun mount(target: Screen, content: @androidx.compose.runtime.Composable () -> Unit) {
        for (i in 0 until container.childCount) (container.getChildAt(i) as? ComposeView)?.disposeComposition()
        container.removeAllViews()
        screen = target
        val view = ComposeView(activity).apply { setContent { MenuTheme(content) } }
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        touch()
    }

}
