package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import org.junit.jupiter.api.assertTimeoutPreemptively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.model.SecretType

class LocalVaultSyncTest {
    @Test
    fun pushRecordsToServerSendsEncryptedProfileAndEnvelope() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-sync-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000001")

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                session.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com",
                )

                val count =
                    session.pushRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        sinceRevision = 0,
                    )

                assertEquals(1, count)
            }

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertTrue(request.path.startsWith("/api/v1/vaults/$vaultId/records/"))
            assertTrue(request.body.contains(""""secretType":"LOGIN_PASSWORD""""))
            assertTrue(request.body.contains(""""encryptedProfile":""""))
            assertTrue(request.body.contains(""""envelope":""""))
            assertFalse(request.body.contains("GitHub"))
            assertFalse(request.body.contains("github"))
            assertFalse(request.body.contains("alice@example.com"))
            assertFalse(request.body.contains("secret-password"))
            assertFalse(request.body.contains("aad"))
        }

    @Test
    fun pullRecordsFromServerImportsEncryptedRecordsLocally() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-sync-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000002")
            lateinit var exported: String

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val id =
                    session.addStructuredSecret(
                        type = SecretType.API_TOKEN,
                        title = "GitHub token",
                        fields = mapOf("token" to "ghp_secret"),
                        category = "development",
                        provider = "github",
                        account = "alice@example.com",
                    )
                session.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                val pushed = requests.single()
                val secretId = pushed.path.substringAfterLast("/")
                exported =
                    pushed.body.replaceFirst(
                        "{",
                        """{"vaultId":"$vaultId","secretId":"$secretId",""",
                    )
                directory.resolve("secrets").resolve("$secretId.properties").deleteIfExists()
                assertTrue(session.listSecrets().isEmpty())
            }

            requests.clear()
            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                recordPage(vaultId, 0, "[$exported]", highestRevision = 1, hasMore = false)

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val count =
                    session.pullRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        sinceRevision = 0,
                    )

                val imported = session.listSecrets().single()
                assertEquals(1, count)
                assertEquals("GitHub token", imported.title)
                assertEquals(SecretType.API_TOKEN.name, imported.type)
                assertEquals("ghp_secret", session.revealField(imported.id, "token"))
            }
        }

    @Test
    fun pullRecordsFromServerRemovesImportedRecordWhenTombstoneOmitsOpaqueFields() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-tombstone-sync-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000021")
            lateinit var secretId: String
            lateinit var exported: String

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { source ->
                source.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "Imported token",
                    fields = mapOf("token" to "test-token"),
                )
                source.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                val pushed = requests.single()
                secretId = pushed.path.substringAfterLast("/")
                exported =
                    pushed.body.replaceFirst(
                        "{",
                        """{"vaultId":"$vaultId","secretId":"$secretId",""",
                    )
                directory.resolve("secrets").resolve("$secretId.properties").deleteIfExists()
            }

            requests.clear()
            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                recordPage(vaultId, 0, "[$exported]", highestRevision = 1, hasMore = false)

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { target ->
                assertEquals(
                    1,
                    target.pullRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        sinceRevision = 0,
                    ),
                )
                assertEquals(listOf(secretId), target.listSecrets().map { it.id })

                val tombstone =
                    """
                    {
                      "vaultId": "$vaultId",
                      "secretId": "$secretId",
                      "revision": 2,
                      "secretType": "API_TOKEN",
                      "deleted": true
                    }
                    """.trimIndent()
                responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=1&limit=100"] =
                    recordPage(vaultId, 1, "[$tombstone]", highestRevision = 2, hasMore = false)

                assertEquals(
                    1,
                    target.pullRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        sinceRevision = 1,
                    ),
                )
                assertTrue(target.listSecrets().isEmpty())
            }
        }

    @Test
    fun pushRecordsToServerSendsDeletesAsTombstoneRequests() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-delete-sync-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000005")

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val id =
                    session.addStructuredSecret(
                        type = SecretType.API_TOKEN,
                        title = "GitHub token",
                        fields = mapOf("token" to "ghp_secret"),
                    )
                session.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                requests.clear()

                session.delete(id)
                val count =
                    session.pushRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        sinceRevision = 1,
                    )

                assertEquals(1, count)
            }

            val request = requests.single()
            assertEquals("DELETE", request.method)
            assertTrue(request.path.startsWith("/api/v1/vaults/$vaultId/records/"))
            assertTrue(request.path.endsWith("?revision=2"))
            assertEquals("", request.body)
        }

    @Test
    fun pushPendingRecordsUsesPersistedRevisionCursorWithoutSkippingNewSecrets() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-pending-sync-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-sync-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000007")

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                session.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "First token",
                    fields = mapOf("token" to "first"),
                )
                assertEquals(
                    1,
                    session.pushPendingRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )
                assertEquals(1, state.lastPushedRevision(vaultId.toString()))
                requests.clear()

                session.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "Second token",
                    fields = mapOf("token" to "second"),
                )
                assertEquals(
                    1,
                    session.pushPendingRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )
                assertEquals(2, state.lastPushedRevision(vaultId.toString()))
            }

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertTrue(request.body.contains(""""revision":2"""))
        }

    @Test
    fun pushPendingRecordsSendsUpdatedSecretRevision() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-update-sync-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-sync-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000009")

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val id =
                    session.addStructuredSecret(
                        type = SecretType.API_TOKEN,
                        title = "GitHub token",
                        fields = mapOf("token" to "old"),
                    )
                session.pushPendingRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), state)
                requests.clear()

                session.updateStructuredSecret(
                    id,
                    title = "GitHub token",
                    fields = mapOf("token" to "new"),
                )

                assertEquals(
                    1,
                    session.pushPendingRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )
            }

            val request = requests.single()
            assertEquals("PUT", request.method)
            assertTrue(request.path.startsWith("/api/v1/vaults/$vaultId/records/"))
            assertTrue(request.body.contains(""""revision":2"""))
        }

    @Test
    fun pushPendingRecordsKeepsCursorWhenServerRejectsStaleRevision() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-conflict-sync-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-sync-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000010")

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val id =
                    session.addStructuredSecret(
                        type = SecretType.API_TOKEN,
                        title = "GitHub token",
                        fields = mapOf("token" to "old"),
                    )
                session.pushPendingRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), state)
                assertEquals(1, state.lastPushedRevision(vaultId.toString()))
                requests.clear()

                responseCode = 409
                session.updateStructuredSecret(
                    id,
                    title = "GitHub token",
                    fields = mapOf("token" to "new"),
                )

                assertFailsWith<KeysteadRevisionConflictException> {
                    session.pushPendingRecordsTo(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    )
                }
                assertEquals(1, state.lastPushedRevision(vaultId.toString()))
            }

            assertEquals(1, requests.size)
            assertTrue(requests.single().body.contains(""""revision":2"""))
        }

    @Test
    fun pullPendingRecordsUsesPersistedRevisionCursor() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-pull-sync-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-sync-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000008")
            lateinit var exported: String

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                session.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "GitHub token",
                    fields = mapOf("token" to "ghp_secret"),
                )
                session.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                val pushed = requests.single()
                val secretId = pushed.path.substringAfterLast("/")
                exported =
                    pushed.body.replaceFirst(
                        "{",
                        """{"vaultId":"$vaultId","secretId":"$secretId",""",
                    )
                directory.resolve("secrets").resolve("$secretId.properties").deleteIfExists()
            }
            requests.clear()
            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                recordPage(vaultId, 0, "[$exported]", highestRevision = 1, hasMore = false)

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { target ->
                assertEquals(
                    1,
                    target.pullPendingRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )
                assertEquals(1, state.lastPulledRevision(vaultId.toString()))
                responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=1&limit=100"] =
                    recordPage(vaultId, 1, "[]", highestRevision = 1, hasMore = false)

                assertEquals(
                    0,
                    target.pullPendingRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )
            }

            assertEquals("/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100", requests[0].path)
            assertEquals("/api/v1/vaults/$vaultId/records/page?sinceRevision=1&limit=100", requests[1].path)
        }

    @Test
    fun pullPendingRecordsConsumesAllServerPagesAndStoresHighestRevision() =
        withServer { baseUrl, requests ->
            val sourceDirectory = createTempDirectory("keystead-client-page-source-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-page-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000013")
            lateinit var firstExported: String
            lateinit var secondExported: String

            LocalVaultSession.openOrCreate(sourceDirectory, vaultId, "master-password".toCharArray()).use { source ->
                source.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "First token",
                    fields = mapOf("token" to "first"),
                )
                source.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                val firstPushed = requests.single()
                val firstSecretId = firstPushed.path.substringAfterLast("/")
                firstExported =
                    firstPushed.body.replaceFirst(
                        "{",
                        """{"vaultId":"$vaultId","secretId":"$firstSecretId",""",
                    )
                requests.clear()

                source.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "Second token",
                    fields = mapOf("token" to "second"),
                )
                source.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 1)
                val secondPushed = requests.single()
                val secondSecretId = secondPushed.path.substringAfterLast("/")
                secondExported =
                    secondPushed.body.replaceFirst(
                        "{",
                        """{"vaultId":"$vaultId","secretId":"$secondSecretId",""",
                    )
                sourceDirectory.resolve("secrets").resolve("$firstSecretId.properties").deleteIfExists()
                sourceDirectory.resolve("secrets").resolve("$secondSecretId.properties").deleteIfExists()
            }
            requests.clear()
            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                recordPage(
                    vaultId = vaultId,
                    sinceRevision = 0,
                    records = "[$firstExported]",
                    highestRevision = 1,
                    hasMore = true,
                    nextSinceRevision = 1,
                )
            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=1&limit=100"] =
                recordPage(
                    vaultId = vaultId,
                    sinceRevision = 1,
                    records = "[$secondExported]",
                    highestRevision = 2,
                    hasMore = false,
                )

            LocalVaultSession.openOrCreate(sourceDirectory, vaultId, "master-password".toCharArray()).use { target ->
                assertEquals(
                    2,
                    target.pullPendingRecordsFrom(
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                        state,
                    ),
                )

                assertEquals(2, state.lastPulledRevision(vaultId.toString()))
                assertEquals(listOf("First token", "Second token"), target.listSecrets().map { it.title }.sorted())
            }

            assertEquals("/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100", requests[0].path)
            assertEquals("/api/v1/vaults/$vaultId/records/page?sinceRevision=1&limit=100", requests[1].path)
        }

    @Test
    fun pullPendingRecordsRejectsServerPageThatDoesNotAdvanceCursor() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-stalled-page-test")
            val state = SyncStateStore(createTempDirectory("keystead-client-stalled-state-test"))
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000020")

            responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                recordPage(
                    vaultId = vaultId,
                    sinceRevision = 0,
                    records = "[]",
                    highestRevision = 0,
                    hasMore = true,
                )

            LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                val failure =
                    assertTimeoutPreemptively(Duration.ofSeconds(2)) {
                        assertFailsWith<IllegalStateException> {
                            session.pullPendingRecordsFrom(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                state,
                            )
                        }
                    }

                assertTrue(failure.message!!.contains("did not advance cursor"))
                assertEquals(0, state.lastPulledRevision(vaultId.toString()))
            }

            assertEquals(1, requests.size)
            assertEquals("/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100", requests.single().path)
        }

    @Test
    fun publishEligibleDeviceVaultKeyPackageSendsOpaqueWrappedKey() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-package-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000003")
            val crypto = DefaultCryptoService()

            crypto.generateDeviceKeyPair().use { device ->
                LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                    val publicKey = device.publicKey()
                    try {
                        val keyPackage =
                            session.publishVaultKeyPackage(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                eligibleDevice("laptop-1", device.keyAlgorithm(), publicKey),
                            )

                        assertEquals(vaultId.toString(), keyPackage.vaultId)
                        assertEquals("laptop-1", keyPackage.deviceId)
                        assertEquals("TINK_DEVICE_KEY_PACKAGE", keyPackage.keyAlgorithm)
                        assertTrue(keyPackage.vaultKeyId.isNotBlank())
                        assertTrue(keyPackage.encryptedVaultKey.isNotBlank())
                    } finally {
                        publicKey.fill(0)
                    }
                }
            }

            val packageRequest = requests.single()
            assertEquals("PUT", packageRequest.method)
            assertEquals("/api/v1/vaults/$vaultId/key-packages/laptop-1", packageRequest.path)
            assertTrue(packageRequest.body.contains(""""keyAlgorithm":""""))
            assertTrue(packageRequest.body.contains(""""encryptedVaultKey":""""))
            assertFalse(packageRequest.body.contains("master-password"))
        }

    @Test
    fun publishVaultKeyPackagesWrapsKeyForRegisteredServerDevices() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-package-list-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000011")
            val crypto = DefaultCryptoService()
            lateinit var targetPublicKey: String

            crypto.generateDeviceKeyPair().use { targetDevice ->
                targetPublicKey = java.util.Base64.getEncoder().encodeToString(targetDevice.publicKey())
                responseBody =
                    """
                    [
                      {
                        "deviceId": "phone-3",
                        "keyAlgorithm": "ED25519",
                        "publicKey": "${java.util.Base64.getEncoder().encodeToString("proof-key".encodeToByteArray())}",
                        "wrappingKeyAlgorithm": "${targetDevice.keyAlgorithm()}",
                        "wrappingPublicKey": "$targetPublicKey",
                        "createdAt": "2026-07-03T00:00:00Z",
                        "verifiedAt": "2026-07-03T00:01:00Z"
                      }
                    ]
                    """.trimIndent()

                LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                    val count =
                        session.publishVaultKeyPackagesForRegisteredDevices(
                            KeysteadServerClient(baseUrl, "alice", "server-password"),
                        )

                    assertEquals(1, count)
                }
            }

            val listRequest = requests[0]
            assertEquals("GET", listRequest.method)
            assertEquals("/api/v1/devices", listRequest.path)

            val packageRequest = requests[1]
            assertEquals("PUT", packageRequest.method)
            assertEquals("/api/v1/vaults/$vaultId/key-packages/phone-3", packageRequest.path)
            assertTrue(packageRequest.body.contains(""""keyAlgorithm":""""))
            assertTrue(packageRequest.body.contains(""""encryptedVaultKey":""""))
            assertFalse(packageRequest.body.contains("master-password"))
            assertFalse(packageRequest.body.contains(targetPublicKey))
        }

    @Test
    fun bulkPackagePublicationSkipsUnverifiedRevokedAndProofOnlyDevices() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-package-eligibility-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000013")
            val crypto = DefaultCryptoService()

            crypto.generateDeviceKeyPair().use { wrappingDevice ->
                val wrappingPublicKey =
                    java.util.Base64.getEncoder().encodeToString(wrappingDevice.publicKey())
                val proofPublicKey =
                    java.util.Base64.getEncoder().encodeToString("proof-key".encodeToByteArray())
                responseBody =
                    """
                    [
                      {
                        "deviceId": "eligible-phone",
                        "keyAlgorithm": "ED25519",
                        "publicKey": "$proofPublicKey",
                        "wrappingKeyAlgorithm": "${wrappingDevice.keyAlgorithm()}",
                        "wrappingPublicKey": "$wrappingPublicKey",
                        "createdAt": "2026-07-03T00:00:00Z",
                        "verifiedAt": "2026-07-03T00:01:00Z"
                      },
                      {
                        "deviceId": "unverified-phone",
                        "keyAlgorithm": "ED25519",
                        "publicKey": "$proofPublicKey",
                        "wrappingKeyAlgorithm": "${wrappingDevice.keyAlgorithm()}",
                        "wrappingPublicKey": "$wrappingPublicKey",
                        "createdAt": "2026-07-03T00:00:00Z"
                      },
                      {
                        "deviceId": "revoked-phone",
                        "keyAlgorithm": "ED25519",
                        "publicKey": "$proofPublicKey",
                        "wrappingKeyAlgorithm": "${wrappingDevice.keyAlgorithm()}",
                        "wrappingPublicKey": "$wrappingPublicKey",
                        "createdAt": "2026-07-03T00:00:00Z",
                        "verifiedAt": "2026-07-03T00:01:00Z",
                        "revokedAt": "2026-07-03T00:02:00Z"
                      },
                      {
                        "deviceId": "legacy-proof-only-phone",
                        "keyAlgorithm": "ED25519",
                        "publicKey": "$proofPublicKey",
                        "createdAt": "2026-07-03T00:00:00Z",
                        "verifiedAt": "2026-07-03T00:01:00Z"
                      }
                    ]
                    """.trimIndent()

                LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
                    assertEquals(
                        1,
                        session.publishVaultKeyPackagesForRegisteredDevices(
                            KeysteadServerClient(baseUrl, "alice", "server-password"),
                        ),
                    )
                }
            }

            val packageRequests = requests.filter { it.method == "PUT" }
            assertEquals(1, packageRequests.size)
            assertEquals(
                "/api/v1/vaults/$vaultId/key-packages/eligible-phone",
                packageRequests.single().path,
            )
        }

    @Test
    fun directPackagePublicationRejectsUnverifiedDeviceBeforeHttpRequest() =
        withServer { baseUrl, requests ->
            val directory = createTempDirectory("keystead-client-package-direct-gate-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000014")
            DefaultCryptoService().generateDeviceKeyPair().use { wrappingDevice ->
                val publicKey = wrappingDevice.publicKey()
                try {
                    val unverified =
                        eligibleDevice("unverified-phone", wrappingDevice.keyAlgorithm(), publicKey)
                            .copy(verifiedAt = null)
                    LocalVaultSession.openOrCreate(
                        directory,
                        vaultId,
                        "master-password".toCharArray(),
                    ).use { session ->
                        assertFailsWith<IllegalStateException> {
                            session.publishVaultKeyPackage(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                unverified,
                            )
                        }
                    }
                } finally {
                    publicKey.fill(0)
                }
            }

            assertTrue(requests.isEmpty())
        }

    @Test
    fun openProvisionedFromServerUsesDevicePackageThenImportsRecords() =
        withServer { baseUrl, requests ->
            val sourceDirectory = createTempDirectory("keystead-client-source-test")
            val targetDirectory = createTempDirectory("keystead-client-target-test")
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000004")
            val crypto = DefaultCryptoService()

            crypto.generateDeviceKeyPair().use { device ->
                var exportedRecord = ""
                LocalVaultSession.openOrCreate(sourceDirectory, vaultId, "master-password".toCharArray()).use { source ->
                    source.addLogin(
                        title = "GitHub",
                        username = "alice@example.com",
                        password = "secret-password",
                        url = "https://github.com",
                    )
                    source.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                    val pushedRecord = requests.single()
                    val secretId = pushedRecord.path.substringAfterLast("/")
                    exportedRecord =
                        pushedRecord.body.replaceFirst(
                            "{",
                            """{"vaultId":"$vaultId","secretId":"$secretId",""",
                        )
                    requests.clear()

                    val publicKey = device.publicKey()
                    val keyPackage =
                        try {
                            source.publishVaultKeyPackage(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                eligibleDevice("phone-1", device.keyAlgorithm(), publicKey),
                            )
                        } finally {
                            publicKey.fill(0)
                        }
                    responseBody =
                        """
                        [
                          {
                            "vaultId": "$vaultId",
                            "deviceId": "phone-1",
                            "keyAlgorithm": "${device.keyAlgorithm()}",
                            "encryptedVaultKey": "${keyPackage.encryptedVaultKey}",
                            "createdAt": "2026-07-03T00:00:00Z",
                            "updatedAt": "2026-07-03T00:00:00Z"
                          }
                        ]
                        """.trimIndent()
                }

                LocalVaultSession.openProvisionedFromServer(
                        targetDirectory,
                        vaultId,
                        deviceId = "phone-1",
                        devicePrivateKey = device.privateKey(),
                        client = KeysteadServerClient(baseUrl, "alice", "server-password"),
                    )
                    .use { target ->
                        responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                            recordPage(vaultId, 0, "[$exportedRecord]", highestRevision = 1, hasMore = false)
                        assertEquals(
                            1,
                            target.pullRecordsFrom(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                0,
                            ),
                        )
                        val imported = target.listSecrets().single()
                        assertEquals("GitHub", imported.title)
                        assertEquals("secret-password", target.revealPassword(imported.id))
                    }
            }
        }

    @Test
    fun persistedDeviceIdentityCanProvisionVaultAfterReload() =
        withServer { baseUrl, requests ->
            val sourceDirectory = createTempDirectory("keystead-client-source-test")
            val targetDirectory = createTempDirectory("keystead-client-target-test")
            val identityDirectory = createTempDirectory("keystead-client-device-test")
            val identityStore = DeviceIdentityStore(identityDirectory)
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000006")
            lateinit var exportedRecord: String

            identityStore
                .createOrLoad("phone-2", "device-passphrase".toCharArray())
                .close()

            val targetIdentity =
                identityStore.createOrLoad("phone-2", "device-passphrase".toCharArray())
            targetIdentity.use { identity ->
                LocalVaultSession.openOrCreate(sourceDirectory, vaultId, "master-password".toCharArray()).use { source ->
                    source.addLogin(
                        title = "GitHub",
                        username = "alice@example.com",
                        password = "secret-password",
                        url = "https://github.com",
                    )
                    source.pushRecordsTo(KeysteadServerClient(baseUrl, "alice", "server-password"), 0)
                    val pushedRecord = requests.single()
                    val secretId = pushedRecord.path.substringAfterLast("/")
                    exportedRecord =
                        pushedRecord.body.replaceFirst(
                            "{",
                            """{"vaultId":"$vaultId","secretId":"$secretId",""",
                        )
                    requests.clear()

                    val keyPackage =
                        source.publishVaultKeyPackage(
                            KeysteadServerClient(baseUrl, "alice", "server-password"),
                            identity,
                            eligibleDevice(identity),
                        )
                    responseBody =
                        """
                        [
                          {
                            "vaultId": "$vaultId",
                            "deviceId": "phone-2",
                            "keyAlgorithm": "${identity.keyAlgorithm}",
                            "encryptedVaultKey": "${keyPackage.encryptedVaultKey}",
                            "createdAt": "2026-07-03T00:00:00Z",
                            "updatedAt": "2026-07-03T00:00:00Z"
                          }
                        ]
                        """.trimIndent()
                }

                LocalVaultSession.openProvisionedFromServer(
                        targetDirectory,
                        vaultId,
                        identity,
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                    )
                    .use { target ->
                        responseByPath["/api/v1/vaults/$vaultId/records/page?sinceRevision=0&limit=100"] =
                            recordPage(vaultId, 0, "[$exportedRecord]", highestRevision = 1, hasMore = false)
                        assertEquals(
                            1,
                            target.pullRecordsFrom(
                                KeysteadServerClient(baseUrl, "alice", "server-password"),
                                0,
                            ),
                        )
                        val imported = target.listSecrets().single()
                        assertEquals("GitHub", imported.title)
                    }
            }
        }

    @Test
    fun openFirstProvisionedFromServerDiscoversVaultForDeviceIdentity() =
        withServer { baseUrl, requests ->
            val sourceDirectory = createTempDirectory("keystead-client-source-test")
            val targetDirectory = createTempDirectory("keystead-client-target-test")
            val identityDirectory = createTempDirectory("keystead-client-device-test")
            val identityStore = DeviceIdentityStore(identityDirectory)
            val vaultId = UUID.fromString("70000000-0000-0000-0000-000000000012")

            identityStore
                .createOrLoad("phone-4", "device-passphrase".toCharArray())
                .close()

            val targetIdentity =
                identityStore.createOrLoad("phone-4", "device-passphrase".toCharArray())
            targetIdentity.use { identity ->
                LocalVaultSession.openOrCreate(sourceDirectory, vaultId, "master-password".toCharArray()).use { source ->
                    val keyPackage =
                        source.publishVaultKeyPackage(
                            KeysteadServerClient(baseUrl, "alice", "server-password"),
                            identity,
                            eligibleDevice(identity),
                        )
                    responseByPath["/api/v1/vaults"] =
                        """
                        [
                          {
                            "vaultId": "$vaultId",
                            "encryptedMetadata": "opaque-vault-metadata",
                            "createdAt": "2026-07-03T00:00:00Z",
                            "updatedAt": "2026-07-03T00:00:00Z"
                          }
                        ]
                        """.trimIndent()
                    responseByPath["/api/v1/vaults/$vaultId/key-packages"] =
                        """
                        [
                          {
                            "vaultId": "$vaultId",
                            "deviceId": "phone-4",
                            "keyAlgorithm": "${identity.keyAlgorithm}",
                            "encryptedVaultKey": "${keyPackage.encryptedVaultKey}",
                            "createdAt": "2026-07-03T00:00:00Z",
                            "updatedAt": "2026-07-03T00:00:00Z"
                          }
                        ]
                        """.trimIndent()
                }
                requests.clear()

                LocalVaultSession.openFirstProvisionedFromServer(
                        targetDirectory,
                        identity,
                        KeysteadServerClient(baseUrl, "alice", "server-password"),
                    )
                    .use { target ->
                        assertTrue(target.listSecrets().isEmpty())
                    }
            }

            assertEquals("/api/v1/vaults", requests[0].path)
            assertEquals("/api/v1/vaults/$vaultId/key-packages", requests[1].path)
        }

    private fun eligibleDevice(
        deviceId: String,
        wrappingKeyAlgorithm: String,
        wrappingPublicKey: ByteArray,
    ): ServerDevice =
        ServerDevice(
            deviceId = deviceId,
            keyAlgorithm = "ED25519",
            publicKey = Base64.getEncoder().encodeToString("proof-$deviceId".encodeToByteArray()),
            wrappingKeyAlgorithm = wrappingKeyAlgorithm,
            wrappingPublicKey = Base64.getEncoder().encodeToString(wrappingPublicKey),
            createdAt = Instant.parse("2026-07-12T00:00:00Z"),
            verifiedAt = Instant.parse("2026-07-12T00:00:01Z"),
        )

    private fun eligibleDevice(identity: LocalDeviceIdentity): ServerDevice {
        val proofPublicKey = identity.proofPublicKey()
        val wrappingPublicKey = identity.publicKey()
        return try {
            ServerDevice(
                deviceId = identity.deviceId,
                keyAlgorithm = identity.proofKeyAlgorithm,
                publicKey = Base64.getEncoder().encodeToString(proofPublicKey),
                wrappingKeyAlgorithm = identity.keyAlgorithm,
                wrappingPublicKey = Base64.getEncoder().encodeToString(wrappingPublicKey),
                createdAt = Instant.parse("2026-07-12T00:00:00Z"),
                verifiedAt = Instant.parse("2026-07-12T00:00:01Z"),
            )
        } finally {
            proofPublicKey.fill(0)
            wrappingPublicKey.fill(0)
        }
    }

    private var responseBody: String = ""
    private var responseCode: Int = 201
    private val responseByPath = mutableMapOf<String, String>()

    private fun withServer(block: (String, MutableList<CapturedRequest>) -> Unit) {
        responseBody = ""
        responseCode = 201
        responseByPath.clear()
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requests.add(
                CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.toString(),
                    body = exchange.requestBody.readBytes().decodeToString(),
                ),
            )
            val response =
                (responseByPath[exchange.requestURI.toString()]
                        ?: responseByPath[exchange.requestURI.path]
                        ?: responseBody)
                    .encodeToByteArray()
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

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val body: String,
    )

    private fun recordPage(
        vaultId: UUID,
        sinceRevision: Long,
        records: String,
        highestRevision: Long,
        hasMore: Boolean,
        nextSinceRevision: Long? = null,
    ): String =
        """
        {
          "vaultId": "$vaultId",
          "sinceRevision": $sinceRevision,
          "records": $records,
          "highestRevision": $highestRevision,
          "hasMore": $hasMore,
          "nextSinceRevision": ${nextSinceRevision?.toString() ?: "null"}
        }
        """.trimIndent()
}
