package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.SecretFormModel
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.model.SecretType

@Composable
fun AddSecretPanel(
    enabled: Boolean,
    selectedType: SecretType,
    onSelectedTypeChange: (SecretType) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onGeneratePassword: () -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    category: String,
    onCategoryChange: (String) -> Unit,
    provider: String,
    onProviderChange: (String) -> Unit,
    software: String,
    onSoftwareChange: (String) -> Unit,
    account: String,
    onAccountChange: (String) -> Unit,
    expiry: String,
    onExpiryChange: (String) -> Unit,
    structuredFields: Map<String, String>,
    onStructuredFieldChange: (String, String) -> Unit,
    onGenerateApiToken: () -> Unit,
    onGenerateSshKey: () -> Unit,
    onGenerateGpgKey: () -> Unit,
    onGenerateCertificate: () -> Unit,
    onGenerateMfaSecret: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    editing: Boolean,
) {
    val strings = LocalStrings.current
    val spec = SecretFormModel.specForOrNull(selectedType)
    val categoryValue = category.ifBlank { spec?.defaultCategory.orEmpty() }
    val providerValue = provider.ifBlank { spec?.defaultProvider.orEmpty() }
    val softwareValue = software.ifBlank { spec?.defaultSoftware.orEmpty() }
    val canSave =
        if (selectedType == SecretType.LOGIN_PASSWORD) {
            SecretFormModel.canSaveLogin(title, username, password)
        } else {
            spec != null &&
                SecretFormModel.canSaveStructured(
                    title,
                    SecretFormModel.fieldValues(spec, structuredFields),
                )
        }
    DestinationCard(
        modifier = Modifier.submitOnCtrlEnter(enabled && canSave, onSave),
    ) {
        Text(
            if (editing) strings.editSecret else strings.newSecret,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            strings.requiredFieldsMarked,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TypeSelector(selectedType, onSelectedTypeChange, enabled)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                title,
                onTitleChange,
                label = { RequiredFieldLabel(strings.fieldTitle) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            if (selectedType == SecretType.LOGIN_PASSWORD) {
                OutlinedTextField(
                    url,
                    onUrlChange,
                    label = { Text(strings.fieldUrl) },
                    enabled = enabled,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (selectedType == SecretType.LOGIN_PASSWORD) {
            LoginSecretFields(
                enabled = enabled,
                username = username,
                onUsernameChange = onUsernameChange,
                password = password,
                onPasswordChange = onPasswordChange,
                onGeneratePassword = onGeneratePassword,
            )
        } else if (spec != null) {
            if (selectedType == SecretType.API_TOKEN) {
                OutlinedButton(onClick = onGenerateApiToken, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.generateApiToken)
                }
            }
            if (selectedType == SecretType.SSH_KEY) {
                OutlinedButton(onClick = onGenerateSshKey, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.generateSshKey)
                }
            }
            if (selectedType == SecretType.GPG_KEY) {
                OutlinedButton(onClick = onGenerateGpgKey, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.generateGpgKey)
                }
            }
            if (selectedType == SecretType.CERTIFICATE) {
                OutlinedButton(onClick = onGenerateCertificate, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.generateCertificate)
                }
            }
            if (selectedType == SecretType.MFA_SECRET) {
                OutlinedButton(onClick = onGenerateMfaSecret, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Text(strings.generateMfaSecret)
                }
            }
            Text(
                strings.atLeastOneFieldRequired,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            spec.fields.forEach { field ->
                OutlinedTextField(
                    structuredFields[field.name].orEmpty(),
                    { onStructuredFieldChange(field.name, it) },
                    label = { Text(strings.secretFieldLabel(field.name)) },
                    enabled = enabled,
                    visualTransformation =
                        if (field.secret) PasswordVisualTransformation()
                        else androidx.compose.ui.text.input.VisualTransformation.None,
                    singleLine = !field.name.lowercase().contains("key") &&
                        field.name != "certificate",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                categoryValue,
                onCategoryChange,
                label = { Text(strings.fieldCategory) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                providerValue,
                onProviderChange,
                label = { Text(strings.fieldProvider) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                softwareValue,
                onSoftwareChange,
                label = { Text(strings.fieldSoftware) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                account,
                onAccountChange,
                label = { Text(strings.fieldAccount) },
                enabled = enabled,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            expiry,
            onExpiryChange,
            label = { Text(strings.fieldExpiry) },
            enabled = enabled,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSave, enabled = enabled && canSave, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        !enabled -> strings.openVaultFirst
                        editing -> strings.updateSelected
                        else -> strings.saveSecret
                    }
                )
            }
            OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.weight(1f)) { Text(strings.cancelClear) }
        }
    }
}

@Composable
private fun TypeSelector(
    selectedType: SecretType,
    onSelectedTypeChange: (SecretType) -> Unit,
    enabled: Boolean,
) {
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SecretFormModel.supportedTypes.chunked(4).forEach { rowTypes ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowTypes.forEach { type ->
                    KeysteadChoiceChip(
                        selected = type == selectedType,
                        onClick = { onSelectedTypeChange(type) },
                        enabled = enabled,
                        label = {
                            Text(
                                strings.secretTypeLabel(type),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - rowTypes.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun LoginSecretFields(
    enabled: Boolean,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onGeneratePassword: () -> Unit,
) {
    val strings = LocalStrings.current
    OutlinedTextField(
        username,
        onUsernameChange,
        label = { Text(strings.fieldUsername) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            password,
            onPasswordChange,
            label = { RequiredFieldLabel(strings.fieldPassword) },
            enabled = enabled,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onGeneratePassword, enabled = enabled, modifier = Modifier.width(128.dp)) {
            Text(strings.generate)
        }
    }
}

@Composable
private fun RequiredFieldLabel(text: String) {
    val errorColor = MaterialTheme.colorScheme.error
    Text(
        buildAnnotatedString {
            append(text)
            append(" ")
            withStyle(SpanStyle(color = errorColor)) {
                append("*")
            }
        },
    )
}
