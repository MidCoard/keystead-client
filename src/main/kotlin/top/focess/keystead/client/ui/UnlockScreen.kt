package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.DeviceUnlockUiModel
import top.focess.keystead.client.VaultUnlockMethod
import top.focess.keystead.client.VaultUnlockMethodPolicy
import top.focess.keystead.client.i18n.LocalStrings

@Composable
fun UnlockScreen(
    vaultDirectory: String,
    masterPassword: String,
    errorMessage: String?,
    deviceUnlock: DeviceUnlockUiModel,
    onVaultDirectoryChange: (String) -> Unit,
    onChooseExistingVault: () -> Unit,
    onChooseNewVaultLocation: () -> Unit,
    onMasterPasswordChange: (String) -> Unit,
    onOpen: () -> Unit,
    onOpenWithDeviceKey: () -> Unit,
) {
    val strings = LocalStrings.current
    var advanced by remember { mutableStateOf(false) }
    val defaultMethod = VaultUnlockMethodPolicy.defaultMethod(deviceUnlock)
    var unlockMethod by remember(defaultMethod) { mutableStateOf(defaultMethod) }
    val deviceLoginReady =
        VaultUnlockMethodPolicy.canSubmitDeviceLogin(deviceUnlock)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier =
                Modifier.widthIn(max = 540.dp).fillMaxWidth()
                    .heightIn(max = 760.dp).padding(24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Text(
                    strings.appTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    strings.vaultLockedHeading,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (VaultUnlockMethodPolicy.shouldOfferDeviceLogin(deviceUnlock)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        VaultUnlockMethod.entries.forEach { method ->
                            KeysteadChoiceChip(
                                selected = unlockMethod == method,
                                onClick = { unlockMethod = method },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    when (method) {
                                        VaultUnlockMethod.DEVICE_LOGIN -> strings.deviceLogin
                                        VaultUnlockMethod.MASTER_PASSWORD -> strings.masterPassword
                                    },
                                )
                            }
                        }
                    }
                }
                when (unlockMethod) {
                    VaultUnlockMethod.DEVICE_LOGIN -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    strings.deviceUnlockStatus(deviceUnlock),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Button(
                            onClick = onOpenWithDeviceKey,
                            enabled = deviceLoginReady,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.unlockWithDeviceLogin)
                        }
                    }
                    VaultUnlockMethod.MASTER_PASSWORD -> {
                        OutlinedTextField(
                            masterPassword,
                            onMasterPasswordChange,
                            label = { Text(strings.masterPassword) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = SubmitKeyboardOptions,
                            keyboardActions =
                                submitKeyboardActions(masterPassword.isNotBlank(), onOpen),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = onOpen,
                            enabled = masterPassword.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.openOrCreateVault)
                        }
                    }
                }
                if (errorMessage != null) {
                    Text(
                        errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = { advanced = !advanced }) {
                    Icon(
                        if (advanced) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    Text(strings.advancedVaultLocation)
                }
                if (advanced) {
                    OutlinedTextField(
                        vaultDirectory,
                        onVaultDirectoryChange,
                        label = { Text(strings.vaultFile) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onChooseExistingVault,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(strings.chooseExistingVault)
                        }
                        OutlinedButton(
                            onClick = onChooseNewVaultLocation,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(strings.chooseNewVaultLocation)
                        }
                    }
                    Text(
                        strings.vaultLocationHelp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
