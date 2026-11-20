// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.vbalex.android.panelo.log.Log

/** Device Admin / Device Owner receiver. Component: com.vbalex.android.panelo/.admin.AdminReceiver */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Log.i("admin_receiver", "enabled")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Log.i("admin_receiver", "disabled")
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i("admin_receiver", "profile_provisioning_complete")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i("admin_receiver", "lock_task_entering", "package" to pkg)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.i("admin_receiver", "lock_task_exiting")
    }
}
