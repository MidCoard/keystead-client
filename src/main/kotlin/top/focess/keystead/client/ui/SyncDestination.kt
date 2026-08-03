package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.ConflictAssessment
import top.focess.keystead.client.PersonalVaultRecordInventory
import top.focess.keystead.client.RecordComparisonEntry
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.RemoteRecordHistoryEntry
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.SyncFormModel
import top.focess.keystead.client.SyncRecordChoice
import top.focess.keystead.client.SyncRecordSelection
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.model.SecretType

@Composable
internal fun SyncPanel(
    vaultOpen: Boolean,
    authenticated: Boolean,
    serverAvailability: ServerAvailability,
    onCheckServer: () -> Unit,
    onPull: () -> Unit,
    onUploadSelected: (Set<String>) -> Unit,
    onRequestRemoveSelected: (Set<String>) -> Unit,
    onRefreshRecords: () -> Unit,
    onPullAndRetry: () -> Unit,
    onDismissConflict: () -> Unit,
    conflictAssessment: ConflictAssessment?,
    recordInventory: PersonalVaultRecordInventory?,
    localRecordTitles: Map<String, String>,
) {
    val strings = LocalStrings.current
    val serverAvailable = serverAvailability.isOnline
    val serverReady = SyncFormModel.canUseServer(authenticated, serverAvailable)

    DestinationCard {
        SectionHeader(strings.serverSync)
        ConnectedAvailabilityNotice(serverAvailability, onCheckServer)

        GroupLabel(strings.groupVaultsAndSync)
        Button(
            onClick = onPull,
            enabled = vaultOpen && serverReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.pull)
        }
        conflictAssessment?.let { assessment ->
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        assessment.title,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        assessment.message,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    assessment.warning?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (assessment.canAutoRecover) {
                            Button(
                                onClick = onPullAndRetry,
                                enabled = serverAvailable,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.pullAndRetry)
                            }
                        } else {
                            Button(
                                onClick = onPull,
                                enabled = serverAvailable,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(strings.pullLatest)
                            }
                        }
                        OutlinedButton(
                            onClick = onDismissConflict,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(strings.dismiss)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel(strings.recordInventory)
        OutlinedButton(
            onClick = onRefreshRecords,
            enabled = serverReady,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.refreshRecordInventory)
        }
        recordInventory?.let { inventory ->
            RecordInventory(
                inventory = inventory,
                actionsEnabled = vaultOpen && serverReady,
                localRecordTitles = localRecordTitles,
                onUploadSelected = onUploadSelected,
                onRequestRemoveSelected = onRequestRemoveSelected,
            )
        }
    }
}

