package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceRevocationServiceTest {
    @Test
    fun successfulRevocationReturnsLocalResultAcrossClockSkewAndClosesSecretsWithoutRelisting() {
        val clock = Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC)
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val request =
                CapturedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                )
            requests += request
            val body =
                if (request.path == "/api/v1/auth/login") {
                    """{"accessToken":"access-one","refreshToken":"refresh-one","accessTokenExpiresAt":"2026-07-12T00:05:00Z","refreshTokenExpiresAt":"2026-08-11T00:00:00Z"}"""
                } else {
                    ""
                }
            val status =
                when {
                    request.path == "/api/v1/auth/login" -> 200
                    request.method == "DELETE" && request.path == "/api/v1/devices/laptop-1" -> 204
                    request.path == "/api/v1/devices" -> 401
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            if (status == 204) {
                exchange.sendResponseHeaders(status, -1)
            } else {
                val bytes = body.encodeToByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val identity =
                DeviceIdentityStore(createTempDirectory("keystead-revoke-identity-test"))
                    .createOrLoad("laptop-1", "device-passphrase".toCharArray())
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login("alice", "server-password".toCharArray(), identity.deviceId)
            val knownDevice = verifiedDevice(identity)

            val revoked = DeviceRevocationService().revoke(session, identity, knownDevice)

            assertEquals("laptop-1", revoked.deviceId)
            assertEquals("DeviceRevocationResult(deviceId=<redacted>)", revoked.toString())
            assertEquals(
                listOf("POST /api/v1/auth/login", "DELETE /api/v1/devices/laptop-1"),
                requests.map { "${it.method} ${it.path}" },
            )
            assertEquals("Bearer access-one", requests.last().authorization)
            assertFailsWith<IllegalStateException> { session.client() }
            assertFailsWith<IllegalStateException> {
                identity.signDeviceChallenge("challenge-1", "nonce-1")
            }
        } finally {
            server.stop(0)
        }
    }

    private fun verifiedDevice(identity: LocalDeviceIdentity): ServerDevice {
        val proof = identity.proofPublicKey()
        val wrapping = identity.publicKey()
        return try {
            ServerDevice(
                deviceId = identity.deviceId,
                keyAlgorithm = identity.proofKeyAlgorithm,
                publicKey = Base64.getEncoder().encodeToString(proof),
                wrappingKeyAlgorithm = identity.keyAlgorithm,
                wrappingPublicKey = Base64.getEncoder().encodeToString(wrapping),
                createdAt = Instant.parse("2026-07-13T00:00:00Z"),
                verifiedAt = Instant.parse("2026-07-13T00:00:01Z"),
            )
        } finally {
            proof.fill(0)
            wrapping.fill(0)
        }
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String,
    )
}
