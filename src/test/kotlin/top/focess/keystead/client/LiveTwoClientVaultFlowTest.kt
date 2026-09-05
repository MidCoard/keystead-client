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
    fun reuploadingAnOlderEqualRevisionPromotesTheChosenLocalVersion() {
        val serverUrl = System.getenv("KEYSTEAD_LIVE_TEST_URL")?.trimEnd('/') ?: return
        val directory = createTempDirectory("keystead-live-equal-revision")
        val username = "resolve${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val accountPassword = "Live-test-account-password!"
        val auth = KeysteadServerAuthClient(serverUrl)
        auth.registerUser(username, accountPassword.toCharArray())

        auth.login(username, accountPassword.toCharArray()).use { clientAAccount ->
            auth.login(username, accountPassword.toCharArray()).use { clientBAccount ->
                LocalVaultSession.openOrCreate(
                    directory.resolve("client-a.kvault"),
                    "client-a-master-password".toCharArray(),
                ).use { clientA ->
                    val secretId =
                        clientA.addLogin(
                            title = "Conflicting login",
                            username = "alice@example.test",
                            password = "base-password",
                            url = "https://example.test",
                        )
                    clientA.pushSelectedPersonalRecordsTo(clientAAccount.client(), setOf(secretId))

                    EphemeralVaultAccessSession.create(serverUrl).use { exchange ->
                        val request = VaultAccessWorkflow(clientBAccount.client()).request(exchange)
                        val pending =
                            VaultAccessWorkflow(clientAAccount.client()).pending()
                                .single { it.requestId == request.requestId }
                        VaultAccessWorkflow(clientAAccount.client()).approve(pending, clientA)
                        val approved =
                            VaultAccessWorkflow(clientBAccount.client()).refresh(request.requestId)
                        val restored =
                            ServerVaultProvisioningService().restore(
                                file = directory.resolve("client-b.kvault"),
                                request = approved,
                                exchangeSession = exchange,
                                newMasterPassphrase = "client-b-master-password".toCharArray(),
                                client = clientBAccount.client(),
                                stateStore = SyncStateStore(directory.resolve("client-b-sync")),
                            )
                        restored.session.use { clientB ->
                            clientA.updateLogin(
                                secretId,
                                "Conflicting login",
                                "alice@example.test",
                                "windows-choice",
                                "https://example.test",
                            )
                            clientB.updateLogin(
                                secretId,
                                "Conflicting login",
                                "alice@example.test",
                                "macos-choice",
                                "https://example.test",
                            )
                            clientA.pushSelectedPersonalRecordsTo(clientAAccount.client(), setOf(secretId))
                            clientB.pushSelectedPersonalRecordsTo(clientBAccount.client(), setOf(secretId))

                            val before =
                                PersonalVaultRecordInventory.compare(
                                    clientA.currentPersonalRecords(),
                                    clientAAccount.client().listAllPersonalRecords(),
                                    canonicalContentKey = clientA::canonicalSyncContentKey,
                                ).comparisons.orEmpty().single { it.secretId == secretId }
                            assertEquals(RecordComparisonStatus.CONFLICT, before.status)

                            assertEquals(
                                2,
                                SelectedRecordUploadCoordinator.upload(
                                    secretIds = setOf(secretId),
                                    push = {
                                        clientA.pushSelectedPersonalRecordsTo(
                                            clientAAccount.client(),
                                            it,
                                        )
                                    },
                                    refreshComparisons = {
                                        PersonalVaultRecordInventory.compare(
                                            clientA.currentPersonalRecords(),
                                            clientAAccount.client().listAllPersonalRecords(),
                                    canonicalContentKey = clientA::canonicalSyncContentKey,
                                        ).comparisons.orEmpty()
                                    },
                                    promote = clientA::promoteLocalRecord,
                                ),
                            )
                            val resolved =
                                PersonalVaultRecordInventory.compare(
                                    clientA.currentPersonalRecords(),
                                    clientAAccount.client().listAllPersonalRecords(),
                                    canonicalContentKey = clientA::canonicalSyncContentKey,
                                ).comparisons.orEmpty().single { it.secretId == secretId }
                            assertEquals(RecordComparisonStatus.MATCHED, resolved.status)
                            assertEquals("windows-choice", clientA.editSnapshot(secretId).password)
                        }
                    }
                }
            }
        }
    }

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
