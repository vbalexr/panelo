// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Schedule alarm, plus re-arming after clock / time zone changes and app updates. */
class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Schedule.ACTION_FIRE) Schedule.fire(context, intent.getLongExtra(Schedule.EXTRA_AT, 0L))
        Schedule.arm(context)
    }
}
