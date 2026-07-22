package top.focess.keystead.client

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import java.util.UUID
import top.focess.keystead.model.VaultId
import top.focess.keystead.service.BackupImportReport
import top.focess.keystead.service.VaultBackupService
import top.focess.keystead.store.FileVaultStore

/**
 * Writes and reads encrypted, versioned vault backup archives by delegating to the core
 * [VaultBackupService]. Archives carry encrypted rows and the wrapped vault key, so the master
 * password is never needed to produce a backup and no plaintext ever leaves the vault session.
 */
internal object VaultBackup {

    /** Exports every record and tombstone in the vault to [output] as an encrypted archive. */
    fun export(directory: Path, vaultId: String, output: OutputStream) {
        require(vaultId.isNotBlank()) { "Vault id is required" }
        val store = FileVaultStore(directory)
        val backup = VaultBackupService()
        val archive = backup.export(store, VaultId(UUID.fromString(vaultId)))
        backup.writeTo(archive, output)
    }

    /**
     * Restores an archive into the vault's store, merging rows and skipping any that are already at
     * least as new locally. The archive must belong to [vaultId]; a mismatch is rejected before the
     * store is touched.
     */
    fun restore(directory: Path, vaultId: String, input: InputStream): BackupImportReport {
        require(vaultId.isNotBlank()) { "Vault id is required" }
        val backup = VaultBackupService()
        val read = backup.readFrom(input)
        val archiveVaultId = read.archive().manifest().vaultId().value().toString()
        if (!archiveVaultId.equals(vaultId, ignoreCase = true)) {
            throw IllegalStateException("Backup is for vault $archiveVaultId, not $vaultId")
        }
        val store = FileVaultStore(directory)
        return backup.restore(store, read)
    }
}
