// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.admin

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import com.vbalex.android.panelo.MainActivity
import com.vbalex.android.panelo.config.KioskConfig
import com.vbalex.android.panelo.log.Log

/** Level 3: Device Owner policies. Everything here is a no-op error unless this app is the Device Owner. */
object DeviceOwner {
    /** BatteryManager.BATTERY_PLUGGED_AC | USB | WIRELESS */
    private const val STAY_ON_ALL = "7"
    private val PERMISSIONS = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun isOwner(context: Context): Boolean =
        context.getSystemService(DevicePolicyManager::class.java).isDeviceOwnerApp(context.packageName)

    /** Applies all kiosk policies. Individual policy failures are logged and reported together. */
    fun apply(context: Context, config: KioskConfig): Result<Unit> {
        if (!isOwner(context)) return Result.failure(NotOwner())
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = DeviceAdmin.component(context)
        val failures = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                failures += name
                Log.e("device_owner", "policy_failed", "policy" to name, "error" to it.toString())
            }
        }

        step("lock_task_packages") { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val features = if (config.allowPowerMenu) DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS else DevicePolicyManager.LOCK_TASK_FEATURE_NONE
            step("lock_task_features") { dpm.setLockTaskFeatures(admin, features) }
        }
        step("home_preference") {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(admin, filter, ComponentName(context, MainActivity::class.java))
        }
        step("status_bar") { if (!dpm.setStatusBarDisabled(admin, true)) error("rejected") }
        step("keyguard") { if (!dpm.setKeyguardDisabled(admin, true)) error("rejected (secure lock set?)") }
        step("stay_on") {
            dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, if (config.keepScreenOn) STAY_ON_ALL else "0")
        }
        step("permissions") {
            for (permission in PERMISSIONS) {
                dpm.setPermissionGrantState(admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        }
        Log.i("device_owner", "applied", "failed" to failures.joinToString(","), "powerMenu" to config.allowPowerMenu)
        if (failures.isNotEmpty()) return Result.failure(PolicyFailed(failures))
        return Result.success(Unit)
    }

    /** Undoes [apply]. [activity] (when given) is used to stop lock task mode first. */
    fun release(context: Context, activity: Activity?): Result<Unit> {
        if (!isOwner(context)) return Result.failure(NotOwner())
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = DeviceAdmin.component(context)
        val failures = mutableListOf<String>()
        fun step(name: String, block: () -> Unit) {
            runCatching(block).onFailure {
                failures += name
                Log.e("device_owner", "release_failed", "policy" to name, "error" to it.toString())
            }
        }

        if (activity != null) step("stop_lock_task") { LockTask.stop(activity) }
        step("home_preference") { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
        step("status_bar") { dpm.setStatusBarDisabled(admin, false) }
        step("keyguard") { dpm.setKeyguardDisabled(admin, false) }
        step("stay_on") { dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0") }
        step("permissions") {
            for (permission in PERMISSIONS) {
                dpm.setPermissionGrantState(admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
            }
        }
        step("lock_task_packages") { dpm.setLockTaskPackages(admin, emptyArray()) }
        Log.i("device_owner", "released", "failed" to failures.joinToString(","))
        if (failures.isNotEmpty()) return Result.failure(PolicyFailed(failures))
        return Result.success(Unit)
    }

    /** Reboots the device (API 24+, Device Owner only). */
    fun reboot(context: Context): Result<Unit> {
        if (!isOwner(context)) return Result.failure(NotOwner())
        Log.i("device_owner", "reboot")
        return runCatching { context.getSystemService(DevicePolicyManager::class.java).reboot(DeviceAdmin.component(context)) }
    }

    /** Release + give up Device Owner (the app can then be uninstalled normally). */
    @Suppress("DEPRECATION")
    fun clear(activity: Activity): Result<Unit> {
        if (!isOwner(activity)) return Result.failure(NotOwner())
        release(activity, activity)
        val result = runCatching { activity.getSystemService(DevicePolicyManager::class.java).clearDeviceOwnerApp(activity.packageName) }
        result.onSuccess { Log.i("device_owner", "cleared") }.onFailure { Log.e("device_owner", "clear_failed", "error" to it.toString()) }
        return result
    }

    class NotOwner : IllegalStateException("This app is not the Device Owner")
    class PolicyFailed(val policies: List<String>) : IllegalStateException("Policies failed: ${policies.joinToString()}")
}
