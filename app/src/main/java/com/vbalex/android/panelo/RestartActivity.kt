// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Lives in its own process (":restart") so it survives a crash of the main process. Shown (black) while
 * waiting, then relaunches [MainActivity]; being visible, it is allowed to start it from "the background".
 */
class RestartActivity : Activity() {
    companion object {
        const val EXTRA_DELAY_MS = "delayMs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 0L)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            finish()
        }, delayMs)
    }
}
