package top.focess.keystead.client

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

class PersonalVaultRecordInventoryTest {
    @Test
    fun unavailableVerifierIsNotMisreportedAsCorruptCiphertext() {
        val row = encrypted("unavailable", 1, "payload")
        assertFailsWith<IllegalStateException> {
            PersonalVaultRecordInventory.compare(listOf(row), listOf(remote(1, row)),
                canonicalContentKey = { throw IllegalStateException("Vault closed") })
        }
    }

    @Test
    fun authenticatedLegacyRepresentationMatchesWithoutPromotingButInvalidEventDoesNot() {
        val local = encrypted("legacy-windows", 7, "payload", contentKey = "canonical-key")
        val legacy = remote(21, encrypted("legacy-windows", 7, "payload", contentKey = "legacy-crlf-key"))
        val matched = PersonalVaultRecordInventory.compare(
            listOf(local), listOf(legacy), canonicalContentKey = { "canonical-key" },
        ).comparisons!!.single()
        assertEquals(RecordComparisonStatus.MATCHED, matched.status)
        assertFalse(SyncUploadConflictResolver.needsPromotion(matched))
        // Canonical comparison must never bypass the advertised event-id check.
        val invalid = PersonalVaultRecordInventory.compare(
            listOf(local), listOf(legacy.copy(eventId = "tampered")),
            canonicalContentKey = { error("Must reject event ID before inspecting ciphertext") },
        )
        assertEquals(RecordComparisonStatus.HASH_MISMATCH, invalid.comparisons!!.single().status)
        assertEquals(1, invalid.invalidRemoteRecords)
    }

    @Test
    fun exposesCompleteHashesForTheRecordAndEachEncryptedComponent() {
        val record =
            EncryptedSyncRecord(
                "6000000000000001",
                "550e8400-e29b-41d4-a716-446655440000",
                7,
                "LOGIN_PASSWORD",
                "profile",
                "payload",
                false,
                "content-key",
            )

        val inventory = verifiedInventory(listOf(record), listOf(remote(11, record)))
        val comparison = inventory.comparisons.orEmpty().single()
        val history = inventory.remoteHistory.single()

        assertEquals("o6nh7ZcyyrKIaBJ74A8c6SGsrv3Vw7I6bp4Acr2cGjQ", comparison.recordHash)
        assertEquals("ac6hbddox4BkPm4eukfXjPBspNdI9c-J4MOaOB2fs6g", comparison.localContentHash)
        assertEquals("ac6hbddox4BkPm4eukfXjPBspNdI9c-J4MOaOB2fs6g", comparison.serverContentHash)
        assertEquals("ac6hbddox4BkPm4eukfXjPBspNdI9c-J4MOaOB2fs6g", comparison.serverAdvertisedContentHash)
        assertEquals("GQDqtsAoSD1xJlme5vUN4NJ5B7XGX6kFJFgLSw-YUrA", comparison.localProfileCiphertextHash)
        assertEquals("GQDqtsAoSD1xJlme5vUN4NJ5B7XGX6kFJFgLSw-YUrA", comparison.serverProfileCiphertextHash)
        assertEquals("I59Z7VXnN8dxR89VrQwbAwttfudIp0JpUvm4UtWpNeU", comparison.localEnvelopeCiphertextHash)
        assertEquals("I59Z7VXnN8dxR89VrQwbAwttfudIp0JpUvm4UtWpNeU", comparison.serverEnvelopeCiphertextHash)
        assertEquals("ac6hbddox4BkPm4eukfXjPBspNdI9c-J4MOaOB2fs6g", history.advertisedContentHash)
        assertEquals("ac6hbddox4BkPm4eukfXjPBspNdI9c-J4MOaOB2fs6g", history.computedContentHash)
        assertEquals("GQDqtsAoSD1xJlme5vUN4NJ5B7XGX6kFJFgLSw-YUrA", history.profileCiphertextHash)
        assertEquals("I59Z7VXnN8dxR89VrQwbAwttfudIp0JpUvm4UtWpNeU", history.envelopeCiphertextHash)
        assertEquals(RemoteRecordVerification.VERIFIED, history.verification)
    }

