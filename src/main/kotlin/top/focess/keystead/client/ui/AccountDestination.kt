package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.AccountAuthMode
import top.focess.keystead.client.AccountAuthPresentation
import top.focess.keystead.client.AccountAuthUiState
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.SyncFormModel
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun AccountPanel(
    authenticated: Boolean,
    serverAvailability: ServerAvailability,
    onCheckServer: () -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordConfirmation: String,
    onPasswordConfirmationChange: (String) -> Unit,
    authState: AccountAuthUiState,
    onAuthModeChange: (AccountAuthMode) -> Unit,
    onLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    val strings = LocalStrings.current
    val serverAvailable = serverAvailability.isOnline
    val connectionEditable = SyncFormModel.canEditConnection(authenticated, serverAvailable)
    val submitReady =
        AccountAuthPresentation.canSubmit(
            mode = authState.mode,
            serverUrl = serverUrl,
            username = username,
            password = password,
            passwordConfirmation = passwordConfirmation,
            serverAvailable = serverAvailable,
            authenticated = authenticated,
        )
    val serverReady = SyncFormModel.canUseServer(authenticated, serverAvailable)

    DestinationCard {
        SectionHeader(strings.destinationLabel(KeysteadDestination.ACCOUNT))
        ConnectedAvailabilityNotice(serverAvailability, onCheckServer)

        if (!authenticated) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AccountAuthPresentation.modes.forEach { mode ->
                    KeysteadChoiceChip(
                        selected = authState.mode == mode,
                        onClick = { onAuthModeChange(mode) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            when (mode) {
                                AccountAuthMode.SIGN_IN -> strings.signIn
                                AccountAuthMode.CREATE_ACCOUNT -> strings.createAccount
                            },
                        )
                    }
                }
            }
            Text(
                if (authState.mode == AccountAuthMode.SIGN_IN) {
                    strings.accountSignInIntro
                } else {
                    strings.accountCreateIntro
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                serverUrl,
                onServerUrlChange,
                label = { Text(strings.serverUrl) },
                enabled = connectionEditable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                username,
                onUsernameChange,
                label = { Text(strings.user) },
                enabled = connectionEditable,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password,
                onPasswordChange,
                label = { Text(strings.serverPassword) },
                enabled = connectionEditable,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                isError =
                    authState.mode == AccountAuthMode.CREATE_ACCOUNT &&
                        password.isNotEmpty() &&
                        !AccountAuthPresentation.registrationPasswordMeetsRequirements(password),
                supportingText =
                    if (authState.mode == AccountAuthMode.CREATE_ACCOUNT) {
                        { Text(strings.serverPasswordRequirement) }
                    } else {
                        null
                    },
                keyboardOptions = SubmitKeyboardOptions,
                keyboardActions =
                    submitKeyboardActions(
                        submitReady && authState.mode == AccountAuthMode.SIGN_IN,
                        onLogin,
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (authState.mode == AccountAuthMode.CREATE_ACCOUNT) {
                OutlinedTextField(
                    passwordConfirmation,
                    onPasswordConfirmationChange,
                    label = { Text(strings.confirmServerPassword) },
                    enabled = connectionEditable,
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = passwordConfirmation.isNotEmpty() && password != passwordConfirmation,
                    keyboardOptions = SubmitKeyboardOptions,
                    keyboardActions = submitKeyboardActions(submitReady, onCreateAccount),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            authState.failure?.let { failure ->
                AccountAuthFailureNotice(
                    title =
                        if (authState.mode == AccountAuthMode.SIGN_IN) {
                            strings.signInFailed
                        } else {
                            strings.createAccountFailed
                        },
                    message = failure,
                )
            }
            Button(
                onClick =
                    if (authState.mode == AccountAuthMode.SIGN_IN) {
                        onLogin
                    } else {
                        onCreateAccount
                    },
                enabled = submitReady,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (authState.mode == AccountAuthMode.SIGN_IN) {
                        strings.signIn
                    } else {
                        strings.createAccount
                    },
                )
            }
        } else if (AccountAuthPresentation.showSessionManagement(authenticated)) {
            GroupLabel(strings.groupServerSignIn)
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        strings.signedInAs(username),
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        serverUrl,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRefresh, enabled = serverReady, modifier = Modifier.weight(1f)) {
                    Text(strings.refreshSession)
                }
                OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                    Text(strings.signOut)
                }
            }
            OutlinedButton(onClick = onLogoutAll, enabled = serverReady, modifier = Modifier.fillMaxWidth()) {
                Text(strings.signOutEverywhere)
            }
        }
    }
}

@Composable
private fun AccountAuthFailureNotice(title: String, message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
