package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ServerVaultMetadataTest {

    @Test
    fun opaqueMetadataDoesNotExposeVaultIdOrReadableMarkers() {
        val vaultId = "70000000-0000-0000-0000-000000000013"

        val metadata =
            ServerVaultMetadata.opaque(vaultId) { size ->
                ByteArray(size) { index -> (index + 1).toByte() }
            }

        assertTrue(metadata.startsWith("v1."))
        assertFalse(metadata.contains(vaultId))
        assertFalse(metadata.contains("keystead", ignoreCase = true))
        assertFalse(metadata.contains("vault", ignoreCase = true))
    }

    @Test
    fun opaqueMetadataUsesFreshRandomMaterial() {
        var seed = 1

        val first =
            ServerVaultMetadata.opaque("vault-id") { size ->
                ByteArray(size) { seed.toByte() }.also { seed++ }
            }
        val second =
            ServerVaultMetadata.opaque("vault-id") { size ->
                ByteArray(size) { seed.toByte() }.also { seed++ }
            }

        assertNotEquals(first, second)
    }
}
