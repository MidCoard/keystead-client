package top.focess.keystead.client

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

class SyncConflictResolutionTest {
    @Test
    fun explicitEqualRevisionChoiceIsAuthenticatedAndGetsFreshRevision() {
        val directory = Files.createTempDirectory("keystead-conflict")
        val left = directory.resolve("left.kvault")
        val right = directory.resolve("right.kvault")
        try {
            val id = LocalVaultSession.openOrCreate(left, "master-password".toCharArray()).use {
                it.addLogin("Title", "alice", "initial", null)
            }
            Files.copy(left, right)
            LocalVaultSession.openOrCreate(left, "master-password".toCharArray()).use { local ->
                LocalVaultSession.openOrCreate(right, "master-password".toCharArray()).use { remote ->
                    local.updateLogin(id, "Title", "alice", "local choice", null)
                    remote.updateLogin(id, "Title", "alice", "server choice", null)
                    val chosen = remote.currentPersonalRecords().single()
                    val inventory = PersonalVaultRecordInventory.compare(
                        local.currentPersonalRecords(),
                        listOf(remote(chosen)),
                        authenticate = local::authenticateSyncRecord,
                    )
                    assertEquals(RecordComparisonStatus.HASH_MISMATCH, inventory.comparisons!!.single().status)
                    assertEquals(RemoteRecordVerification.VERIFIED, inventory.remoteHistory.single().verification)
                    assertEquals(1, local.importSelectedSyncRecords(listOf(chosen)).imported())
                    assertEquals("server choice", local.revealPassword(id))
                    assertTrue(local.currentPersonalRecords().single().revision() > chosen.revision())
                    val resolved = local.currentPersonalRecords().single()
                    assertTrue(remote.authenticateSyncRecord(resolved))
                    assertEquals(1, remote.importSelectedSyncRecords(listOf(resolved)).imported())
                    assertEquals(resolved.revision(), remote.currentPersonalRecords().single().revision())
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun matchingStructureWithoutDecryptionIsNeverCalledVerified() {
        val record = EncryptedSyncRecord(
            "6000000000000001", "550e8400-e29b-41d4-a716-446655440000", 1,
            "LOGIN_PASSWORD", "profile", "payload", false, "content",
        )
        val inventory = PersonalVaultRecordInventory.compare(listOf(record), listOf(remote(record)))
        assertEquals(RemoteRecordVerification.UNVERIFIED, inventory.remoteHistory.single().verification)
        assertEquals(RecordComparisonStatus.UNVERIFIED, inventory.comparisons!!.single().status)
    }

    private fun remote(record: EncryptedSyncRecord) = PersonalVaultRecord(
        1, SyncRecordEventId.of(record), record.fingerprint(), record.secretId(), record.revision(), record.secretType(),
        record.encryptedProfile(), record.envelope(), record.deleted(), record.contentKey(), Instant.now(),
    )
}
