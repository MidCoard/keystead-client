package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.BackupFormModel
import top.focess.keystead.client.BackupRestoreSelection
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun BackupPanel(
    vaultOpen: Boolean,
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    backupPasswordConfirmation: String,
    onBackupPasswordConfirmationChange: (String) -> Unit,
    onExportBackup: () -> Unit,
) {
    val strings = LocalStrings.current
    val passwordsMatch =
        backupPasswordConfirmation.isEmpty() || backupPassword == backupPasswordConfirmation
    val exportReady =
        BackupFormModel.canExport(vaultOpen, backupPassword, backupPasswordConfirmation)
    DestinationCard {
        SectionHeader(strings.destinationLabel(KeysteadDestination.BACKUP))
        Text(
            strings.fullBackupIntro,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            strings.createPortableBackupHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!vaultOpen) {
            Text(
                strings.openVaultToCreateBackup,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedTextField(
            value = backupPassword,
            onValueChange = onBackupPasswordChange,
            label = { Text(strings.backupPassword) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = backupPasswordConfirmation,
            onValueChange = onBackupPasswordConfirmationChange,
            label = { Text(strings.confirmBackupPassword) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = !passwordsMatch,
            supportingText =
                if (!passwordsMatch) {
                    { Text(strings.backupPasswordsDoNotMatch) }
                } else {
                    null
                },
            keyboardOptions = SubmitKeyboardOptions,
            keyboardActions = submitKeyboardActions(exportReady, onExportBackup),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onExportBackup,
            enabled = exportReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.exportBackup)
        }
    }
}

@Composable
internal fun PortableBackupRestorePanel(
    backupPassword: String,
    onBackupPasswordChange: (String) -> Unit,
    backupPasswordConfirmation: String,
    onBackupPasswordConfirmationChange: (String) -> Unit,
    newMasterPassphrase: String,
    onNewMasterPassphraseChange: (String) -> Unit,
    newMasterPassphraseConfirmation: String,
    onNewMasterPassphraseConfirmationChange: (String) -> Unit,
    restoreSelection: BackupRestoreSelection,
    onChooseBackupSource: () -> Unit,
    onChooseRestoreTarget: () -> Unit,
    onReviewRestore: () -> Unit,
) {
    val strings = LocalStrings.current
    val backupPasswordsMatch =
        backupPasswordConfirmation.isEmpty() || backupPassword == backupPasswordConfirmation
    val masterPassphrasesMatch =
        newMasterPassphraseConfirmation.isEmpty() ||
            newMasterPassphrase == newMasterPassphraseConfirmation
    val reviewReady =
        BackupFormModel.canReviewRestore(
            restoreSelection,
            backupPassword,
            backupPasswordConfirmation,
            newMasterPassphrase,
            newMasterPassphraseConfirmation,
        )

    DestinationCard {
        SectionHeader(strings.restorePortableBackup)
        Text(
            strings.restorePortableBackupHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = restoreSelection.source?.toString().orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(strings.backupSourceFile) },
                isError = restoreSelection.source != null && !restoreSelection.sourceReady,
                supportingText =
                    if (restoreSelection.source != null && !restoreSelection.sourceReady) {
                        { Text(strings.backupSourceInvalid) }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onChooseBackupSource) {
                Text(strings.chooseBackupSource)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = restoreSelection.target?.toString().orEmpty(),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(strings.restoreTargetVault) },
                isError = restoreSelection.target != null && !restoreSelection.targetReady,
                supportingText =
                    if (restoreSelection.target != null && !restoreSelection.targetReady) {
                        { Text(strings.restoreTargetMustBeNew) }
                    } else {
                        null
                    },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onChooseRestoreTarget) {
                Text(strings.chooseRestoreTarget)
            }
        }
        OutlinedTextField(
            value = backupPassword,
            onValueChange = onBackupPasswordChange,
            label = { Text(strings.backupPassword) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = backupPasswordConfirmation,
            onValueChange = onBackupPasswordConfirmationChange,
            label = { Text(strings.confirmBackupPassword) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            isError = !backupPasswordsMatch,
            supportingText =
                if (!backupPasswordsMatch) {
                    { Text(strings.backupPasswordsDoNotMatch) }
                } else {
                    null
                },
            modifier = Modifier.fillMaxWidth(),
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
            keyboardActions = submitKeyboardActions(reviewReady, onReviewRestore),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onReviewRestore,
            enabled = reviewReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.reviewBackupRestore)
        }
    }
}
