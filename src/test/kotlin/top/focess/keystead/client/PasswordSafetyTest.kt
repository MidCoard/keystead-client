package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordSafetyTest {
    @Test
    fun strengthEvaluationDistinguishesWeakAndStrongDrafts() {
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEvaluator.evaluate("password"))
        assertEquals(PasswordStrength.FAIR, PasswordStrengthEvaluator.evaluate("longer passphrase"))
        assertEquals(PasswordStrength.STRONG, PasswordStrengthEvaluator.evaluate("A-very-long-Password-2026!"))
    }

    @Test
    fun rangeLookupSendsOnlyTheFiveCharacterHashPrefixWithPadding() {
        val password = "correct horse battery staple"
        val fullHash = sha1(password)
        val request = arrayOfNulls<CapturedRequest>(1)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/range/${fullHash.take(5)}") { exchange ->
            request[0] =
                CapturedRequest(
                    exchange.requestURI,
                    exchange.requestHeaders.getFirst("Add-Padding"),
                    exchange.requestHeaders.getFirst("User-Agent"),
                )
            val body = "${fullHash.drop(5)}:42\r\n00000000000000000000000000000000000:0\r\n"
                .toByteArray(StandardCharsets.US_ASCII)
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val count =
                PwnedPasswordChecker(
                    URI("http://127.0.0.1:${server.address.port}/range/"),
                ).breachCount(password.toCharArray())

            assertEquals(42, count)
            assertEquals("/range/${fullHash.take(5)}", request[0]?.uri?.path)
            assertEquals("true", request[0]?.padding)
            assertTrue(request[0]?.userAgent?.startsWith("Keystead/") == true)
            assertFalse(request[0]?.uri.toString().contains(password) == true)
            assertFalse(request[0]?.uri.toString().contains(fullHash) == true)
        } finally {
            server.stop(0)
        }
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02X".format(it) }

    private data class CapturedRequest(
        val uri: URI,
        val padding: String?,
        val userAgent: String?,
    )
}
