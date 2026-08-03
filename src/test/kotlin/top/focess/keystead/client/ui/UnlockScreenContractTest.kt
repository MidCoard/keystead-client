package top.focess.keystead.client.ui

import androidx.compose.runtime.Composable
import kotlin.test.Test
import kotlin.test.assertNotNull
import top.focess.keystead.client.DeviceUnlockState
import top.focess.keystead.client.DeviceUnlockUiModel

class UnlockScreenContractTest {
    @Test
    fun `locked home does not require a recovery navigation action`() {
        assertNotNull(::lockedHomeWithoutRecoveryShortcut)
    }
}

@Composable
private fun lockedHomeWithoutRecoveryShortcut() {
    UnlockScreen(
        vaultDirectory = "vault.kvault",
        masterPassword = "",
        errorMessage = null,
        deviceUnlock = DeviceUnlockUiModel(DeviceUnlockState.NOT_CONFIGURED),
        onVaultDirectoryChange = {},
        onChooseExistingVault = {},
        onChooseNewVaultLocation = {},
        onMasterPasswordChange = {},
        onOpen = {},
        onOpenWithDeviceKey = {},
    )
}
