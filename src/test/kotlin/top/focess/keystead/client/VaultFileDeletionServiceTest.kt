package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultFileDeletionServiceTest {
    @Test
    fun deletionRemovesOnlyTheSelectedVaultFile() {
        val root = Files.createTempDirectory("keystead-delete-vault")
        val selected = root.resolve("selected.kvault")
        val sibling = root.resolve("keep.kvault")
        Files.writeString(selected, "selected")
        Files.writeString(sibling, "keep")

        val deleted = VaultFileDeletionService().delete(selected)

        assertEquals(selected.toAbsolutePath().normalize(), deleted)
        assertFalse(Files.exists(selected))
        assertTrue(Files.exists(sibling))
        assertEquals("keep", Files.readString(sibling))
    }

    @Test
    fun deletionRejectsDirectoriesAndUnrelatedFiles() {
        val root = Files.createTempDirectory("keystead-delete-vault-reject")
        val directoryNamedLikeVault = root.resolve("folder.kvault")
        val unrelatedFile = root.resolve("notes.txt")
        Files.createDirectories(directoryNamedLikeVault)
        Files.writeString(unrelatedFile, "keep")

        assertFailsWith<IllegalArgumentException> {
            VaultFileDeletionService().delete(directoryNamedLikeVault)
        }
        assertFailsWith<IllegalArgumentException> {
            VaultFileDeletionService().delete(unrelatedFile)
        }

        assertTrue(Files.exists(directoryNamedLikeVault))
        assertEquals("keep", Files.readString(unrelatedFile))
    }

    @Test
    fun deletionReportsAFileThatAlreadyDisappeared() {
        val root = Files.createTempDirectory("keystead-delete-vault-missing")
        val missing = root.resolve("missing.kvault")

        assertFailsWith<IllegalStateException> {
            VaultFileDeletionService().delete(missing)
        }
    }
}
