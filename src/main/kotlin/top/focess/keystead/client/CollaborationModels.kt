package top.focess.keystead.client

import java.time.Instant

enum class ServerVaultMemberState { INVITED, ACCEPTED_PENDING_KEY, ACTIVE, REMOVED }
enum class ServerVaultKeyLifecycleState { STABLE, ROTATION_REQUIRED, ROTATING }
enum class ServerVaultRotationState { OPEN, PACKAGING, READY, COMMITTED }
enum class ServerVaultRotationTargetType { DEVICE, AUTOMATION, RECOVERY }

data class ServerVaultMembership(
    val vaultId: String,
    val ownerId: String,
    val encryptedMetadata: String,
    val role: String,
    val membershipState: ServerVaultMemberState,
    val currentVaultKeyId: String?,
    val keyLifecycleState: ServerVaultKeyLifecycleState,
    val lifecycleVersion: Long,
)

data class ServerVaultMember(
    val vaultId: String,
    val userId: String,
    val role: String,
    val state: ServerVaultMemberState,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class ServerVaultRecipientDevice(
    val userId: String,
    val role: String,
    val memberState: ServerVaultMemberState,
    val deviceId: String,
    val keyAlgorithm: String,
    val publicKey: String,
    val covered: Boolean,
)

data class ServerVaultRotationTarget(
    val targetId: String,
    val targetType: ServerVaultRotationTargetType,
    val recipientId: String?,
    val deviceId: String?,
    val principalId: String?,
    val enrollmentId: String?,
    val recoveryGeneration: Long?,
    val keyAlgorithm: String,
    val publicKey: String,
    val required: Boolean,
    val covered: Boolean,
)

data class ServerVaultRotation(
    val generationId: String,
    val vaultId: String,
    val sourceVaultKeyId: String,
    val targetVaultKeyId: String,
    val state: ServerVaultRotationState,
    val lifecycleVersion: Long,
    val targets: List<ServerVaultRotationTarget>,
)

class KeysteadVaultLifecycleConflictException(
    val lifecycleState: ServerVaultKeyLifecycleState?,
) : KeysteadServerException(409, "Vault lifecycle changed; refresh before continuing.") {
    override fun toString(): String = "KeysteadVaultLifecycleConflictException(lifecycleState=$lifecycleState)"
}

data class ServerRotationPackage(
    val targetId: String,
    val vaultKeyId: String,
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
) {
    override fun toString(): String = "ServerRotationPackage(targetId=$targetId, vaultKeyId=$vaultKeyId, keyAlgorithm=$keyAlgorithm, encryptedVaultKey=<redacted>)"
}
