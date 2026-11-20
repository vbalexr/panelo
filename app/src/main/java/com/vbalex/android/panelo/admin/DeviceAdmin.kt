// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.vbalex.android.panelo.log.Log

/** Plain (non-owner) Device Admin: only the force-lock policy, used for a real screen-off. */
object DeviceAdmin {
    fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

    fun isActive(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(component(context))

    /** Opens the system "Activate device admin app?" screen. Result is observed via [isActive] on resume. */
    fun requestEnable(activity: Activity): Result<Unit> {
        if (isActive(activity)) return Result.success(Unit)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, component(activity))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Lets Panelo turn the screen off for real (lock the device). No other control is requested.")
        try {
            activity.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w("device_admin", "enable_unavailable")
            return Result.failure(e)
        }
        Log.i("device_admin", "enable_requested")
        return Result.success(Unit)
    }

    fun remove(context: Context): Result<Unit> {
        if (!isActive(context)) return Result.success(Unit)
        if (DeviceOwner.isOwner(context)) return Result.failure(IllegalStateException("Device Owner is active; remove it instead"))
        context.getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(component(context))
        Log.i("device_admin", "removed")
        return Result.success(Unit)
    }

    /** Real screen off (phase 6). Works for Device Admin and Device Owner. */
    fun lockNow(context: Context): Result<Unit> {
        if (!isActive(context)) return Result.failure(IllegalStateException("Device Admin not active"))
        val result = runCatching { context.getSystemService(DevicePolicyManager::class.java).lockNow() }
        result.onSuccess { Log.i("device_admin", "lock_now") }.onFailure { Log.e("device_admin", "lock_now_failed", "error" to it.toString()) }
        return result
    }
}
