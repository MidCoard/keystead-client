package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.CollaborationUiState
import top.focess.keystead.client.SecureStorageUiModel
import top.focess.keystead.client.SecureStorageUiState
import top.focess.keystead.client.ServerRecoveryDeviceRequest
import top.focess.keystead.client.ServerVaultKeyLifecycleState

@Composable
internal fun LifecyclePanel(
    authenticated: Boolean,
    vaultOpen: Boolean,
    identityLoaded: Boolean,
    secureStorage: SecureStorageUiModel,
    collaboration: CollaborationUiState,
    recoveryKit: String,
    replacementRequest: ServerRecoveryDeviceRequest?,
    onCheckNativeStorage: () -> Unit,
    onSelectNativeStorage: () -> Unit,
    onSelectPassphraseStorage: () -> Unit,
    onSelectMemoryStorage: () -> Unit,
    onMigrateIdentity: () -> Unit,
    onRefreshCollaboration: () -> Unit,
    onAcceptInvitation: () -> Unit,
    onDeclineInvitation: () -> Unit,
    onInviteMember: (String, String) -> Unit,
    onChangeMemberRole: (String, String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onPublishCollaborationPackages: () -> Unit,
    onRotateVault: () -> Unit,
    onResumeRotation: () -> Unit,
    onEnrollRecoveryKit: () -> Unit,
    onCopyRecoveryKit: () -> Unit,
    onOfflineRecover: (String, String) -> Unit,
    onRequestVerifiedDeviceRecovery: () -> Unit,
    onApproveVerifiedDeviceRecovery: () -> Unit,
    onCompleteVerifiedDeviceRecovery: (String) -> Unit,
) {
    var memberId by remember { mutableStateOf("") }
    var memberRole by remember { mutableStateOf("EDITOR") }
    var recoveryKitInput by remember { mutableStateOf("") }
    var replacementPassword by remember { mutableStateOf("") }
    val invitation = collaboration as? CollaborationUiState.Invitations
    val managing = collaboration as? CollaborationUiState.Managing
    val rotationRequired =
        managing?.lifecycleState == ServerVaultKeyLifecycleState.ROTATION_REQUIRED

    DestinationCard {
        SectionHeader("Protection, sharing, and recovery")
        GroupLabel("OS-level protection")
        Text(storageStatus(secureStorage), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCheckNativeStorage, modifier = Modifier.weight(1f)) {
                Text("Check OS storage")
            }
            Button(
                onClick = onSelectNativeStorage,
                enabled = secureStorage.state == SecureStorageUiState.NATIVE_AVAILABLE,
                modifier = Modifier.weight(1f),
            ) { Text("Use OS storage") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSelectPassphraseStorage, modifier = Modifier.weight(1f)) {
                Text("Use passphrase file")
            }
            OutlinedButton(onClick = onSelectMemoryStorage, modifier = Modifier.weight(1f)) {
                Text("Memory only")
            }
        }
        OutlinedButton(
            onClick = onMigrateIdentity,
            enabled = secureStorage.state == SecureStorageUiState.NATIVE_AVAILABLE,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Move this device identity to OS protection")
        }
        Text(
            "OS storage is scoped to the signed-in operating-system user. It is not biometric gating and never silently falls back.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Collaborative vault")
        Text(collaborationStatus(collaboration), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onRefreshCollaboration,
                enabled = authenticated,
                modifier = Modifier.weight(1f),
            ) { Text("Refresh access") }
            OutlinedButton(
                onClick = onPublishCollaborationPackages,
                enabled = authenticated && vaultOpen,
                modifier = Modifier.weight(1f),
            ) { Text("Package devices") }
        }
        if (invitation != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onAcceptInvitation, modifier = Modifier.weight(1f)) { Text("Accept invite") }
                OutlinedButton(onClick = onDeclineInvitation, modifier = Modifier.weight(1f)) { Text("Decline") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                memberId,
                { memberId = it },
                label = { Text("Member user") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                memberRole,
                { memberRole = it.uppercase() },
                label = { Text("Role") },
                singleLine = true,
                modifier = Modifier.weight(0.7f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { onInviteMember(memberId, memberRole) },
                enabled = authenticated && memberId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Invite") }
            OutlinedButton(
                onClick = { onChangeMemberRole(memberId, memberRole) },
                enabled = authenticated && memberId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Change role") }
            OutlinedButton(
                onClick = { onRemoveMember(memberId) },
                enabled = authenticated && memberId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text("Remove") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onRotateVault,
                enabled = authenticated && vaultOpen && identityLoaded,
                modifier = Modifier.weight(1f),
            ) { Text(if (rotationRequired) "Rotate now (required)" else "Rotate vault key") }
            OutlinedButton(
                onClick = onResumeRotation,
                enabled = authenticated && vaultOpen && identityLoaded,
                modifier = Modifier.weight(1f),
            ) { Text("Resume rotation") }
        }
        Text(
            "Removing a member stops future access only after the mandatory key rotation completes.",
            color = if (rotationRequired) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Account and vault recovery")
        OutlinedButton(
            onClick = onEnrollRecoveryKit,
            enabled = authenticated && vaultOpen,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Create or replace offline recovery kit") }
        if (recoveryKit.isNotBlank()) {
            OutlinedTextField(
                recoveryKit,
                {},
                readOnly = true,
                label = { Text("One-time recovery kit — store offline") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onCopyRecoveryKit, modifier = Modifier.fillMaxWidth()) {
                Text("Copy recovery kit temporarily")
            }
        }
        OutlinedTextField(
            recoveryKitInput,
            { recoveryKitInput = it },
            label = { Text("Offline recovery kit") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            replacementPassword,
            { replacementPassword = it },
            label = { Text("New server password") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onOfflineRecover(recoveryKitInput, replacementPassword)
                replacementPassword = ""
            },
            enabled = identityLoaded && recoveryKitInput.isNotBlank() && replacementPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Recover with offline kit") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onRequestVerifiedDeviceRecovery,
                enabled = identityLoaded,
                modifier = Modifier.weight(1f),
            ) { Text("Request trusted-device approval") }
            OutlinedButton(
                onClick = onApproveVerifiedDeviceRecovery,
                enabled = authenticated && vaultOpen && identityLoaded,
                modifier = Modifier.weight(1f),
            ) { Text("Approve pending device") }
        }
        replacementRequest?.let { request ->
            Text(
                "Replacement request ${request.fingerprint} is ${request.state.name.lowercase()}.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(
            onClick = {
                onCompleteVerifiedDeviceRecovery(replacementPassword)
                replacementPassword = ""
            },
            enabled = replacementRequest != null && identityLoaded && replacementPassword.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Complete trusted-device recovery") }
    }
}

internal fun storageStatus(model: SecureStorageUiModel): String =
    when (model.state) {
        SecureStorageUiState.CHECKING -> "Checking OS-user-protected storage"
        SecureStorageUiState.NATIVE_AVAILABLE ->
            "OS-user-protected storage available through ${model.providerId ?: "native provider"}"
        SecureStorageUiState.NATIVE_UNAVAILABLE ->
            "Native storage unavailable (${model.diagnosticCode ?: "provider-unavailable"}); choose a fallback explicitly"
        SecureStorageUiState.PASSPHRASE_SELECTED -> "Passphrase-encrypted file storage selected"
        SecureStorageUiState.MEMORY_SELECTED -> "Memory-only storage selected; identity is discarded on exit"
    }

internal fun collaborationStatus(state: CollaborationUiState): String =
    when (state) {
        CollaborationUiState.Loading -> "Refresh to inspect invitations, members, and device coverage"
        CollaborationUiState.Empty -> "No collaborative vault membership"
        is CollaborationUiState.Invitations -> "${state.values.size} invitation(s) awaiting a decision"
        is CollaborationUiState.WaitingForKey ->
            "Invitation accepted; waiting for an owner to package the vault key"
        is CollaborationUiState.Managing ->
            "${state.members.size} member(s), ${state.uncoveredDevices} device(s) missing a key package; lifecycle ${state.lifecycleState.name.lowercase()}"
        is CollaborationUiState.Rotating ->
            "Rotation ${state.completed}/${state.required}${if (state.resumable) "; resumable" else ""}"
        is CollaborationUiState.Error -> "Collaboration operation failed (${state.diagnosticCode})"
    }
