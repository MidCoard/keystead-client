package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.ServerVaultAccessRequest
import top.focess.keystead.client.ServerVaultRestoreModel
import top.focess.keystead.client.ServerVaultRestoreStage
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun ServerRestorePanel(
    model: ServerVaultRestoreModel,
    serverAvailability: ServerAvailability,
    onCheckServer: () -> Unit,
    request: ServerVaultAccessRequest?,
    targetPath: String,
    targetPathAvailable: Boolean,
    onChooseTarget: () -> Unit,
    newMasterPassphrase: String,
    onNewMasterPassphraseChange: (String) -> Unit,
    newMasterPassphraseConfirmation: String,
    onNewMasterPassphraseConfirmationChange: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onCreateRequest: () -> Unit,
    onRefreshRequest: () -> Unit,
    onRestore: () -> Unit,
) {
    val strings = LocalStrings.current
    val masterPassphrasesMatch =
        newMasterPassphraseConfirmation.isEmpty() ||
            newMasterPassphrase == newMasterPassphraseConfirmation
    DestinationCard {
        SectionHeader(strings.restoreAnotherDevice)
        Text(
            strings.restoreAnotherDeviceIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        ConnectedAvailabilityNotice(serverAvailability, onCheckServer)
        RestoreStatus(strings.serverVaultRestoreStatus(model))

        when (model.stage) {
            ServerVaultRestoreStage.SIGN_IN_REQUIRED ->
                Button(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.destinationLabel(KeysteadDestination.ACCOUNT))
                }
            ServerVaultRestoreStage.ACCESS_REQUEST_REQUIRED,
            ServerVaultRestoreStage.REQUEST_EXPIRED,
            ->
                Button(
                    onClick = onCreateRequest,
                    enabled = model.canCreateRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.createApprovalRequest)
                }
            ServerVaultRestoreStage.WAITING_FOR_APPROVAL,
            ServerVaultRestoreStage.WAITING_FOR_PACKAGE,
            -> {
                request?.let { VaultAccessRequestSummary(it) }
                Text(
                    strings.trustedDeviceRequestHelp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = onRefreshRequest,
                    enabled = model.canRefreshRequest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(strings.checkApprovalStatus)
                }
            }
            ServerVaultRestoreStage.SERVER_OFFLINE,
            ServerVaultRestoreStage.READY_TO_RESTORE,
            ServerVaultRestoreStage.TARGET_IN_USE,
            ServerVaultRestoreStage.MASTER_PASSPHRASE_REQUIRED,
            -> Unit
        }

        request?.approvedPackage?.let { packageValue ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            GroupLabel(strings.restoreStepVault)
            Text(
                packageValue.fingerprint,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = targetPath,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text(strings.restoreTargetVault) },
                    isError = targetPath.isNotBlank() && !targetPathAvailable,
                    supportingText =
                        if (targetPath.isNotBlank() && !targetPathAvailable) {
                            { Text(strings.restoreTargetMustBeNew) }
                        } else {
                            null
                        },
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onChooseTarget) {
                    Text(strings.chooseRestoreTarget)
                }
            }
            Text(
                strings.restoreCreatesLocalFile,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = newMasterPassphrase,
                onValueChange = onNewMasterPassphraseChange,
                label = { Text(strings.newVaultMasterPassphrase) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newMasterPassphraseConfirmation,
                onValueChange = onNewMasterPassphraseConfirmationChange,
                label = { Text(strings.confirmNewVaultMasterPassphrase) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = !masterPassphrasesMatch,
                supportingText =
                    if (!masterPassphrasesMatch) {
                        { Text(strings.masterPassphrasesDoNotMatch) }
                    } else {
                        null
                    },
                keyboardOptions = SubmitKeyboardOptions,
                keyboardActions = submitKeyboardActions(model.canRestore, onRestore),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onRestore,
                enabled = model.canRestore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(strings.createLocalVaultFromServer)
            }
        }
    }
}

@Composable
private fun VaultAccessRequestSummary(request: ServerVaultAccessRequest) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                request.fingerprint,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                request.requestId,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                request.expiresAt.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RestoreStatus(value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            value,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}
