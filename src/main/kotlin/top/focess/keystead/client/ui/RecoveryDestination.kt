package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.i18n.LocalStrings

internal enum class RecoveryMethod {
    PORTABLE_BACKUP,
    SERVER,
}

internal object RecoveryHubPresentation {
    val methods: List<RecoveryMethod> =
        listOf(RecoveryMethod.PORTABLE_BACKUP, RecoveryMethod.SERVER)
    val defaultMethod: RecoveryMethod = RecoveryMethod.PORTABLE_BACKUP
}

@Composable
internal fun RecoveryHub(
    method: RecoveryMethod,
    onMethodChange: (RecoveryMethod) -> Unit,
    portableBackupContent: @Composable () -> Unit,
    serverRestoreContent: @Composable () -> Unit,
) {
    val strings = LocalStrings.current
    DestinationCard {
        SectionHeader(strings.destinationLabel(KeysteadDestination.RECOVERY))
        Text(
            strings.recoveryHubIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            RecoveryHubPresentation.methods.forEach { candidate ->
                KeysteadChoiceChip(
                    selected = method == candidate,
                    onClick = { onMethodChange(candidate) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (candidate) {
                            RecoveryMethod.PORTABLE_BACKUP -> strings.recoverFromBackup
                            RecoveryMethod.SERVER -> strings.recoverFromServer
                        },
                    )
                }
            }
        }
    }
    when (method) {
        RecoveryMethod.PORTABLE_BACKUP -> portableBackupContent()
        RecoveryMethod.SERVER -> serverRestoreContent()
    }
}
