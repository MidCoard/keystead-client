package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Comparator
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.KeyId
import top.focess.keystead.model.VaultFingerprint
import top.focess.keystead.recovery.DefaultRecoveryCryptoService
import top.focess.keystead.recovery.RecoveryCryptoService
import top.focess.keystead.recovery.RecoveryKit
import top.focess.keystead.recovery.RecoveryKitCodec
import top.focess.keystead.recovery.RecoveryVaultKeyPackage
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.DeviceVaultKeyPackage

/** Mirrors the canonical recovery-kit size cap enforced by core's codec. */
private const val MAX_ENCODED_KIT_CHARACTERS = 512

class OfflineRecoveryWorkflow(
    client: KeysteadServerClient,
    private val crypto: RecoveryCryptoService = DefaultRecoveryCryptoService(),
) {
    private val recovery = RecoveryClient(client)

    fun recover(
        username: String,
        encodedKit: String,
        newPassword: CharArray,
        identity: LocalDeviceIdentity,
        vaultRoot: Path,
    ): ServerRecoveryCompletion {
        require(encodedKit.isNotEmpty() && encodedKit.length <= MAX_ENCODED_KIT_CHARACTERS) {
            "Recovery kit is invalid"
        }
        val kitChars = encodedKit.toCharArray()
        try {
            SecretBuffer.fromChars(kitChars).use { encoded ->
                RecoveryKitCodec.decode(encoded).use { kit ->
                    return recoverWithKit(username, kit, newPassword, identity, vaultRoot)
                }
            }
        } finally {
            Wipe.wipe(kitChars)
        }
    }

    private fun recoverWithKit(
        username: String,
        kit: RecoveryKit,
        newPassword: CharArray,
        identity: LocalDeviceIdentity,
        vaultRoot: Path,
    ): ServerRecoveryCompletion {
        val (challengeId) = recovery.createChallenge(username, kit.enrollmentId(), kit.generation())
            val credential = crypto.accountCredential(kit)
            val credentialText = Base64.getUrlEncoder().withoutPadding().encodeToString(credential)
            Wipe.wipe(credential)
            val session = recovery.verifyKit(challengeId, credentialText)
            val material = recovery.material(session.token)
            check(material.enrollmentId == kit.enrollmentId() && material.generation == kit.generation()) {
                "Server recovery material does not match the offline kit"
            }
            val encryptedPrivateKey = Base64.getDecoder().decode(material.encryptedPrivateKey)
            val transientRoot = Files.createDirectories(vaultRoot).let { Files.createTempDirectory(it, ".keystead-recovery-") }
            val packages = mutableListOf<RecoveryCompletionVaultPackage>()
            val devicePublicKey = identity.publicKey()
            try {
                material.vaultPackages.forEach { value ->
                    val encryptedRecoveryKey = Base64.getDecoder().decode(value.encryptedVaultKey)
                    val recoveryPackage = RecoveryVaultKeyPackage(
                        username, value.fingerprint, KeyId(value.vaultKeyId), value.enrollmentId,
                        value.generation, value.keyAlgorithm, encryptedRecoveryKey,
                    )
                    Wipe.wipe(encryptedRecoveryKey)
                    val temporaryFile = transientRoot.resolve("${value.fingerprint}.kvault")
                    val service = DefaultVaultService()
                    crypto.openVault(service, temporaryFile, recoveryPackage, kit, encryptedPrivateKey).use { handle ->
                        val context = LocalVaultSession.vaultKeyPackageContext(value.fingerprint, identity.deviceId)
                        val devicePackage = try { handle.wrapVaultKeyPackageForDevice(devicePublicKey, context) } finally { Wipe.wipe(context) }
                        val encryptedDeviceKey = devicePackage.encryptedVaultKey()
                        try {
                            packages += RecoveryCompletionVaultPackage(
                                value.fingerprint, devicePackage.vaultKeyId().value(), devicePackage.keyAlgorithm(),
                                Base64.getEncoder().encodeToString(encryptedDeviceKey),
                            )
                        } finally { Wipe.wipe(encryptedDeviceKey) }
                    }
                }
                val completion = recovery.complete(session.token, String(newPassword), identity, packages)
                provisionRecoveredVaults(vaultRoot, identity, packages)
                return completion
            } finally {
                Wipe.wipe(newPassword)
                Wipe.wipe(encryptedPrivateKey)
                Wipe.wipe(devicePublicKey)
                deleteTree(transientRoot)
            }
    }

    private fun provisionRecoveredVaults(
        root: Path,
        identity: LocalDeviceIdentity,
        packages: List<RecoveryCompletionVaultPackage>,
    ) {
        val privateKey = identity.privateKey()
        try {
            Files.createDirectories(root)
            packages.forEach { value ->
                val realFile = root.resolve("${value.fingerprint}.kvault")
                check(!Files.exists(realFile)) {
                    "Recovered vault already exists locally"
                }
                val encrypted = Base64.getDecoder().decode(value.encryptedVaultKey)
                val context = LocalVaultSession.vaultKeyPackageContext(value.fingerprint, identity.deviceId)
                try {
                    DefaultVaultService().provisionVault(
                        realFile,
                        DeviceVaultKeyPackage(
                            VaultFingerprint.fromHexString(value.fingerprint),
                            KeyId(value.vaultKeyId),
                            value.keyAlgorithm,
                            encrypted,
                        ),
                        privateKey,
                        context,
                    ).close()
                } finally { Wipe.wipe(encrypted); Wipe.wipe(context) }
            }
        } finally { Wipe.wipe(privateKey) }
    }

    private fun deleteTree(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
