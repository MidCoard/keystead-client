package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

internal enum class RecordComparisonStatus {
    MATCHED,
    UNVERIFIED,
    LOCAL_ONLY,
    SERVER_ONLY,
    LOCAL_NEWER,
    SERVER_NEWER,
    LEGACY_UNVERIFIABLE,
    HASH_MISMATCH,
    CONFLICT,
}

internal enum class RemoteRecordVerification {
    VERIFIED,
    UNVERIFIED,
    LEGACY_UNVERIFIABLE,
    INVALID,
}

internal object SyncUploadConflictResolver {
    fun needsPromotion(entry: RecordComparisonEntry): Boolean =
        entry.status in setOf(RecordComparisonStatus.CONFLICT, RecordComparisonStatus.HASH_MISMATCH) &&
            entry.localRevision != null &&
            entry.localRevision == entry.serverRevision
}

internal object SelectedRecordUploadCoordinator {
    fun upload(
        secretIds: Set<String>,
        push: (Set<String>) -> Int,
        refreshComparisons: () -> List<RecordComparisonEntry>,
        promote: (String) -> Unit,
    ): Int {
        var pushed = push(secretIds)
        val promoteIds =
            refreshComparisons()
                .filter { it.secretId in secretIds && SyncUploadConflictResolver.needsPromotion(it) }
                .mapTo(linkedSetOf(), RecordComparisonEntry::secretId)
        if (promoteIds.isEmpty()) return pushed
        promoteIds.forEach(promote)
        pushed += push(promoteIds)
        refreshComparisons()
        return pushed
    }
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
    val secretId: String,
    val recordHash: String,
    val revision: Long,
    val secretType: String,
    val advertisedContentHash: String,
    val computedContentHash: String?,
    val profileCiphertextHash: String,
    val envelopeCiphertextHash: String,
    val verification: RemoteRecordVerification,
    val deleted: Boolean,
    val createdAt: Instant,
) {
    val hashValid: Boolean
        get() = verification == RemoteRecordVerification.VERIFIED
}

