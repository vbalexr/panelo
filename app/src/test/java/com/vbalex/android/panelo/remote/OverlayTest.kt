// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import com.vbalex.android.panelo.config.ConfigInvalid
import com.vbalex.android.panelo.config.KioskConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class OverlayTest {
    private val base = KioskConfig(startUrl = "https://a.example.com", pageZoom = 90, autoDim = com.vbalex.android.panelo.config.AutoDimConfig(enabled = true, min = 0.2f))
    private val hash = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
    private val salt = Base64.getEncoder().encodeToString(ByteArray(16) { 2 })

    private fun problems(result: Result<Overlay.Merged>): List<String> = (result.exceptionOrNull() as ConfigInvalid).problems

    @Test fun partialOverlayKeepsOtherKeys() {
        val merged = Overlay.merge(base, """{"schemaVersion":1,"pageZoom":70}""").getOrThrow()
        assertEquals(70, merged.config.pageZoom)
        assertEquals("https://a.example.com", merged.config.startUrl)
        assertEquals(listOf("pageZoom"), merged.changedKeys)
    }

    @Test fun nestedObjectsMergePerKey() {
        val merged = Overlay.merge(base, """{"schemaVersion":1,"autoDim":{"max":0.8}}""").getOrThrow()
        assertTrue(merged.config.autoDim.enabled)
        assertEquals(0.2f, merged.config.autoDim.min)
        assertEquals(0.8f, merged.config.autoDim.max)
        assertEquals(listOf("autoDim.max"), merged.changedKeys)
    }

    @Test fun unknownKeysIgnoredAndReported() {
        val merged = Overlay.merge(base, """{"schemaVersion":1,"futureThing":5,"autoDim":{"x":1}}""").getOrThrow()
        assertEquals(listOf("futureThing", "autoDim.x"), merged.ignoredKeys)
        assertTrue(merged.changedKeys.isEmpty())
    }

    @Test fun remoteCannotDisableOrRedirectItself() {
        val current = base.copy(remote = base.remote.copy(enabled = true, url = "https://x.example.com/c.json"))
        val merged = Overlay.merge(current, """{"schemaVersion":1,"remote":{"enabled":false,"url":"https://evil.example.com","pollIntervalMin":30}}""").getOrThrow()
        assertTrue(merged.config.remote.enabled)
        assertEquals("https://x.example.com/c.json", merged.config.remote.url)
        assertEquals(30, merged.config.remote.pollIntervalMin)
    }

    @Test fun schemaVersionRequired() {
        assertTrue(Overlay.merge(base, """{"pageZoom":70}""").isFailure)
        assertTrue(Overlay.merge(base, """{"schemaVersion":2}""").isFailure)
    }

    @Test fun invalidValuesAreRejected() {
        assertTrue(problems(Overlay.merge(base, """{"schemaVersion":1,"pageZoom":500}""")).any { "pageZoom" in it })
        assertTrue(Overlay.merge(base, """{"schemaVersion":1,"pageZoom":"big"}""").isFailure)
        assertTrue(Overlay.merge(base, "not json").isFailure)
        assertTrue(Overlay.merge(base, "[1]").isFailure)
        assertTrue(problems(Overlay.merge(base, """{"schemaVersion":1,"remote":{"pollIntervalMin":1}}""")).any { "pollIntervalMin" in it })
    }

    @Test fun pinHashAndSaltTogether() {
        val ok = Overlay.merge(base, """{"schemaVersion":1,"adminPinHash":"$hash","adminPinSalt":"$salt"}""").getOrThrow()
        assertEquals(hash, ok.config.adminPinHash)
        assertTrue(Overlay.merge(base, """{"schemaVersion":1,"adminPinHash":"$hash"}""").isFailure)
        assertTrue(Overlay.merge(base, """{"schemaVersion":1,"adminPinHash":"abc","adminPinSalt":"$salt"}""").isFailure)
    }

    @Test fun plaintextPinRejected() {
        assertTrue(problems(Overlay.merge(base, """{"schemaVersion":1,"adminPin":"1234"}""")).any { "plaintext" in it })
    }

    @Test fun lockedKeysTakenFromRemote() {
        val merged = Overlay.merge(base, """{"schemaVersion":1,"lockedKeys":["startUrl"]}""").getOrThrow()
        assertEquals(listOf("startUrl"), merged.config.lockedKeys)
    }

    @Test fun changedTopLevelDetectsEdits() {
        assertEquals(setOf("pageZoom", "autoDim"), Overlay.changedTopLevel(base, base.copy(pageZoom = 80, autoDim = base.autoDim.copy(max = 0.5f))))
    }
}
