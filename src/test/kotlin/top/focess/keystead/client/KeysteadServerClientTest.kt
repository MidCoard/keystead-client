package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeysteadServerClientTest {
    @Test
    fun putEncryptedRecordSendsAuthenticatedJson() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret")
                .putRecord(
                    "vault-1",
                    "secret-1",
                    ServerEncryptedRecord(
                        revision = 2,
                        secretType = "LOGIN_PASSWORD",
                        encryptedProfile = "profile",
                        envelope = "cipher",
                        deleted = false,
                    ),
                )

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/vaults/vault-1/records/secret-1", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
            assertEquals(
                """{"revision":2,"secretType":"LOGIN_PASSWORD","encryptedProfile":"profile","envelope":"cipher","deleted":false}""",
                request.body,
            )
        }

    @Test
    fun deleteRecordSendsRevisionTombstoneRequest() =
        withServer(204) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret").deleteRecord("vault-1", "secret-1", 3)

            val request = requests.single()
            assertEquals("DELETE", request.method)
            assertEquals("/api/v1/vaults/vault-1/records/secret-1?revision=3", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
        }

    @Test
    fun registerUserSendsPublicAccountSetupRequest() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "ignored", "ignored")
                .registerUser("alice", "correct horse battery staple")

            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/users", request.path)
            assertEquals("", request.authorization)
            assertEquals(
                """{"username":"alice","password":"correct horse battery staple"}""",
                request.body,
            )
        }

    @Test
    fun registerUserReportsDuplicateAccountConflict() =
        withServer(409) { baseUrl, _ ->
            val error =
                assertFailsWith<KeysteadAccountConflictException> {
                    KeysteadServerClient(baseUrl, "ignored", "ignored")
                        .registerUser("alice", "correct horse battery staple")
                }

            assertEquals(409, error.statusCode)
            assertEquals("Server user already exists.", error.message)
        }

    @Test
    fun authenticatedRequestsReportCredentialFailures() =
        withServer(401) { baseUrl, _ ->
            val error =
                assertFailsWith<KeysteadAuthenticationException> {
                    KeysteadServerClient(baseUrl, "alice", "wrong-password").listDevices()
                }

            assertEquals(401, error.statusCode)
            assertEquals("Server rejected the username or password.", error.message)
        }

    @Test
    fun putVaultSendsAuthenticatedOpaqueMetadata() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret")
                .putVault("vault-1", "opaque-vault-metadata")

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/vaults/vault-1", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
            assertEquals("""{"encryptedMetadata":"opaque-vault-metadata"}""", request.body)
        }

    @Test
    fun listVaultsReadsOwnedServerVaults() =
        withServer(
            200,
            """
            [
              {
                "vaultId": "vault-1",
                "encryptedMetadata": "opaque-vault-metadata",
                "createdAt": "2026-07-03T00:00:00Z",
                "updatedAt": "2026-07-03T00:01:00Z"
              }
            ]
            """.trimIndent(),
        ) { baseUrl, requests ->
            val vaults = KeysteadServerClient(baseUrl, "alice", "secret").listVaults()

            val request = requests.single()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/vaults", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
            assertEquals(
                listOf(ServerVault("vault-1", "opaque-vault-metadata")),
                vaults,
            )
        }

    @Test
    fun putEncryptedRecordReportsRevisionConflictOnHttp409() =
        withServer(409) { baseUrl, requests ->
            val error =
                assertFailsWith<KeysteadRevisionConflictException> {
                    KeysteadServerClient(baseUrl, "alice", "secret")
                        .putRecord(
                            "vault-1",
                            "secret-1",
                            ServerEncryptedRecord(
                                revision = 1,
                                secretType = "API_TOKEN",
                                encryptedProfile = "profile",
                                envelope = "cipher",
                                deleted = false,
                            ),
                        )
                }

            assertEquals(409, error.statusCode)
            assertEquals(
                "Server has a newer revision; pull before pushing again.",
                error.message,
            )
            assertEquals("/api/v1/vaults/vault-1/records/secret-1", requests.single().path)
        }

    @Test
    fun putEncryptedRecordReadsRevisionConflictDetails() =
        withServer(
            409,
            """
            {
              "code": "REVISION_CONFLICT",
              "message": "Record revision must increase",
              "vaultId": "vault-1",
              "secretId": "secret-1",
              "latestRevision": 2,
              "rejectedRevision": 1,
              "serverRevision": 2,
              "clientRevision": 1,
              "serverDeleted": true,
              "serverUpdatedAt": "2026-07-03T15:00:00Z"
            }
            """.trimIndent(),
        ) { baseUrl, _ ->
            val error =
                assertFailsWith<KeysteadRevisionConflictException> {
                    KeysteadServerClient(baseUrl, "alice", "secret")
                        .putRecord(
                            "vault-1",
                            "secret-1",
                            ServerEncryptedRecord(
                                revision = 1,
                                secretType = "API_TOKEN",
                                encryptedProfile = "profile",
                                envelope = "cipher",
                                deleted = false,
                            ),
                        )
                }

            assertEquals("Record revision must increase", error.message)
            assertEquals(2L, error.latestRevision)
            assertEquals(1L, error.rejectedRevision)
            assertEquals("vault-1", error.vaultId)
            assertEquals("secret-1", error.secretId)
            assertEquals(2L, error.serverRevision)
            assertEquals(1L, error.clientRevision)
            assertEquals(true, error.serverDeleted)
            assertEquals("2026-07-03T15:00:00Z", error.serverUpdatedAt)
        }

    @Test
    fun listRecordsReadsEncryptedRecordResponses() =
        withServer(
            200,
            """
            [
              {
                "vaultId": "vault-1",
                "secretId": "secret-1",
                "revision": 2,
                "secretType": "API_TOKEN",
                "encryptedProfile": "profile",
                "envelope": "cipher",
                "deleted": false
              }
            ]
            """.trimIndent(),
        ) { baseUrl, requests ->
            val records = KeysteadServerClient(baseUrl, "alice", "secret").listRecords("vault-1", 1)

            assertEquals("/api/v1/vaults/vault-1/records?sinceRevision=1", requests.single().path)
            assertEquals(
                listOf(
                    ServerEncryptedRecord(
                        secretId = "secret-1",
                        revision = 2,
                        secretType = "API_TOKEN",
                        encryptedProfile = "profile",
                        envelope = "cipher",
                        deleted = false,
                    ),
                ),
                records,
            )
        }

    @Test
    fun listRecordsReadsTombstoneWithoutOpaqueFields() =
        withServer(
            200,
            """
            [
              {
                "vaultId": "vault-1",
                "secretId": "secret-1",
                "revision": 3,
                "secretType": "API_TOKEN",
                "deleted": true
              }
            ]
            """.trimIndent(),
        ) { baseUrl, _ ->
            val records = KeysteadServerClient(baseUrl, "alice", "secret").listRecords("vault-1", 2)

            assertEquals(
                listOf(
                    ServerEncryptedRecord(
                        secretId = "secret-1",
                        revision = 3,
                        secretType = "API_TOKEN",
                        encryptedProfile = "",
                        envelope = "",
                        deleted = true,
                    ),
                ),
                records,
            )
        }

    @Test
    fun listRecordPageReadsCursorResponse() =
        withServer(
            200,
            """
            {
              "vaultId": "vault-1",
              "sinceRevision": 1,
              "records": [
                {
                  "vaultId": "vault-1",
                  "secretId": "secret-2",
                  "revision": 3,
                  "secretType": "SECURE_NOTE",
                  "encryptedProfile": "profile-2",
                  "envelope": "cipher-2",
                  "deleted": false
                }
              ],
              "highestRevision": 3,
              "hasMore": true,
              "nextSinceRevision": 3
            }
            """.trimIndent(),
        ) { baseUrl, requests ->
            val page =
                KeysteadServerClient(baseUrl, "alice", "secret")
                    .listRecordPage("vault-1", sinceRevision = 1, limit = 2)

            assertEquals("/api/v1/vaults/vault-1/records/page?sinceRevision=1&limit=2", requests.single().path)
            assertEquals(
                ServerEncryptedRecordPage(
                    vaultId = "vault-1",
                    sinceRevision = 1,
                    records =
                        listOf(
                            ServerEncryptedRecord(
                                secretId = "secret-2",
                                revision = 3,
                                secretType = "SECURE_NOTE",
                                encryptedProfile = "profile-2",
                                envelope = "cipher-2",
                                deleted = false,
                            ),
                        ),
                    highestRevision = 3,
                    hasMore = true,
                    nextSinceRevision = 3,
                ),
                page,
            )
        }

    @Test
    fun registerDeviceSendsPublicKeyMaterial() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret")
                .registerDevice(
                    ServerDevice(
                        deviceId = "laptop-1",
                        keyAlgorithm = "ED25519",
                        publicKey = "base64-proof-public-key",
                        wrappingKeyAlgorithm = "TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM",
                        wrappingPublicKey = "base64-wrapping-public-key",
                    ),
                )

            val request = requests.single()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/devices", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
            assertEquals(
                """{"deviceId":"laptop-1","keyAlgorithm":"ED25519","publicKey":"base64-proof-public-key","wrappingKeyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","wrappingPublicKey":"base64-wrapping-public-key"}""",
                request.body,
            )
        }

    @Test
    fun registerDeviceSupportsBearerAuthorization() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, ServerAuthorization { "Bearer access-token" })
                .registerDevice(
                    ServerDevice("laptop-1", "ED25519", "proof", "TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM", "wrapped"),
                )

            assertEquals("Bearer access-token", requests.single().authorization)
        }

    @Test
    fun deviceLifecycleConflictIsRedactedEvenWhenServerProvidesDetails() =
        withServer(409, "{\"message\":\"secret device detail\",\"deviceId\":\"laptop-1\"}") { baseUrl, _ ->
            val error = assertFailsWith<KeysteadServerException> {
                KeysteadServerClient(baseUrl, ServerAuthorization { "Bearer access-token" }).listDevices()
            }
            assertEquals(409, error.statusCode)
            assertEquals("Device lifecycle request was rejected by the server", error.message)
        }

    @Test
    fun listDevicesReadsPublicKeyResponses() =
        withServer(
            200,
            """
            [
              {
                "deviceId": "phone-1",
                "keyAlgorithm": "ED25519",
                "publicKey": "base64-phone-proof-key",
                "wrappingKeyAlgorithm": "TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM",
                "wrappingPublicKey": "base64-phone-wrapping-key",
                "createdAt": "2026-07-03T00:00:00Z",
                "verifiedAt": "2026-07-03T00:01:00Z",
                "lastSeenAt": "2026-07-03T00:02:00Z"
              }
            ]
            """.trimIndent(),
        ) { baseUrl, requests ->
            val devices = KeysteadServerClient(baseUrl, "alice", "secret").listDevices()

            assertEquals("/api/v1/devices", requests.single().path)
            assertEquals(
                listOf(
                    ServerDevice(
                        deviceId = "phone-1",
                        keyAlgorithm = "ED25519",
                        publicKey = "base64-phone-proof-key",
                        wrappingKeyAlgorithm = "TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM",
                        wrappingPublicKey = "base64-phone-wrapping-key",
                        createdAt = Instant.parse("2026-07-03T00:00:00Z"),
                        verifiedAt = Instant.parse("2026-07-03T00:01:00Z"),
                        lastSeenAt = Instant.parse("2026-07-03T00:02:00Z"),
                    ),
                ),
                devices,
            )
        }

    @Test
    fun listDevicesHandlesBracesInsideEscapedStringValues() =
        withServer(
            200,
            """[{"deviceId":"phone-{east}","keyAlgorithm":"ED25519","publicKey":"proof-{key}","createdAt":"2026-07-03T00:00:00Z"}]""",
        ) { baseUrl, _ ->
            val device = KeysteadServerClient(baseUrl, "alice", "secret").listDevices().single()

            assertEquals("phone-{east}", device.deviceId)
            assertEquals("proof-{key}", device.publicKey)
        }

    @Test
    fun listDevicesRejectsInvalidProofAndWrappingShapes() {
        listOf(
            """[{"deviceId":" ","keyAlgorithm":"ED25519","publicKey":"proof","createdAt":"2026-07-03T00:00:00Z"}]""",
            """[{"deviceId":"phone","keyAlgorithm":"RAW_RSA","publicKey":"proof","createdAt":"2026-07-03T00:00:00Z"}]""",
            """[{"deviceId":"phone","keyAlgorithm":"ED25519","publicKey":"proof","wrappingKeyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","createdAt":"2026-07-03T00:00:00Z"}]""",
            """[{"deviceId":"phone","keyAlgorithm":"ED25519","publicKey":"proof","wrappingKeyAlgorithm":"TINK_DEVICE_KEY_PACKAGE","wrappingPublicKey":"wrapped","createdAt":"2026-07-03T00:00:00Z"}]""",
        ).forEach { response ->
            withServer(200, response) { baseUrl, _ ->
                assertFailsWith<IllegalArgumentException> {
                    KeysteadServerClient(baseUrl, "alice", "secret").listDevices()
                }
            }
        }
    }

    @Test
    fun listDevicesRejectsWrongTypedOptionalFields() {
        withServer(200, "[{\"deviceId\":\"phone\",\"keyAlgorithm\":\"ED25519\",\"publicKey\":\"proof\",\"createdAt\":123}]") { baseUrl, _ ->
            assertFailsWith<IllegalStateException> {
                KeysteadServerClient(baseUrl, "alice", "secret").listDevices()
            }
        }
    }

    @Test
    fun createDeviceChallengeReadsStrictLifecycleResponse() =
        withServer(
            201,
            """{"deviceId":"laptop-1","challengeId":"challenge-1","nonce":"nonce-1","expiresAt":"2026-07-12T00:05:00Z"}""",
        ) { baseUrl, requests ->
            val challenge =
                KeysteadServerClient(baseUrl, "alice", "secret")
                    .createDeviceChallenge("laptop-1")

            assertEquals("POST", requests.single().method)
            assertEquals("/api/v1/devices/laptop-1/challenges", requests.single().path)
            assertEquals("Basic ${basic("alice", "secret")}", requests.single().authorization)
            assertEquals(
                ServerDeviceChallenge(
                    "laptop-1",
                    "challenge-1",
                    "nonce-1",
                    Instant.parse("2026-07-12T00:05:00Z"),
                ),
                challenge,
            )
        }

    @Test
    fun createDeviceChallengeRejectsBlankFields() =
        withServer(
            201,
            """{"deviceId":"laptop-1","challengeId":" ","nonce":"nonce-1","expiresAt":"2026-07-12T00:05:00Z"}""",
        ) { baseUrl, _ ->
            assertFailsWith<IllegalArgumentException> {
                KeysteadServerClient(baseUrl, "alice", "secret")
                    .createDeviceChallenge("laptop-1")
            }
        }

    @Test
    fun deviceProofFailureDoesNotExposeSignatureOrResponseBody() =
        withServer(500, "server-proof-failure-sentinel") { baseUrl, _ ->
            val error =
                assertFailsWith<KeysteadServerException> {
                    KeysteadServerClient(baseUrl, "alice", "secret")
                        .proveDevice("laptop-1", "challenge-1", "signature-sentinel")
                }

            assertFalse(error.message.orEmpty().contains("signature-sentinel"))
            assertFalse(error.message.orEmpty().contains("server-proof-failure-sentinel"))
        }

    @Test
    fun proveDeviceSendsChallengeIdAndSignature() =
        withServer(204) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret")
                .proveDevice("laptop-1", "challenge-1", "base64-signature")

            assertEquals("POST", requests.single().method)
            assertEquals("/api/v1/devices/laptop-1/proof", requests.single().path)
            assertEquals(
                """{"challengeId":"challenge-1","signature":"base64-signature"}""",
                requests.single().body,
            )
        }

    @Test
    fun revokeDeviceUsesAuthenticatedDelete() =
        withServer(204) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret").revokeDevice("laptop-1")

            assertEquals("DELETE", requests.single().method)
            assertEquals("/api/v1/devices/laptop-1", requests.single().path)
            assertEquals("Basic ${basic("alice", "secret")}", requests.single().authorization)
        }

    @Test
    fun putVaultKeyPackageSendsAuthenticatedJson() =
        withServer(201) { baseUrl, requests ->
            KeysteadServerClient(baseUrl, "alice", "secret")
                .putVaultKeyPackage(
                    "vault-1",
                    "laptop-1",
                    ServerVaultKeyPackage(
                        vaultId = "vault-1",
                        deviceId = "laptop-1",
                        keyAlgorithm = "RSA_OAEP_SHA256",
                        encryptedVaultKey = "wrapped-key",
                    ),
                )

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertEquals("/api/v1/vaults/vault-1/key-packages/laptop-1", request.path)
            assertEquals("Basic ${basic("alice", "secret")}", request.authorization)
            assertEquals(
                """{"vaultKeyId":"legacy","keyAlgorithm":"RSA_OAEP_SHA256","encryptedVaultKey":"wrapped-key"}""",
                request.body,
            )
        }

    @Test
    fun listVaultKeyPackagesReadsProvisioningResponses() =
        withServer(
            200,
            """
            [
              {
                "vaultId": "vault-1",
                "deviceId": "phone-1",
                "keyAlgorithm": "RSA_OAEP_SHA256",
                "encryptedVaultKey": "wrapped-phone-key",
                "createdAt": "2026-07-03T00:00:00Z",
                "updatedAt": "2026-07-03T00:01:00Z"
              }
            ]
            """.trimIndent(),
        ) { baseUrl, requests ->
            val packages =
                KeysteadServerClient(baseUrl, "alice", "secret").listVaultKeyPackages("vault-1")

            assertEquals("/api/v1/vaults/vault-1/key-packages", requests.single().path)
            assertEquals(
                listOf(
                    ServerVaultKeyPackage(
                        vaultId = "vault-1",
                        deviceId = "phone-1",
                        keyAlgorithm = "RSA_OAEP_SHA256",
                        encryptedVaultKey = "wrapped-phone-key",
                    ),
                ),
                packages,
            )
        }

    private fun withServer(
        responseCode: Int,
        responseBody: String = "",
        block: (String, MutableList<CapturedRequest>) -> Unit,
    ) {
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.toString(),
                    authorization = exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                    body = exchange.requestBody.readBytes().decodeToString(),
                ),
            )
            val response = responseBody.encodeToByteArray()
            exchange.sendResponseHeaders(responseCode, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private fun basic(username: String, password: String): String =
        Base64.getEncoder().encodeToString("$username:$password".encodeToByteArray())

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String,
        val body: String,
    )
}
