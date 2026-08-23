package top.focess.keystead.client

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class LocalVaultSyncPromotionTest {
    private lateinit var directory: Path

    @AfterTest
    fun clean() {
        if (::directory.isInitialized) directory.toFile().deleteRecursively()
    }

    @Test
    fun promotingALoginPreservesItsPlaintextAndAdvancesTheRevision() {
        val session = open()
        session.use {
            val id =
                it.addLogin(
                    title = "GitHub",
                    username = "alice@example.com",
                    password = "secret-password",
                    url = "https://github.com",
                    category = "development",
                    provider = "github",
                    software = "github.com",
                    account = "personal",
                    expiry = "2030-01-01",
                )
            val before = it.editSnapshot(id)
            val beforeRecord = it.currentPersonalRecords().single { record -> record.secretId() == id }

            val promotedRevision = it.promoteLocalRecord(id)

            val after = it.editSnapshot(id)
            val afterRecord = it.currentPersonalRecords().single { record -> record.secretId() == id }
            assertEquals(before, after)
            assertEquals(beforeRecord.revision() + 1, promotedRevision)
            assertEquals(promotedRevision, afterRecord.revision())
            assertTrue(beforeRecord.contentKey() != afterRecord.contentKey())
        }
    }

    @Test
    fun promotingAStructuredSecretPreservesAllFields() {
        val session = open()
        session.use {
            val id =
                it.addStructuredSecret(
                    type = SecretType.SSH_KEY,
                    title = "Deploy key",
                    fields = mapOf("publicKey" to "ssh-ed25519 AAA", "privateKey" to "PRIVATE", "passphrase" to "phrase"),
                    category = "development",
                    provider = "ssh",
                    software = "openssh",
                )
            val before = it.editSnapshot(id)

            it.promoteLocalRecord(id)

            assertEquals(before, it.editSnapshot(id))
        }
    }

    @Test
    fun onlyAnEqualRevisionContentMismatchNeedsPromotionAfterAReupload() {
        val conflict = comparison(RecordComparisonStatus.HASH_MISMATCH, 4, 4)
        val invalidOrDifferentRevision = comparison(RecordComparisonStatus.HASH_MISMATCH, 5, 4)
        val matched = comparison(RecordComparisonStatus.MATCHED, 4, 4)

        assertTrue(SyncUploadConflictResolver.needsPromotion(conflict))
        assertEquals(false, SyncUploadConflictResolver.needsPromotion(invalidOrDifferentRevision))
        assertEquals(false, SyncUploadConflictResolver.needsPromotion(matched))
    }

    @Test
    fun selectedUploadPromotesAndReuploadsAnEqualRevisionConflict() {
        val sequence = mutableListOf<String>()
        var refreshCount = 0
        val conflict = comparison(RecordComparisonStatus.HASH_MISMATCH, 4, 4)

        val pushed =
            SelectedRecordUploadCoordinator.upload(
                secretIds = setOf("secret"),
                push = {
                    sequence += "push:${it.sorted().joinToString()}"
                    it.size
                },
                refreshComparisons = {
                    refreshCount += 1
                    sequence += "refresh"
                    if (refreshCount == 1) listOf(conflict) else listOf(comparison(RecordComparisonStatus.MATCHED, 5, 5))
                },
                promote = { sequence += "promote:$it" },
            )

        assertEquals(2, pushed)
        assertEquals(
            listOf("push:secret", "refresh", "promote:secret", "push:secret", "refresh"),
            sequence,
        )
    }

    private fun open(): LocalVaultSession {
        directory = createTempDirectory("keystead-sync-promotion")
        return LocalVaultSession.openOrCreate(directory.resolve("vault.kvault"), "master-password".toCharArray())
    }

    private fun comparison(status: RecordComparisonStatus, localRevision: Long, serverRevision: Long) =
        RecordComparisonEntry(
            secretId = "secret",
            recordHash = "record",
            secretType = SecretType.SECURE_NOTE.name,
            localRevision = localRevision,
            serverRevision = serverRevision,
            localContentHash = "local",
            serverContentHash = "server",
            serverAdvertisedContentHash = "server",
            localProfileCiphertextHash = null,
            serverProfileCiphertextHash = null,
            localEnvelopeCiphertextHash = null,
            serverEnvelopeCiphertextHash = null,
            serverSequence = 1,
            localDeleted = false,
            serverDeleted = false,
            status = status,
        )
}
