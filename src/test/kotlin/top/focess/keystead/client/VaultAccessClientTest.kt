package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.focess.keystead.access.VaultAccessRequest
import top.focess.keystead.access.VaultAccessRequestCodec
import top.focess.keystead.crypto.DefaultCryptoService

class VaultAccessClientTest {
    @Test
    fun eachLoginScopedExchangeUsesANewUuidAndPublicKey() {
        EphemeralVaultAccessSession.create("https://vault.example").use { first ->
            EphemeralVaultAccessSession.create("https://vault.example").use { second ->
                assertNotEquals(first.requestId, second.requestId)
                assertTrue(!first.publicKey.contentEquals(second.publicKey))
            }
        }
    }

    @Test
    fun createPublishesOnlyEphemeralExchangeMaterial() {
        val body = AtomicReference("")
        EphemeralVaultAccessSession.create("https://vault.example").use { exchangeSession ->
            val response = requestJson(exchangeSession, "alice")
            withServer(response) { request ->
                body.set(request.requestBody.bufferedReader().readText())
            }.use { server ->
                val created =
                    VaultAccessClient(
                        KeysteadServerClient(server.baseUrl, "alice", "secret"),
                    ).create(exchangeSession)

                assertEquals(exchangeSession.requestId, created.requestId)
                assertTrue(body.get().contains("\"requestId\":\"${exchangeSession.requestId}\""))
                assertTrue(body.get().contains("\"exchangePublicKey\":"))
                assertTrue(!body.get().contains("deviceId"))
                assertTrue(!body.get().contains("proof", ignoreCase = true))
            }
        }
    }

    @Test
    fun approvalPublishesOneEncryptedDekWithoutDeviceIdentityOrSignature() {
        val body = AtomicReference("")
        withServer("") { exchange ->
            body.set(exchange.requestBody.bufferedReader().readText())
        }.use { server ->
            VaultAccessClient(
                KeysteadServerClient(server.baseUrl, "alice", "secret"),
            ).approve(
                requestId = "550e8400-e29b-41d4-a716-446655440000",
                packageValue =
                    VaultAccessKeyPackage(
                        fingerprint = "6000000000000001",
                        vaultKeyId = "key-1",
                        keyAlgorithm = DefaultCryptoService.DEVICE_KEY_ALGORITHM,
                        encryptedVaultKey = "opaque-package",
                    ),
            )
        }
        assertTrue(body.get().contains("\"encryptedVaultKey\":\"opaque-package\""))
        assertTrue(!body.get().contains("deviceId"))
        assertTrue(!body.get().contains("signature"))
    }

    @Test
    fun closingTheExchangeSessionDestroysThePrivateKey() {
        val session = EphemeralVaultAccessSession.create("https://vault.example")
        session.close()

        assertFailsWith<RuntimeException> {
            session.withPrivateKey { }
        }
    }

    private fun requestJson(session: EphemeralVaultAccessSession, account: String): String {
        val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
        val request =
            VaultAccessRequest(
                VaultAccessRequest.FORMAT_VERSION,
                session.requestId,
                account,
                session.serverOrigin,
                expiresAt,
                session.keyAlgorithm,
                session.publicKey,
            )
        val canonical =
            Base64.getUrlEncoder().withoutPadding().encodeToString(VaultAccessRequestCodec.encode(request))
        return """{"requestId":"${session.requestId}","accountId":"$account","serverOrigin":"${session.serverOrigin}","fingerprint":"${VaultAccessRequestCodec.fingerprint(request)}","keyAlgorithm":"${session.keyAlgorithm}","exchangePublicKey":"${Base64.getEncoder().encodeToString(session.publicKey)}","state":"PENDING","expiresAt":"$expiresAt","canonicalRequest":"$canonical","approvedPackage":null,"approvedAt":null}"""
    }

    private fun withServer(
        responseBody: String,
        inspect: (com.sun.net.httpserver.HttpExchange) -> Unit,
    ): TestServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            inspect(exchange)
            val response = responseBody.encodeToByteArray()
            val status = if (response.isEmpty()) 204 else 201
            exchange.sendResponseHeaders(status, if (status == 204) -1 else response.size.toLong())
            if (response.isNotEmpty()) exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        return TestServer(server)
    }

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }
}
