package top.focess.keystead.client

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import top.focess.keystead.memory.Wipe

enum class PasswordStrength { WEAK, FAIR, STRONG }

sealed interface PasswordBreachResult {
    data object NotChecked : PasswordBreachResult
    data object Checking : PasswordBreachResult
    data object NotFound : PasswordBreachResult
    data class Found(val count: Int) : PasswordBreachResult
    data object Failed : PasswordBreachResult
}

object PasswordStrengthEvaluator {
    fun evaluate(password: CharSequence): PasswordStrength {
        if (password.length < 12) return PasswordStrength.WEAK
        val categories =
            listOf(
                password.any(Char::isLowerCase),
                password.any(Char::isUpperCase),
                password.any(Char::isDigit),
                password.any { !it.isLetterOrDigit() },
            ).count { it }
        return when {
            password.length >= 20 && categories >= 2 -> PasswordStrength.STRONG
            password.length >= 16 && categories >= 3 -> PasswordStrength.STRONG
            else -> PasswordStrength.FAIR
        }
    }
}

fun interface PasswordBreachLookup {
    fun prepare(password: CharArray): PasswordBreachQuery
}

interface PasswordBreachQuery : AutoCloseable {
    fun breachCount(): Int
}

class PwnedPasswordChecker(
    private val rangeEndpoint: URI = URI("https://api.pwnedpasswords.com/range/"),
    private val http: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : PasswordBreachLookup {
    /** Hashes the password locally and retains only the range prefix and wipeable hash suffix. */
    override fun prepare(password: CharArray): PasswordBreachQuery {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        var utf8 = ByteArray(0)
        var digest = ByteArray(0)
        var hash = CharArray(0)
        try {
            val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
            utf8 = ByteArray(encoded.remaining()).also(encoded::get)
            digest = MessageDigest.getInstance("SHA-1").digest(utf8)
            hash = digest.toUpperHexChars()
            val prefix = String(hash, 0, PREFIX_LENGTH)
            val suffix = hash.copyOfRange(PREFIX_LENGTH, hash.size)
            return RangeQuery(prefix, suffix)
        } finally {
            Wipe.wipe(utf8)
            Wipe.wipe(digest)
            Wipe.wipe(hash)
        }
    }

    private inner class RangeQuery(
        private val prefix: String,
        private val suffix: CharArray,
    ) : PasswordBreachQuery {
        private var closed = false

        override fun breachCount(): Int {
            check(!closed) { "Password breach query is closed" }
            val request =
                HttpRequest.newBuilder(rangeEndpoint.resolve(prefix))
                    .timeout(Duration.ofSeconds(8))
                    .header("Add-Padding", "true")
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build()
            val response =
                http.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.US_ASCII),
                )
            if (response.statusCode() != 200) {
                throw IOException("Password breach service returned HTTP ${response.statusCode()}")
            }
            return response.body().lineSequence()
                .map(String::trim)
                .mapNotNull { line ->
                    val separator = line.indexOf(':')
                    if (separator <= 0) return@mapNotNull null
                    if (!line.matchesHashSuffix(separator, suffix)) return@mapNotNull null
                    line.substring(separator + 1).toIntOrNull()
                }
                .firstOrNull()
                ?: 0
        }

        override fun close() {
            if (!closed) {
                closed = true
                Wipe.wipe(suffix)
            }
        }
    }

    private fun ByteArray.toUpperHexChars(): CharArray {
        val output = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            output[index * 2] = HEX[value ushr 4]
            output[index * 2 + 1] = HEX[value and 0x0f]
        }
        return output
    }

    private fun String.matchesHashSuffix(separator: Int, suffix: CharArray): Boolean {
        if (separator != suffix.size) return false
        for (index in suffix.indices) {
            if (this[index].uppercaseChar() != suffix[index]) return false
        }
        return true
    }

    private companion object {
        const val PREFIX_LENGTH = 5
        const val USER_AGENT = "Keystead/Desktop"
        val HEX = "0123456789ABCDEF".toCharArray()
    }
}

internal data class PasswordBreachAuditResult(
    val findings: Map<String, Int>,
    val checkedSecretIds: Set<String>,
    val checked: Int,
    val failed: Int,
)

internal class PasswordBreachAuditor(
    private val lookup: PasswordBreachLookup,
) {
    fun audit(
        session: LocalVaultSession,
        secrets: List<SecretListItem>,
    ): PasswordBreachAuditResult {
        val findings = linkedMapOf<String, Int>()
        val checkedSecretIds = linkedSetOf<String>()
        var checked = 0
        var failed = 0
        secrets
            .filter { it.type == top.focess.keystead.model.SecretType.LOGIN_PASSWORD.name }
            .forEach { secret ->
                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                try {
                    var query: PasswordBreachQuery? = null
                    session.withPassword(secret.id) { password ->
                        if (password.isNotEmpty()) {
                            query = lookup.prepare(password)
                        }
                    }
                    query?.use {
                        val count = it.breachCount()
                        checkedSecretIds += secret.id
                        checked += 1
                        if (count > 0) findings[secret.id] = count
                    }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw error
                } catch (_: Exception) {
                    failed += 1
                }
            }
        return PasswordBreachAuditResult(findings, checkedSecretIds, checked, failed)
    }
}
