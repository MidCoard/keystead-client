package top.focess.keystead.client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.focess.keystead.client.KeysteadLayoutMode
import top.focess.keystead.client.KeysteadBrand
import top.focess.keystead.client.ActionFeedback
import top.focess.keystead.client.ActionFeedbackTone
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.i18n.LocalStrings

enum class KeysteadZone {
    LOCAL_VAULT,
    CONNECTED,
    SYSTEM,
    INTERNAL,
}

internal object KeysteadRailPresentation {
    const val widthDp = 192
    const val connectionLabelChangesLayout = false
    const val hasStrongZoneDividers = true
    const val destinationsScrollVertically = true
    const val lockActionStaysVisible = true
    const val usesHorizontalRows = true
    const val usesExplicitHighContrastText = true
    const val labelFontSizeSp = 16

    fun destinationEnabled(
        vaultOpen: Boolean,
        destination: KeysteadDestination,
    ): Boolean =
        vaultOpen ||
            destination == KeysteadDestination.SECRETS ||
            isVaultIndependent(destination)

    fun usesUnlockContent(
        vaultOpen: Boolean,
        destination: KeysteadDestination,
    ): Boolean =
        !vaultOpen &&
            !isVaultIndependent(destination)

    private fun isVaultIndependent(destination: KeysteadDestination): Boolean =
        when (destination) {
            KeysteadDestination.RECOVERY,
            KeysteadDestination.ACCOUNT,
            KeysteadDestination.SHARE,
            KeysteadDestination.SETTINGS,
            -> true
            else -> false
        }
}

enum class KeysteadDestination(
    val zone: KeysteadZone,
    val visibleInSidebar: Boolean = true,
) {
    SECRETS(KeysteadZone.LOCAL_VAULT),
    ADD(KeysteadZone.INTERNAL, visibleInSidebar = false),
    BACKUP(KeysteadZone.LOCAL_VAULT),
    RECOVERY(KeysteadZone.LOCAL_VAULT),
    DEVICE_ACCESS(KeysteadZone.LOCAL_VAULT),
    ACCOUNT(KeysteadZone.CONNECTED),
    SYNC(KeysteadZone.CONNECTED),
    SHARE(KeysteadZone.CONNECTED),
    SETTINGS(KeysteadZone.SYSTEM),
    ;

    companion object {
        val visibleEntries: List<KeysteadDestination> =
            entries.filter(KeysteadDestination::visibleInSidebar)
    }
}

@Composable
internal fun KeysteadAppShell(
    vaultOpen: Boolean,
    destination: KeysteadDestination,
    onDestinationChange: (KeysteadDestination) -> Unit,
    serverAvailability: ServerAvailability,
    feedback: ActionFeedback?,
    onDismissFeedback: (Long) -> Unit,
    layoutMode: KeysteadLayoutMode,
    inspectorSheetVisible: Boolean,
    onDismissInspectorSheet: () -> Unit,
    onLockVault: () -> Unit,
    secretsContent: @Composable (Modifier) -> Unit,
    inspectorContent: @Composable (Modifier) -> Unit,
    addContent: @Composable () -> Unit,
    backupContent: @Composable () -> Unit,
    deviceAccessContent: @Composable () -> Unit,
    accountContent: @Composable () -> Unit,
    syncContent: @Composable () -> Unit,
    shareContent: @Composable () -> Unit,
    recoveryContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    unlockContent: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            KeysteadRail(
                vaultOpen,
                destination,
                serverAvailability,
                onDestinationChange,
                onLockVault,
            )
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                feedback?.let { result ->
                    ActionFeedbackBanner(
                        feedback = result,
                        onDismiss = { onDismissFeedback(result.id) },
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        KeysteadRailPresentation.usesUnlockContent(vaultOpen, destination) ->
                            unlockContent()
                        destination == KeysteadDestination.RECOVERY ->
                            CenteredScrollContent(maxWidth = 640.dp) { recoveryContent() }
                        destination == KeysteadDestination.DEVICE_ACCESS ->
                            CenteredScrollContent(maxWidth = 640.dp) { deviceAccessContent() }
                        destination == KeysteadDestination.ACCOUNT ->
                            CenteredScrollContent(maxWidth = 560.dp) { accountContent() }
                        destination == KeysteadDestination.SETTINGS ->
                            CenteredScrollContent(maxWidth = 560.dp) { settingsContent() }
                        !vaultOpen -> unlockContent()
                        destination == KeysteadDestination.BACKUP ->
                            CenteredScrollContent(maxWidth = 560.dp) { backupContent() }
                        destination == KeysteadDestination.SYNC ->
                            CenteredScrollContent(maxWidth = 760.dp) { syncContent() }
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
                        destination == KeysteadDestination.SHARE ->
                            CenteredScrollContent(maxWidth = 640.dp) { shareContent() }
                    }
                }
            }
        }
        StableStatusBar(vaultOpen = vaultOpen, serverAvailability = serverAvailability)
    }
}

