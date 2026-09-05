package top.focess.keystead.client

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.model.SecretClassification
import top.focess.keystead.model.SecretType
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

class ReviewDataIntegrityRegressionTest {
    @Test
    fun corruptRemotePayloadFailsAuthentication() {
        val directory = Files.createTempDirectory("client-integrity-")
        try {
            LocalVaultSession.openOrCreate(
                directory.resolve("vault.kvault"),
                "master-password".toCharArray(),
            ).use { session ->
                session.addLogin("Title", "user", "secret", null)
                val record = session.currentPersonalRecords().single()
                val remote = PersonalVaultRecord(
                    serverSequence = 1,
                    eventId = SyncRecordEventId.of(record),
                    fingerprint = record.fingerprint(),
                    secretId = record.secretId(),
                    revision = record.revision(),
                    secretType = record.secretType(),
                    encryptedProfile = record.encryptedProfile(),
                    envelope = "corrupt",
                    deleted = record.deleted(),
                    contentKey = record.contentKey(),
                    createdAt = Instant.now(),
                )
                val inventory = PersonalVaultRecordInventory.compare(
                    listOf(record),
                    listOf(remote),
                    authenticate = session::authenticateSyncRecord,
                )
                assertEquals(RecordComparisonStatus.HASH_MISMATCH, inventory.comparisons!!.single().status)
                assertEquals(RemoteRecordVerification.INVALID, inventory.remoteHistory.single().verification)
                val damaged = EncryptedSyncRecord(
                    remote.fingerprint,
                    remote.secretId,
                    remote.revision,
                    remote.secretType,
                    remote.encryptedProfile,
                    remote.envelope,
                    remote.deleted,
                    remote.contentKey,
                )
                assertNull(session.previewSyncRecordFields(damaged))
                var serverHead = remote
                val pushed = SelectedRecordUploadCoordinator.upload(
                    secretIds = setOf(record.secretId()),
                    push = {
                        val current = session.currentPersonalRecords().single()
                        val eventId = SyncRecordEventId.of(current)
                        // The server deduplicates the original event even if its stored blob is corrupt.
                        if (eventId != serverHead.eventId) {
                            serverHead = serverHead.copy(
                                eventId = eventId,
                                revision = current.revision(),
                                encryptedProfile = current.encryptedProfile(),
                                envelope = current.envelope(),
                                contentKey = current.contentKey(),
                            )
                        }
                        1
                    },
                    refreshComparisons = {
                        PersonalVaultRecordInventory.compare(
                            session.currentPersonalRecords(), listOf(serverHead),
                            authenticate = session::authenticateSyncRecord,
                        ).comparisons.orEmpty()
                    },
                    promote = session::promoteLocalRecord,
                )
                assertEquals(2, pushed)
                assertEquals(
                    RemoteRecordVerification.VERIFIED,
                    PersonalVaultRecordInventory.compare(
                        session.currentPersonalRecords(), listOf(serverHead),
                        authenticate = session::authenticateSyncRecord,
                    ).remoteHistory.single().verification,
                )
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun noteAndStructuredEditsPreserveHiddenMetadata() {
        val directory = Files.createTempDirectory("client-metadata-")
        val file = directory.resolve("vault.kvault")
        try {
            val ids = DefaultVaultService().createVault(file, "master-password".toCharArray()).use { handle ->
                SecretBuffer.fromChars("private".toCharArray()).use { secret ->
                    val classification = SecretClassification("category", null, null, null, setOf("label"))
                    listOf(
                        handle.saveSecureNote {
                            it.title("Note").body(secret).tag("tag")
                                .attribute("custom", "keep").classification(classification)
                        }.value().toString(),
                        handle.saveSecret(SecretType.API_TOKEN) {
                            it.title("Token").field("token", secret).tag("tag")
                                .attribute("custom", "keep").classification(classification)
                        }.value().toString(),
                    )
                }
            }
            LocalVaultSession.openOrCreate(file, "master-password".toCharArray()).use { session ->
                ids.forEach { id ->
                    val before = session.editSnapshot(id)
                    session.updateStructuredSecret(id, "Edited", before.fields)
                    val after = session.listSecrets().first { it.id == id }
                    assertEquals(setOf("label"), after.labels)
                    assertEquals(setOf("tag"), after.tags)
                    assertEquals(mapOf("custom" to "keep"), after.attributes)
                    assertEquals(before.fields, session.editSnapshot(id).fields)
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun editingTitlePreservesExistingNotesAndTags() {
        val directory = Files.createTempDirectory("client-edit-")
        val file = directory.resolve("vault.kvault")
        try {
            val id = DefaultVaultService().createVault(file, "master-password".toCharArray()).use { handle ->
                SecretBuffer.fromChars("user".toCharArray()).use { username ->
                    SecretBuffer.fromChars("secret".toCharArray()).use { password ->
                        SecretBuffer.fromChars("important-note".toCharArray()).use { notes ->
                            handle.saveLogin {
                                it.title("Before").username(username).password(password)
                                    .notes(notes).tag("important").attribute("custom", "keep")
                            }.value().toString()
                        }
                    }
                }
            }
            LocalVaultSession.openOrCreate(file, "master-password".toCharArray()).use { session ->
                assertEquals("important-note", session.localRecordFields(id)["notes"])
                assertEquals(setOf("important"), session.listSecrets().single().tags)
                val before = session.editSnapshot(id)
                session.updateLogin(id, "After", before.username, before.password, before.url)
                assertEquals("important-note", session.localRecordFields(id)["notes"])
                assertEquals(setOf("important"), session.listSecrets().single().tags)
                assertEquals(mapOf("custom" to "keep"), session.listSecrets().single().attributes)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
