package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VaultBackupTest {
    @Test
    fun passwordProtectedBackupRestoresNewVaultWithoutSourceDeviceOrServer() {
        val directory = createTempDirectory("keystead-full-backup")
        val sourceFile = directory.resolve("source.kvault")
        val restoredFile = directory.resolve("restored.kvault")
        val archive =
            ByteArrayOutputStream().use { output ->
                LocalVaultSession.openOrCreate(
                    sourceFile,
                    "original-local-passphrase".toCharArray(),
                ).use { source ->
                    source.addLogin(
                        "GitHub",
                        "alice@example.com",
                        "secret-password",
                        "https://github.com",
                    )
                    VaultBackup.export(
                        source,
                        "portable-backup-password".toCharArray(),
                        output,
                    )
                }
                output.toByteArray()
            }
        Files.delete(sourceFile)

        val archiveText = String(archive, StandardCharsets.ISO_8859_1)
        assertFalse(archiveText.contains("GitHub"))
        assertFalse(archiveText.contains("secret-password"))

        ByteArrayInputStream(archive).use { input ->
            VaultBackup.restore(
                restoredFile,
                input,
                "portable-backup-password".toCharArray(),
                "new-local-master-passphrase".toCharArray(),
            ).use { restored ->
                val login = restored.listLogins().single()
                assertEquals("GitHub", login.title)
                assertEquals("secret-password", restored.revealPassword(login.id))
            }
        }

        LocalVaultSession.openOrCreate(
            restoredFile,
            "new-local-master-passphrase".toCharArray(),
        ).use { reopened ->
            assertEquals(listOf("GitHub"), reopened.listLogins().map { it.title })
        }
        assertFails {
            LocalVaultSession.openOrCreate(
                restoredFile,
                "original-local-passphrase".toCharArray(),
            )
        }
    }

    @Test
    fun restoringBackupNeverOverwritesAnExistingVault() {
        val directory = createTempDirectory("keystead-backup-no-overwrite")
        val sourceFile = directory.resolve("source.kvault")
        val existingTarget = directory.resolve("existing.kvault")
        val archive =
            ByteArrayOutputStream().use { output ->
                LocalVaultSession.openOrCreate(
                    sourceFile,
                    "source-master-passphrase".toCharArray(),
                ).use { source ->
                    source.addLogin("Source", "source-user", "source-password", "")
                    VaultBackup.export(source, "backup-password".toCharArray(), output)
                }
                output.toByteArray()
            }
        LocalVaultSession.openOrCreate(
            existingTarget,
            "existing-master-passphrase".toCharArray(),
        ).use { existing ->
            existing.addLogin("Keep me", "existing-user", "existing-password", "")
        }

        assertFailsWith<FileAlreadyExistsException> {
            ByteArrayInputStream(archive).use { input ->
                VaultBackup.restore(
                    existingTarget,
                    input,
                    "backup-password".toCharArray(),
                    "new-master-passphrase".toCharArray(),
                )
            }
        }

        LocalVaultSession.openOrCreate(
            existingTarget,
            "existing-master-passphrase".toCharArray(),
        ).use { existing ->
            assertEquals(listOf("Keep me"), existing.listLogins().map { it.title })
            assertEquals("existing-password", existing.revealPassword(existing.listLogins().single().id))
        }
    }
}
