package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultBackupTest {
    @Test
    fun exportAndRestoreRoundTripsSecretIntoProvisionedVault() {
        val directory = createTempDirectory("keystead-backup")
        val sourceFile = directory.resolve("source.kvault")
        lateinit var fingerprint: String
        val archive = ByteArrayOutputStream().use { out ->
            LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
                fingerprint = session.fingerprintValue()
                session.addLogin("GitHub", "alice@example.com", "secret-password", "https://github.com")
                VaultBackup.export(session, out)
            }
            out.toByteArray()
        }
        // The archive is encrypted: neither the title nor the URL may appear in plaintext.
        val archiveText = String(archive)
        assertFalse(archiveText.contains("GitHub"))
        assertFalse(archiveText.contains("github.com"))

        // A backup is bound to its vault's data-encryption key, so restoring into an empty vault is only
        // valid into a device-provisioned target that shares that key. Provision one, then restore.
        DeviceIdentityStore(directory.resolve("device"))
            .createOrLoad("laptop-1", "identity-password".toCharArray())
            .use { identity ->
                val publicKey = identity.publicKey()
                val proofPublicKey = identity.proofPublicKey()
                var publishedPackage: String? = null
                try {
                    withServer(
                        responseFor = { request ->
                            when (request.path) {
                                "/api/v1/devices" ->
                                    TestResponse(
                                        200,
                                        """[{"deviceId":"${identity.deviceId}","keyAlgorithm":"${identity.proofKeyAlgorithm}","publicKey":"${Base64.getEncoder().encodeToString(proofPublicKey)}","wrappingKeyAlgorithm":"${identity.keyAlgorithm}","wrappingPublicKey":"${Base64.getEncoder().encodeToString(publicKey)}","createdAt":"2026-07-12T00:00:00Z","verifiedAt":"2026-07-12T00:00:01Z"}]""",
                                    )
                                "/api/v1/vaults/$fingerprint/key-packages/${identity.deviceId}" -> {
                                    publishedPackage = request.body
                                    TestResponse(204)
                                }
                                "/api/v1/vaults/$fingerprint/key-packages" ->
                                    TestResponse(
                                        200,
                                        "[{\"fingerprint\":\"$fingerprint\",\"deviceId\":\"${identity.deviceId}\",${publishedPackage!!.drop(1).dropLast(1)}}]",
                                    )
                                else -> error("Unexpected request: ${request.method} ${request.path}")
                            }
                        },
                    ) { baseUrl, _ ->
                        val client = KeysteadServerClient(baseUrl, "alice", "secret")
                        LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
                            session.publishVaultKeyPackagesForRegisteredDevices(client)
                        }
                        val targetFile = directory.resolve("target.kvault")
                        LocalVaultSession.openProvisionedFromServer(targetFile, fingerprint, identity, client).use { target ->
                            val report = ByteArrayInputStream(archive).use { input ->
                                VaultBackup.restore(target, input)
                            }
                            assertEquals(1, report.imported)
                            assertEquals(0, report.skipped)
                            assertTrue(report.conflicts.isEmpty())
                            assertTrue(target.listLogins().map { it.title }.contains("GitHub"))
                        }
                    }
                } finally {
                    publicKey.fill(0)
                    proofPublicKey.fill(0)
                }
            }
    }

    @Test
    fun restoreIntoSameVaultSkipsRowsAlreadyAtLeastAsNew() {
        val directory = createTempDirectory("keystead-backup-conflict")
        val sourceFile = directory.resolve("source.kvault")
        LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
            session.addLogin("GitHub", "alice@example.com", "secret-password", "https://github.com")
        }

        val archive = ByteArrayOutputStream().use { out ->
            LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
                VaultBackup.export(session, out)
            }
            out.toByteArray()
        }

        val report = ByteArrayInputStream(archive).use { input ->
            LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
                VaultBackup.restore(session, input)
            }
        }
        assertEquals(0, report.imported)
        assertEquals(1, report.skipped)
        // Same-revision, same-deletion-state rows are a pure skip: a conflict is only reported when
        // the deletion state differs (one side tombstoned, the other active).
        assertTrue(report.conflicts.isEmpty())
    }

    @Test
    fun restoreRejectsArchiveForADifferentVault() {
        val directory = createTempDirectory("keystead-backup-reject")
        val sourceFile = directory.resolve("source.kvault")
        LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
            session.addLogin("GitHub", "alice@example.com", "secret-password", "https://github.com")
        }

        val otherFile = directory.resolve("other.kvault")
        val otherArchive = ByteArrayOutputStream().use { out ->
            LocalVaultSession.openOrCreate(otherFile, "other-password".toCharArray()).use { session ->
                session.addLogin("Other", "bob", "other-password", null)
                VaultBackup.export(session, out)
            }
            out.toByteArray()
        }

        assertFailsWith<IllegalArgumentException> {
            ByteArrayInputStream(otherArchive).use { input ->
                LocalVaultSession.openOrCreate(sourceFile, "master-password".toCharArray()).use { session ->
                    VaultBackup.restore(session, input)
                }
            }
        }
    }

    private fun withServer(
        responseFor: (CapturedRequest) -> TestResponse,
        block: (String, MutableList<CapturedRequest>) -> Unit,
    ) {
        val requests = mutableListOf<CapturedRequest>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val request =
                CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.toString(),
                    body = exchange.requestBody.readBytes().decodeToString(),
                )
            requests.add(request)
            val testResponse = responseFor(request)
            val response = testResponse.body.encodeToByteArray()
            exchange.sendResponseHeaders(testResponse.statusCode, response.size.toLong())
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

    private data class CapturedRequest(val method: String, val path: String, val body: String)

    private data class TestResponse(val statusCode: Int, val body: String = "")
}
