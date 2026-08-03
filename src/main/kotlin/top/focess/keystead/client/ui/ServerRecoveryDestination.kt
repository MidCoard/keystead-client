package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.ServerRecoveryTask
import top.focess.keystead.client.ServerRecoveryTaskPresentation
import top.focess.keystead.client.ServerVaultAccessRequest
import top.focess.keystead.client.ServerVaultAccessRequestState
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun ServerRecoveryHub(
    task: ServerRecoveryTask,
    onTaskChange: (ServerRecoveryTask) -> Unit,
    restoreContent: @Composable () -> Unit,
    approvalContent: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    DestinationCard {
        SectionHeader(strings.recoverFromServer)
        Text(
            strings.serverRecoveryIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ServerRecoveryTaskPresentation.tasks.forEach { candidate ->
                KeysteadChoiceChip(
                    selected = task == candidate,
                    onClick = { onTaskChange(candidate) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (candidate) {
                            ServerRecoveryTask.RESTORE_THIS_DEVICE -> strings.restoreThisDeviceTask
                            ServerRecoveryTask.APPROVE_ANOTHER_DEVICE -> strings.approveAnotherDeviceTask
                        },
                    )
                }
            }
        }
    }
    when (task) {
        ServerRecoveryTask.RESTORE_THIS_DEVICE -> restoreContent()
        ServerRecoveryTask.APPROVE_ANOTHER_DEVICE -> approvalContent()
    }
}

@Composable
internal fun VaultAccessApprovalPanel(
    authenticated: Boolean,
    serverAvailability: ServerAvailability,
    vaultOpen: Boolean,
    pendingAccessRequest: ServerVaultAccessRequest?,
    onCheckServer: () -> Unit,
    onFindPendingAccessRequest: () -> Unit,
    onApprovePendingAccessRequest: () -> Unit,
) {
    val strings = LocalStrings.current
    DestinationCard {
        SectionHeader(strings.approveAnotherDeviceTask)
        Text(
            strings.approveAnotherDeviceIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ConnectedAvailabilityNotice(serverAvailability, onCheckServer)
        Text(
            when {
                !authenticated -> strings.vaultAccessApprovalSignInHelp
                !vaultOpen -> strings.vaultAccessApprovalUnlockHelp
                else -> strings.trustedDeviceApprovalHelp
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        pendingAccessRequest?.let { request ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        request.fingerprint,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        request.requestId,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        strings.vaultAccessRequestState(request.state),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        OutlinedButton(
            onClick = onFindPendingAccessRequest,
            enabled = authenticated && serverAvailability.isOnline,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.findPendingRequest)
        }
        Button(
            onClick = onApprovePendingAccessRequest,
            enabled =
                authenticated &&
                    serverAvailability.isOnline &&
                    vaultOpen &&
                    pendingAccessRequest?.state == ServerVaultAccessRequestState.PENDING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.approveVaultAccess)
        }
    }
}
