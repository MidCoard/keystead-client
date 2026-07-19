package top.focess.keystead.client

import java.util.Base64
import java.util.UUID
import top.focess.keystead.recovery.DefaultRecoveryCryptoService
import top.focess.keystead.recovery.RecoveryCryptoService
import top.focess.keystead.recovery.RecoveryKitCodec

data class RecoveryEnrollmentResult(
    val recoveryKit: String,
    val enrollment: ServerRecoveryEnrollment,
) {
    override fun toString() = "RecoveryEnrollmentResult(recoveryKit=<redacted>, enrollment=$enrollment)"
}

class RecoveryEnrollmentWorkflow(
    client: KeysteadServerClient,
    private val crypto: RecoveryCryptoService = DefaultRecoveryCryptoService(),
) {
    private val recovery = RecoveryClient(client)

    fun enroll(
        username: String,
        session: LocalVaultSession,
        generation: Long,
    ): RecoveryEnrollmentResult {
        val enrollmentId = UUID.randomUUID().toString()
        crypto.enroll(enrollmentId, generation).use { material ->
            val credential = material.accountCredential()
            val publicKey = material.publicKey().publicKey()
            val encryptedPrivate = material.encryptedPrivateKey()
            try {
                val created = recovery.createEnrollment(
                    enrollmentId,
                    generation,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(credential),
                    material.publicKey().keyAlgorithm(),
                    Base64.getEncoder().encodeToString(publicKey),
                    Base64.getEncoder().encodeToString(encryptedPrivate),
                )
                check(created.enrollmentId == enrollmentId) { "Server changed the recovery enrollment binding" }
                val wrapped = session.wrapRecoveryVaultKey(crypto, material.publicKey(), username)
                val encryptedVaultKey = wrapped.encryptedVaultKey()
                try {
                    recovery.putVaultPackage(
                        username,
                        ServerRecoveryVaultPackage(
                            enrollmentId, generation, wrapped.vaultId(), wrapped.vaultKeyId().value(),
                            wrapped.keyAlgorithm(), Base64.getEncoder().encodeToString(encryptedVaultKey),
                        ),
                    )
                } finally { encryptedVaultKey.fill(0) }
                val committed = recovery.commitEnrollment(enrollmentId, generation)
                return RecoveryEnrollmentResult(RecoveryKitCodec.encode(material.kit()), committed)
            } finally {
                credential.fill(0); publicKey.fill(0); encryptedPrivate.fill(0)
            }
        }
    }
}
