package top.focess.keystead.client

sealed interface CollaborationUiState {
    data object Loading : CollaborationUiState
    data object Empty : CollaborationUiState
    data class Invitations(val values: List<ServerVaultMembership>) : CollaborationUiState
    data class WaitingForKey(val vaultId: String) : CollaborationUiState
    data class Managing(
        val vaultId: String,
        val members: List<ServerVaultMember>,
        val devices: List<ServerVaultRecipientDevice>,
        val lifecycleState: ServerVaultKeyLifecycleState,
    ) : CollaborationUiState {
        val uncoveredDevices: Int
            get() = devices.count { !it.covered }
    }
    data class Rotating(
        val vaultId: String,
        val completed: Int,
        val required: Int,
        val resumable: Boolean,
        val mandatory: Boolean,
    ) : CollaborationUiState
    data class Error(val diagnosticCode: String) : CollaborationUiState
}

internal interface CollaborationGateway {
    fun listMemberships(): List<ServerVaultMembership>
    fun listMembers(vaultId: String): List<ServerVaultMember>
    fun packageRecipients(vaultId: String): List<ServerVaultRecipientDevice>
    fun accept(vaultId: String)
    fun decline(vaultId: String)
    fun invite(vaultId: String, userId: String, role: String)
    fun changeRole(vaultId: String, userId: String, role: String)
    fun remove(vaultId: String, userId: String)
}

private class ClientCollaborationGateway(
    private val client: VaultRotationClient,
) : CollaborationGateway {
    override fun listMemberships() = client.listMemberships()
    override fun listMembers(vaultId: String) = client.listMembers(vaultId)
    override fun packageRecipients(vaultId: String) = client.packageRecipients(vaultId)
    override fun accept(vaultId: String) = client.accept(vaultId)
    override fun decline(vaultId: String) = client.decline(vaultId)
    override fun invite(vaultId: String, userId: String, role: String) = client.invite(vaultId, userId, role)
    override fun changeRole(vaultId: String, userId: String, role: String) = client.changeRole(vaultId, userId, role)
    override fun remove(vaultId: String, userId: String) = client.remove(vaultId, userId)
}

class CollaborationViewModel internal constructor(
    private val gateway: CollaborationGateway,
) {
    constructor(client: KeysteadServerClient) : this(ClientCollaborationGateway(VaultRotationClient(client)))

    var state: CollaborationUiState = CollaborationUiState.Loading
        private set

    fun refresh(vaultId: String? = null): CollaborationUiState = guarded {
        val memberships = gateway.listMemberships()
        val selected = vaultId?.let { id -> memberships.firstOrNull { it.vaultId == id } }
        when {
            selected?.membershipState == ServerVaultMemberState.INVITED ->
                CollaborationUiState.Invitations(listOf(selected))
            selected?.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY ->
                CollaborationUiState.WaitingForKey(selected.vaultId)
            selected?.membershipState == ServerVaultMemberState.ACTIVE -> managing(selected)
            memberships.any { it.membershipState == ServerVaultMemberState.INVITED } ->
                CollaborationUiState.Invitations(
                    memberships.filter { it.membershipState == ServerVaultMemberState.INVITED },
                )
            memberships.any { it.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY } -> {
                val pending = memberships.first { it.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY }
                CollaborationUiState.WaitingForKey(pending.vaultId)
            }
            memberships.any { it.membershipState == ServerVaultMemberState.ACTIVE } ->
                managing(memberships.first { it.membershipState == ServerVaultMemberState.ACTIVE })
            else -> CollaborationUiState.Empty
        }
    }

    fun accept(vaultId: String): CollaborationUiState = mutate(vaultId) { gateway.accept(vaultId) }
    fun decline(vaultId: String): CollaborationUiState = mutate(null) { gateway.decline(vaultId) }
    fun invite(vaultId: String, userId: String, role: String): CollaborationUiState =
        mutate(vaultId) { gateway.invite(vaultId, userId, role) }
    fun changeRole(vaultId: String, userId: String, role: String): CollaborationUiState =
        mutate(vaultId) { gateway.changeRole(vaultId, userId, role) }
    fun remove(vaultId: String, userId: String): CollaborationUiState =
        mutate(vaultId) { gateway.remove(vaultId, userId) }

    fun rotation(
        vaultId: String,
        completed: Int,
        required: Int,
        resumable: Boolean,
        mandatory: Boolean,
    ): CollaborationUiState {
        require(completed in 0..required) { "Rotation progress is invalid" }
        return CollaborationUiState.Rotating(vaultId, completed, required, resumable, mandatory)
            .also { state = it }
    }

    private fun managing(membership: ServerVaultMembership) = CollaborationUiState.Managing(
        membership.vaultId,
        gateway.listMembers(membership.vaultId),
        gateway.packageRecipients(membership.vaultId),
        membership.keyLifecycleState,
    )

    private fun mutate(vaultId: String?, action: () -> Unit): CollaborationUiState = guarded {
        action()
        refresh(vaultId)
    }

    private fun guarded(action: () -> CollaborationUiState): CollaborationUiState = try {
        action().also { state = it }
    } catch (_: RuntimeException) {
        CollaborationUiState.Error("collaboration-operation-failed").also { state = it }
    }
}
