package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.SecretFormModel
import top.focess.keystead.client.LoginUsernameSuggestions
import top.focess.keystead.client.PasswordBreachResult
import top.focess.keystead.client.PasswordStrength
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.model.SecretType

internal object UsernameSuggestionMenuPresentation {
    fun widthDp(fieldWidthPixels: Int, density: Float): Float {
        require(density > 0f)
        return fieldWidthPixels / density
    }
}

@Composable
fun AddSecretPanel(
    enabled: Boolean,
    selectedType: SecretType,
    onSelectedTypeChange: (SecretType) -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    usernameSuggestions: List<String>,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    passwordStrength: PasswordStrength,
    passwordBreachResult: PasswordBreachResult,
    onCheckPassword: () -> Unit,
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
                usernameSuggestions = usernameSuggestions,
                password = password,
                onPasswordChange = onPasswordChange,
                passwordVisible = passwordVisible,
                onPasswordVisibilityChange = onPasswordVisibilityChange,
                passwordStrength = passwordStrength,
                passwordBreachResult = passwordBreachResult,
                onCheckPassword = onCheckPassword,
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
    usernameSuggestions: List<String>,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onPasswordVisibilityChange: (Boolean) -> Unit,
    passwordStrength: PasswordStrength,
    passwordBreachResult: PasswordBreachResult,
    onCheckPassword: () -> Unit,
    onGeneratePassword: () -> Unit,
) {
    val strings = LocalStrings.current
    var suggestionsExpanded by remember { mutableStateOf(false) }
    var usernameFieldWidthPixels by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current.density
    val matchingUsernames =
        remember(usernameSuggestions, username) {
            LoginUsernameSuggestions.match(usernameSuggestions, username)
                .filterNot { it.equals(username, ignoreCase = true) }
        }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            username,
            onValueChange = {
                onUsernameChange(it)
                suggestionsExpanded = true
            },
            label = { Text(strings.fieldUsername) },
            enabled = enabled,
            singleLine = true,
            modifier =
                Modifier.fillMaxWidth().onGloballyPositioned {
                    usernameFieldWidthPixels = it.size.width
                }.onFocusChanged {
                    if (it.isFocused) suggestionsExpanded = true
                },
        )
        DropdownMenu(
            expanded = enabled && suggestionsExpanded && matchingUsernames.isNotEmpty(),
            onDismissRequest = { suggestionsExpanded = false },
            modifier =
                if (usernameFieldWidthPixels > 0) {
                    Modifier.width(
                        UsernameSuggestionMenuPresentation
                            .widthDp(usernameFieldWidthPixels, density)
                            .dp,
                    )
                } else {
                    Modifier
                },
        ) {
            matchingUsernames.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onUsernameChange(suggestion)
                        suggestionsExpanded = false
                    },
                )
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        OutlinedTextField(
            password,
            onPasswordChange,
            label = { RequiredFieldLabel(strings.fieldPassword) },
            enabled = enabled,
            visualTransformation =
                if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                PasswordVisibilityButton(
                    visible = passwordVisible,
                    onClick = { onPasswordVisibilityChange(!passwordVisible) },
                )
            },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onGeneratePassword, enabled = enabled, modifier = Modifier.width(128.dp)) {
            Text(strings.generate)
        }
    }
    Text(
        when (passwordStrength) {
            PasswordStrength.WEAK -> strings.passwordStrengthWeak
            PasswordStrength.FAIR -> strings.passwordStrengthFair
            PasswordStrength.STRONG -> strings.passwordStrengthStrong
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(
        onClick = onCheckPassword,
        enabled = enabled && password.isNotEmpty() && passwordBreachResult != PasswordBreachResult.Checking,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (passwordBreachResult == PasswordBreachResult.Checking) {
                strings.checkingPassword
            } else {
                strings.checkBreachedPassword
            }
        )
    }
    val breachMessage =
        when (passwordBreachResult) {
            PasswordBreachResult.NotChecked -> null
            PasswordBreachResult.Checking -> strings.checkingPassword
            PasswordBreachResult.NotFound -> strings.passwordNotFoundInBreaches
            is PasswordBreachResult.Found -> strings.passwordFoundInBreaches(passwordBreachResult.count)
            PasswordBreachResult.Failed -> strings.passwordBreachCheckUnavailable
        }
    breachMessage?.let {
        Text(
            it,
            style = MaterialTheme.typography.bodySmall,
            color =
                if (passwordBreachResult is PasswordBreachResult.Found) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
    Text(
        strings.passwordBreachPrivacy,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PasswordVisibilityButton(visible: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current
    TextButton(onClick = onClick) {
        Text(if (visible) strings.hide else strings.reveal)
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
