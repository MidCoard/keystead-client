package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

internal enum class RecordComparisonStatus {
    MATCHED,
    LOCAL_ONLY,
    SERVER_ONLY,
    LOCAL_NEWER,
    SERVER_NEWER,
    HASH_MISMATCH,
}

internal data class RecordComparisonEntry(
    val secretId: String,
    val recordHash: String,
    val secretType: String,
    val localRevision: Long?,
    val serverRevision: Long?,
    val localContentHash: String?,
    val serverContentHash: String?,
    val serverAdvertisedContentHash: String?,
    val localProfileCiphertextHash: String?,
    val serverProfileCiphertextHash: String?,
    val localEnvelopeCiphertextHash: String?,
    val serverEnvelopeCiphertextHash: String?,
    val serverSequence: Long?,
    val localDeleted: Boolean?,
    val serverDeleted: Boolean?,
    val status: RecordComparisonStatus,
)

internal data class RemoteRecordHistoryEntry(
    val serverSequence: Long,
    val recordHash: String,
    val revision: Long,
    val secretType: String,
    val advertisedContentHash: String,
    val computedContentHash: String,
    val profileCiphertextHash: String,
    val envelopeCiphertextHash: String,
    val hashValid: Boolean,
    val deleted: Boolean,
    val createdAt: Instant,
)

internal data class PersonalVaultRecordInventory(
    val serverFingerprint: String?,
    val localFingerprint: String?,
    val vaultMismatch: Boolean,
    val remoteHistory: List<RemoteRecordHistoryEntry>,
    val comparisons: List<RecordComparisonEntry>?,
    val invalidRemoteRecords: Int,
) {
    companion object {
        fun compare(
            localRecords: List<EncryptedSyncRecord>?,
            remoteRecords: List<PersonalVaultRecord>,
            localFingerprint: String? = localRecords?.firstOrNull()?.fingerprint(),
        ): PersonalVaultRecordInventory {
            val serverFingerprint = remoteRecords.firstOrNull()?.fingerprint
            val effectiveLocalFingerprint =
                localFingerprint ?: localRecords?.firstOrNull()?.fingerprint()
            val vaultMismatch =
                effectiveLocalFingerprint != null &&
                    serverFingerprint != null &&
                    effectiveLocalFingerprint != serverFingerprint
            val history =
                remoteRecords
                    .sortedByDescending { it.serverSequence }
                    .map { remote ->
                        val computedContentHash = remote.contentHash()
                        RemoteRecordHistoryEntry(
                            serverSequence = remote.serverSequence,
                            recordHash = RecordDisplayHash.of(remote.secretId),
                            revision = remote.revision,
                            secretType = remote.secretType,
                            advertisedContentHash = remote.eventId,
                            computedContentHash = computedContentHash,
                            profileCiphertextHash = RecordDisplayHash.of(remote.encryptedProfile),
                            envelopeCiphertextHash = RecordDisplayHash.of(remote.envelope),
                            hashValid = remote.eventId == computedContentHash,
                            deleted = remote.deleted,
                            createdAt = remote.createdAt,
                        )
                    }
            val comparisons =
                if (localRecords == null || vaultMismatch) {
                    null
                } else {
                    compareCurrent(localRecords, remoteRecords)
                }
            return PersonalVaultRecordInventory(
                serverFingerprint = serverFingerprint,
                localFingerprint = effectiveLocalFingerprint,
                vaultMismatch = vaultMismatch,
                remoteHistory = history,
                comparisons = comparisons,
                invalidRemoteRecords = history.count { !it.hashValid },
            )
        }

        private fun compareCurrent(
            localRecords: List<EncryptedSyncRecord>,
            remoteRecords: List<PersonalVaultRecord>,
        ): List<RecordComparisonEntry> {
            val localById =
                localRecords
                    .groupBy { it.secretId() }
                    .mapValues { (_, values) -> values.maxBy { it.revision() } }
            val remoteById =
                remoteRecords
                    .groupBy { it.secretId }
                    .mapValues { (_, values) ->
                        values.maxWith(compareBy<PersonalVaultRecord> { it.revision }.thenBy { it.serverSequence })
                    }
            return (localById.keys + remoteById.keys)
                .sortedBy(RecordDisplayHash::of)
                .map { secretId -> compareOne(secretId, localById[secretId], remoteById[secretId]) }
        }

        private fun compareOne(
            secretId: String,
            local: EncryptedSyncRecord?,
            remote: PersonalVaultRecord?,
        ): RecordComparisonEntry {
            val localHash = local?.let(SyncRecordEventId::of)
            val remoteHash = remote?.contentHash()
            val remoteHashValid = remote == null || remote.eventId == remoteHash
            val status =
                when {
                    !remoteHashValid -> RecordComparisonStatus.HASH_MISMATCH
                    local == null -> RecordComparisonStatus.SERVER_ONLY
                    remote == null -> RecordComparisonStatus.LOCAL_ONLY
                    local.revision() > remote.revision -> RecordComparisonStatus.LOCAL_NEWER
                    local.revision() < remote.revision -> RecordComparisonStatus.SERVER_NEWER
                    localHash == remoteHash -> RecordComparisonStatus.MATCHED
                    else -> RecordComparisonStatus.HASH_MISMATCH
                }
            return RecordComparisonEntry(
                secretId = secretId,
                recordHash = RecordDisplayHash.of(secretId),
                secretType = local?.secretType() ?: remote?.secretType.orEmpty(),
                localRevision = local?.revision(),
                serverRevision = remote?.revision,
                localContentHash = localHash,
                serverContentHash = remoteHash,
                serverAdvertisedContentHash = remote?.eventId,
                localProfileCiphertextHash = local?.encryptedProfile()?.let(RecordDisplayHash::of),
                serverProfileCiphertextHash = remote?.encryptedProfile?.let(RecordDisplayHash::of),
                localEnvelopeCiphertextHash = local?.envelope()?.let(RecordDisplayHash::of),
                serverEnvelopeCiphertextHash = remote?.envelope?.let(RecordDisplayHash::of),
                serverSequence = remote?.serverSequence,
                localDeleted = local?.deleted(),
                serverDeleted = remote?.deleted,
                status = status,
            )
        }
    }
}

internal object RecordDisplayHash {
    fun of(value: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(StandardCharsets.UTF_8)),
        )
}

private fun PersonalVaultRecord.contentHash(): String =
    SyncRecordEventId.of(
        EncryptedSyncRecord(
            fingerprint,
            secretId,
            revision,
            secretType,
            encryptedProfile,
            envelope,
            deleted,
        ),
    )
