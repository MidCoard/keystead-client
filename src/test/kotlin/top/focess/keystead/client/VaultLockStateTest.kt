package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultLockStateTest {
    @Test
    fun closeDuringSyncHidesImmediatelyThenClosesAfterCompletion() {
        var state = VaultLockState()
        val runningSync = state.generation
        state = state.request()
        assertFalse(state.exposesVault(vaultOpen = true))
        assertFalse(state.shouldClose(vaultOperationActive = true))
        assertFalse(state.accepts(runningSync))
        assertTrue(state.shouldClose(vaultOperationActive = false))
        state = state.completed()
        assertFalse(state.accepts(runningSync))
        assertTrue(state.accepts(state.generation))
    }

    @Test
    fun lateShareResultCannotRestorePlaintextAfterImmediateLock() {
        val original = VaultLockState()
        val shareGeneration = original.generation
        val locked = original.request()
        assertTrue(locked.shouldClose(vaultOperationActive = false))
        assertFalse(locked.completed().accepts(shareGeneration))
    }
}
