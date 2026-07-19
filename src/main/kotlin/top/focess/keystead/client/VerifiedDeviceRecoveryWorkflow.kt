package top.focess.keystead.client

import java.util.Base64
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.recovery.RecoveryDeviceRequestCodec

class VerifiedDeviceRecoveryWorkflow(client: KeysteadServerClient) {
    private val recovery = RecoveryClient(client)

    fun request(username: String, replacementIdentity: LocalDeviceIdentity): ServerRecoveryDeviceRequest =
        recovery.requestDeviceRecovery(username, replacementIdentity)

    fun approve(
        request: ServerRecoveryDeviceRequest,
        approverIdentity: LocalDeviceIdentity,
        vaults: List<LocalVaultSession>,
    ) {
        val canonical = Base64.getUrlDecoder().decode(request.canonicalRequest)
        val decoded = try { RecoveryDeviceRequestCodec.decode(canonical) } finally { canonical.fill(0) }
        val publicKey = decoded.wrappingPublicKey()
        val packages = mutableListOf<RecoveryCompletionVaultPackage>()
        try {
            vaults.forEach { vault ->
                require(decoded.wrappingKeyAlgorithm() == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
                    "Replacement device wrapping algorithm is unsupported"
                }
                val context = LocalVaultSession.vaultKeyPackageContext(vault.vaultIdValue(), decoded.deviceId())
                val wrapped = try { vault.wrapCurrentVaultKey(publicKey, context) } finally { context.fill(0) }
                val encrypted = wrapped.encryptedVaultKey()
                try {
                    packages += RecoveryCompletionVaultPackage(
                        vault.vaultIdValue(), wrapped.vaultKeyId().value(), wrapped.keyAlgorithm(),
                        Base64.getEncoder().encodeToString(encrypted),
                    )
                } finally { encrypted.fill(0) }
            }
        } finally { publicKey.fill(0) }
        val payload = Base64.getUrlDecoder().decode(request.canonicalRequest)
        val signature = try { approverIdentity.signRecoveryRequest(payload) } finally { payload.fill(0) }
        recovery.approveDeviceRecovery(request.requestId, approverIdentity.deviceId, signature, packages)
    }

    fun claim(request: ServerRecoveryDeviceRequest, replacementIdentity: LocalDeviceIdentity): ServerRecoverySession {
        val payload = Base64.getUrlDecoder().decode(request.canonicalRequest)
        val signature = try { replacementIdentity.signRecoveryRequest(payload) } finally { payload.fill(0) }
        return recovery.claimDeviceRecovery(request.requestId, signature)
    }

    fun complete(
        request: ServerRecoveryDeviceRequest,
        replacementIdentity: LocalDeviceIdentity,
        newPassword: CharArray,
    ): ServerRecoveryCompletion {
        return try {
            val recoverySession = claim(request, replacementIdentity)
            recovery.complete(
                recoverySession.token,
                String(newPassword),
                replacementIdentity,
                emptyList(),
            )
        } finally {
            newPassword.fill('\u0000')
        }
    }
}
