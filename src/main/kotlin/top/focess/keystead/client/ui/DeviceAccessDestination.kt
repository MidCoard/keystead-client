package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.BiometricAvailability
import top.focess.keystead.client.DeviceAccessMode
import top.focess.keystead.client.DeviceAccessPresentation
import top.focess.keystead.client.DeviceLoginPresentation
import top.focess.keystead.client.DeviceLoginState
import top.focess.keystead.client.SecureStorageUiModel
import top.focess.keystead.client.i18n.LocalStrings

@Composable
internal fun LocalLoginPanel(
    secureStorage: SecureStorageUiModel,
    presentation: DeviceAccessPresentation,
    credentialLoaded: Boolean,
    localLogin: DeviceLoginPresentation,
    onLoadCredential: () -> Unit,
    onCreateBiometricCredential: () -> Unit,
    onRemoveLocalLogin: () -> Unit,
) {
    val strings = LocalStrings.current
    val biometricAvailable =
        secureStorage.biometricAvailability == BiometricAvailability.AVAILABLE

    DestinationCard {
        SectionHeader(strings.destinationLabel(KeysteadDestination.DEVICE_ACCESS))
        Text(
            strings.deviceAccessIntro,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )

        LocalLoginSection {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GroupLabel(strings.deviceLogin)
                LocalLoginBadge(
                    label =
                        if (localLogin.state == DeviceLoginState.ENABLED) {
                            strings.deviceLoginEnabledLabel
                        } else {
                            strings.deviceLoginNotEnabledLabel
                        },
                    ready = localLogin.state == DeviceLoginState.ENABLED,
                )
            }

            Text(
                when (localLogin.state) {
                    DeviceLoginState.ENABLED -> strings.deviceLoginEnabledHelp
                    DeviceLoginState.READY_TO_ENABLE -> strings.deviceLoginReady
                    DeviceLoginState.CREDENTIAL_LOCKED -> strings.deviceLoginIdentityLocked
                    DeviceLoginState.VAULT_LOCKED -> strings.deviceLoginVaultLocked
                    DeviceLoginState.UNAVAILABLE -> strings.deviceLoginUnavailable
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            Text(
                when (presentation.mode) {
                    DeviceAccessMode.EXISTING_BIOMETRIC ->
                        strings.deviceProtectionLabel(presentation.provider)
                    DeviceAccessMode.NEW_BIOMETRIC ->
                        strings.deviceProtectionAvailableLabel(presentation.provider)
                    DeviceAccessMode.BIOMETRIC_UNAVAILABLE ->
                        strings.deviceProtectionUnavailableLabel(presentation.provider)
                },
                color =
                    if (presentation.mode == DeviceAccessMode.BIOMETRIC_UNAVAILABLE) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            when (presentation.mode) {
                DeviceAccessMode.EXISTING_BIOMETRIC ->
                    if (!credentialLoaded) {
                        Button(
                            onClick = onLoadCredential,
                            enabled = biometricAvailable,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.verifyLocalLogin)
                        }
                    }
                DeviceAccessMode.NEW_BIOMETRIC ->
                    Button(
                        onClick = onCreateBiometricCredential,
                        enabled = biometricAvailable,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(strings.createProtectedIdentity)
                    }
                DeviceAccessMode.BIOMETRIC_UNAVAILABLE -> Unit
            }
            if (localLogin.canRemove) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                OutlinedButton(
                    onClick = onRemoveLocalLogin,
                    modifier = Modifier.fillMaxWidth(),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(strings.removeDeviceLogin)
                }
            }
        }
    }
}

@Composable
private fun LocalLoginSection(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.82f)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun LocalLoginBadge(label: String, ready: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (ready) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color =
                if (ready) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            fontWeight = FontWeight.SemiBold,
        )
    }
}
