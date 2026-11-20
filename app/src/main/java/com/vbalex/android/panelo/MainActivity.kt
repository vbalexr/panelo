// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo

import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vbalex.android.panelo.admin.LockTask
import com.vbalex.android.panelo.config.ConfigStore
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.config.Orientation
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.menu.AdminActions
import com.vbalex.android.panelo.menu.AdminMenu
import com.vbalex.android.panelo.menu.EdgeSwipe
import com.vbalex.android.panelo.power.Dimmer
import com.vbalex.android.panelo.power.Presence
import com.vbalex.android.panelo.power.Schedule
import com.vbalex.android.panelo.power.ScreenOff
import com.vbalex.android.panelo.recovery.Connectivity
import com.vbalex.android.panelo.remote.RemotePoller
import com.vbalex.android.panelo.remote.RemoteState
import com.vbalex.android.panelo.recovery.Recovery
import com.vbalex.android.panelo.recovery.Snapshot
import com.vbalex.android.panelo.recovery.Watchdog
import com.vbalex.android.panelo.web.Heartbeat
import com.vbalex.android.panelo.web.WebChrome
import com.vbalex.android.panelo.web.WebHost
import com.vbalex.android.panelo.web.WebPolicy
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Single activity: Home/launcher, hosts the WebView layer and overlays. */
class MainActivity : ComponentActivity() {
    private lateinit var host: WebHost
    private lateinit var chrome: WebChrome
    private lateinit var recovery: Recovery
    private lateinit var heartbeat: Heartbeat
    private lateinit var watchdog: Watchdog
    private lateinit var connectivity: Connectivity
    private lateinit var menu: AdminMenu
    private lateinit var actions: AdminActions
    private lateinit var poller: RemotePoller
    private lateinit var edgeSwipe: EdgeSwipe

    lateinit var dimmer: Dimmer
        private set
    lateinit var presence: Presence
        private set
    lateinit var screenOff: ScreenOff
        private set

    private lateinit var overlay: View
    private lateinit var snapshotImage: ImageView
    private lateinit var noSnapshot: LinearLayout
    private lateinit var offlineBar: LinearLayout
    private lateinit var offlineText: TextView

    /** Admin menu overlay container (Compose views are added lazily by [AdminMenu]). */
    lateinit var menuContainer: FrameLayout
        private set

    /** Extension point (phase 6): true while a page uses the camera. */
    val cameraInUse: StateFlow<Boolean> get() = chrome.cameraInUse

    /** Extension point (phase 3+): recovery state. */
    val recoveryState: StateFlow<Recovery.State> get() = recovery.state

    private var loadedEntry: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        setContentView(R.layout.activity_main)
        bindViews()
        applyDebugUrl(intent)

        chrome = WebChrome(this, findViewById(R.id.fullscreen_container)) { url -> host.load(url) }
        host = WebHost(this, findViewById(R.id.web_container), chrome) { recovery }
        val snapshot = Snapshot(this)
        recovery = Recovery(this, host, snapshot) { entryUrl(ConfigStore.state.value) }
        heartbeat = Heartbeat(lifecycleScope, host, { recovery.isLive }) { recovery.recreate("heartbeat_timeout") }
        watchdog = Watchdog(lifecycleScope, onReload = { recovery.reload() }, onIdle = { recovery.start(entryUrl(ConfigStore.state.value)) })
        poller = RemotePoller(this, lifecycleScope)
        dimmer = Dimmer(this)
        presence = Presence(this, chrome.cameraInUse)
        screenOff = ScreenOff(this, findViewById(R.id.screen_off), dimmer, presence)
        connectivity = Connectivity(this) {
            recovery.onNetworkAvailable()
            poller.onNetworkAvailable()
        }

        actions = AdminActions(this, host, recovery, snapshot) { entryUrl(ConfigStore.state.value) }
        menu = AdminMenu(this, menuContainer, actions)
        edgeSwipe = EdgeSwipe(this, findViewById(R.id.root), onSwipe = { menu.open() }, cancelPage = { window.superDispatchTouchEvent(it) })
        edgeSwipe.attach()

