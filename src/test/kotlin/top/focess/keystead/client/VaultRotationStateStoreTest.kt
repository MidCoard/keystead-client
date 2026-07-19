package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VaultRotationStateStoreTest {
    @Test
    fun `rotation checkpoint survives restart without raw key material`() {
        val file = Files.createTempDirectory("keystead-rotation-state").resolve("rotation.properties")
        val state = LocalRotationState(
            "vault-1", "generation-1", "key-old", "key-new", "device-1",
            LocalRotationStage.LOCAL_COMMITTED,
        )

        VaultRotationStateStore(file).save(state)

        assertEquals(state, VaultRotationStateStore(file).load())
        val text = Files.readString(file)
        assertFalse(text.contains("encryptedVaultKey"))
        assertFalse(text.contains("privateKey"))
        assertFalse(text.contains("ciphertext"))
        VaultRotationStateStore(file).clear()
        assertEquals(null, VaultRotationStateStore(file).load())
    }
}
