package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerRecoveryTaskPresentationTest {
    @Test
    fun restoreAndApprovalExposeDifferentRequirements() {
        val restore = ServerRecoveryTaskPresentation.forTask(ServerRecoveryTask.RESTORE_THIS_DEVICE)
        val approval = ServerRecoveryTaskPresentation.forTask(ServerRecoveryTask.APPROVE_ANOTHER_DEVICE)

        assertFalse(restore.requiresOpenVault)
        assertTrue(restore.createsNewLocalVault)
        assertTrue(approval.requiresOpenVault)
        assertFalse(approval.createsNewLocalVault)
        assertEquals(
            listOf(
                ServerRecoveryTask.RESTORE_THIS_DEVICE,
                ServerRecoveryTask.APPROVE_ANOTHER_DEVICE,
            ),
            ServerRecoveryTaskPresentation.tasks,
        )
    }
}