@Composable
private fun KeysteadRail(
    vaultOpen: Boolean,
    destination: KeysteadDestination,
    serverAvailability: ServerAvailability,
    onDestinationChange: (KeysteadDestination) -> Unit,
    onLockVault: () -> Unit,
) {
    val strings = LocalStrings.current
    val railScrollState = rememberScrollState()
    val brandIcon =
        remember {
            BitmapPainter(KeysteadBrand.loadIconImage().toComposeImageBitmap())
        }
    Surface(
        modifier =
            Modifier
                .width(KeysteadRailPresentation.widthDp.dp)
                .fillMaxHeight(),
        color = if (isSystemInDarkTheme()) RailBackgroundDark else RailBackground,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(38.dp)) {
                    Image(
                        painter = brandIcon,
                        contentDescription = "Keystead",
                        modifier = Modifier.size(34.dp).align(Alignment.Center),
                    )
                    Box(
                        modifier =
                            Modifier
                                .size(10.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = 1.dp, y = 1.dp)
                                .background(
                                    if (vaultOpen) VaultOpenAccent else VaultLockedAccent,
                                    CircleShape,
                                )
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(end = 12.dp)
                            .verticalScroll(railScrollState),
                ) {
                    KeysteadZone.entries.filterNot { it == KeysteadZone.INTERNAL }.forEach { zone ->
                        if (zone != KeysteadZone.LOCAL_VAULT) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                color = RailContentMuted.copy(alpha = 0.82f),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        RailZoneHeader(
                            label = strings.destinationZoneLabel(zone),
                            availability =
                                if (zone == KeysteadZone.CONNECTED) {
                                    serverAvailability
                                } else {
                                    null
                                },
                        )
                        KeysteadDestination.visibleEntries
                            .filter { it.zone == zone }
                            .filterNot {
                                it == KeysteadDestination.DEVICE_ACCESS && !vaultOpen
                            }
                            .forEach { dest ->
                            RailDestinationItem(
                                selected =
                                    destination == dest &&
                                        KeysteadRailPresentation.destinationEnabled(vaultOpen, dest),
                                enabled =
                                    KeysteadRailPresentation.destinationEnabled(vaultOpen, dest),
                                icon = destinationIcon(dest),
                                label = strings.destinationLabel(dest),
                                onClick = { onDestinationChange(dest) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(railScrollState),
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                )
            }

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                color = RailContentMuted.copy(alpha = 0.82f),
            )
            RailDestinationItem(
                selected = false,
                enabled = vaultOpen,
                icon = Icons.Default.Lock,
                label = strings.lock,
                onClick = onLockVault,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RailZoneHeader(
    label: String,
    availability: ServerAvailability?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (availability != null) {
            Box(
                Modifier.size(7.dp).background(
                    when (availability) {
                        ServerAvailability.CHECKING -> RailContentMuted
                        ServerAvailability.ONLINE -> VaultOpenAccent
                        ServerAvailability.OFFLINE -> VaultLockedAccent
                    },
                    CircleShape,
                )
            )
        }
        Text(
            label.uppercase(),
            color = RailContentMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RailDestinationItem(
    selected: Boolean,
    enabled: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val contentColor =
        when {
            !enabled -> RailContentMuted.copy(alpha = 0.68f)
            selected -> VaultOpenAccent
            else -> RailContent
        }
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 2.dp),
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                VaultOpenAccent.copy(alpha = 0.14f)
            } else {
                Color.Transparent
            },
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .background(
                        if (selected) VaultOpenAccent else Color.Transparent,
                        CircleShape,
                    )
            )
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(21.dp),
                tint = contentColor,
            )
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = contentColor,
                fontSize = KeysteadRailPresentation.labelFontSizeSp.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun destinationIcon(dest: KeysteadDestination) =
    when (dest) {
        KeysteadDestination.SECRETS -> KeyIcon
        KeysteadDestination.ADD -> Icons.Default.Add
        KeysteadDestination.BACKUP -> BackupIcon
        KeysteadDestination.DEVICE_ACCESS -> ShieldIcon
        KeysteadDestination.ACCOUNT -> AccountIcon
        KeysteadDestination.SYNC -> SyncIcon
        KeysteadDestination.SHARE -> ShareIcon
        KeysteadDestination.RECOVERY -> RecoveryIcon
        KeysteadDestination.SETTINGS -> SettingsIcon
    }

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
internal fun ActionFeedbackBanner(
    feedback: ActionFeedback,
    onDismiss: () -> Unit,
) {
    val strings = LocalStrings.current
    val accent =
        when (feedback.tone) {
            ActionFeedbackTone.SUCCESS -> VaultOpenAccent
            ActionFeedbackTone.INFO -> MaterialTheme.colorScheme.primary
            ActionFeedbackTone.ERROR -> MaterialTheme.colorScheme.error
        }
    val container =
        when (feedback.tone) {
            ActionFeedbackTone.SUCCESS -> MaterialTheme.colorScheme.secondaryContainer
            ActionFeedbackTone.INFO -> MaterialTheme.colorScheme.primaryContainer
            ActionFeedbackTone.ERROR -> MaterialTheme.colorScheme.errorContainer
        }
    val contentColor =
        when (feedback.tone) {
            ActionFeedbackTone.SUCCESS -> MaterialTheme.colorScheme.onSecondaryContainer
            ActionFeedbackTone.INFO -> MaterialTheme.colorScheme.onPrimaryContainer
            ActionFeedbackTone.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        }
    val icon =
        when (feedback.tone) {
            ActionFeedbackTone.SUCCESS -> Icons.Default.CheckCircle
            ActionFeedbackTone.INFO -> Icons.Default.Info
            ActionFeedbackTone.ERROR -> Icons.Default.Warning
        }

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    liveRegion =
                        if (feedback.tone == ActionFeedbackTone.ERROR) {
                            LiveRegionMode.Assertive
                        } else {
                            LiveRegionMode.Polite
                        }
                },
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = contentColor,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(5.dp)
                    .heightIn(min = 52.dp)
                    .background(accent, MaterialTheme.shapes.medium)
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(start = 14.dp).size(21.dp),
            )
            Text(
                text = feedback.message,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
            ) {
                Text(strings.dismiss)
            }
        }
    }
}

@Composable
private fun StableStatusBar(
    vaultOpen: Boolean,
    serverAvailability: ServerAvailability,
) {
    val strings = LocalStrings.current
    val serverLabel =
        when (serverAvailability) {
            ServerAvailability.CHECKING -> strings.serverChecking
            ServerAvailability.ONLINE -> strings.serverOnline
            ServerAvailability.OFFLINE -> strings.connectedOffline
        }
    val serverColor =
        when (serverAvailability) {
            ServerAvailability.CHECKING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f)
            ServerAvailability.ONLINE -> VaultOpenAccent
            ServerAvailability.OFFLINE -> VaultLockedAccent
        }
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
                if (vaultOpen) strings.vaultOpen else strings.vaultLocked,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).background(serverColor, CircleShape))
            Text(
                serverLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
