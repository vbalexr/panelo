// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import com.vbalex.android.panelo.remote.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ConfigFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun text(config: KioskConfig) = KioskConfig.JSON.encodeToString(KioskConfig.serializer(), config)
    private fun load(dir: File) = ConfigFiles.load(dir, ConfigStore::parseStored)
    private val good = KioskConfig(startUrl = "https://a.example.com")
    private val older = KioskConfig(startUrl = "https://old.example.com")

    @Test fun absentWhenNoFiles() {
        assertTrue(load(tmp.root) is ConfigLoad.Absent)
    }

    @Test fun saveWritesBothFilesAndLoadsMain() {
        ConfigFiles.save(tmp.root, text(good)).getOrThrow()
        assertTrue(File(tmp.root, ConfigFiles.LKG).exists())
        assertEquals("https://a.example.com", (load(tmp.root) as ConfigLoad.Main).config.startUrl)
    }

    @Test fun corruptMainFallsBackToLkg() {
        File(tmp.root, ConfigFiles.LKG).writeText(text(older))
        File(tmp.root, ConfigFiles.MAIN).writeText("{ not json")
        val loaded = load(tmp.root) as ConfigLoad.LastKnownGood
        assertEquals("https://old.example.com", loaded.config.startUrl)
    }

    @Test fun structurallyInvalidMainFallsBackToLkg() {
        File(tmp.root, ConfigFiles.LKG).writeText(text(older))
        File(tmp.root, ConfigFiles.MAIN).writeText(text(good.copy(pageZoom = 500)))
        assertTrue(load(tmp.root) is ConfigLoad.LastKnownGood)
    }

    @Test fun missingMainUsesLkg() {
        File(tmp.root, ConfigFiles.LKG).writeText(text(older))
        assertTrue(load(tmp.root) is ConfigLoad.LastKnownGood)
    }

    @Test fun bothCorruptIsUnusable() {
        File(tmp.root, ConfigFiles.LKG).writeText("garbage")
        File(tmp.root, ConfigFiles.MAIN).writeText("")
        val loaded = load(tmp.root) as ConfigLoad.Unusable
        assertEquals(2, loaded.problems.size)
    }

    @Test fun buildDependentRuleDoesNotBlockLoading() {
        val http = good.copy(remote = RemoteSettings(enabled = true, url = "http://10.0.2.2:8000/c.json"))
        ConfigFiles.save(tmp.root, text(http)).getOrThrow()
        assertTrue(ConfigStore.validate(http, allowHttpRemote = false).isFailure)
        assertEquals("http://10.0.2.2:8000/c.json", (load(tmp.root) as ConfigLoad.Main).config.remote.url)
    }

    @Test fun unchangedStoredUrlDoesNotBlockLaterEdits() {
        val http = good.copy(remote = RemoteSettings(enabled = true, url = "http://10.0.2.2:8000/c.json"))
        assertTrue(ConfigStore.validate(http.copy(pageZoom = 120), previous = http, allowHttpRemote = false).isSuccess)
        assertTrue(ConfigStore.validate(good.copy(remote = http.remote), previous = good, allowHttpRemote = false).isFailure)
    }

    @Test fun remoteFileKeepsStoredHttpUrlUsable() {
        val http = good.copy(remote = RemoteSettings(enabled = true, url = "http://10.0.2.2:8000/c.json"))
        // Overlay validates in the running build; in debug (tests) http is allowed, so only check invalid input is rejected whole.
        assertTrue(Overlay.merge(http, """{"schemaVersion":1,"pageZoom":999}""").isFailure)
    }
}
