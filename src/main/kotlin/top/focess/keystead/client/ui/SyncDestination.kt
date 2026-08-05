package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.ConflictAssessment
import top.focess.keystead.client.PersonalVaultRecordInventory
import top.focess.keystead.client.RecordComparisonEntry
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.SyncFormModel
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
        if (!authenticated) {
            Text(
                strings.syncNotSignedInHelp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        CapabilityGroupLabel(strings.groupVaultsAndSync, strings.loginRequired, serverReady)
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
        CapabilityGroupLabel(strings.recordInventory, strings.loginRequired, serverReady)
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
                serverActionsEnabled = serverReady,
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
    serverActionsEnabled: Boolean,
    localRecordTitles: Map<String, String>,
    onUploadSelected: (Set<String>) -> Unit,
    onRequestRemoveSelected: (Set<String>) -> Unit,
) {
    val strings = LocalStrings.current
    val currentRemoteCount = inventory.remoteHistory.map { it.recordHash }.distinct().size
    if (inventory.remoteHistory.isNotEmpty()) {
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
    }
    val comparisons = inventory.comparisons
    if (comparisons == null) {
        Text(
            strings.unlockVaultToCompare,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    } else {
        comparisons.forEach { entry ->
            UnifiedRecordRow(
                entry = entry,
                localTitle = localRecordTitles[entry.secretId],
                otherVault = inventory.vaultMismatch && entry.serverRevision != null,
                uploadEnabled = actionsEnabled && !inventory.vaultMismatch,
                removeEnabled = serverActionsEnabled,
                onUpload = { onUploadSelected(setOf(entry.secretId)) },
                onRemoveServerCopy = { onRequestRemoveSelected(setOf(entry.secretId)) },
            )
        }
    }
}

@Composable
private fun UnifiedRecordRow(
    entry: RecordComparisonEntry,
    localTitle: String?,
    otherVault: Boolean,
    uploadEnabled: Boolean,
    removeEnabled: Boolean,
    onUpload: () -> Unit,
    onRemoveServerCopy: () -> Unit,
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
                if (entry.localRevision != null) {
                    SideBadge(strings.localBadge, MaterialTheme.colorScheme.primary)
                }
                if (otherVault) {
                    SideBadge(strings.otherVaultBadge, MaterialTheme.colorScheme.error)
                } else if (entry.serverRevision != null) {
                    SideBadge(strings.serverBadge, MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    strings.recordComparisonStatus(entry.status),
                    color = accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entry.localRevision != null) {
                    Button(
                        onClick = onUpload,
                        // Records whose stable content already matches the latest server
                        // event are not offered for upload; the server would no-op anyway.
                        enabled = uploadEnabled && entry.status != RecordComparisonStatus.MATCHED,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(strings.uploadRecord)
                    }
                }
                if (entry.serverRevision != null) {
                    OutlinedButton(
                        onClick = onRemoveServerCopy,
                        enabled = removeEnabled,
                        modifier = Modifier.weight(1f),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(strings.removeServerCopy)
                    }
                }
            }
            HashEvidenceBlock {
                HashEvidenceRow(strings.recordIdentifierHash, entry.recordHash)
                if (entry.localRevision != null) {
                    HashEvidenceRow(
                        "${strings.localBadge} · ${strings.revisionLabel}",
                        entry.localRevision.toString(),
                    )
                    HashEvidenceRow(
                        "${strings.localBadge} · ${strings.recordStateLabel}",
                        strings.recordStateValue(entry.localDeleted),
                    )
                    HashEvidenceRow(
                        strings.localProfileCiphertextHash,
                        entry.localProfileCiphertextHash,
                    )
                    HashEvidenceRow(
                        strings.localEnvelopeCiphertextHash,
                        entry.localEnvelopeCiphertextHash,
                    )
                }
                if (entry.serverRevision != null) {
                    HashEvidenceRow(
                        "${strings.serverBadge} · ${strings.revisionLabel}",
                        entry.serverRevision.toString(),
                    )
                    HashEvidenceRow(
                        "${strings.serverBadge} · ${strings.recordStateLabel}",
                        strings.recordStateValue(entry.serverDeleted),
                    )
                    Text(
                        strings.serverSequence(entry.serverSequence),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    HashEvidenceRow(
                        strings.serverAdvertisedContentHash,
                        entry.serverAdvertisedContentHash,
                    )
                    HashEvidenceRow(strings.serverComputedContentHash, entry.serverContentHash)
                    HashEvidenceRow(
                        strings.serverProfileCiphertextHash,
                        entry.serverProfileCiphertextHash,
                    )
                    HashEvidenceRow(
                        strings.serverEnvelopeCiphertextHash,
                        entry.serverEnvelopeCiphertextHash,
                    )
                }
            }
        }
    }
}

@Composable
private fun SideBadge(label: String, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = color.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f)),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
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
