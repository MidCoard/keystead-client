package top.focess.keystead.client

import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import top.focess.keystead.model.SecretType

class SecretExpiryFlowTest {
    private val vaultId = UUID.fromString("71000000-0000-0000-0000-000000000005")
    private lateinit var directory: Path

    @AfterTest
    fun clean() {
        if (::directory.isInitialized) {
            directory.toFile().deleteRecursively()
        }
    }

    private fun open(): LocalVaultSession {
        directory = createTempDirectory("keystead-expiry")
        return LocalVaultSession.openOrCreate(directory, vaultId, "master-password".toCharArray())
    }

    @Test
    fun loginExpiryRoundTripsThroughAttributes() {
        val session = open()
        session.use {
            val expiry = LocalDate.now().plusDays(5).toString()
            val id =
                it.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com",
                    expiry = expiry,
                )
            val item = it.listSecrets().first { s -> s.id == id }
            assertEquals(expiry, item.expiry)
            val snapshot = it.editSnapshot(id)
            assertEquals(expiry, snapshot.expiry)
            val state = SecretExpiry.state(item.expiry)
            assertNotNull(state)
            assertEquals(SecretExpiryStatus.DUE_SOON, state.status)
        }
    }

    @Test
    fun blankExpiryOnUpdateClearsExpiry() {
        val session = open()
        session.use {
            val id =
                it.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = null,
                    expiry = LocalDate.now().plusDays(30).toString(),
                )
            assertEquals(
                SecretExpiryStatus.ACTIVE,
                SecretExpiry.state(it.listSecrets().first { s -> s.id == id }.expiry)?.status,
            )
            it.updateLogin(
                secretId = id,
                title = "GitHub",
                username = "alice@example.com",
                password = "secret-password",
                url = null,
                expiry = null,
            )
            assertNull(it.listSecrets().first { s -> s.id == id }.expiry)
        }
    }

    @Test
    fun structuredSecretExpiryRoundTripsThroughAttributes() {
        val session = open()
        session.use {
            val expiry = LocalDate.now().minusDays(1).toString()
            val id =
                it.addStructuredSecret(
                    type = SecretType.API_TOKEN,
                    title = "CI token",
                    fields = mapOf("token" to "tok-123"),
                    expiry = expiry,
                )
            val item = it.listSecrets().first { s -> s.id == id }
            assertEquals(expiry, item.expiry)
            assertEquals(SecretExpiryStatus.EXPIRED, SecretExpiry.state(item.expiry)?.status)
            val snapshot = it.editSnapshot(id)
            assertEquals(expiry, snapshot.expiry)
        }
    }
}
