package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.PersonalVaultRecord
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.i18n.Strings
import top.focess.keystead.service.EncryptedSyncRecord

internal data class SyncFieldDiff(val name: String, val local: String, val server: String, val changed: Boolean)

internal data class SyncComparisonItem(
    val secretId: String,
    val title: String,
    val secretType: String,
    val status: RecordComparisonStatus,
    val fields: List<SyncFieldDiff>,
    val serverRecord: EncryptedSyncRecord,
)

internal fun PersonalVaultRecord.toEncryptedSyncRecord(): EncryptedSyncRecord =
    EncryptedSyncRecord(
        fingerprint,
        secretId,
        revision,
        secretType,
        encryptedProfile,
        envelope,
        deleted,
        contentKey,
    )

internal fun buildSyncDiff(local: Map<String, String>, server: Map<String, String>): List<SyncFieldDiff> {
    val names = (local.keys + server.keys).sorted()
    return names.map { name ->
        val l = local[name].orEmpty()
        val s = server[name].orEmpty()
        SyncFieldDiff(name, l, s, l != s)
    }
}

@Composable
internal fun SyncCompareDialog(
    items: List<SyncComparisonItem>,
    accept: Map<String, Boolean>,
    onAcceptChange: (String, Boolean) -> Unit,
    onAcceptAll: () -> Unit,
    onAcceptSelected: () -> Unit,
    onCancel: () -> Unit,
    strings: Strings,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(strings.compareSyncTitle) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (items.isEmpty()) {
                    Text(strings.compareSyncEmpty)
                }
                items.forEach { item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Checkbox(
                                checked = accept[item.secretId] ?: false,
                                onCheckedChange = { onAcceptChange(item.secretId, it) },
                            )
                            Column(
                                modifier = Modifier.padding(start = 4.dp).fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    "${item.title}  [${item.secretType}]",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                item.fields.forEach { f ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            f.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            f.local.ifBlank { "—" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (f.changed) {
                                                    MaterialTheme.colorScheme.error
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            modifier = Modifier.weight(1.2f),
                                        )
                                        Text("→", style = MaterialTheme.typography.labelSmall)
                                        Text(
                                            f.server.ifBlank { "—" },
                                            style = MaterialTheme.typography.labelSmall,
                                            color =
                                                if (f.changed) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                            modifier = Modifier.weight(1.2f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onAcceptSelected) {
                Text(strings.compareAcceptSelected)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(onClick = onAcceptAll) {
                    Text(strings.compareAcceptAll)
                }
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text(strings.cancel)
                }
            }
        },
    )
}
