package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsPresentationTest {
    @Test
    fun vaultDeletionRequiresAnOpenVaultAndAnExistingRegularVaultFile() {
        assertTrue(
            SettingsPresentation.derive(
                vaultOpen = true,
                vaultFileExists = true,
            ).canDeleteVaultFile,
        )
        assertFalse(
            SettingsPresentation.derive(
                vaultOpen = false,
                vaultFileExists = true,
            ).canDeleteVaultFile,
        )
        assertFalse(
            SettingsPresentation.derive(
                vaultOpen = true,
                vaultFileExists = false,
            ).canDeleteVaultFile,
        )
    }
}
