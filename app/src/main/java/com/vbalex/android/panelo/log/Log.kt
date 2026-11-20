// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.log

import android.content.Context
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import android.util.Log as Logcat

/**
 * Structured JSON logging: one JSON object per line to Logcat and a rolling file
 * (kiosk.log + kiosk.log.1, each capped at [MAX_FILE_BYTES]) in app-private storage.
 * Line shape: {"ts","level","component","event","fields":{...}}
 */
object Log {
    private const val TAG = "Panelo"
    private const val MAX_FILE_BYTES = 512L * 1024

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "kiosk-log").apply { isDaemon = true }
    }

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        val logDir = File(context.filesDir, "logs")
        logDir.mkdirs()
        dir = logDir
    }

    fun d(component: String, event: String, vararg fields: Pair<String, Any?>) =
        write(Logcat.DEBUG, "D", component, event, fields)

    fun i(component: String, event: String, vararg fields: Pair<String, Any?>) =
        write(Logcat.INFO, "I", component, event, fields)

    fun w(component: String, event: String, vararg fields: Pair<String, Any?>) =
        write(Logcat.WARN, "W", component, event, fields)

    fun e(component: String, event: String, vararg fields: Pair<String, Any?>) =
        write(Logcat.ERROR, "E", component, event, fields)

    /** Log files, oldest first (for export). */
    fun files(): List<File> {
        val logDir = dir ?: return emptyList()
        return listOf(File(logDir, "kiosk.log.1"), File(logDir, "kiosk.log")).filter { it.exists() }
    }

    /** Last [maxLines] JSON lines, oldest first. Blocking IO: call off the main thread. */
    fun read(maxLines: Int = 2000): List<String> {
        val lines = files().flatMap { file -> runCatching { file.readLines() }.getOrDefault(emptyList()) }
        return lines.takeLast(maxLines)
    }

    /** Wait (briefly) until queued lines are on disk. Used by the crash handler. */
    fun flush() {
        runCatching { executor.submit { }.get(1500, TimeUnit.MILLISECONDS) }
    }

    private fun write(priority: Int, level: String, component: String, event: String, fields: Array<out Pair<String, Any?>>) {
        val line = buildJsonObject {
            put("ts", Instant.now().toString())
            put("level", level)
            put("component", component)
            put("event", event)
            put("fields", JsonObject(fields.associate { (key, value) -> key to toJson(value) }))
        }.toString()
        Logcat.println(priority, TAG, line)
        val logDir = dir ?: return
        executor.execute { append(logDir, line) }
    }

    private fun toJson(value: Any?) = when (value) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private fun append(logDir: File, line: String) {
        val current = File(logDir, "kiosk.log")
        runCatching {
            if (current.length() >= MAX_FILE_BYTES) {
                val old = File(logDir, "kiosk.log.1")
                old.delete()
                current.renameTo(old)
            }
            current.appendText(line + "\n")
        }
    }
}
