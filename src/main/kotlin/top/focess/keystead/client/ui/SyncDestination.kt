package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import top.focess.keystead.client.ConflictAssessment
import top.focess.keystead.client.SyncFormModel

@Composable
internal fun SyncPanel(
    vaultOpen: Boolean,
    authenticated: Boolean,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    deviceId: String,
    onDeviceIdChange: (String) -> Unit,
    devicePassphrase: String,
    onDevicePassphraseChange: (String) -> Unit,
    devicePassphraseRequired: Boolean,
    identityLoaded: Boolean,
    identityName: String,
    deviceRegistered: Boolean,
    deviceTrustLabel: String,
    onLogin: () -> Unit,
    onDeviceLogin: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onLogoutAll: () -> Unit,
    onRegisterUser: () -> Unit,
    onCreateServerVault: () -> Unit,
    onListServerVaults: () -> Unit,
    onLoadIdentity: () -> Unit,
    onUnloadIdentity: () -> Unit,
    onEnrollDevice: () -> Unit,
    onRevokeDevice: () -> Unit,
    onPublishKeyPackage: () -> Unit,
    onPush: () -> Unit,
    onPull: () -> Unit,
    onPullAndRetry: () -> Unit,
    onDismissConflict: () -> Unit,
    conflictAssessment: ConflictAssessment?,
    onOpenProvisioned: () -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
) {
    val loginReady = SyncFormModel.canLogin(serverUrl, username, password)
    val deviceLoginReady =
        SyncFormModel.canLoginWithDevice(serverUrl, username, password, identityLoaded)
    val serverReady = SyncFormModel.canUseServer(authenticated)
    val registrationReady = SyncFormModel.canRegisterUser(serverUrl, username, password)
    val serverVaultReady = SyncFormModel.canCreateServerVault(vaultOpen, authenticated)
    val identityReady = identityLoaded
    val identityInputReady =
        deviceId.isNotBlank() &&
            (!devicePassphraseRequired || devicePassphrase.isNotBlank())
    val enrollmentReady = SyncFormModel.canEnrollDevice(authenticated, identityLoaded)
    val revocationReady =
        SyncFormModel.canRevokeDevice(authenticated, identityLoaded, deviceRegistered)
    val packagePublicationReady = SyncFormModel.canPublishKeyPackages(vaultOpen, authenticated)
    DestinationCard {
        SectionHeader("Server sync")

        GroupLabel("Server sign-in")
        OutlinedTextField(
            serverUrl,
            onServerUrlChange,
            label = { Text("Server URL") },
            enabled = !authenticated,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                username,
                onUsernameChange,
                label = { Text("User") },
                enabled = !authenticated,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                password,
                onPasswordChange,
                label = { Text("Server password") },
                enabled = !authenticated,
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Button(onClick = onLogin, enabled = loginReady && !authenticated, modifier = Modifier.fillMaxWidth()) {
            Text(if (authenticated) "Signed in" else "Sign in")
        }
        OutlinedButton(onClick = onDeviceLogin, enabled = deviceLoginReady && !authenticated, modifier = Modifier.fillMaxWidth()) {
            Text("Sign in with loaded device")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onRefresh, enabled = authenticated, modifier = Modifier.weight(1f)) {
                Text("Refresh session")
            }
            OutlinedButton(onClick = onLogout, enabled = authenticated, modifier = Modifier.weight(1f)) {
                Text("Sign out")
            }
        }
        OutlinedButton(onClick = onLogoutAll, enabled = authenticated, modifier = Modifier.fillMaxWidth()) {
            Text("Sign out everywhere")
        }
        OutlinedButton(onClick = onRegisterUser, enabled = registrationReady && !authenticated, modifier = Modifier.fillMaxWidth()) {
            Text("Create user")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Device identity")
        OutlinedButton(onClick = onUnloadIdentity, enabled = identityReady, modifier = Modifier.fillMaxWidth()) {
            Text("Lock device identity")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                deviceId,
                onDeviceIdChange,
                label = { Text("Device ID") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                devicePassphrase,
                onDevicePassphraseChange,
                label = {
                    Text(if (devicePassphraseRequired) "Device passphrase" else "Migration passphrase (if needed)")
                },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onLoadIdentity, enabled = identityInputReady, modifier = Modifier.weight(1f)) {
                Text(if (identityReady) "Reload identity" else "Load identity")
            }
            OutlinedButton(
                onClick = onOpenProvisioned,
                enabled = !vaultOpen && serverReady && identityReady,
                modifier = Modifier.weight(1f),
            ) {
                Text("Open from server")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onEnrollDevice, enabled = enrollmentReady, modifier = Modifier.weight(1f)) {
                Text("Enroll and verify")
            }
            OutlinedButton(onClick = onRevokeDevice, enabled = revocationReady, modifier = Modifier.weight(1f)) {
                Text("Revoke device")
            }
        }
        OutlinedButton(onClick = onPublishKeyPackage, enabled = packagePublicationReady, modifier = Modifier.fillMaxWidth()) {
            Text("Share with verified devices")
        }
        Text(
            if (identityReady) {
                "Device identity: $identityName ($deviceTrustLabel)"
            } else {
                "No local device identity loaded"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Vaults and sync")
        OutlinedButton(onClick = onCreateServerVault, enabled = serverVaultReady, modifier = Modifier.fillMaxWidth()) {
            Text("Create vault")
        }
        OutlinedButton(onClick = onListServerVaults, enabled = serverReady, modifier = Modifier.fillMaxWidth()) {
            Text("List vaults")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onPush, enabled = vaultOpen && serverReady, modifier = Modifier.weight(1f)) {
                Text("Push")
            }
            Button(onClick = onPull, enabled = vaultOpen && serverReady, modifier = Modifier.weight(1f)) {
                Text("Pull")
            }
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
                    Text(assessment.title, color = MaterialTheme.colorScheme.onTertiaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(assessment.message, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                    assessment.warning?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (assessment.canAutoRecover) {
                            Button(onClick = onPullAndRetry, modifier = Modifier.weight(1f)) {
                                Text("Pull and retry")
                            }
                        } else {
                            Button(onClick = onPull, modifier = Modifier.weight(1f)) {
                                Text("Pull latest")
                            }
                        }
                        OutlinedButton(onClick = onDismissConflict, modifier = Modifier.weight(1f)) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Backup")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(onClick = onExportBackup, enabled = vaultOpen, modifier = Modifier.weight(1f)) {
                Text("Export backup")
            }
            OutlinedButton(onClick = onRestoreBackup, enabled = vaultOpen, modifier = Modifier.weight(1f)) {
                Text("Restore backup")
            }
        }
    }
}
