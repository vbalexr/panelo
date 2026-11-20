// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUrlTest {
    @Test fun httpsOnlyUnlessDebug() {
        assertTrue(RemoteUrl.isAllowed("https://example.com/c.json", false))
        assertFalse(RemoteUrl.isAllowed("http://example.com/c.json", false))
        assertTrue(RemoteUrl.isAllowed("http://10.0.2.2:8002/c.json", true))
        assertFalse(RemoteUrl.isAllowed("ftp://example.com/c.json", true))
        assertFalse(RemoteUrl.isAllowed("not a url", true))
    }

    @Test fun blobWithoutTokenBecomesRaw() {
        val r = RemoteUrl.resolve("https://github.com/o/r/blob/main/conf/kiosk.json", false)
        assertEquals("https://raw.githubusercontent.com/o/r/main/conf/kiosk.json", r.url)
        assertTrue(r.headers.isEmpty())
    }

    @Test fun blobWithTokenUsesApi() {
        val r = RemoteUrl.resolve("https://github.com/o/r/blob/main/conf/kiosk.json", true)
        assertEquals("https://api.github.com/repos/o/r/contents/conf/kiosk.json?ref=main", r.url)
        assertEquals("application/vnd.github.raw", r.headers["Accept"])
        assertEquals("2022-11-28", r.headers["X-GitHub-Api-Version"])
    }

    @Test fun rawWithTokenUsesApi() {
        assertEquals(
            "https://api.github.com/repos/o/r/contents/k.json?ref=dev",
            RemoteUrl.resolve("https://raw.githubusercontent.com/o/r/dev/k.json", true).url,
        )
        assertEquals(
            "https://api.github.com/repos/o/r/contents/a/k.json?ref=main",
            RemoteUrl.resolve("https://raw.githubusercontent.com/o/r/refs/heads/main/a/k.json", true).url,
        )
    }

    @Test fun rawWithoutTokenAndOthersUnchanged() {
        val raw = "https://raw.githubusercontent.com/o/r/main/k.json"
        assertEquals(raw, RemoteUrl.resolve(raw, false).url)
        val other = "https://example.com/k.json"
        assertEquals(other, RemoteUrl.resolve(other, true).url)
        assertEquals("https://github.com/o/r", RemoteUrl.resolve("https://github.com/o/r", true).url)
    }
}
