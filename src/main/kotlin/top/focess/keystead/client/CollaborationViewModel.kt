package top.focess.keystead.client

sealed interface CollaborationUiState {
    data object Loading : CollaborationUiState
    data object Empty : CollaborationUiState
    data class Invitations(val values: List<ServerVaultMembership>) : CollaborationUiState
    data class WaitingForKey(val fingerprint: String) : CollaborationUiState
    data class Managing(
        val fingerprint: String,
        val members: List<ServerVaultMember>,
        val devices: List<ServerVaultRecipientDevice>,
        val lifecycleState: ServerVaultKeyLifecycleState,
    ) : CollaborationUiState {
        val uncoveredDevices: Int
            get() = devices.count { !it.covered }
    }
    data class Rotating(
        val fingerprint: String,
        val completed: Int,
        val required: Int,
        val resumable: Boolean,
        val mandatory: Boolean,
    ) : CollaborationUiState
    data class Error(val diagnosticCode: String) : CollaborationUiState
}

internal interface CollaborationGateway {
    fun listMemberships(): List<ServerVaultMembership>
    fun listMembers(fingerprint: String): List<ServerVaultMember>
    fun packageRecipients(fingerprint: String): List<ServerVaultRecipientDevice>
    fun accept(fingerprint: String)
    fun decline(fingerprint: String)
    fun invite(fingerprint: String, userId: String, role: String)
    fun changeRole(fingerprint: String, userId: String, role: String)
    fun remove(fingerprint: String, userId: String)
}

private class ClientCollaborationGateway(
    private val client: VaultRotationClient,
) : CollaborationGateway {
    override fun listMemberships() = client.listMemberships()
    override fun listMembers(fingerprint: String) = client.listMembers(fingerprint)
    override fun packageRecipients(fingerprint: String) = client.packageRecipients(fingerprint)
    override fun accept(fingerprint: String) = client.accept(fingerprint)
    override fun decline(fingerprint: String) = client.decline(fingerprint)
    override fun invite(fingerprint: String, userId: String, role: String) = client.invite(fingerprint, userId, role)
    override fun changeRole(fingerprint: String, userId: String, role: String) = client.changeRole(fingerprint, userId, role)
    override fun remove(fingerprint: String, userId: String) = client.remove(fingerprint, userId)
}

class CollaborationViewModel internal constructor(
    private val gateway: CollaborationGateway,
) {
    constructor(client: KeysteadServerClient) : this(ClientCollaborationGateway(VaultRotationClient(client)))

    var state: CollaborationUiState = CollaborationUiState.Loading
        private set

    fun refresh(fingerprint: String? = null): CollaborationUiState = guarded {
        val memberships = gateway.listMemberships()
        val selected = fingerprint?.let { id -> memberships.firstOrNull { it.fingerprint == id } }
        when {
            selected?.membershipState == ServerVaultMemberState.INVITED ->
                CollaborationUiState.Invitations(listOf(selected))
            selected?.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY ->
                CollaborationUiState.WaitingForKey(selected.fingerprint)
            selected?.membershipState == ServerVaultMemberState.ACTIVE -> managing(selected)
            memberships.any { it.membershipState == ServerVaultMemberState.INVITED } ->
                CollaborationUiState.Invitations(
                    memberships.filter { it.membershipState == ServerVaultMemberState.INVITED },
                )
            memberships.any { it.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY } -> {
                val pending = memberships.first { it.membershipState == ServerVaultMemberState.ACCEPTED_PENDING_KEY }
                CollaborationUiState.WaitingForKey(pending.fingerprint)
            }
            memberships.any { it.membershipState == ServerVaultMemberState.ACTIVE } ->
                managing(memberships.first { it.membershipState == ServerVaultMemberState.ACTIVE })
            else -> CollaborationUiState.Empty
        }
    }

    fun accept(fingerprint: String): CollaborationUiState = mutate(fingerprint) { gateway.accept(fingerprint) }
    fun decline(fingerprint: String): CollaborationUiState = mutate(null) { gateway.decline(fingerprint) }
    fun invite(fingerprint: String, userId: String, role: String): CollaborationUiState =
        mutate(fingerprint) { gateway.invite(fingerprint, userId, role) }
    fun changeRole(fingerprint: String, userId: String, role: String): CollaborationUiState =
        mutate(fingerprint) { gateway.changeRole(fingerprint, userId, role) }
    fun remove(fingerprint: String, userId: String): CollaborationUiState =
        mutate(fingerprint) { gateway.remove(fingerprint, userId) }

    fun rotation(
        fingerprint: String,
        completed: Int,
        required: Int,
        resumable: Boolean,
        mandatory: Boolean,
    ): CollaborationUiState {
        require(completed in 0..required) { "Rotation progress is invalid" }
        return CollaborationUiState.Rotating(fingerprint, completed, required, resumable, mandatory)
            .also { state = it }
    }

    private fun managing(membership: ServerVaultMembership) = CollaborationUiState.Managing(
        membership.fingerprint,
        gateway.listMembers(membership.fingerprint),
        gateway.packageRecipients(membership.fingerprint),
        membership.keyLifecycleState,
    )

    private fun mutate(fingerprint: String?, action: () -> Unit): CollaborationUiState = guarded {
        action()
        refresh(fingerprint)
    }

    private fun guarded(action: () -> CollaborationUiState): CollaborationUiState = try {
        action().also { state = it }
    } catch (_: RuntimeException) {
        CollaborationUiState.Error("collaboration-operation-failed").also { state = it }
    }
}