        host.create()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (menu.isOpen) {
                    menu.close()
                    return
                }
                if (chrome.isFullscreen) {
                    chrome.exitFullscreen()
                    return
                }
                host.goBack()
            }
        })
        lifecycleScope.launch { recovery.state.collect { render(it) } }
        lifecycleScope.launch { ConfigStore.state.collect { applyConfig(it) } }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { RemoteState.pending.collect { runRemoteCommands(it) } } }
        if (savedInstanceState == null) menuContainer.post { menu.openIfSetupNeeded() }
        Log.i("activity", "created")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDebugUrl(intent)
        if (intent.getBooleanExtra(Schedule.EXTRA_EVAL, false)) screenOff.evaluate()
        if (intent.hasCategory(Intent.CATEGORY_HOME)) LockTask.resume()
    }

    override fun onStart() {
        super.onStart()
        connectivity.start()
        poller.start()
        heartbeat.start(ConfigStore.state.value)
        dimmer.start()
        presence.start()
        screenOff.start()
    }

    override fun onStop() {
        screenOff.stop()
        presence.stop()
        dimmer.stop()
        menu.onStop()
        heartbeat.stop()
        poller.stop()
        connectivity.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        menu.onResume()
        host.resume()
        LockTask.sync(this, ConfigStore.state.value)
    }

    override fun onPause() {
        host.pause()
        super.onPause()
    }

    override fun onDestroy() {
        host.destroy()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        applyImmersive(ConfigStore.state.value)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) LockTask.sync(this, ConfigStore.state.value)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        host.apply(ConfigStore.state.value)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (screenOff.onTouch(ev)) return true
        watchdog.touch()
        menu.touch()
        if (!menu.isOpen && edgeSwipe.onTouch(ev)) return true
        return super.dispatchTouchEvent(ev)
    }

    /** Key / touch interaction while the menu is open (typing in a field keeps the panel alive). */
    override fun onUserInteraction() {
        super.onUserInteraction()
        menu.touch()
    }

    /** Apply a new config at runtime (called on every ConfigStore change; also the hook for later phases). */
    private fun applyConfig(config: KioskConfig) {
        requestedOrientation = Orientation.requested(config.orientation)
        if (config.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersive(config)
        edgeSwipe.apply(config)
        host.apply(config)
        heartbeat.start(config)
        watchdog.apply(config)
        recovery.applyConfig()
        dimmer.apply(config)
        presence.apply(config)
        screenOff.evaluate()
        Schedule.arm(this, config)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) LockTask.sync(this, config)
        val entry = entryUrl(config)
        if (entry == loadedEntry) return
        loadedEntry = entry
        recovery.start(entry)
    }

    /** One-shot commands from remote nonces (clear data already reloads, clear cache is implied by it). */
    private fun runRemoteCommands(commands: Set<String>) {
        if (commands.isEmpty()) return
        Log.i("remote", "run_commands", "commands" to commands.joinToString(","))
        RemoteState.clearPending(commands)
        if (RemoteState.CLEAR_DATA in commands) {
            actions.clearData()
            return
        }
        if (RemoteState.CLEAR_CACHE in commands) actions.clearCache()
        if (RemoteState.RELOAD in commands) actions.reload()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
    }

    private fun entryUrl(config: KioskConfig): String =
        config.startUrl.ifEmpty { WebPolicy.ASSET_PREFIX + "not_configured.html" }

    private fun applyImmersive(config: KioskConfig) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (!config.immersive) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Debug builds only: `adb shell am start -n com.vbalex.android.panelo/.MainActivity --es url https://...` */
    private fun applyDebugUrl(intent: Intent) {
        if (!BuildConfig.DEBUG) return
        val url = intent.getStringExtra("url")?.takeIf { it.isNotEmpty() } ?: return
        ConfigStore.update { it.copy(startUrl = url) }
            .onFailure { Log.w("activity", "debug_url_rejected", "error" to it.message) }
    }

    private fun bindViews() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            // The keyboard is handled by windowSoftInputMode=adjustResize (adding ime insets here would count it twice).
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        overlay = findViewById(R.id.overlay)
        snapshotImage = findViewById(R.id.snapshot_image)
        noSnapshot = findViewById(R.id.no_snapshot)
        offlineBar = findViewById(R.id.offline_bar)
        offlineText = findViewById(R.id.offline_text)
        menuContainer = findViewById(R.id.menu_container)
        findViewById<Button>(R.id.offline_retry).setOnClickListener { recovery.retryNow() }
        findViewById<Button>(R.id.no_snapshot_retry).setOnClickListener { recovery.retryNow() }
    }

    private fun render(state: Recovery.State) {
        val showingSnapshot = state is Recovery.State.ShowingSnapshot
        val showingNone = state is Recovery.State.NoSnapshot
        overlay.visibility = if (showingSnapshot || showingNone) View.VISIBLE else View.GONE
        snapshotImage.visibility = if (showingSnapshot) View.VISIBLE else View.GONE
        offlineBar.visibility = if (showingSnapshot) View.VISIBLE else View.GONE
        noSnapshot.visibility = if (showingNone) View.VISIBLE else View.GONE
        if (state !is Recovery.State.ShowingSnapshot) {
            snapshotImage.setImageDrawable(null)
            return
        }
        snapshotImage.setImageBitmap(state.shot.bitmap)
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.shot.savedAtMs))
        offlineText.text = getString(R.string.offline_saved, time)
    }
}
