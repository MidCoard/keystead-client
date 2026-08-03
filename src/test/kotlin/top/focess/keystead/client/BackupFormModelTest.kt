package top.focess.keystead.client

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupFormModelTest {
    @Test
    fun newVaultMasterPassphraseMustBePresentAndMatch() {
        assertTrue(BackupFormModel.canUseNewMasterPassphrase("new-master", "new-master"))
        assertFalse(BackupFormModel.canUseNewMasterPassphrase("", ""))
        assertFalse(BackupFormModel.canUseNewMasterPassphrase("new-master", "different"))
    }

    @Test
    fun exportRequiresOpenVaultAndMatchingBackupPassword() {
        assertFalse(BackupFormModel.canExport(false, "backup-password", "backup-password"))
        assertFalse(BackupFormModel.canExport(true, "backup-password", "different"))
        assertTrue(BackupFormModel.canExport(true, "backup-password", "backup-password"))
    }

    @Test
    fun restoreReviewRequiresSelectedFilesAndMatchingPasswordPairs() {
        val directory = createTempDirectory("keystead-backup-form")
        val source = directory.resolve("portable.ksbackup")
        Files.writeString(source, "backup")
        val readySelection = BackupRestoreSelection(source, directory.resolve("restored.kvault"))

        assertTrue(
            BackupFormModel.canReviewRestore(
                readySelection,
                "backup-password",
                "backup-password",
                "new-local-master",
                "new-local-master",
            ),
        )
        assertFalse(
            BackupFormModel.canReviewRestore(
                readySelection,
                "backup-password",
                "wrong-backup-confirmation",
                "new-local-master",
                "new-local-master",
            ),
        )
        assertFalse(
            BackupFormModel.canReviewRestore(
                readySelection,
                "backup-password",
                "backup-password",
                "new-local-master",
                "wrong-master-confirmation",
            ),
        )
        assertFalse(
            BackupFormModel.canReviewRestore(
                BackupRestoreSelection(source, null),
                "backup-password",
                "backup-password",
                "new-local-master",
                "new-local-master",
            ),
        )
    }
}
