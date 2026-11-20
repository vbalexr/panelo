// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.admin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vbalex.android.panelo.MainActivity
import com.vbalex.android.panelo.log.Log
import com.vbalex.android.panelo.power.Schedule

/**
 * Backup start after boot (the Home role normally does it). Android 10+ restricts background activity
 * starts, so this may be refused for a non-owner app; the outcome is logged either way.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != QUICKBOOT) return
        Log.i("boot", "received", "action" to action, "owner" to DeviceOwner.isOwner(context), "admin" to DeviceAdmin.isActive(context))
        Schedule.arm(context)
        val start = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(start) }
            .onSuccess { Log.i("boot", "activity_start_requested") }
            .onFailure { Log.w("boot", "activity_start_failed", "error" to it.toString()) }
    }

    private companion object {
        const val QUICKBOOT = "android.intent.action.QUICKBOOT_POWERON"
    }
}
