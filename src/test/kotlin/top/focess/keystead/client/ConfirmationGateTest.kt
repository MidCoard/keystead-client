package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        assertEquals("Delete secret", delete.title)
        assertTrue(delete.message.contains("GitHub token"))
        assertTrue(delete.message.contains("cannot be undone"))
        val revoke = DestructiveConfirmation.RevokeDevice
        assertEquals("Revoke device", revoke.title)
        assertTrue(revoke.message.contains("signed out"))
    }
}
