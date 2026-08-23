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

class PwnedPasswordChecker(
    private val rangeEndpoint: URI = URI("https://api.pwnedpasswords.com/range/"),
    private val http: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) {
    /** Returns the breach corpus occurrence count without transmitting the password or full hash. */
    fun breachCount(password: CharArray): Int {
        require(password.isNotEmpty()) { "Password cannot be empty" }
        var utf8 = ByteArray(0)
        var digest = ByteArray(0)
        try {
            val encoded = StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(password))
            utf8 = ByteArray(encoded.remaining()).also(encoded::get)
            digest = MessageDigest.getInstance("SHA-1").digest(utf8)
            val hash = digest.joinToString("") { "%02X".format(it) }
            val prefix = hash.take(PREFIX_LENGTH)
            val suffix = hash.drop(PREFIX_LENGTH)
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
                    val candidate = line.substring(0, separator)
                    if (!candidate.equals(suffix, ignoreCase = true)) return@mapNotNull null
                    line.substring(separator + 1).toIntOrNull()
                }
                .firstOrNull()
                ?: 0
        } finally {
            Wipe.wipe(utf8)
            Wipe.wipe(digest)
        }
    }

    private companion object {
        const val PREFIX_LENGTH = 5
        const val USER_AGENT = "Keystead/Desktop"
    }
}
