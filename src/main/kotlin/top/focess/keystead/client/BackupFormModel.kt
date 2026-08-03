package top.focess.keystead.client

object BackupFormModel {
    fun canUseNewMasterPassphrase(
        newMasterPassphrase: String,
        confirmation: String,
    ): Boolean =
        newMasterPassphrase.isNotBlank() &&
            newMasterPassphrase == confirmation

    fun canExport(
        vaultOpen: Boolean,
        backupPassword: String,
        confirmation: String,
    ): Boolean =
        vaultOpen &&
            backupPassword.isNotBlank() &&
            backupPassword == confirmation

    fun canRestore(
        backupPassword: String,
        backupConfirmation: String,
        newMasterPassphrase: String,
        newMasterConfirmation: String,
    ): Boolean =
        backupPassword.isNotBlank() &&
            backupPassword == backupConfirmation &&
            canUseNewMasterPassphrase(newMasterPassphrase, newMasterConfirmation)

    internal fun canReviewRestore(
        selection: BackupRestoreSelection,
        backupPassword: String,
        backupConfirmation: String,
        newMasterPassphrase: String,
        newMasterConfirmation: String,
    ): Boolean =
        selection.canReview &&
            canRestore(
                backupPassword,
                backupConfirmation,
                newMasterPassphrase,
                newMasterConfirmation,
            )
}
