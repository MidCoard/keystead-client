package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceEnrollmentServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun newDeviceRegistersBothKeysProvesCanonicalChallengeAndConfirmsTrust() {
        withIdentity { identity ->
            withServer(
                responseFor = { request, number ->
                    when {
                        request.method == "GET" && request.path == "/api/v1/devices" && number == 1 ->
                            TestResponse(200, "[]")
                        request.method == "POST" && request.path == "/api/v1/devices" ->
                            TestResponse(201)
                        request.path == "/api/v1/devices/laptop-1/challenges" ->
                            TestResponse(201, challengeJson("2026-07-12T00:05:00Z"))
                        request.path == "/api/v1/devices/laptop-1/proof" -> TestResponse(204)
                        request.method == "GET" && request.path == "/api/v1/devices" ->
                            TestResponse(200, deviceJson(identity, verified = true))
                        else -> error("Unexpected request: ${request.method} ${request.path}")
                    }
                },
            ) { baseUrl, requests ->
                val enrolled =
                    DeviceEnrollmentService(clock)
                        .enroll(KeysteadServerClient(baseUrl, "alice", "secret"), identity)

                assertTrue(enrolled.canReceiveVaultKeyPackage)
                assertEquals(
                    listOf(
                        "GET /api/v1/devices",
                        "POST /api/v1/devices",
                        "POST /api/v1/devices/laptop-1/challenges",
                        "POST /api/v1/devices/laptop-1/proof",
                        "GET /api/v1/devices",
                    ),
                    requests.map { "${it.method} ${it.path}" },
                )
                val registration = requests[1].body
                assertTrue(registration.contains("\"keyAlgorithm\":\"ED25519\""))
                assertTrue(registration.contains("\"wrappingKeyAlgorithm\":\"${identity.keyAlgorithm}\""))
                val encodedSignature = jsonField(requests[3].body, "signature")
                assertTrue(
                    verifies(
                        identity.proofPublicKey(),
                        "keystead-device-proof:v1:challenge-1:nonce-1",
                        encodedSignature,
                    ),
                )
                val wrappingPrivateKey = identity.privateKey()
                try {
                    assertFalse(
                        requests.any {
                            it.body.contains(Base64.getEncoder().encodeToString(wrappingPrivateKey))
                        },
                    )
                } finally {
                    wrappingPrivateKey.fill(0)
                }
            }
        }
    }

    @Test
    fun matchingUnverifiedRegistrationResumesWithoutRegisteringAgain() {
        withIdentity { identity ->
            withServer(
                responseFor = { request, number ->
                    when {
                        request.method == "GET" && request.path == "/api/v1/devices" && number == 1 ->
                            TestResponse(200, deviceJson(identity, verified = false))
                        request.path.endsWith("/challenges") ->
                            TestResponse(201, challengeJson("2026-07-12T00:05:00Z"))
                        request.path.endsWith("/proof") -> TestResponse(204)
                        request.method == "GET" && request.path == "/api/v1/devices" ->
                            TestResponse(200, deviceJson(identity, verified = true))
                        else -> error("Unexpected request: ${request.method} ${request.path}")
                    }
                },
            ) { baseUrl, requests ->
                val enrolled =
                    DeviceEnrollmentService(clock)
                        .enroll(KeysteadServerClient(baseUrl, "alice", "secret"), identity)

                assertTrue(enrolled.verifiedAt != null)
                assertFalse(requests.any { it.method == "POST" && it.path == "/api/v1/devices" })
            }
        }
    }

    @Test
    fun matchingVerifiedRegistrationIsIdempotent() {
        withIdentity { identity ->
            withServer(
                responseFor = { request, _ ->
                    if (request.method == "GET" && request.path == "/api/v1/devices") {
                        TestResponse(200, deviceJson(identity, verified = true))
                    } else {
                        error("Unexpected request: ${request.method} ${request.path}")
                    }
                },
            ) { baseUrl, requests ->
                val enrolled =
                    DeviceEnrollmentService(clock)
                        .enroll(KeysteadServerClient(baseUrl, "alice", "secret"), identity)

                assertTrue(enrolled.canReceiveVaultKeyPackage)
                assertEquals(listOf("GET /api/v1/devices"), requests.map { "${it.method} ${it.path}" })
            }
        }
    }

    @Test
    fun mismatchedOrRevokedRegistrationFailsBeforeChallenge() {
        withIdentity { identity ->
            val mismatched = deviceJson(identity, verified = false, wrappingPublicKey = "different-key")
            withSingleDeviceResponse(mismatched) { client, requests ->
                assertFailsWith<IllegalStateException> {
                    DeviceEnrollmentService(clock).enroll(client, identity)
                }
                assertEquals(1, requests.size)
            }

            val revoked = deviceJson(identity, verified = true, revoked = true)
            withSingleDeviceResponse(revoked) { client, requests ->
                assertFailsWith<IllegalStateException> {
                    DeviceEnrollmentService(clock).enroll(client, identity)
                }
                assertEquals(1, requests.size)
            }
        }
    }

    @Test
    fun expiredChallengeFailsBeforeSignatureSubmission() {
        withIdentity { identity ->
            withServer(
                responseFor = { request, number ->
                    when {
                        request.method == "GET" && number == 1 ->
                            TestResponse(200, deviceJson(identity, verified = false))
                        request.path.endsWith("/challenges") ->
                            TestResponse(201, challengeJson("2026-07-12T00:00:00Z"))
                        else -> error("Unexpected request: ${request.method} ${request.path}")
                    }
                },
            ) { baseUrl, requests ->
                assertFailsWith<IllegalStateException> {
                    DeviceEnrollmentService(clock)
                        .enroll(KeysteadServerClient(baseUrl, "alice", "secret"), identity)
                }

                assertFalse(requests.any { it.path.endsWith("/proof") })
            }
        }
    }

    private fun withSingleDeviceResponse(
        responseBody: String,
        action: (KeysteadServerClient, MutableList<CapturedRequest>) -> Unit,
    ) {
        withServer(
            responseFor = { request, _ ->
                if (request.method == "GET" && request.path == "/api/v1/devices") {
                    TestResponse(200, responseBody)
                } else {
                    error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            action(KeysteadServerClient(baseUrl, "alice", "secret"), requests)
        }
    }

    private fun withIdentity(action: (LocalDeviceIdentity) -> Unit) {
        val directory: Path = createTempDirectory("keystead-device-enrollment-test")
        DeviceIdentityStore(directory)
            .createOrLoad("laptop-1", "device-passphrase".toCharArray())
            .use(action)
    }

    private fun deviceJson(
        identity: LocalDeviceIdentity,
        verified: Boolean,
        revoked: Boolean = false,
        wrappingPublicKey: String? = null,
    ): String {
        val proofPublicKey = identity.proofPublicKey()
        val wrappingKey = identity.publicKey()
        return try {
            val wrapping =
                wrappingPublicKey ?: Base64.getEncoder().encodeToString(wrappingKey)
            """
            [{
              "deviceId":"${identity.deviceId}",
              "keyAlgorithm":"${identity.proofKeyAlgorithm}",
              "publicKey":"${Base64.getEncoder().encodeToString(proofPublicKey)}",
              "wrappingKeyAlgorithm":"${identity.keyAlgorithm}",
              "wrappingPublicKey":"$wrapping",
              "createdAt":"2026-07-12T00:00:00Z"${if (verified) ",\"verifiedAt\":\"2026-07-12T00:00:01Z\"" else ""}${if (revoked) ",\"revokedAt\":\"2026-07-12T00:00:02Z\"" else ""}
            }]
            """.trimIndent()
        } finally {
            proofPublicKey.fill(0)
            wrappingKey.fill(0)
        }
    }

    private fun challengeJson(expiresAt: String): String =
        """{"deviceId":"laptop-1","challengeId":"challenge-1","nonce":"nonce-1","expiresAt":"$expiresAt"}"""

    private fun verifies(publicKeyBytes: ByteArray, payload: String, encodedSignature: String): Boolean {
        val publicKey =
            KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        return verifier.verify(Base64.getDecoder().decode(encodedSignature))
    }

    private fun jsonField(json: String, key: String): String =
        Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]+)\"")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?: error("Missing $key")

    private fun withServer(
        responseFor: (CapturedRequest, Int) -> TestResponse,
        action: (String, MutableList<CapturedRequest>) -> Unit,
    ) {
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val request =
                CapturedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                    exchange.requestBody.readBytes().decodeToString(),
                )
            requests += request
            val response = responseFor(request, requests.size)
            val body = response.body.encodeToByteArray()
            if (response.statusCode == 204) {
                exchange.sendResponseHeaders(response.statusCode, -1)
            } else {
                exchange.sendResponseHeaders(response.statusCode, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            exchange.close()
        }
        server.start()
        try {
            action("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String,
        val body: String,
    )

    private data class TestResponse(val statusCode: Int, val body: String = "")
}
