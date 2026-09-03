package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiActionGateTest {
    @Test
    fun `same action group cannot start twice until it finishes`() {
        val gate = UiActionGate<UiActionGroup>()

        assertTrue(gate.tryStart(UiActionGroup.SYNC))
        assertFalse(gate.tryStart(UiActionGroup.SYNC))

        gate.finish(UiActionGroup.SYNC)
        assertTrue(gate.tryStart(UiActionGroup.SYNC))
    }

    @Test
    fun `independent action groups may run together`() {
        val gate = UiActionGate<UiActionGroup>()

        assertTrue(gate.tryStart(UiActionGroup.SYNC))
        assertTrue(gate.tryStart(UiActionGroup.ACCOUNT))
    }

    @Test
    fun `conflicting action groups cannot touch the vault together`() {
        val gate = UiActionGate<UiActionGroup>()
        val vaultUsers = setOf(UiActionGroup.VAULT, UiActionGroup.SYNC)

        assertTrue(gate.tryStart(UiActionGroup.VAULT, vaultUsers))
        assertFalse(gate.tryStart(UiActionGroup.SYNC, vaultUsers))
    }
}
