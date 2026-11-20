// SPDX-FileCopyrightText: 2026 Alex Vargas
// SPDX-License-Identifier: GPL-3.0-or-later
package com.vbalex.android.panelo.config

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 salted PIN hashing. Hash and salt are base64 strings. */
object Pin {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    fun salt(): String {
        val bytes = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hash(pin: String, salt: String): Result<String> = runCatching {
        val saltBytes = Base64.decode(salt, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_BITS)
        try {
            val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            Base64.encodeToString(key, Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    /** Constant-time compare. False when no PIN is set or hashing fails. */
    fun verify(pin: String, hash: String, salt: String): Boolean {
        if (hash.isEmpty() || salt.isEmpty()) return false
        val computed = hash(pin, salt).getOrElse { return false }
        return MessageDigest.isEqual(computed.toByteArray(), hash.toByteArray())
    }

    /** JSON snippet with the current hash + salt, ready to paste into a remote config file. Empty when no PIN is set. */
    fun snippet(config: KioskConfig): String =
        "{\"adminPinHash\": \"${config.adminPinHash}\", \"adminPinSalt\": \"${config.adminPinSalt}\"}"

    fun isValidFormat(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }

    /** Hash [pin] with a fresh salt and store it. Blocking (PBKDF2): call off the main thread. */
    fun change(pin: String): Result<KioskConfig> {
        if (!isValidFormat(pin)) return Result.failure(ConfigInvalid(listOf("PIN must be 4-8 digits")))
        val salt = salt()
        val hash = hash(pin, salt).getOrElse { return Result.failure(it) }
        return ConfigStore.update { it.copy(adminPinHash = hash, adminPinSalt = salt) }
    }
}
