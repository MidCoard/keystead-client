package top.focess.keystead.client

import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import top.focess.keystead.model.SecretType
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

/** Runs the UI's selected upload/import paths through a real server and two independent vaults. */
@EnabledIfEnvironmentVariable(named = "KEYSTEAD_LIVE_TEST_URL", matches = ".+")
class LiveSyncConvergenceTest {
    @Test
    fun twoSameOsClientsConvergeAcrossEditsUnchangedSyncAndExplicitConflicts() {
        val serverUrl = requireNotNull(System.getenv("KEYSTEAD_LIVE_TEST_URL")).trimEnd('/')
        val directory = createTempDirectory("keystead-live-convergence")
        val username = "converge${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val password = "Convergence-fixture-account-password!"
        val auth = KeysteadServerAuthClient(serverUrl)
        try {
            auth.registerUser(username, password.toCharArray())
            auth.login(username, password.toCharArray()).use { firstAccount ->
                auth.login(username, password.toCharArray()).use { secondAccount ->
                    val firstClient = firstAccount.client()
                    val secondClient = secondAccount.client()
                    LocalVaultSession.openOrCreate(
                        directory.resolve("first.kvault"), "first-master".toCharArray(),
                    ).use { first ->
                        val login = first.addLogin("邮箱", "alice@example.test", "initial", "https://example.test")
                        val note = first.addStructuredSecret(SecretType.SECURE_NOTE, "Note", mapOf("note" to "initial note"))
                        val ssh = first.addStructuredSecret(SecretType.SSH_KEY, "SSH", sshFields("initial"))
                        val mfa = first.addStructuredSecret(SecretType.MFA_SECRET, "MFA", mapOf("seed" to "JBSWY3DPEHPK3PXP"))
                        val api = first.addStructuredSecret(SecretType.API_TOKEN, "API", mapOf("token" to "initial-token"))
                        EphemeralVaultAccessSession.create(serverUrl).use { exchange ->
                            val requester = VaultAccessWorkflow(secondClient)
                            val pending = requester.request(exchange)
                            VaultAccessWorkflow(firstClient).approve(pending, first)
                            ServerVaultProvisioningService().restore(
                                directory.resolve("second.kvault"), requester.refresh(pending.requestId), exchange,
                                "second-master".toCharArray(), secondClient,
                                SyncStateStore(directory.resolve("second-sync")),
                            ).session.use { second ->
                                assertEquals(first.fingerprintValue(), second.fingerprintValue())
                                assertEquals(5, second.listSecrets().size)
                                assertMatched(first, firstClient, "first client after restore")
                                assertMatched(second, secondClient, "second client after restore")

                                repeat(3) { round ->
                                    first.updateLogin(login, "邮箱", "alice@example.test", "first-$round", "https://example.test")
                                    first.updateStructuredSecret(ssh, "SSH", sshFields("first-$round"))
                                    uploadSelected(first, firstClient, allIds(first))
                                    pullSelected(second, secondClient)
                                    assertEquals("first-$round", second.revealPassword(login))
                                    assertMatched(first, firstClient, "first upload round $round")
                                    assertMatched(second, secondClient, "second pull round $round")

                                    second.updateStructuredSecret(note, "Note", mapOf("note" to "第二端-$round\r\n内容"))
                                    second.updateStructuredSecret(mfa, "MFA", mapOf(
                                        "seed" to "JBSWY3DPEHPK3PXP", "otpauthUri" to "otpauth://totp/round-$round?secret=JBSWY3DPEHPK3PXP",
                                    ))
                                    second.updateStructuredSecret(api, "API", mapOf("token" to "second-$round"))
                                    uploadSelected(second, secondClient, allIds(second))
                                    pullSelected(first, firstClient)
                                    assertEquals("第二端-$round\r\n内容", first.revealField(note, "note"))
                                    assertMatched(first, firstClient, "first pull round $round")
                                    assertMatched(second, secondClient, "second upload round $round")
                                    assertEquals(identities(first), identities(second))
                                }

                                val stable = identities(first)
                                val eventCount = firstClient.listAllPersonalRecords().size
                                repeat(3) {
                                    assertEquals(0, first.importSelectedSyncRecords(latest(firstClient).map(::encrypted)).imported())
                                    assertEquals(0, second.importSelectedSyncRecords(latest(secondClient).map(::encrypted)).imported())
                                    uploadSelected(first, firstClient, allIds(first))
                                    uploadSelected(second, secondClient, allIds(second))
                                    assertEquals(stable, identities(first), "Unchanged first client must not promote revisions")
                                    assertEquals(stable, identities(second), "Unchanged second client must not promote revisions")
                                    assertEquals(eventCount, firstClient.listAllPersonalRecords().size, "Unchanged uploads must deduplicate")
                                    assertMatched(first, firstClient, "unchanged first client")
                                    assertMatched(second, secondClient, "unchanged second client")
                                }

                                // A real concurrent edit requires an explicit remote choice and one fresh revision.
                                first.updateStructuredSecret(note, "Note", mapOf("note" to "chosen server content"))
                                second.updateStructuredSecret(note, "Note", mapOf("note" to "different local content"))
                                val conflictingRevision = first.currentPersonalRecords().first { it.secretId() == note }.revision()
                                assertEquals(conflictingRevision, second.currentPersonalRecords().first { it.secretId() == note }.revision())
                                uploadSelected(first, firstClient, setOf(note))
                                assertEquals(RecordComparisonStatus.CONFLICT, comparisons(second, secondClient).first { it.secretId == note }.status)
                                pullSelected(second, secondClient)
                                assertEquals("chosen server content", second.revealField(note, "note"))
                                assertTrue(second.currentPersonalRecords().first { it.secretId() == note }.revision() > conflictingRevision)
                                uploadSelected(second, secondClient, setOf(note))
                                pullSelected(first, firstClient)
                                assertEquals(identities(first), identities(second))
                                assertMatched(first, firstClient, "first client after explicit remote choice")
                                assertMatched(second, secondClient, "second client after explicit remote choice")
                            }
                        }
                    }
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private fun sshFields(value: String) = linkedMapOf(
        "publicKey" to "ssh-ed25519 $value", "privateKey" to "$value\r\nprivate-line", "passphrase" to "key-passphrase",
    )

    private fun allIds(session: LocalVaultSession) = session.currentPersonalRecords().mapTo(linkedSetOf()) { it.secretId() }

    private fun uploadSelected(session: LocalVaultSession, client: KeysteadServerClient, ids: Set<String>) {
        SelectedRecordUploadCoordinator.upload(
            secretIds = ids,
            push = { session.pushSelectedPersonalRecordsTo(client, it) },
            refreshComparisons = { comparisons(session, client) },
            promote = session::promoteLocalRecord,
        )
    }

    private fun pullSelected(session: LocalVaultSession, client: KeysteadServerClient) {
        val eligible = comparisons(session, client).filter {
            it.status in setOf(RecordComparisonStatus.SERVER_ONLY, RecordComparisonStatus.SERVER_NEWER, RecordComparisonStatus.CONFLICT)
        }.mapTo(hashSetOf()) { it.secretId }
        val chosen = latest(client).filter { it.secretId in eligible }.map(::encrypted)
        chosen.forEach { assertNotNull(session.previewSyncRecordFields(it), "UI preview must authenticate before accepting") }
        val report = session.importSelectedSyncRecords(chosen)
        assertEquals(chosen.size, report.imported())
        assertTrue(report.rejected().isEmpty())
        assertTrue(report.conflicts().isEmpty())
    }

    private fun comparisons(session: LocalVaultSession, client: KeysteadServerClient) =
        PersonalVaultRecordInventory.compare(
            session.currentPersonalRecords(), client.listAllPersonalRecords(),
            canonicalContentKey = session::canonicalSyncContentKey,
        ).comparisons.orEmpty()

    private fun assertMatched(session: LocalVaultSession, client: KeysteadServerClient, stage: String) {
        val entries = comparisons(session, client)
        assertEquals(5, entries.size)
        assertTrue(entries.all { it.status == RecordComparisonStatus.MATCHED }, "$stage: ${entries.map { it.status }}")
    }

    private fun identities(session: LocalVaultSession) = session.currentPersonalRecords().associate {
        it.secretId() to Triple(it.revision(), it.contentKey(), SyncRecordEventId.of(it))
    }

    private fun latest(client: KeysteadServerClient) = client.listAllPersonalRecords().groupBy { it.secretId }.values.map {
        it.maxWith(compareBy<PersonalVaultRecord> { record -> record.revision }.thenBy { record -> record.serverSequence })
    }

    private fun encrypted(record: PersonalVaultRecord) = EncryptedSyncRecord(
        record.fingerprint, record.secretId, record.revision, record.secretType,
        record.encryptedProfile, record.envelope, record.deleted, record.contentKey,
    )
}
