package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerVaultRestoreModelTest {
    @Test
    fun `server restore depends on account login but never local login or biometrics`() {
        assertEquals(
            ServerVaultRestoreStage.SIGN_IN_REQUIRED,
            model(authenticated = false).stage,
        )
        assertEquals(
            ServerVaultRestoreStage.ACCESS_REQUEST_REQUIRED,
            model(authenticated = true).stage,
        )
    }

    @Test
    fun `approved package requires a new path and local master passphrase`() {
        val passphraseMissing =
            model(
                requestState = ServerVaultAccessRequestState.APPROVED,
                approvedPackageAvailable = true,
                masterPassphraseReady = false,
            )
        assertEquals(ServerVaultRestoreStage.MASTER_PASSPHRASE_REQUIRED, passphraseMissing.stage)
        assertFalse(passphraseMissing.canRestore)

        val targetInUse =
            model(
                requestState = ServerVaultAccessRequestState.APPROVED,
                approvedPackageAvailable = true,
                targetPathAvailable = false,
            )
        assertEquals(ServerVaultRestoreStage.TARGET_IN_USE, targetInUse.stage)
    }

    @Test
    fun `request moves from approval wait to restorable package`() {
        val initial = model()
        val pending = model(requestState = ServerVaultAccessRequestState.PENDING)
        val approvedWithoutPackage = model(requestState = ServerVaultAccessRequestState.APPROVED)
        val ready =
            model(
                requestState = ServerVaultAccessRequestState.APPROVED,
                approvedPackageAvailable = true,
            )

        assertTrue(initial.canCreateRequest)
        assertEquals(ServerVaultRestoreStage.WAITING_FOR_APPROVAL, pending.stage)
        assertTrue(pending.canRefreshRequest)
        assertEquals(ServerVaultRestoreStage.WAITING_FOR_PACKAGE, approvedWithoutPackage.stage)
        assertTrue(approvedWithoutPackage.canRefreshRequest)
        assertEquals(ServerVaultRestoreStage.READY_TO_RESTORE, ready.stage)
        assertTrue(ready.canRestore)
    }

    @Test
    fun `expired approval request can be replaced`() {
        val value = model(requestState = ServerVaultAccessRequestState.EXPIRED)
        assertEquals(ServerVaultRestoreStage.REQUEST_EXPIRED, value.stage)
        assertTrue(value.canCreateRequest)
    }

    private fun model(
        authenticated: Boolean = true,
        serverAvailability: ServerAvailability = ServerAvailability.ONLINE,
        masterPassphraseReady: Boolean = true,
        requestState: ServerVaultAccessRequestState? = null,
        approvedPackageAvailable: Boolean = false,
        targetPathAvailable: Boolean = true,
    ) =
        ServerVaultRestoreModel.derive(
            authenticated = authenticated,
            serverAvailability = serverAvailability,
            masterPassphraseReady = masterPassphraseReady,
            requestState = requestState,
            approvedPackageAvailable = approvedPackageAvailable,
            targetPathAvailable = targetPathAvailable,
        )
}
