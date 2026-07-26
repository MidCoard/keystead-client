package top.focess.keystead.client

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncImportReport

/**
 * Writes and reads encrypted, versioned vault backup archives by streaming the vault's encrypted
 * sync records through the open [LocalVaultSession].
 *
 * Archives carry encrypted rows and tombstones bound to a single vault fingerprint and vault key id;
 * the master password and vault key are never needed to produce or consume an archive, and no
 * plaintext ever leaves the vault session. Restore is only valid into the same vault (same fingerprint
 * and vault key id), since rows stay encrypted under the vault's data-encryption key.
 */
internal object VaultBackup {

    private const val FORMAT_VERSION = 1
    private val gson = Gson()

    private data class BackupRecord(
        val secretId: String,
        val revision: Long,
        val secretType: String,
        val encryptedProfile: String,
        val envelope: String,
        val deleted: Boolean,
    )

    private data class BackupArchive(
        val formatVersion: Int,
        val fingerprint: String,
        val vaultKeyId: String,
        val records: List<BackupRecord>,
    )

    /** Exports every record and tombstone in the session's vault to [output] as an encrypted archive. */
    fun export(session: LocalVaultSession, output: OutputStream) {
        val fingerprint = session.fingerprintValue()
        val vaultKeyId = session.vaultKeyIdValue()
        val records =
            session.exportAllRecords().map { record ->
                BackupRecord(
                    secretId = record.secretId(),
                    revision = record.revision(),
                    secretType = record.secretType(),
                    encryptedProfile = record.encryptedProfile(),
                    envelope = record.envelope(),
                    deleted = record.deleted(),
                )
            }
        val archive = BackupArchive(FORMAT_VERSION, fingerprint, vaultKeyId, records)
        output.write(gson.toJson(archive).toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Restores an archive into the session's vault, merging rows and skipping any that are already at
     * least as new locally. The archive must belong to the session's vault (same fingerprint and vault
     * key id); a mismatch is rejected before the vault is touched.
     */
    fun restore(session: LocalVaultSession, input: InputStream): SyncImportReport {
        val text = input.readBytes().toString(StandardCharsets.UTF_8)
        val archive =
            try {
                gson.fromJson(JsonParser.parseString(text), BackupArchive::class.java)
            } catch (error: RuntimeException) {
                throw IllegalStateException("Backup archive is malformed", error)
            }
        requireNotNull(archive) { "Backup archive is empty" }
        require(archive.formatVersion == FORMAT_VERSION) {
            "Unsupported backup format version ${archive.formatVersion}"
        }
        val fingerprint = session.fingerprintValue()
        val vaultKeyId = session.vaultKeyIdValue()
        require(archive.fingerprint.equals(fingerprint, ignoreCase = true)) {
            "Backup is for vault ${archive.fingerprint}, not $fingerprint"
        }
        require(archive.vaultKeyId == vaultKeyId) {
            "Backup was taken under vault key ${archive.vaultKeyId}, not $vaultKeyId"
        }
        val records =
            archive.records.map { record ->
                EncryptedSyncRecord(
                    fingerprint,
                    record.secretId,
                    record.revision,
                    record.secretType,
                    record.encryptedProfile,
                    record.envelope,
                    record.deleted,
                )
            }
        return session.importBackupRecords(records)
    }
}
