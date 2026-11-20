// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import com.vbalex.android.panelo.config.KioskConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoncesTest {
    @Test fun firstSightOnlyRecords() {
        val now = Nonces.of(KioskConfig(clearDataNonce = 7))
        assertTrue(Nonces.due(emptyMap(), now).isEmpty())
    }

    @Test fun changedValueIsDue() {
        val last = Nonces.of(KioskConfig(reloadNonce = 1, clearCacheNonce = 4))
        val now = Nonces.of(KioskConfig(reloadNonce = 2, clearCacheNonce = 4))
        assertEquals(listOf(Nonces.RELOAD), Nonces.due(last, now))
    }

    @Test fun unchangedIsNotDue() {
        val same = Nonces.of(KioskConfig(rebootNonce = 3))
        assertTrue(Nonces.due(same, same).isEmpty())
    }

    @Test fun newlySeenNonceDoesNotFire() {
        assertTrue(Nonces.due(mapOf(Nonces.RELOAD to 1L), Nonces.of(KioskConfig(reloadNonce = 1, clearDataNonce = 5))).isEmpty())
    }
}