    @Test
    fun keepsLocalAndServerDeletionStatesSeparate() {
        val local = encrypted("deletion-conflict", 2, "payload")
        val remote = remote(12, encrypted("deletion-conflict", 2, "payload").withDeleted(true))

        val comparison =
            verifiedInventory(listOf(local), listOf(remote))
                .comparisons.orEmpty().single()

        assertFalse(comparison.localDeleted ?: true)
        assertTrue(comparison.serverDeleted ?: false)
        assertEquals(RecordComparisonStatus.CONFLICT, comparison.status)
    }

    @Test
    fun comparesCurrentLocalRecordsWithTheHighestRemoteRevisionPerSecret() {
        val local =
            listOf(
                encrypted("same", 2, "same-payload"),
                encrypted("local-only", 1, "local"),
                encrypted("local-newer", 3, "local-newer"),
                encrypted("server-newer", 1, "old-local"),
            )
        val remote =
            listOf(
                remote(1, encrypted("same", 1, "old-server")),
                remote(2, encrypted("same", 2, "same-payload")),
                remote(3, encrypted("server-only", 1, "server")),
                remote(4, encrypted("local-newer", 2, "old-server")),
                remote(5, encrypted("server-newer", 2, "new-server")),
            )

        val inventory = verifiedInventory(local, remote)

        assertFalse(inventory.vaultMismatch)
        assertEquals(5, inventory.comparisons?.size)
        assertEquals(RecordComparisonStatus.MATCHED, inventory.statusOf("same"))
        assertEquals(RecordComparisonStatus.LOCAL_ONLY, inventory.statusOf("local-only"))
        assertEquals(RecordComparisonStatus.SERVER_ONLY, inventory.statusOf("server-only"))
        assertEquals(RecordComparisonStatus.LOCAL_NEWER, inventory.statusOf("local-newer"))
        assertEquals(RecordComparisonStatus.SERVER_NEWER, inventory.statusOf("server-newer"))
    }

    @Test
    fun reExportedProfileKeepsStableEventIdAndMatches() {
        // Re-exporting an unchanged record re-encrypts the sync profile with a fresh random
        // nonce, but the KVE2 event id binds the stable content key instead of the profile
        // ciphertext, so the fresh export and the pushed copy share the same event id.
        val local = encrypted("reexport", 3, "stable-payload")
        val pushed =
            EncryptedSyncRecord(
                local.fingerprint(),
                local.secretId(),
                local.revision(),
                local.secretType(),
                "profile-under-a-fresh-nonce",
                local.envelope(),
                false,
                local.contentKey(),
            )

        val comparison =
            verifiedInventory(listOf(local), listOf(remote(9, pushed)))
                .comparisons.orEmpty().single()

        assertEquals(RecordComparisonStatus.MATCHED, comparison.status)
        assertEquals(comparison.localContentHash, comparison.serverAdvertisedContentHash)
    }

    @Test
    fun detectsEqualRevisionContentMismatchAndInvalidAdvertisedHash() {
        val local = listOf(encrypted("content-conflict", 4, "local", contentKey = "local-content"))
        val remoteRecord =
            remote(7, encrypted("content-conflict", 4, "remote", contentKey = "remote-content"))
        val invalidHistoryRecord = remote(8, encrypted("invalid-hash", 1, "payload")).copy(eventId = "bogus")

        val inventory = verifiedInventory(local, listOf(remoteRecord, invalidHistoryRecord))

        assertEquals(RecordComparisonStatus.CONFLICT, inventory.statusOf("content-conflict"))
        assertEquals(RecordComparisonStatus.HASH_MISMATCH, inventory.statusOf("invalid-hash"))
        assertEquals(1, inventory.invalidRemoteRecords)
        assertEquals(0, inventory.legacyRemoteRecords)
        assertFalse(inventory.remoteHistory.single { it.serverSequence == 8L }.hashValid)
        assertEquals(
            RemoteRecordVerification.INVALID,
            inventory.remoteHistory.single { it.serverSequence == 8L }.verification,
        )
    }

