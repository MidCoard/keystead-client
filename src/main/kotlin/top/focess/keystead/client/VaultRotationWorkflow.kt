package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.util.Base64
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.KeyId
import top.focess.keystead.service.DeviceVaultKeyPackage
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.PreparedVaultKeyRotation

class VaultRotationWorkflow(
    client: KeysteadServerClient,
    private val stateStore: VaultRotationStateStore,
) {
    private val rotations = VaultRotationClient(client)

    fun rotate(
        session: LocalVaultSession,
        identity: LocalDeviceIdentity,
        lifecycleVersion: Long,
        selectedPendingUsers: Set<String> = emptySet(),
    ): ServerVaultRotation {
        session.prepareVaultKeyRotation().use { prepared ->
            val begun = rotations.begin(
                session.vaultIdValue(), prepared.sourceVaultKeyId().value(),
                prepared.targetVaultKeyId().value(), lifecycleVersion, selectedPendingUsers,
            )
            stateStore.save(state(begun, identity.deviceId, LocalRotationStage.PACKAGING))
            return finish(session, identity, begun, prepared)
        }
    }

    fun resume(session: LocalVaultSession, identity: LocalDeviceIdentity): ServerVaultRotation {
        val local = stateStore.load() ?: throw IllegalStateException("No vault rotation is pending")
        require(local.vaultId == session.vaultIdValue() && local.deviceId == identity.deviceId) {
            "Rotation state belongs to another vault or device"
        }
        val server = rotations.status(local.vaultId, local.generationId)
        if (local.stage == LocalRotationStage.LOCAL_COMMITTED) return commitServer(server)
        val self = try {
            rotations.selfPackage(local.vaultId, local.generationId, identity.deviceId)
        } catch (error: KeysteadServerException) {
            if (error.statusCode != 404) throw error
            rotations.cancel(local.vaultId, local.generationId)
            stateStore.clear()
            val refreshed = rotations.listMemberships()
                .firstOrNull { it.vaultId == local.vaultId }
                ?: throw IllegalStateException("Vault membership was not found after rotation cancellation")
            return rotate(session, identity, refreshed.lifecycleVersion)
        }
        session.resumeVaultKeyRotation(self, identity).use { prepared ->
            return finish(session, identity, server, prepared)
        }
    }

    private fun finish(
        session: LocalVaultSession,
        identity: LocalDeviceIdentity,
        initial: ServerVaultRotation,
        prepared: PreparedVaultKeyRotation,
    ): ServerVaultRotation {
        var current = initial
        var localPackage: DeviceVaultKeyPackage? = null
        current.targets.filter { it.required && !it.covered }.forEach { target ->
            require(target.keyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
                "Rotation target key algorithm is unsupported"
            }
            val publicKey = Base64.getDecoder().decode(target.publicKey)
            val context = context(current.vaultId, target)
            val keyPackage = try {
                prepared.wrapVaultKeyPackageForDevice(publicKey, context)
            } finally { Wipe.wipe(publicKey); Wipe.wipe(context) }
            val encrypted = keyPackage.encryptedVaultKey()
            try {
                current = rotations.upload(
                    current.vaultId, current.generationId, target,
                    keyPackage.vaultKeyId().value(), Base64.getEncoder().encodeToString(encrypted),
                )
            } finally { Wipe.wipe(encrypted) }
            if (target.targetType == ServerVaultRotationTargetType.DEVICE &&
                target.recipientId != null && target.deviceId == identity.deviceId
            ) localPackage = keyPackage
        }
        current = rotations.status(current.vaultId, current.generationId)
        require(current.state == ServerVaultRotationState.READY) { "Rotation package coverage is incomplete" }
        stateStore.save(state(current, identity.deviceId, LocalRotationStage.PACKAGED))
        val initiatingPackage = localPackage ?: rotations.selfPackage(
            current.vaultId, current.generationId, identity.deviceId,
        ).let {
            val algorithm =
                if (it.keyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
                    DefaultVaultService.DEVICE_KEY_PACKAGE_ALGORITHM
                } else {
                    it.keyAlgorithm
                }
            DeviceVaultKeyPackage(
                KeyId(it.vaultKeyId),
                algorithm,
                Base64.getDecoder().decode(it.encryptedVaultKey),
            )
        }
        session.commitPreparedRotation(prepared, initiatingPackage)
        stateStore.save(state(current, identity.deviceId, LocalRotationStage.LOCAL_COMMITTED))
        return commitServer(current)
    }

    private fun commitServer(current: ServerVaultRotation): ServerVaultRotation {
        val committed = rotations.commit(current.vaultId, current.generationId)
        stateStore.clear()
        return committed
    }

    private fun state(rotation: ServerVaultRotation, deviceId: String, stage: LocalRotationStage) =
        LocalRotationState(
            rotation.vaultId, rotation.generationId, rotation.sourceVaultKeyId,
            rotation.targetVaultKeyId, deviceId, stage,
        )

    private fun context(vaultId: String, target: ServerVaultRotationTarget): ByteArray =
        when (target.targetType) {
            ServerVaultRotationTargetType.DEVICE ->
                LocalVaultSession.vaultKeyPackageContext(vaultId, requireNotNull(target.deviceId))
            ServerVaultRotationTargetType.AUTOMATION ->
                LocalVaultSession.automationVaultKeyPackageContext(vaultId, requireNotNull(target.principalId))
            ServerVaultRotationTargetType.RECOVERY ->
                "keystead-recovery-vault-package-v1|vault:$vaultId|enrollment:${requireNotNull(target.enrollmentId)}|generation:${requireNotNull(target.recoveryGeneration)}"
                    .toByteArray(StandardCharsets.UTF_8)
        }
}
