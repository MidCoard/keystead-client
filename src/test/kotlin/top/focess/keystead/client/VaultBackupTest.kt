package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultBackupTest {
    private val vaultId = UUID.fromString("71000000-0000-0000-0000-000000000001")

    @Test
    fun exportAndRestoreRoundTripsSecretIntoFreshDirectory() {
        val source = createTempDirectory("keystead-backup-source")
        val password = "master-password".toCharArray()
        LocalVaultSession.openOrCreate(source, vaultId, password).use { session ->
            session.addLogin(
                title = "GitHub",
                username = "alice@example.com",
                password = "secret-password",
                url = "https://github.com",
            )
        }

        val archive = ByteArrayOutputStream().use { out ->
            VaultBackup.export(source, vaultId.toString(), out)
            out.toByteArray()
        }
        // The archive is encrypted: neither the title nor the URL may appear in plaintext.
        val archiveText = String(archive)
        assertFalse(archiveText.contains("GitHub"))
        assertFalse(archiveText.contains("github.com"))

        val target = createTempDirectory("keystead-backup-target")
        val report = ByteArrayInputStream(archive).use { input ->
            VaultBackup.restore(target, vaultId.toString(), input)
        }
        assertEquals(1, report.imported)
        assertEquals(0, report.skipped)
        assertTrue(report.conflicts.isEmpty())

        // The restored vault opens with the same master password and carries the secret.
        // (openOrCreate wipes the password array it is handed, so pass a fresh copy.)
        LocalVaultSession.openOrCreate(target, vaultId, "master-password".toCharArray()).use { session ->
            val titles = session.listLogins().map { it.title }
            assertTrue(titles.contains("GitHub"))
        }
    }

    @Test
    fun restoreIntoExistingVaultSkipsRowsAlreadyAtLeastAsNew() {
        val source = createTempDirectory("keystead-backup-conflict")
        val password = "master-password".toCharArray()
        LocalVaultSession.openOrCreate(source, vaultId, password).use { session ->
            session.addLogin(
                title = "GitHub",
                username = "alice@example.com",
                password = "secret-password",
                url = "https://github.com",
            )
        }

        val archive = ByteArrayOutputStream().use { out ->
            VaultBackup.export(source, vaultId.toString(), out)
            out.toByteArray()
        }

        val report = ByteArrayInputStream(archive).use { input ->
            VaultBackup.restore(source, vaultId.toString(), input)
        }
        assertEquals(0, report.imported)
        assertEquals(1, report.skipped)
        assertEquals(1, report.conflicts.size)
        assertEquals(1L, report.conflicts.first().existingRevision)
        assertEquals(1L, report.conflicts.first().incomingRevision)
    }

    @Test
    fun restoreRejectsArchiveForADifferentVault() {
        val source = createTempDirectory("keystead-backup-source")
        LocalVaultSession.openOrCreate(source, vaultId, "master-password".toCharArray()).use { session ->
            session.addLogin(
                title = "GitHub",
                username = "alice@example.com",
                password = "secret-password",
                url = "https://github.com",
            )
        }

        val otherId = UUID.fromString("72000000-0000-0000-0000-000000000002")
        val otherDir = createTempDirectory("keystead-backup-other")
        LocalVaultSession.openOrCreate(otherDir, otherId, "other-password".toCharArray()).use { session ->
            session.addLogin(
                title = "Other",
                username = "bob",
                password = "other-password",
                url = null,
            )
        }
        val otherArchive = ByteArrayOutputStream().use { out ->
            VaultBackup.export(otherDir, otherId.toString(), out)
            out.toByteArray()
        }

        assertFailsWith<IllegalStateException> {
            ByteArrayInputStream(otherArchive).use { input ->
                VaultBackup.restore(source, vaultId.toString(), input)
            }
        }
    }
}