    @Test
    fun legacyHistoryDoesNotTurnAMatchedCurrentRecordIntoAHashMismatch() {
        val record = encrypted("upgraded", 3, "payload")
        val legacy =
            remote(1, record).copy(
                eventId = "legacy-kve1-event-id",
                contentKey = "",
            )
        val current = remote(2, record)

        val inventory = verifiedInventory(listOf(record), listOf(legacy, current))

        assertEquals(RecordComparisonStatus.MATCHED, inventory.statusOf("upgraded"))
        assertEquals(0, inventory.invalidRemoteRecords)
        assertEquals(1, inventory.legacyRemoteRecords)
        assertEquals(
            RemoteRecordVerification.LEGACY_UNVERIFIABLE,
            inventory.remoteHistory.single { it.serverSequence == 1L }.verification,
        )
        assertEquals(
            RemoteRecordVerification.VERIFIED,
            inventory.remoteHistory.single { it.serverSequence == 2L }.verification,
        )
    }

    @Test
    fun legacyLatestRecordIsUpgradeableWithoutBeingReportedAsCorrupt() {
        val record = encrypted("legacy-only", 2, "payload")
        val legacy =
            remote(1, record).copy(
                eventId = "legacy-kve1-event-id",
                contentKey = "",
            )

        val inventory = verifiedInventory(listOf(record), listOf(legacy))
        val comparison = inventory.comparisons.orEmpty().single()

        assertEquals(RecordComparisonStatus.LEGACY_UNVERIFIABLE, comparison.status)
        assertFalse(SyncUploadConflictResolver.needsPromotion(comparison))
        assertEquals(0, inventory.invalidRemoteRecords)
        assertEquals(1, inventory.legacyRemoteRecords)
    }

    @Test
    fun differentVaultFingerprintsStillListRecordsWithTheirSides() {
        val local = listOf(encrypted("local", 1, "payload", fingerprint = "local-vault"))
        val remote = listOf(remote(1, encrypted("server", 1, "payload", fingerprint = "server-vault")))

        val inventory = verifiedInventory(local, remote)

        assertTrue(inventory.vaultMismatch)
        assertEquals("local-vault", inventory.localFingerprint)
        assertEquals("server-vault", inventory.serverFingerprint)
        // Records stay listed (marked by side in the UI) instead of being hidden.
        assertEquals(2, inventory.comparisons?.size)
        assertEquals(RecordComparisonStatus.LOCAL_ONLY, inventory.statusOf("local"))
        assertEquals(RecordComparisonStatus.SERVER_ONLY, inventory.statusOf("server"))
        assertEquals(1, inventory.remoteHistory.size)
    }

    private fun PersonalVaultRecordInventory.statusOf(secretId: String): RecordComparisonStatus =
        comparisons.orEmpty().single { it.secretId == secretId }.status

    private fun encrypted(
        secretId: String,
        revision: Long,
        envelope: String,
        contentKey: String = "content-$secretId-$revision",
        fingerprint: String = "6000000000000001",
    ) =
        EncryptedSyncRecord(
            fingerprint,
            secretId,
            revision,
            "SECURE_NOTE",
            "profile-$secretId-$revision",
            envelope,
            false,
            contentKey,
        )

    private fun verifiedInventory(local: List<EncryptedSyncRecord>?, remote: List<PersonalVaultRecord>) =
        PersonalVaultRecordInventory.compare(local, remote, authenticate = { true })

    private fun remote(sequence: Long, record: EncryptedSyncRecord) =
        PersonalVaultRecord(
            serverSequence = sequence,
            eventId = SyncRecordEventId.of(record),
            fingerprint = record.fingerprint(),
            secretId = record.secretId(),
            revision = record.revision(),
            secretType = record.secretType(),
            encryptedProfile = record.encryptedProfile(),
            envelope = record.envelope(),
            deleted = record.deleted(),
            contentKey = record.contentKey(),
            createdAt = Instant.parse("2030-01-01T00:00:00Z").plusSeconds(sequence),
        )

    private fun EncryptedSyncRecord.withDeleted(deleted: Boolean) =
        EncryptedSyncRecord(
            fingerprint(),
            secretId(),
            revision(),
            secretType(),
            encryptedProfile(),
            if (deleted) "" else envelope(),
            deleted,
            contentKey(),
        )
}
