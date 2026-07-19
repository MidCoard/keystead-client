package top.focess.keystead.client

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CollaborationViewModelTest {
    private val active = membership("vault-active", ServerVaultMemberState.ACTIVE)
    private val invitation = membership("vault-invited", ServerVaultMemberState.INVITED)
    private val waiting = membership("vault-waiting", ServerVaultMemberState.ACCEPTED_PENDING_KEY)

    @Test
    fun `refresh surfaces invitations before active management`() {
        val gateway = FakeGateway(mutableListOf(active, invitation))
        val state = CollaborationViewModel(gateway).refresh()

        val invitations = assertIs<CollaborationUiState.Invitations>(state)
        assertEquals(listOf("vault-invited"), invitations.values.map { it.vaultId })
    }

    @Test
    fun `accepted membership waits for an owner key package`() {
        val state = CollaborationViewModel(FakeGateway(mutableListOf(waiting))).refresh("vault-waiting")

        assertEquals(CollaborationUiState.WaitingForKey("vault-waiting"), state)
    }

    @Test
    fun `active vault includes members and uncovered devices`() {
        val gateway = FakeGateway(mutableListOf(active))
        gateway.members += member("owner", "OWNER")
        gateway.recipients += recipient("member-device", covered = false)

        val state = assertIs<CollaborationUiState.Managing>(
            CollaborationViewModel(gateway).refresh("vault-active"),
        )
        assertEquals("owner", state.members.single().userId)
        assertEquals(1, state.uncoveredDevices)
    }

    @Test
    fun `accept decline member changes and removal delegate then refresh`() {
        val gateway = FakeGateway(mutableListOf(invitation))
        val viewModel = CollaborationViewModel(gateway)

        viewModel.accept("vault-invited")
        assertEquals(listOf("vault-invited"), gateway.accepted)
        gateway.memberships.clear()
        gateway.memberships += active
        viewModel.invite("vault-active", "member", "EDITOR")
        viewModel.changeRole("vault-active", "member", "VIEWER")
        viewModel.remove("vault-active", "member")
        viewModel.decline("vault-other")

        assertEquals(listOf("vault-active:member:EDITOR"), gateway.invited)
        assertEquals(listOf("vault-active:member:VIEWER"), gateway.roles)
        assertEquals(listOf("vault-active:member"), gateway.removed)
        assertEquals(listOf("vault-other"), gateway.declined)
    }

    @Test
    fun `rotation state explains mandatory progress and resume`() {
        val state = CollaborationViewModel(FakeGateway()).rotation(
            "vault-active",
            completed = 2,
            required = 3,
            resumable = true,
            mandatory = true,
        )

        val rotating = assertIs<CollaborationUiState.Rotating>(state)
        assertTrue(rotating.mandatory)
        assertEquals(2, rotating.completed)
        assertEquals(3, rotating.required)
        assertTrue(rotating.resumable)
    }

    @Test
    fun `errors and states do not expose package ciphertext`() {
        val marker = "ciphertext-secret-marker"
        val gateway = FakeGateway(mutableListOf(active)).apply {
            failure = IllegalStateException(marker)
        }

        val text = CollaborationViewModel(gateway).refresh("vault-active").toString()
        assertFalse(text.contains(marker))
    }

    private class FakeGateway(
        val memberships: MutableList<ServerVaultMembership> = mutableListOf(),
    ) : CollaborationGateway {
        val members = mutableListOf<ServerVaultMember>()
        val recipients = mutableListOf<ServerVaultRecipientDevice>()
        val accepted = mutableListOf<String>()
        val declined = mutableListOf<String>()
        val invited = mutableListOf<String>()
        val roles = mutableListOf<String>()
        val removed = mutableListOf<String>()
        var failure: RuntimeException? = null

        override fun listMemberships() = failure?.let { throw it } ?: memberships.toList()
        override fun listMembers(vaultId: String) = members.toList()
        override fun packageRecipients(vaultId: String) = recipients.toList()
        override fun accept(vaultId: String) { accepted += vaultId }
        override fun decline(vaultId: String) { declined += vaultId }
        override fun invite(vaultId: String, userId: String, role: String) { invited += "$vaultId:$userId:$role" }
        override fun changeRole(vaultId: String, userId: String, role: String) { roles += "$vaultId:$userId:$role" }
        override fun remove(vaultId: String, userId: String) { removed += "$vaultId:$userId" }
    }

    private fun membership(vaultId: String, state: ServerVaultMemberState) = ServerVaultMembership(
        vaultId, "owner", "opaque", "EDITOR", state, "key-1",
        ServerVaultKeyLifecycleState.STABLE, 1,
    )

    private fun member(userId: String, role: String) = ServerVaultMember(
        "vault-active", userId, role, ServerVaultMemberState.ACTIVE, Instant.EPOCH, Instant.EPOCH,
    )

    private fun recipient(deviceId: String, covered: Boolean) = ServerVaultRecipientDevice(
        "member", "EDITOR", ServerVaultMemberState.ACTIVE, deviceId,
        "TINK_X25519_HKDF_SHA256_AES256_GCM", "public", covered,
    )
}
