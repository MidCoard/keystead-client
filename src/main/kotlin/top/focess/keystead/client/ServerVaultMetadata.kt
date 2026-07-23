package top.focess.keystead.client

import java.security.SecureRandom
import java.util.Base64
import top.focess.keystead.memory.Wipe

object ServerVaultMetadata {

    private const val VERSION = "v1"
    private const val RANDOM_BYTES = 32
    private val secureRandom = SecureRandom()

    fun opaque(
        vaultId: String,
        randomBytes: (Int) -> ByteArray = ServerVaultMetadata::secureRandomBytes,
    ): String {
        require(vaultId.isNotBlank()) { "Vault id is required" }
        val bytes = randomBytes(RANDOM_BYTES)
        require(bytes.size == RANDOM_BYTES) {
            "Vault metadata random source returned ${bytes.size} bytes"
        }
        return try {
            "$VERSION.${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
        } finally {
            Wipe.wipe(bytes)
        }
    }

    private fun secureRandomBytes(size: Int): ByteArray =
        ByteArray(size).also { secureRandom.nextBytes(it) }
}
