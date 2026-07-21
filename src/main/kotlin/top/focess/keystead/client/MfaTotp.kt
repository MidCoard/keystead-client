package top.focess.keystead.client

import java.time.Instant
import top.focess.keystead.generator.DefaultTotpCodeGenerator
import top.focess.keystead.memory.SecretBuffer

/**
 * Generates the current RFC 6238 TOTP code for an MFA secret on the client.
 *
 * The seed is the Base32 string stored in the vault secret's `seed` field; the digit count and
 * period are read from the `otpauth` URI (defaulting to 6 digits / 30 seconds, matching what
 * [MfaSecretDraftGenerator] emits). HMAC-SHA1 is used, matching the `algorithm=SHA1` in the URI.
 * The seed is held in a [SecretBuffer] only for the duration of the computation and the returned
 * code is a short-lived UI value.
 */
internal object MfaTotp {
    private val generator = DefaultTotpCodeGenerator()

    /** Parses the `digits` parameter from the otpauth URI, defaulting to 6. */
    fun digits(uri: String?): Int = queryInt(uri, "digits", 6).coerceIn(6, 8)

    /** Parses the `period` parameter from the otpauth URI, defaulting to 30. */
    fun period(uri: String?): Int = queryInt(uri, "period", 30).takeIf { it > 0 } ?: 30

    /** Seconds remaining in the current TOTP period. */
    fun secondsRemaining(periodSeconds: Int, now: Instant): Int {
        val period = if (periodSeconds > 0) periodSeconds else 30
        return period - (now.epochSecond % period).toInt()
    }

    /**
     * Generates the current code, or `null` if the seed is blank or cannot be decoded.
     */
    fun currentCode(seed: String, uri: String?, now: Instant): String? {
        if (seed.isBlank()) return null
        val chars = seed.toCharArray()
        return try {
            SecretBuffer.fromChars(chars).use { buffer ->
                val code = generator.generate(digits(uri), period(uri), buffer, now)
                try {
                    String(code)
                } finally {
                    code.fill(0.toChar())
                }
            }
        } catch (_: RuntimeException) {
            null
        } finally {
            chars.fill(0.toChar())
        }
    }

    private fun queryInt(uri: String?, name: String, default: Int): Int {
        if (uri == null) return default
        val key = "$name="
        val start = uri.indexOf(key)
        if (start < 0) return default
        val valueStart = start + key.length
        val valueEnd = uri.indexOfAny(charArrayOf('&', '#'), valueStart)
        val text =
            if (valueEnd < 0) {
                uri.substring(valueStart)
            } else {
                uri.substring(valueStart, valueEnd)
            }
        return text.toIntOrNull() ?: default
    }
}
