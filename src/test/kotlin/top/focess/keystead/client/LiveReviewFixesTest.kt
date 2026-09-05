package top.focess.keystead.client

import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import top.focess.keystead.model.SecretType

/** Exercises review fixes across the real account API and two independently encrypted files. */
@EnabledIfEnvironmentVariable(named = "KEYSTEAD_LIVE_TEST_URL", matches = ".+")
class LiveReviewFixesTest {
    @Test
    fun secureNotesRestoreFromZeroAndAcceptAnEqualRevisionRemoteEdit() {
        val serverUrl = requireNotNull(System.getenv("KEYSTEAD_LIVE_TEST_URL")).trimEnd('/')
        val directory = createTempDirectory("keystead-live-review-fixes")
        val username = "review${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val password = "Review-fixture-account-password!"
        val auth = KeysteadServerAuthClient(serverUrl)
        try {
            auth.registerUser(username, password.toCharArray())
            auth.login(username, password.toCharArray()).use { account ->
                val client = account.client()
                LocalVaultSession.openOrCreate(directory.resolve("source.kvault"), "source-password".toCharArray()).use { source ->
                    val id = source.addStructuredSecret(SecretType.SECURE_NOTE, "Note", mapOf("body" to "initial"))
                    val sharedState = SyncStateStore(directory.resolve("sync"))
                    EphemeralVaultAccessSession.create(serverUrl).use { exchange ->
                        val workflow = VaultAccessWorkflow(client)
                        val pending = workflow.request(exchange)
                        workflow.approve(pending, source)
                        source.pullPendingPersonalRecordsFrom(client, sharedState)
                        assertTrue(sharedState.lastPulledServerSequence(source.fingerprintValue()) > 0)
                        val restored = ServerVaultProvisioningService().restore(
                            directory.resolve("restored.kvault"), workflow.refresh(pending.requestId), exchange,
                            "restored-password".toCharArray(), client, sharedState,
                        )
                        restored.session.use { target ->
                            assertEquals(1, restored.pulledRecords)
                            assertEquals("initial", target.revealField(id, "body"))
                            source.updateStructuredSecret(id, "Note", mapOf("body" to "remote edit"))
                            target.updateStructuredSecret(id, "Note", mapOf("body" to "local edit"))
                            val remote = source.currentPersonalRecords().single()
                            val local = target.currentPersonalRecords().single()
                            assertEquals(local.revision(), remote.revision())
                            source.pushAllPersonalRecordsTo(client)
                            val serverRecord = client.listAllPersonalRecords().maxBy { it.serverSequence }
                            val chosen = top.focess.keystead.service.EncryptedSyncRecord(
                                serverRecord.fingerprint, serverRecord.secretId, serverRecord.revision,
                                serverRecord.secretType, serverRecord.encryptedProfile, serverRecord.envelope,
                                serverRecord.deleted, serverRecord.contentKey,
                            )
                            val report = target.importSelectedSyncRecords(listOf(chosen))
                            assertEquals(1, report.imported())
                            assertEquals("remote edit", target.revealField(id, "body"))
                            assertTrue(target.currentPersonalRecords().single().revision() > remote.revision())
                            target.pushAllPersonalRecordsTo(client)
                            source.pullPendingPersonalRecordsFrom(client, sharedState)
                            assertEquals("remote edit", source.revealField(id, "body"))
                        }
                    }
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
