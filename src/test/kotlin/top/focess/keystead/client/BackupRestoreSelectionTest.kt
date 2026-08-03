package top.focess.keystead.client

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupRestoreSelectionTest {
    @Test
    fun `valid backup and unused vault target can be reviewed`() {
        val directory = createTempDirectory("keystead-backup-selection")
        val source = directory.resolve("portable.ksbackup")
        Files.writeString(source, "backup")

        val selection = BackupRestoreSelection(source, directory.resolve("restored.kvault"))

        assertTrue(selection.sourceReady)
        assertTrue(selection.targetReady)
        assertTrue(selection.canReview)
    }

    @Test
    fun `existing vault target can never be reviewed`() {
        val directory = createTempDirectory("keystead-backup-selection-existing")
        val source = directory.resolve("portable.ksbackup")
        val target = directory.resolve("existing.kvault")
        Files.writeString(source, "backup")
        Files.writeString(target, "existing vault")

        val selection = BackupRestoreSelection(source, target)

        assertTrue(selection.targetExists)
        assertFalse(selection.targetReady)
        assertFalse(selection.canReview)
    }

    @Test
    fun `restore selection requires the portable and vault file types`() {
        val directory = createTempDirectory("keystead-backup-selection-types")
        val wrongSource = directory.resolve("portable.zip")
        Files.writeString(wrongSource, "backup")

        assertFalse(
            BackupRestoreSelection(wrongSource, directory.resolve("restored.kvault")).canReview,
        )
        assertFalse(
            BackupRestoreSelection(null, directory.resolve("restored.kvault")).canReview,
        )
        assertFalse(
            BackupRestoreSelection(directory.resolve("missing.ksbackup"), directory.resolve("restored.kvault"))
                .canReview,
        )
        assertFalse(
            BackupRestoreSelection(wrongSource.resolveSibling("portable.ksbackup"), directory.resolve("restored.db"))
                .canReview,
        )
    }
}