internal data class PersonalVaultRecordInventory(
    val serverFingerprint: String?,
    val localFingerprint: String?,
    val vaultMismatch: Boolean,
    val remoteHistory: List<RemoteRecordHistoryEntry>,
    val comparisons: List<RecordComparisonEntry>?,
    val invalidRemoteRecords: Int,
    val legacyRemoteRecords: Int,
) {
    companion object {
        fun compare(
            localRecords: List<EncryptedSyncRecord>?,
            remoteRecords: List<PersonalVaultRecord>,
            localFingerprint: String? = localRecords?.firstOrNull()?.fingerprint(),
            authenticate: ((EncryptedSyncRecord) -> Boolean)? = null,
            canonicalContentKey: ((EncryptedSyncRecord) -> String)? = null,
        ): PersonalVaultRecordInventory {
            val serverFingerprint = remoteRecords.firstOrNull()?.fingerprint
            val effectiveLocalFingerprint =
                localFingerprint ?: localRecords?.firstOrNull()?.fingerprint()
            val vaultMismatch =
                effectiveLocalFingerprint != null &&
                    serverFingerprint != null &&
                    effectiveLocalFingerprint != serverFingerprint
            val canonicalKeys = mutableMapOf<Long, String>()
            val history =
                remoteRecords
                    .sortedByDescending { it.serverSequence }
                    .map { remote ->
                        val computedContentHash = remote.contentHash()
                        val verifier: ((EncryptedSyncRecord) -> Boolean)? = when {
                            remote.fingerprint != effectiveLocalFingerprint -> null
                            canonicalContentKey != null -> { record ->
                                try {
                                    canonicalKeys[remote.serverSequence] = canonicalContentKey(record)
                                    true
                                } catch (_: top.focess.keystead.service.ValidationException) {
                                    false
                                } catch (_: top.focess.keystead.crypto.CryptoException) {
                                    false
                                }
                            }
                            else -> authenticate
                        }
                        RemoteRecordHistoryEntry(
                            serverSequence = remote.serverSequence,
                            secretId = remote.secretId,
                            recordHash = RecordDisplayHash.of(remote.secretId),
                            revision = remote.revision,
                            secretType = remote.secretType,
                            advertisedContentHash = remote.eventId,
                            computedContentHash = computedContentHash,
                            profileCiphertextHash = RecordDisplayHash.of(remote.encryptedProfile),
                            envelopeCiphertextHash = RecordDisplayHash.of(remote.envelope),
                            verification = remote.verification(computedContentHash, verifier),
                            deleted = remote.deleted,
                            createdAt = remote.createdAt,
                        )
                    }
            val comparisons =
                if (localRecords == null) {
                    null
                } else {
                    // Even on a vault-fingerprint mismatch the records are listed: local
                    // entries are marked local-only and server entries carry an other-vault
                    // badge in the UI, so orphaned records stay visible and removable.
                    compareCurrent(localRecords, remoteRecords, history.associate { it.serverSequence to it.verification }, canonicalKeys)
                }
            return PersonalVaultRecordInventory(
                serverFingerprint = serverFingerprint,
                localFingerprint = effectiveLocalFingerprint,
                vaultMismatch = vaultMismatch,
                remoteHistory = history,
                comparisons = comparisons,
                invalidRemoteRecords = history.count { it.verification == RemoteRecordVerification.INVALID },
                legacyRemoteRecords = history.count {
                    it.verification == RemoteRecordVerification.LEGACY_UNVERIFIABLE
                },
            )
        }

        private fun compareCurrent(
            localRecords: List<EncryptedSyncRecord>,
            remoteRecords: List<PersonalVaultRecord>,
            verification: Map<Long, RemoteRecordVerification>,
            canonicalKeys: Map<Long, String>,
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
                .map { secretId -> compareOne(secretId, localById[secretId], remoteById[secretId], verification, canonicalKeys) }
        }

        private fun compareOne(
            secretId: String,
            local: EncryptedSyncRecord?,
            remote: PersonalVaultRecord?,
            verification: Map<Long, RemoteRecordVerification>,
            canonicalKeys: Map<Long, String>,
        ): RecordComparisonEntry {
            val localHash = local?.let(SyncRecordEventId::of)
            val remoteHash = remote?.contentHash()
            val equivalentContent = remote != null && local != null &&
                local.fingerprint() == remote.fingerprint && local.secretType() == remote.secretType &&
                local.deleted() == remote.deleted &&
                canonicalKeys[remote.serverSequence]?.let { it == local.contentKey() } == true
            val status =
                when {
                    remote == null -> RecordComparisonStatus.LOCAL_ONLY
                    remote.contentKey.isBlank() -> RecordComparisonStatus.LEGACY_UNVERIFIABLE
                    verification[remote.serverSequence] == RemoteRecordVerification.INVALID -> RecordComparisonStatus.HASH_MISMATCH
                    local == null -> RecordComparisonStatus.SERVER_ONLY
                    local.revision() > remote.revision -> RecordComparisonStatus.LOCAL_NEWER
                    local.revision() < remote.revision -> RecordComparisonStatus.SERVER_NEWER
                    // Historical Windows profiles used CRLF. Authenticate their original
                    // event first, then compare canonical plaintext identity; never promote
                    // a record solely because an older writer serialized it differently.
                    localHash == remoteHash || equivalentContent -> if (verification[remote.serverSequence] == RemoteRecordVerification.VERIFIED)
                        RecordComparisonStatus.MATCHED else RecordComparisonStatus.UNVERIFIED
                    else -> if (verification[remote.serverSequence] == RemoteRecordVerification.VERIFIED)
                        RecordComparisonStatus.CONFLICT else RecordComparisonStatus.UNVERIFIED
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

private fun PersonalVaultRecord.verification(
    computedContentHash: String?,
    authenticate: ((EncryptedSyncRecord) -> Boolean)?,
): RemoteRecordVerification = when {
    contentKey.isBlank() -> RemoteRecordVerification.LEGACY_UNVERIFIABLE
    eventId != computedContentHash -> RemoteRecordVerification.INVALID
    authenticate == null -> RemoteRecordVerification.UNVERIFIED
    authenticate(EncryptedSyncRecord(fingerprint, secretId, revision, secretType,
        encryptedProfile, envelope, deleted, contentKey)) -> RemoteRecordVerification.VERIFIED
    else -> RemoteRecordVerification.INVALID
}

private fun PersonalVaultRecord.contentHash(): String? {
    // Legacy pre-KVE2 events carry an empty content key and can never verify.
    if (contentKey.isBlank()) return null
    return SyncRecordEventId.of(
        EncryptedSyncRecord(
            fingerprint,
            secretId,
            revision,
            secretType,
            encryptedProfile,
            envelope,
            deleted,
            contentKey,
        ),
    )
}
