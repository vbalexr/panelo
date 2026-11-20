// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.remote

import java.net.URI

/**
 * Remote config URL rules and GitHub URL conversion (pure functions).
 *
 * GitHub handling:
 * - Without a token, a `github.com/{owner}/{repo}/blob/{branch}/{path}` URL is rewritten to
 *   `https://raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}` (the HTML page is not JSON).
 * - With a token, both blob URLs and `raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}` URLs are rewritten to the
 *   API contents endpoint `https://api.github.com/repos/{owner}/{repo}/contents/{path}?ref={branch}` and fetched with
 *   `Accept: application/vnd.github.raw` and `X-GitHub-Api-Version: 2022-11-28`. This is the only way to read a private
 *   repo, and it is not served from the raw CDN cache. The branch is the first path segment after the repo
 *   (branch names containing `/` are not supported; `refs/heads/{branch}` is understood).
 */
object RemoteUrl {
    const val API_VERSION = "2022-11-28"

    /** The URL to request plus extra request headers (the Authorization header is added by the caller). */
    class Resolved(val url: String, val headers: Map<String, String>)

    /** https always; http only when [allowHttp] (debug builds, local testing). */
    fun isAllowed(url: String, allowHttp: Boolean): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.host.isNullOrEmpty()) return false
        if (uri.scheme == "https") return true
        return allowHttp && uri.scheme == "http"
    }

    fun resolve(url: String, hasToken: Boolean): Resolved {
        val plain = Resolved(url, emptyMap())
        val uri = runCatching { URI(url) }.getOrNull() ?: return plain
        if (uri.scheme != "https") return plain
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        return when (uri.host) {
            "github.com", "www.github.com" -> fromBlob(segments, hasToken) ?: plain
            "raw.githubusercontent.com" -> fromRaw(segments, hasToken) ?: plain
            else -> plain
        }
    }

    private fun fromBlob(segments: List<String>, hasToken: Boolean): Resolved? {
        if (segments.size < 5 || segments[2] != "blob") return null
        val owner = segments[0]
        val repo = segments[1]
        val rest = segments.drop(3)
        if (hasToken) return api(owner, repo, rest)
        return Resolved("https://raw.githubusercontent.com/$owner/$repo/${rest.joinToString("/")}", emptyMap())
    }

    private fun fromRaw(segments: List<String>, hasToken: Boolean): Resolved? {
        if (!hasToken) return null
        if (segments.size < 4) return null
        return api(segments[0], segments[1], segments.drop(2))
    }

    /** [rest] = branch segment(s) followed by the file path. */
    private fun api(owner: String, repo: String, rest: List<String>): Resolved? {
        val branchLength = if (rest.size > 3 && rest[0] == "refs" && rest[1] == "heads") 3 else 1
        if (rest.size <= branchLength) return null
        val branch = rest.take(branchLength).last()
        val path = rest.drop(branchLength).joinToString("/")
        return Resolved(
            "https://api.github.com/repos/$owner/$repo/contents/$path?ref=$branch",
            mapOf("Accept" to "application/vnd.github.raw", "X-GitHub-Api-Version" to API_VERSION),
        )
    }
}
