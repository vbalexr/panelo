// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Outcome of reading the persisted config (pure java.io, unit-testable). */
sealed interface ConfigLoad {
    /** Neither file exists: first run. */
    data object Absent : ConfigLoad

    /** The main file was good. */
    class Main(val config: KioskConfig) : ConfigLoad

    /** The main file was missing, unreadable or invalid; the last-known-good copy was used. */
    class LastKnownGood(val config: KioskConfig, val mainProblem: String) : ConfigLoad

    /** Both files exist but neither could be used: the caller falls back to defaults. */
    class Unusable(val problems: List<String>) : ConfigLoad
}

/**
 * Persistence of the config as two files: `config.json` and its last-known-good copy `config.json.lkg`.
 * Every save writes both (each through temp file + fsync + atomic rename).
 */
object ConfigFiles {
    const val MAIN = "config.json"
    const val LKG = "config.json.lkg"

    /** Write [text] to the main file and then to the last-known-good file. */
    fun save(dir: File, text: String): Result<Unit> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        writeAtomic(File(dir, MAIN), bytes).onFailure { return Result.failure(it) }
        writeAtomic(File(dir, LKG), bytes).onFailure { return Result.failure(it) }
        return Result.success(Unit)
    }

    /** Main file first, then the last-known-good copy; [parse] decides what counts as usable. */
    fun load(dir: File, parse: (String) -> Result<KioskConfig>): ConfigLoad {
        val main = File(dir, MAIN)
        val lkg = File(dir, LKG)
        if (!main.exists() && !lkg.exists()) return ConfigLoad.Absent

        val fromMain = read(main, parse)
        fromMain.getOrNull()?.let { return ConfigLoad.Main(it) }
        val mainProblem = "${MAIN}: ${why(fromMain.exceptionOrNull())}"

        val fromLkg = read(lkg, parse)
        fromLkg.getOrNull()?.let { return ConfigLoad.LastKnownGood(it, mainProblem) }
        return ConfigLoad.Unusable(listOf(mainProblem, "${LKG}: ${why(fromLkg.exceptionOrNull())}"))
    }

    private fun why(error: Throwable?): String = error?.message?.lineSequence()?.firstOrNull().orEmpty().take(160)

    private fun read(file: File, parse: (String) -> Result<KioskConfig>): Result<KioskConfig> {
        if (!file.exists()) return Result.failure(IOException("missing"))
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrElse { return Result.failure(it) }
        return parse(text)
    }

    private fun writeAtomic(target: File, bytes: ByteArray): Result<Unit> = runCatching {
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use {
            it.write(bytes)
            it.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        Unit
    }
}
