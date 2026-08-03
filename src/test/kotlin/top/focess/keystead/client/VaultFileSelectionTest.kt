package top.focess.keystead.client

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultFileSelectionTest {
    @Test
    fun newVaultTargetAddsTheKvaultExtensionWhenItIsMissing() {
        assertEquals(
            Path.of("D:/vaults/work.kvault"),
            VaultFileSelection.newTarget(Path.of("D:/vaults/work")),
        )
    }

    @Test
    fun newVaultTargetKeepsAnExistingKvaultExtensionCaseInsensitively() {
        assertEquals(
            Path.of("D:/vaults/work.KVAULT"),
            VaultFileSelection.newTarget(Path.of("D:/vaults/work.KVAULT")),
        )
    }
}