@Composable
private fun RecordInventory(
    inventory: PersonalVaultRecordInventory,
    actionsEnabled: Boolean,
    localRecordTitles: Map<String, String>,
    onUploadSelected: (Set<String>) -> Unit,
    onRequestRemoveSelected: (Set<String>) -> Unit,
) {
    val strings = LocalStrings.current
    val choices =
        inventory.comparisons.orEmpty().map { entry ->
            SyncRecordChoice(
                secretId = entry.secretId,
                canUpload = entry.localRevision != null,
                canRemoveFromServer = entry.serverRevision != null,
            )
        }
    var selection by remember(inventory.localFingerprint, inventory.serverFingerprint) {
        mutableStateOf(SyncRecordSelection())
    }
    LaunchedEffect(choices) {
        selection = selection.reconcile(choices)
    }
    val currentRemoteCount = inventory.remoteHistory.map { it.recordHash }.distinct().size
    if (inventory.remoteHistory.isEmpty()) {
        Text(
            strings.recordInventoryEmpty,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    strings.remoteRecordSummary(inventory.remoteHistory.size, currentRemoteCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
                inventory.serverFingerprint?.let {
                    Text(
                        it,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                if (inventory.invalidRemoteRecords > 0) {
                    Text(
                        strings.hashInvalid,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (inventory.vaultMismatch) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text(
                strings.personalVaultMismatch(
                    inventory.serverFingerprint.orEmpty(),
                    inventory.localFingerprint.orEmpty(),
                ),
                modifier = Modifier.padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else if (inventory.comparisons == null) {
        Text(
            strings.unlockVaultToCompare,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        GroupLabel(strings.currentRecordComparison)
        Text(
            strings.recordSelectionHelp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { selection = selection.selectAll(choices) },
                enabled = choices.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.selectAllRecords)
            }
            OutlinedButton(
                onClick = { selection = selection.clear() },
                enabled = selection.selectedIds.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.clearRecordSelection)
            }
        }
        val uploadableIds = selection.uploadableIds(choices)
        val removableIds = selection.removableIds(choices)
        Text(
            strings.selectedRecordSummary(
                selected = selection.selectedIds.size,
                uploadable = uploadableIds.size,
                removable = removableIds.size,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { onUploadSelected(uploadableIds) },
                enabled = actionsEnabled && uploadableIds.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.uploadSelectedRecords(uploadableIds.size))
            }
            OutlinedButton(
                onClick = { onRequestRemoveSelected(removableIds) },
                enabled = actionsEnabled && removableIds.isNotEmpty(),
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text(strings.removeSelectedServerCopies(removableIds.size))
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(inventory.comparisons, key = { it.secretId }) { entry ->
                ComparisonRow(
                    entry = entry,
                    localTitle = localRecordTitles[entry.secretId],
                    selected = entry.secretId in selection.selectedIds,
                    onSelectedChange = { selection = selection.toggle(entry.secretId) },
                )
            }
        }
    }

    if (inventory.remoteHistory.isNotEmpty()) {
        GroupLabel(strings.serverRecordHistory)
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(inventory.remoteHistory, key = { it.serverSequence }) { entry ->
                HistoryRow(entry)
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    entry: RecordComparisonEntry,
    localTitle: String?,
    selected: Boolean,
    onSelectedChange: () -> Unit,
) {
    val strings = LocalStrings.current
    val accent =
        when (entry.status) {
            RecordComparisonStatus.MATCHED -> MaterialTheme.colorScheme.primary
            RecordComparisonStatus.LOCAL_ONLY,
            RecordComparisonStatus.SERVER_ONLY,
            RecordComparisonStatus.LOCAL_NEWER,
            RecordComparisonStatus.SERVER_NEWER,
            -> MaterialTheme.colorScheme.tertiary
            RecordComparisonStatus.HASH_MISMATCH -> MaterialTheme.colorScheme.error
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(checked = selected, onCheckedChange = { onSelectedChange() })
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        localTitle ?: secretTypeLabel(entry.secretType),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (localTitle != null) {
                        Text(
                            secretTypeLabel(entry.secretType),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
                Text(
                    strings.recordComparisonStatus(entry.status),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                strings.recordRevisions(entry.localRevision, entry.serverRevision),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                strings.recordDeletionStates(entry.localDeleted, entry.serverDeleted),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                strings.serverSequence(entry.serverSequence),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            HashEvidenceBlock {
                HashEvidenceRow(strings.recordIdentifierHash, entry.recordHash)
                HashEvidenceRow(strings.localContentHash, entry.localContentHash)
                HashEvidenceRow(strings.serverComputedContentHash, entry.serverContentHash)
                HashEvidenceRow(
                    strings.serverAdvertisedContentHash,
                    entry.serverAdvertisedContentHash,
                )
                HashEvidenceRow(
                    strings.localProfileCiphertextHash,
                    entry.localProfileCiphertextHash,
                )
                HashEvidenceRow(
                    strings.serverProfileCiphertextHash,
                    entry.serverProfileCiphertextHash,
                )
                HashEvidenceRow(
                    strings.localEnvelopeCiphertextHash,
                    entry.localEnvelopeCiphertextHash,
                )
                HashEvidenceRow(
                    strings.serverEnvelopeCiphertextHash,
                    entry.serverEnvelopeCiphertextHash,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: RemoteRecordHistoryEntry) {
    val strings = LocalStrings.current
    val accent =
        if (entry.hashValid) MaterialTheme.colorScheme.outline
        else MaterialTheme.colorScheme.error
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    secretTypeLabel(entry.secretType),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (entry.hashValid) strings.hashVerified else strings.hashInvalid,
                    color = accent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                strings.serverRecordMetadata(
                    secretTypeLabel(entry.secretType),
                    entry.revision,
                    entry.deleted,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                strings.serverSequence(entry.serverSequence),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            HashEvidenceBlock {
                HashEvidenceRow(strings.recordIdentifierHash, entry.recordHash)
                HashEvidenceRow(
                    strings.serverAdvertisedContentHash,
                    entry.advertisedContentHash,
                )
                HashEvidenceRow(
                    strings.serverComputedContentHash,
                    entry.computedContentHash,
                )
                HashEvidenceRow(
                    strings.serverProfileCiphertextHash,
                    entry.profileCiphertextHash,
                )
                HashEvidenceRow(
                    strings.serverEnvelopeCiphertextHash,
                    entry.envelopeCiphertextHash,
                )
            }
            Text(
                entry.createdAt.toString(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun HashEvidenceBlock(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.46f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        SelectionContainer {
            Column(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun HashEvidenceRow(label: String, value: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value ?: "—",
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun secretTypeLabel(value: String): String {
    val strings = LocalStrings.current
    val type = runCatching { SecretType.valueOf(value) }.getOrNull()
    return type?.let(strings::secretTypeLabel) ?: value
}
