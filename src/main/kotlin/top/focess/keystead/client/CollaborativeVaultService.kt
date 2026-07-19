package top.focess.keystead.client

import java.util.Base64
import top.focess.keystead.crypto.DefaultCryptoService

class CollaborativeVaultService(
    private val client: KeysteadServerClient,
    private val collaboration: VaultRotationClient = VaultRotationClient(client),
) {
    fun publishUncoveredRecipientPackages(session: LocalVaultSession): Int {
        var published = 0
        collaboration.packageRecipients(session.vaultIdValue())
            .filter { !it.covered && it.keyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM }
            .forEach { target ->
                val publicKey = Base64.getDecoder().decode(target.publicKey)
                val context = LocalVaultSession.vaultKeyPackageContext(session.vaultIdValue(), target.deviceId)
                try {
                    val wrapped = session.wrapCurrentVaultKey(publicKey, context)
                    val encrypted = wrapped.encryptedVaultKey()
                    try {
                        client.putRecipientVaultKeyPackage(
                            session.vaultIdValue(),
                            target.userId,
                            target.deviceId,
                            ServerVaultKeyPackage(
                                session.vaultIdValue(), target.deviceId, wrapped.vaultKeyId().value(),
                                wrapped.keyAlgorithm(), Base64.getEncoder().encodeToString(encrypted),
                            ),
                        )
                        published++
                    } finally { encrypted.fill(0) }
                } finally { publicKey.fill(0); context.fill(0) }
            }
        return published
    }
}
