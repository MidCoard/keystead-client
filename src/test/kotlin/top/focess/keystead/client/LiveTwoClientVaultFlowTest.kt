package top.focess.keystead.client

import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opt-in end-to-end check against a real Keystead Server.
 *
 * Run with KEYSTEAD_LIVE_TEST_URL set. The test represents two independent client sessions:
 * client A owns an open local vault; client B owns only a server session and a memory-only
 * exchange key. A approves B, B reconstructs a different local file, and then decrypts the
 * synchronized record.
 */
class LiveTwoClientVaultFlowTest {
    @Test
    fun selectiveUploadAndServerRemovalLeaveUnselectedAndLocalRecordsUntouched() {
        val serverUrl = System.getenv("KEYSTEAD_LIVE_TEST_URL")?.trimEnd('/') ?: return
        val directory = createTempDirectory("keystead-live-selective-sync")
        val username = "select${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val accountPassword = "Live-test-account-password!"
        val auth = KeysteadServerAuthClient(serverUrl)
        auth.registerUser(username, accountPassword.toCharArray())

        auth.login(username, accountPassword.toCharArray()).use { account ->
            LocalVaultSession.openOrCreate(
                directory.resolve("selective.kvault"),
                "selective-master-password".toCharArray(),
            ).use { vault ->
                val selected =
                    vault.addLogin(
                        title = "Selected",
                        username = "selected@example.test",
                        password = "selected-password",
                        url = "https://selected.test",
                    )
                val unselected =
                    vault.addLogin(
                        title = "Unselected",
                        username = "unselected@example.test",
                        password = "unselected-password",
                        url = "https://unselected.test",
                    )

                assertEquals(
                    1,
                    vault.pushSelectedPersonalRecordsTo(account.client(), setOf(selected)),
                )
                assertEquals(
                    listOf(selected),
                    account.client().listAllPersonalRecords().map { it.secretId },
                )

                val removed = account.client().deletePersonalRecordHistory(selected)

                assertEquals(1, removed.deletedEvents)
                assertTrue(account.client().listAllPersonalRecords().isEmpty())
                assertEquals(setOf(selected, unselected), vault.listSecrets().map { it.id }.toSet())
            }
        }
    }

    @Test
    fun sameAccountApprovalTransfersTheDekAndReconstructsASecondLocalVault() {
        val serverUrl = System.getenv("KEYSTEAD_LIVE_TEST_URL")?.trimEnd('/') ?: return
        val directory = createTempDirectory("keystead-live-two-client")
        val username = "live${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val accountPassword = "Live-test-account-password!"
        val auth = KeysteadServerAuthClient(serverUrl)
        auth.registerUser(username, accountPassword.toCharArray())

        auth.login(username, accountPassword.toCharArray()).use { clientAAccount ->
            auth.login(username, accountPassword.toCharArray()).use { clientBAccount ->
                LocalVaultSession.openOrCreate(
                    directory.resolve("client-a.kvault"),
                    "client-a-master-password".toCharArray(),
                ).use { clientAVault ->
                    val secretId =
                        clientAVault.addLogin(
                            title = "Live flow login",
                            username = "alice@example.test",
                            password = "record-password",
                            url = "https://example.test",
                        )

                    EphemeralVaultAccessSession.create(serverUrl).use { clientBExchange ->
                        val clientBRequest =
                            VaultAccessWorkflow(clientBAccount.client()).request(clientBExchange)
                        val requestSeenByA =
                            VaultAccessWorkflow(clientAAccount.client())
                                .pending()
                                .single { it.requestId == clientBRequest.requestId }
                        assertEquals(clientBRequest.fingerprint, requestSeenByA.fingerprint)

                        VaultAccessWorkflow(clientAAccount.client())
                            .approve(requestSeenByA, clientAVault)
                        val approved =
                            VaultAccessWorkflow(clientBAccount.client())
                                .refresh(clientBRequest.requestId)
                        assertNotNull(approved.approvedPackage)

                        val restored =
                            ServerVaultProvisioningService().restore(
                                file = directory.resolve("client-b.kvault"),
                                request = approved,
                                exchangeSession = clientBExchange,
                                newMasterPassphrase =
                                    "client-b-master-password".toCharArray(),
                                client = clientBAccount.client(),
                                stateStore = SyncStateStore(directory.resolve("client-b-sync")),
                            )
                        restored.session.use { clientBVault ->
                            assertEquals(clientAVault.fingerprintValue(), clientBVault.fingerprintValue())
                            assertEquals(1, restored.pulledRecords)
                            assertEquals(0, restored.rejectedRecords)
                            assertTrue(clientBVault.listSecrets().any { it.id == secretId })
                            val restoredSecret = clientBVault.editSnapshot(secretId)
                            assertEquals("alice@example.test", restoredSecret.username)
                            assertEquals("record-password", restoredSecret.password)
                        }
                    }
                }
            }
        }
    }
}
