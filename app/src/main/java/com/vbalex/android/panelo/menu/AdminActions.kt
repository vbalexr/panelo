// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.menu

import android.content.ActivityNotFoundException
import android.content.Intent
import com.vbalex.android.panelo.MainActivity
import com.vbalex.android.panelo.admin.LockTask
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.recovery.Recovery
import com.vbalex.android.panelo.recovery.Snapshot
import com.vbalex.android.panelo.web.WebHost

/** Quick actions of the admin panel. */
class AdminActions(
    val activity: MainActivity,
    private val host: WebHost,
    private val recovery: Recovery,
    private val snapshot: Snapshot,
    private val entry: () -> String,
) {
    /** Starts an intent whose result matters (role requests); set by [AdminMenu]. */
    var forResult: (Intent) -> Unit = { launch(it) }

    fun reload() {
        Log.i("admin", "reload")
        recovery.reload()
    }

    fun goToStart() {
        Log.i("admin", "go_to_start")
        recovery.start(entry())
    }

    fun clearCache() {
        Log.i("admin", "clear_cache")
        host.clearCache()
    }

    fun clearData() {
        Log.i("admin", "clear_data")
        host.clearData()
        snapshot.clear()
        recovery.start(entry())
    }

    /** Opens the system Home chooser so another launcher can be picked (until the next Home press/boot). */
    fun exitKiosk(): Result<Unit> {
        Log.i("admin", "exit_kiosk")
        LockTask.suspend(activity)
        return launch(HomeRole.settingsIntent())
    }

    fun launch(intent: Intent): Result<Unit> {
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("admin", "launch_failed", "action" to intent.action)
            return Result.failure(e)
        }
        return Result.success(Unit)
    }
}
