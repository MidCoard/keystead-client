package top.focess.keystead.client

import java.util.UUID
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.crypto.DeviceKeyPair

/** A one-login, memory-only exchange key used solely for restoring the personal vault. */
class EphemeralVaultAccessSession private constructor(
    val requestId: String,
    val serverOrigin: String,
    private val keyPair: DeviceKeyPair,
) : AutoCloseable {
    val keyAlgorithm: String
        get() = keyPair.keyAlgorithm()

    val publicKey: ByteArray
        get() = keyPair.publicKey()

    fun withPrivateKey(action: (ByteArray) -> Unit) {
        keyPair.copyPrivateKey(action)
    }

    override fun close() {
        keyPair.close()
    }

    override fun toString(): String =
        "EphemeralVaultAccessSession(requestId=$requestId, serverOrigin=$serverOrigin, key=<redacted>)"

    companion object {
        fun create(serverOrigin: String): EphemeralVaultAccessSession {
            require(serverOrigin.isNotBlank()) { "Server origin must not be blank" }
            return EphemeralVaultAccessSession(
                requestId = UUID.randomUUID().toString(),
                serverOrigin = serverOrigin.trimEnd('/'),
                keyPair = DefaultCryptoService().generateDeviceKeyPair(),
            )
        }
    }
}
