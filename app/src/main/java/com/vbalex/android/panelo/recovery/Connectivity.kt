// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.recovery

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper

/** Default-network callback wrapper; [onAvailable] is invoked on the main thread. */
class Connectivity(context: Context, private val onAvailable: () -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onAvailable()
    }

    fun start() {
        if (registered) return
        manager.registerDefaultNetworkCallback(callback, handler)
        registered = true
    }

    fun stop() {
        if (!registered) return
        manager.unregisterNetworkCallback(callback)
        registered = false
    }
}
