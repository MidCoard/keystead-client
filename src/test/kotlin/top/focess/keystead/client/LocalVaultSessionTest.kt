package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class LocalVaultSessionTest {
    @Test
    fun editSnapshotToStringRedactsAllDecryptedFields() {
        val snapshot =
            SecretEditSnapshot(
                id = "secret-id-sentinel",
                type = SecretType.GPG_KEY.name,
                title = "title-sentinel",
                username = "username-sentinel",
                password = "password-sentinel",
                fields =
                    mapOf(
                        "privateKey" to "private-key-sentinel",
                        "passphrase" to "passphrase-sentinel",
                    ),
            )

        val rendered = snapshot.toString()

        assertEquals("SecretEditSnapshot(<redacted>)", rendered)
        listOf(
            "secret-id-sentinel",
            "title-sentinel",
            "username-sentinel",
            "password-sentinel",
            "private-key-sentinel",
            "passphrase-sentinel",
        ).forEach { sentinel -> assertFalse(rendered.contains(sentinel)) }
    }

    @Test
    fun addRevealAndDeleteLogin() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000001")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com",
                    category = "development",
                    provider = "github",
                    software = "github.com",
                    account = "alice@example.com",
                )

            assertEquals(listOf("GitHub"), session.listLogins().map { it.title })
            assertEquals("github.com", session.listSecrets().single().software)
            assertEquals("secret-password", session.revealPassword(id))

            session.delete(id)

            assertTrue(session.listLogins().isEmpty())
        }
    }

    @Test
    fun reopenExistingVaultKeepsSavedLogin() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000002")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            session.addLogin("GitHub", "alice@example.com", "secret-password", "https://github.com")
        }

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            assertEquals(listOf("GitHub"), session.listLogins().map { it.title })
        }
    }

    @Test
    fun addRevealAndDeleteStructuredDeveloperSecret() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000003")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addStructuredSecret(
                    type = SecretType.SSH_KEY,
                    title = "Work SSH",
                    fields =
                        mapOf(
                            "publicKey" to "ssh-ed25519 AAAA",
                            "privateKey" to "-----BEGIN OPENSSH PRIVATE KEY-----",
                            "passphrase" to "private-passphrase",
                        ),
                    category = "development",
                    provider = "ssh",
                    software = "openssh",
                    account = "git@example.com",
                )

            assertEquals(listOf("Work SSH"), session.listSecrets().map { it.title })
            assertEquals(SecretType.SSH_KEY.name, session.listSecrets().single().type)
            assertEquals("openssh", session.listSecrets().single().software)
            assertEquals(
                "-----BEGIN OPENSSH PRIVATE KEY-----",
                session.revealField(id, "privateKey"),
            )

            session.delete(id)

            assertTrue(session.listSecrets().isEmpty())
        }
    }

    @Test
    fun updateLoginReplacesPasswordInSameRecord() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000004")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "old-password",
                    url = "https://github.com",
                )

            session.updateLogin(
                id,
                title = "GitHub",
                username = "alice@example.com",
                password = "new-password",
                url = "https://github.com",
            )

            assertEquals("new-password", session.revealPassword(id))
            assertEquals(listOf(id), session.listSecrets().map { it.id })
        }
    }

    @Test
    fun updateStructuredSecretReplacesFieldsInSameRecord() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000005")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "GitHub token",
                    fields = mapOf("token" to "ghp_old"),
                    category = "development",
                    provider = "github",
                )

            session.updateStructuredSecret(
                id,
                title = "GitHub token",
                fields = mapOf("token" to "ghp_new"),
                category = "development",
                provider = "github",
                software = "github.com",
                account = "alice@example.com",
            )

            assertEquals("ghp_new", session.revealField(id, "token"))
            val item = session.listSecrets().single()
            assertEquals(id, item.id)
            assertEquals("github.com", item.software)
            assertEquals("alice@example.com", item.account)
        }
    }

    @Test
    fun editSnapshotLoadsLoginFormValues() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000006")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com",
                    category = "development",
                    provider = "github",
                    software = "github.com",
                    account = "alice@example.com",
                )

            val snapshot = session.editSnapshot(id)

            assertEquals(id, snapshot.id)
            assertEquals(SecretType.LOGIN_PASSWORD.name, snapshot.type)
            assertEquals("GitHub", snapshot.title)
            assertEquals("alice@example.com", snapshot.username)
            assertEquals("secret-password", snapshot.password)
            assertEquals("https://github.com", snapshot.url)
            assertEquals("github.com", snapshot.software)
            assertEquals("alice@example.com", snapshot.account)
        }
    }

    @Test
    fun editSnapshotLoadsStructuredFormValues() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000007")

        LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray()).use { session ->
            val id =
                session.addStructuredSecret(
                    type = SecretType.SSH_KEY,
                    title = "Work SSH",
                    fields =
                        mapOf(
                            "publicKey" to "ssh-ed25519 AAAA",
                            "privateKey" to "-----BEGIN OPENSSH PRIVATE KEY-----",
                            "passphrase" to "private-passphrase",
                        ),
                    category = "development",
                    provider = "ssh",
                    software = "openssh",
                    account = "git@example.com",
                )

            val snapshot = session.editSnapshot(id)

            assertEquals(id, snapshot.id)
            assertEquals(SecretType.SSH_KEY.name, snapshot.type)
            assertEquals("Work SSH", snapshot.title)
            assertEquals("development", snapshot.category)
            assertEquals("ssh", snapshot.provider)
            assertEquals("openssh", snapshot.software)
            assertEquals("git@example.com", snapshot.account)
            assertEquals("ssh-ed25519 AAAA", snapshot.fields["publicKey"])
            assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----", snapshot.fields["privateKey"])
            assertEquals("private-passphrase", snapshot.fields["passphrase"])
        }
    }

    @Test
    fun rotateVaultKeyAndPublishDeclaresReplacementAndRepublishesDevicePackage() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000008")
        DeviceIdentityStore(directory.resolve("device"))
            .createOrLoad("laptop-1", "identity-password".toCharArray())
            .use { identity ->
                val publicKey = identity.publicKey()
                val proofPublicKey = identity.proofPublicKey()
                try {
                    withServer(
                        responseFor = { request ->
                            when (request.path) {
                                "/api/v1/vaults/$vaultId/key-rotation" -> TestResponse(204)
                                "/api/v1/devices" ->
                                    TestResponse(
                                        200,
                                        """[{"deviceId":"laptop-1","keyAlgorithm":"${identity.proofKeyAlgorithm}","publicKey":"${Base64.getEncoder().encodeToString(proofPublicKey)}","wrappingKeyAlgorithm":"${identity.keyAlgorithm}","wrappingPublicKey":"${Base64.getEncoder().encodeToString(publicKey)}","createdAt":"2026-07-12T00:00:00Z","verifiedAt":"2026-07-12T00:00:01Z"}]""",
                                    )
                                "/api/v1/vaults/$vaultId/key-packages/laptop-1" -> TestResponse(204)
                                else -> error("Unexpected request: ${request.method} ${request.path}")
                            }
                        },
                    ) { baseUrl, requests ->
                        LocalVaultSession.openOrCreate(
                            directory.resolve("vault"),
                            vaultId,
                            "master-password".toCharArray(),
                        ).use { session ->
                            val vaultKeyId =
                                session.rotateVaultKeyAndPublish(
                                    KeysteadServerClient(baseUrl, "alice", "secret"),
                                    "master-password".toCharArray(),
                                )

                            assertEquals(3, requests.size)
                            assertEquals("PUT", requests[0].method)
                            assertEquals(
                                "/api/v1/vaults/$vaultId/key-rotation",
                                requests[0].path,
                            )
                            assertTrue(requests[0].body.contains("\"vaultKeyId\":\"$vaultKeyId\""))
                            assertEquals("GET", requests[1].method)
                            assertEquals("/api/v1/devices", requests[1].path)
                            assertEquals("PUT", requests[2].method)
                            assertEquals(
                                "/api/v1/vaults/$vaultId/key-packages/laptop-1",
                                requests[2].path,
                            )
                            assertTrue(requests[2].body.contains("\"vaultKeyId\":\"$vaultKeyId\""))
                            assertTrue(requests[2].body.contains("\"encryptedVaultKey\":"))
                            assertTrue(!requests[2].body.contains("master-password"))
                        }
                    }
                } finally {
                    publicKey.fill(0)
                    proofPublicKey.fill(0)
                }
            }
    }

    @Test
    fun publishAutomationVaultKeyPackageSendsRecipientBoundCiphertextOnly() {
        val directory = createTempDirectory("keystead-client-test")
        val vaultId = UUID.fromString("40000000-0000-0000-0000-000000000009")
        DeviceIdentityStore(directory.resolve("automation"))
            .createOrLoad("automation-key", "identity-password".toCharArray())
            .use { identity ->
                val publicKey = identity.publicKey()
                try {
                    withServer(
                        responseFor = { request ->
                            when (request.path) {
                                "/api/v1/vaults/$vaultId/automation-principals/backup/key-package" ->
                                    TestResponse(204)
                                else -> error("Unexpected request: ${request.method} ${request.path}")
                            }
                        },
                    ) { baseUrl, requests ->
                        LocalVaultSession.openOrCreate(
                            directory.resolve("vault"),
                            vaultId,
                            "master-password".toCharArray(),
                        ).use { session ->
                            val keyPackage =
                                session.publishAutomationVaultKeyPackage(
                                    KeysteadServerClient(baseUrl, "alice", "secret"),
                                    "backup",
                                    publicKey,
                                )

                            val request = requests.single()
                            assertEquals("PUT", request.method)
                            assertEquals(
                                "/api/v1/vaults/$vaultId/automation-principals/backup/key-package",
                                request.path,
                            )
                            assertTrue(request.body.contains("\"vaultKeyId\":\"${keyPackage.vaultKeyId}\""))
                            assertTrue(request.body.contains("\"encryptedVaultKey\":"))
                            assertTrue(!request.body.contains("master-password"))
                            assertTrue(!request.body.contains("backup"))
                        }
                    }
                } finally {
                    publicKey.fill(0)
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
