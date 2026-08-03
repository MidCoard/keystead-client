package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.focess.keystead.client.i18n.EnStrings

class ConfirmationGateTest {

    @Test
    fun requestThenConfirmReturnsAndClearsTarget() {
        val gate = ConfirmationGate<String>()
        gate.request("delete-secret-1")
        assertTrue(gate.isPending)
        assertEquals("delete-secret-1", gate.pending)
        assertEquals("delete-secret-1", gate.confirm())
        assertFalse(gate.isPending)
        assertNull(gate.pending)
    }

    @Test
    fun cancelClearsPendingTarget() {
        val gate = ConfirmationGate<String>()
        gate.request("delete-secret-1")
        gate.cancel()
        assertFalse(gate.isPending)
        assertNull(gate.pending)
    }

    @Test
    fun confirmWithNothingPendingReturnsNull() {
        val gate = ConfirmationGate<String>()
        assertNull(gate.confirm())
        assertFalse(gate.isPending)
    }

    @Test
    fun newRequestReplacesPendingTarget() {
        val gate = ConfirmationGate<String>()
        gate.request("first")
        gate.request("second")
        assertEquals("second", gate.pending)
        assertEquals("second", gate.confirm())
    }

    @Test
    fun destructiveConfirmationCopyIncludesPayload() {
        val delete = DestructiveConfirmation.DeleteSecret("id-1", "GitHub token")
        assertEquals("Delete secret", delete.title(EnStrings))
        assertTrue(delete.message(EnStrings).contains("GitHub token"))
        assertTrue(delete.message(EnStrings).contains("cannot be undone"))
        val removeDeviceLogin = DestructiveConfirmation.RemoveDeviceLogin
        assertEquals("Remove local login?", removeDeviceLogin.title(EnStrings))
        assertTrue(removeDeviceLogin.message(EnStrings).contains("master password"))
        assertTrue(removeDeviceLogin.message(EnStrings).contains("local-login slots"))
        val deleteVault =
            DestructiveConfirmation.DeleteVaultFile(
                "C:\\Users\\Alice\\Vaults\\personal.kvault",
            )
        assertEquals("Delete vault file?", deleteVault.title(EnStrings))
        assertTrue(deleteVault.message(EnStrings).contains("personal.kvault"))
        assertTrue(deleteVault.message(EnStrings).contains("cannot be undone"))
        val removeServerRecords =
            DestructiveConfirmation.RemoveServerRecords(setOf("secret-a", "secret-b"))
        assertEquals("Remove server copies?", removeServerRecords.title(EnStrings))
        assertTrue(removeServerRecords.message(EnStrings).contains("2"))
        assertTrue(removeServerRecords.message(EnStrings).contains("local vault"))
        assertTrue(removeServerRecords.message(EnStrings).contains("uploaded again"))
    }
}
