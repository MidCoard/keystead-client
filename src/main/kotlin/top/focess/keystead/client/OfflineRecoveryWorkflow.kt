package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.Comparator
import java.util.UUID
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.model.KeyId
import top.focess.keystead.model.VaultId
import top.focess.keystead.recovery.DefaultRecoveryCryptoService
import top.focess.keystead.recovery.RecoveryCryptoService
import top.focess.keystead.recovery.RecoveryKit
import top.focess.keystead.recovery.RecoveryKitCodec
import top.focess.keystead.recovery.RecoveryVaultKeyPackage
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.DeviceVaultKeyPackage
import top.focess.keystead.store.FileVaultStore

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
            kitChars.fill('\u0000')
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
            credential.fill(0)
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
                        username, value.vaultId, KeyId(value.vaultKeyId), value.enrollmentId,
                        value.generation, value.keyAlgorithm, encryptedRecoveryKey,
                    )
                    encryptedRecoveryKey.fill(0)
                    val temporaryDirectory = transientRoot.resolve(value.vaultId)
                    val service = DefaultVaultService(FileVaultStore(temporaryDirectory))
                    crypto.openVault(service, VaultId(UUID.fromString(value.vaultId)), recoveryPackage, kit, encryptedPrivateKey).use { handle ->
                        val context = LocalVaultSession.vaultKeyPackageContext(value.vaultId, identity.deviceId)
                        val devicePackage = try { handle.wrapVaultKeyPackageForDevice(devicePublicKey, context) } finally { context.fill(0) }
                        val encryptedDeviceKey = devicePackage.encryptedVaultKey()
                        try {
                            packages += RecoveryCompletionVaultPackage(
                                value.vaultId, devicePackage.vaultKeyId().value(), devicePackage.keyAlgorithm(),
                                Base64.getEncoder().encodeToString(encryptedDeviceKey),
                            )
                        } finally { encryptedDeviceKey.fill(0) }
                    }
                }
                val completion = recovery.complete(session.token, String(newPassword), identity, packages)
                provisionRecoveredVaults(vaultRoot, identity, packages)
                return completion
            } finally {
                newPassword.fill('\u0000')
                encryptedPrivateKey.fill(0)
                devicePublicKey.fill(0)
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
            packages.forEach { value ->
                val directory = root.resolve(value.vaultId)
                check(!Files.exists(directory.resolve("vault.properties"))) {
                    "Recovered vault already exists locally"
                }
                val encrypted = Base64.getDecoder().decode(value.encryptedVaultKey)
                val context = LocalVaultSession.vaultKeyPackageContext(value.vaultId, identity.deviceId)
                try {
                    DefaultVaultService(FileVaultStore(directory)).provisionVault(
                        VaultId(UUID.fromString(value.vaultId)),
                        DeviceVaultKeyPackage(KeyId(value.vaultKeyId), value.keyAlgorithm, encrypted),
                        privateKey,
                        context,
                    ).close()
                } finally { encrypted.fill(0); context.fill(0) }
            }
        } finally { privateKey.fill(0) }
    }

    private fun deleteTree(directory: Path) {
        if (!Files.exists(directory)) return
        Files.walk(directory).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
