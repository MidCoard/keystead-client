package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.SecretType

class ShareExchangeTest {
    @Test
    fun mintAndRedeemRoundTripsThePayload() {
        val now = Instant.parse("2026-07-25T12:00:00Z")
        val exchange = ShareExchange(clock = Clock.fixed(now, ZoneOffset.UTC))
        val mintPassphrase = "CorrectHorse42!".toCharArray()
        val redeemPassphrase = "CorrectHorse42!".toCharArray()
        var storedShareString: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/v1/shares") { http ->
            val path = http.requestURI.toString()
            when {
                http.requestMethod == "POST" && path == "/api/v1/shares" -> {
                    val body = http.requestBody.readBytes().decodeToString()
                    storedShareString = extractPayload(body)
                    val response = """{"code":"code-1","expiresAt":"2026-08-01T00:00:00Z"}""".encodeToByteArray()
                    http.sendResponseHeaders(201, response.size.toLong())
                    http.responseBody.use { it.write(response) }
                    http.close()
                }
                http.requestMethod == "GET" && path == "/api/v1/shares/code-1" -> {
                    val payload = storedShareString.orEmpty()
                    val response = "{\"payload\":${jsonStringLiteral(payload)}}".encodeToByteArray()
                    http.sendResponseHeaders(200, response.size.toLong())
                    http.responseBody.use { it.write(response) }
                    http.close()
                }
                else -> {
                    http.sendResponseHeaders(404, -1)
                    http.close()
                }
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = KeysteadServerClient(baseUrl, ServerAuthorization { "Bearer test-token" })
            val minted =
                exchange.mint(
                    client,
                    "Site credentials",
                    "the-secret-payload",
                    mintPassphrase,
                    ShareExchange.ShareTtl.ONE_WEEK,
                    true,
                )
            assertEquals("code-1", minted.code)

            val contents = exchange.redeem(client, "code-1", redeemPassphrase)
            assertEquals("Site credentials", contents.title)
            assertEquals(SecretType.SECURE_NOTE, contents.secretType)
            assertEquals("the-secret-payload", contents.fields["body"])
        } finally {
            server.stop(0)
            Wipe.wipe(mintPassphrase)
            Wipe.wipe(redeemPassphrase)
        }
    }

    @Test
    fun redeemRejectsWrongPassphrase() {
        val now = Instant.parse("2026-07-25T12:00:00Z")
        val exchange = ShareExchange(clock = Clock.fixed(now, ZoneOffset.UTC))
        val mintPassphrase = "CorrectHorse42!".toCharArray()
        val wrongPassphrase = "TotallyDifferent99".toCharArray()
        var storedShareString: String? = null
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/v1/shares") { http ->
            val path = http.requestURI.toString()
            when {
                http.requestMethod == "POST" && path == "/api/v1/shares" -> {
                    storedShareString = extractPayload(http.requestBody.readBytes().decodeToString())
                    val response = """{"code":"code-2","expiresAt":"2026-08-01T00:00:00Z"}""".encodeToByteArray()
                    http.sendResponseHeaders(201, response.size.toLong())
                    http.responseBody.use { it.write(response) }
                    http.close()
                }
                http.requestMethod == "GET" && path == "/api/v1/shares/code-2" -> {
                    val payload = storedShareString.orEmpty()
                    val response = "{\"payload\":${jsonStringLiteral(payload)}}".encodeToByteArray()
                    http.sendResponseHeaders(200, response.size.toLong())
                    http.responseBody.use { it.write(response) }
                    http.close()
                }
                else -> {
                    http.sendResponseHeaders(404, -1)
                    http.close()
                }
            }
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = KeysteadServerClient(baseUrl, ServerAuthorization { "Bearer test-token" })
            exchange.mint(
                client,
                "Site credentials",
                "the-secret-payload",
                mintPassphrase,
                ShareExchange.ShareTtl.ONE_WEEK,
                false,
            )
            assertFailsWithWrongPassphrase {
                exchange.redeem(client, "code-2", wrongPassphrase)
            }
        } finally {
            server.stop(0)
            Wipe.wipe(mintPassphrase)
            Wipe.wipe(wrongPassphrase)
        }
    }

    @Test
    fun meetsPassphrasePolicyRejectsShortPassphrases() {
        assertFalse(ShareExchange.meetsPassphrasePolicy(""))
        assertFalse(ShareExchange.meetsPassphrasePolicy("Ab1!Ab1!Ab1"))
    }

    @Test
    fun meetsPassphrasePolicyRequiresThreeCharacterClasses() {
        assertFalse(ShareExchange.meetsPassphrasePolicy("abcdefgh1234"))
        assertTrue(ShareExchange.meetsPassphrasePolicy("Abcdefgh1234"))
    }

    @Test
    fun meetsPassphrasePolicyDoesNotCountWhitespaceAsSymbol() {
        assertTrue(ShareExchange.meetsPassphrasePolicy("Correct Horse42"))
        assertFalse(ShareExchange.meetsPassphrasePolicy("correct 1234  "))
    }

    private fun extractPayload(body: String): String =
        Regex(""""payload"\s*:\s*"((?:\\.|[^"])*)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?: error("mint request is missing the payload field")

    private fun jsonStringLiteral(value: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u")
                    sb.append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun assertFailsWithWrongPassphrase(block: () -> Unit) {
        try {
            block()
            error("Expected the share open to fail")
        } catch (e: RuntimeException) {
            assertTrue(
                e.message?.contains("passphrase", ignoreCase = true) == true,
                "Expected a passphrase failure message but was: ${e.message}",
            )
        }
    }
}
