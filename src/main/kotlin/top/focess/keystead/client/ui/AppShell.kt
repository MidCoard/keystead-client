package top.focess.keystead.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.KeysteadLayoutMode

enum class KeysteadDestination(val label: String) {
    SECRETS("Secrets"),
    ADD("Add"),
    SYNC("Sync"),
    PROTECTION("Protection"),
}

@Composable
fun KeysteadAppShell(
    vaultOpen: Boolean,
    destination: KeysteadDestination,
    onDestinationChange: (KeysteadDestination) -> Unit,
    status: String,
    layoutMode: KeysteadLayoutMode,
    inspectorSheetVisible: Boolean,
    onDismissInspectorSheet: () -> Unit,
    onLockVault: () -> Unit,
    secretsContent: @Composable (Modifier) -> Unit,
    inspectorContent: @Composable (Modifier) -> Unit,
    addContent: @Composable () -> Unit,
    syncContent: @Composable () -> Unit,
    protectionContent: @Composable () -> Unit,
    unlockContent: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            KeysteadRail(vaultOpen, destination, onDestinationChange, onLockVault)
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    !vaultOpen -> unlockContent()
                    destination == KeysteadDestination.SECRETS ->
                        SecretsArea(
                            layoutMode = layoutMode,
                            inspectorSheetVisible = inspectorSheetVisible,
                            onDismissInspectorSheet = onDismissInspectorSheet,
                            secretsContent = secretsContent,
                            inspectorContent = inspectorContent,
                        )
                    destination == KeysteadDestination.ADD ->
                        CenteredScrollContent(maxWidth = 720.dp) { addContent() }
                    destination == KeysteadDestination.SYNC ->
                        CenteredScrollContent(maxWidth = 560.dp) { syncContent() }
                    destination == KeysteadDestination.PROTECTION ->
                        CenteredScrollContent(maxWidth = 560.dp) { protectionContent() }
                }
            }
        }
        StatusBar(status = status, vaultOpen = vaultOpen)
    }
}

@Composable
private fun KeysteadRail(
    vaultOpen: Boolean,
    destination: KeysteadDestination,
    onDestinationChange: (KeysteadDestination) -> Unit,
    onLockVault: () -> Unit,
) {
    NavigationRail(
        containerColor = if (isSystemInDarkTheme()) RailBackgroundDark else RailBackground,
        header = {
            Box(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    tint = RailContent,
                    modifier = Modifier.size(28.dp),
                )
                Box(
                    modifier =
                        Modifier.size(10.dp).align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp)
                            .background(
                                if (vaultOpen) VaultOpenAccent else VaultLockedAccent,
                                CircleShape,
                            )
                )
            }
        },
    ) {
        KeysteadDestination.entries.forEach { dest ->
            NavigationRailItem(
                selected = vaultOpen && destination == dest,
                onClick = { onDestinationChange(dest) },
                enabled = vaultOpen,
                icon = { Icon(destinationIcon(dest), contentDescription = null, modifier = Modifier.size(22.dp)) },
                label = { Text(dest.label) },
                colors = railItemColors(),
            )
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(
            selected = false,
            onClick = onLockVault,
            enabled = vaultOpen,
            icon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(22.dp)) },
            label = { Text("Lock") },
            colors = railItemColors(),
        )
        Spacer(Modifier.height(12.dp))
    }
}

private fun destinationIcon(dest: KeysteadDestination) =
    when (dest) {
        KeysteadDestination.SECRETS -> KeyIcon
        KeysteadDestination.ADD -> Icons.Default.Add
        KeysteadDestination.SYNC -> SyncIcon
        KeysteadDestination.PROTECTION -> ShieldIcon
    }

@Composable
private fun railItemColors() =
    NavigationRailItemDefaults.colors(
        selectedIconColor = RailBackground,
        selectedTextColor = VaultOpenAccent,
        indicatorColor = VaultOpenAccent,
        unselectedIconColor = RailContentMuted,
        unselectedTextColor = RailContentMuted,
        disabledIconColor = RailContentMuted.copy(alpha = 0.38f),
        disabledTextColor = RailContentMuted.copy(alpha = 0.38f),
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecretsArea(
    layoutMode: KeysteadLayoutMode,
    inspectorSheetVisible: Boolean,
    onDismissInspectorSheet: () -> Unit,
    secretsContent: @Composable (Modifier) -> Unit,
    inspectorContent: @Composable (Modifier) -> Unit,
) {
    when (layoutMode) {
        KeysteadLayoutMode.WIDE ->
            Row(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                secretsContent(Modifier.weight(1f).fillMaxHeight())
                inspectorContent(Modifier.width(360.dp).fillMaxHeight())
            }
        KeysteadLayoutMode.COMPACT -> {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                secretsContent(Modifier.fillMaxSize())
            }
            if (inspectorSheetVisible) {
                ModalBottomSheet(onDismissRequest = onDismissInspectorSheet) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                        inspectorContent(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredScrollContent(maxWidth: Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier.widthIn(max = maxWidth).fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun StatusBar(status: String, vaultOpen: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(8.dp).background(
                    if (vaultOpen) VaultOpenAccent else VaultLockedAccent,
                    CircleShape,
                )
            )
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
