package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import top.focess.keystead.client.PasswordGeneratorOptions
import top.focess.keystead.client.STANDARD_PASSWORD_SYMBOLS
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun PasswordGeneratorDialog(
    onDismiss: () -> Unit,
    onGenerate: (PasswordGeneratorOptions) -> Unit,
) {
    val strings = LocalStrings.current
    val colors = MaterialTheme.colorScheme
    var options by remember { mutableStateOf(PasswordGeneratorOptions()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.passwordGeneratorTitle) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.passwordGeneratorLength(options.length))
                Slider(
                    value = options.length.toFloat(),
                    onValueChange = { options = options.copy(length = it.roundToInt()) },
                    valueRange = 8f..64f,
                    steps = 55,
                    colors =
                        SliderDefaults.colors(
                            thumbColor = colors.secondary,
                            activeTrackColor = colors.secondary,
                            activeTickColor = colors.onSecondary,
                            inactiveTrackColor = colors.secondaryContainer,
                            inactiveTickColor = colors.secondary,
                        ),
                )
                GeneratorToggle(strings.passwordGeneratorUppercase, options.uppercase) {
                    options = options.copy(uppercase = it)
                }
                GeneratorToggle(strings.passwordGeneratorLowercase, options.lowercase) {
                    options = options.copy(lowercase = it)
                }
                GeneratorToggle(strings.passwordGeneratorDigits, options.digits) {
                    options = options.copy(digits = it)
                }
                GeneratorToggle(strings.passwordGeneratorSymbols, options.symbols) {
                    options = options.copy(symbols = it)
                }
                if (options.symbols) {
                    Text(
                        strings.passwordGeneratorSelectSymbols,
                        style = MaterialTheme.typography.labelLarge,
                    )
                    STANDARD_PASSWORD_SYMBOLS.chunked(8).forEach { rowSymbols ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            rowSymbols.forEach { symbol ->
                                FilterChip(
                                    selected = symbol in options.allowedSymbols,
                                    onClick = {
                                        options =
                                            options.copy(
                                                allowedSymbols =
                                                    if (symbol in options.allowedSymbols) {
                                                        options.allowedSymbols - symbol
                                                    } else {
                                                        options.allowedSymbols + symbol
                                                    },
                                            )
                                    },
                                    label = { Text(symbol.toString()) },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surfaceVariant,
                                            labelColor = colors.onSurfaceVariant,
                                            selectedContainerColor = colors.secondaryContainer,
                                            selectedLabelColor = colors.onSecondaryContainer,
                                        ),
                                )
                            }
                        }
                    }
                }
                GeneratorToggle(strings.passwordGeneratorAvoidAmbiguous, options.avoidAmbiguous) {
                    options = options.copy(avoidAmbiguous = it)
                }
                Text(
                    strings.passwordGeneratorReplaceNotice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!options.isValid) {
                    Text(
                        strings.passwordGeneratorInvalidSelection,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onGenerate(options) },
                enabled = options.isValid,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = colors.secondary,
                        contentColor = colors.onSecondary,
                    ),
            ) {
                Text(strings.generate)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = colors.secondary),
            ) {
                Text(strings.cancel)
            }
        },
        containerColor = colors.surface,
        iconContentColor = colors.secondary,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
    )
}

@Composable
private fun GeneratorToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                CheckboxDefaults.colors(
                    checkedColor = colors.secondary,
                    checkmarkColor = colors.onSecondary,
                    uncheckedColor = colors.outline,
                ),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
