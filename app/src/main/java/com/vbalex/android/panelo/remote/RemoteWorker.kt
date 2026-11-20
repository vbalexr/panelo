// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.vbalex.android.panelo.config.ConfigStore
import java.util.concurrent.TimeUnit

/**
 * Backup poller (WorkManager periodic, at least 15 min) for when the activity is not in the foreground.
 * It fetches and applies; MainActivity picks changes up through ConfigStore.state and RemoteState.pending.
 */
class RemoteWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!ConfigStore.state.value.remote.enabled) return Result.success()
        RemoteConfig.pull(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "panelo_remote_config"
        private const val MIN_PERIOD_MIN = 15L

        fun schedule(context: Context, intervalMin: Int) {
            val request = PeriodicWorkRequestBuilder<RemoteWorker>(maxOf(intervalMin.toLong(), MIN_PERIOD_MIN), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
