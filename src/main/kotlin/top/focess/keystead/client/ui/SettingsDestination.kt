package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.SettingsPresentation
import top.focess.keystead.client.i18n.AppLocale
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun SettingsPanel(
    vaultFile: String,
    presentation: SettingsPresentation,
    locale: AppLocale,
    onLocaleChange: (AppLocale) -> Unit,
    onDeleteVaultFile: () -> Unit,
) {
    val strings = LocalStrings.current
    DestinationCard {
        SectionHeader(strings.settingsTitle)
        Text(
            strings.settingsIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        GroupLabel(strings.groupLanguage)
        Text(
            strings.languageHelp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            AppLocale.entries.forEach { option ->
                KeysteadChoiceChip(
                    selected = locale == option,
                    onClick = { onLocaleChange(option) },
                    label = { Text(option.nativeName) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel(strings.groupVaultFile)
        Text(
            strings.sessionVaultFile(vaultFile),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            strings.deleteVaultFileHelp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(
            onClick = onDeleteVaultFile,
            enabled = presentation.canDeleteVaultFile,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
        ) {
            Text(strings.deleteVaultFile)
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel(strings.groupAbout)
        Text(
            strings.aboutText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
