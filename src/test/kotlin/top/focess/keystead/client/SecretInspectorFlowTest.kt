package top.focess.keystead.client

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class SecretInspectorFlowTest {
    private lateinit var directory: Path

    @AfterTest
    fun clean() {
        if (::directory.isInitialized) directory.toFile().deleteRecursively()
    }

    @Test
    fun loginInspectorReceivesItsUrlAndCompleteStoredMetadata() {
        open().use { session ->
            val id =
                session.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com/alice",
                    category = "development",
                    provider = "github",
                    software = "github.com",
                    account = "alice",
                    expiry = "2030-12-31",
                )

            val item = session.listSecrets().single { it.id == id }

            assertEquals("https://github.com/alice", item.url)
            assertEquals("development", item.category)
            assertEquals("github", item.provider)
            assertEquals("github.com", item.software)
            assertEquals("alice", item.account)
            assertEquals("2030-12-31", item.expiry)
            assertNotNull(item.createdAt)
            assertNotNull(item.updatedAt)
            assertEquals(1, item.revision)
            assertTrue(item.tags.isEmpty())
            assertTrue(item.labels.isEmpty())
            assertEquals(mapOf("expiry" to "2030-12-31"), item.attributes)
        }
    }

    @Test
    fun structuredInspectorReceivesEveryFieldWithoutPreloadingSecretValues() {
        open().use { session ->
            val id =
                session.addStructuredSecret(
                    type = SecretType.SSH_KEY,
                    title = "Deploy key",
                    fields =
                        linkedMapOf(
                            "publicKey" to "ssh-ed25519 AAAA-public",
                            "privateKey" to "PRIVATE",
                            "passphrase" to "PASSPHRASE",
                        ),
                )

            val fields = session.listSecrets().single { it.id == id }.fields

            assertEquals(listOf("publicKey", "privateKey", "passphrase"), fields.map { it.name })
            assertEquals("ssh-ed25519 AAAA-public", fields[0].value)
            assertEquals(false, fields[0].secret)
            assertNull(fields[1].value)
            assertEquals(true, fields[1].secret)
            assertNull(fields[2].value)
            assertEquals(true, fields[2].secret)
        }
    }

    private fun open(): LocalVaultSession {
        directory = createTempDirectory("keystead-inspector")
        return LocalVaultSession.openOrCreate(
            directory.resolve("vault.kvault"),
            "master-password".toCharArray(),
        )
    }
}
