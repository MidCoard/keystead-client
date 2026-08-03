package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.focess.keystead.client.ConnectedNoticeTone
import top.focess.keystead.client.FormSubmitPolicy
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.ServerFeatureModel
import top.focess.keystead.client.i18n.LocalStrings

internal val SubmitKeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)

internal fun submitKeyboardActions(
    enabled: Boolean,
    onSubmit: () -> Unit,
): KeyboardActions =
    KeyboardActions(
        onDone = { FormSubmitPolicy.submitIfEnabled(enabled, onSubmit) },
    )

internal fun Modifier.submitOnCtrlEnter(
    enabled: Boolean,
    onSubmit: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val submitKey = event.key == Key.Enter || event.key == Key.NumPadEnter
        FormSubmitPolicy.handleCtrlEnter(
            enabled = enabled,
            ctrlPressed = event.isCtrlPressed,
            submitKey = submitKey,
            keyUp = event.type == KeyEventType.KeyUp,
            onSubmit = onSubmit,
        )
    }

@Composable
fun DestinationCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun SectionHeader(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun KeysteadChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable () -> Unit,
) {
    ElevatedFilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        modifier = modifier,
    )
}

@Composable
fun GroupLabel(value: String) {
    Text(
        value.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
fun CapabilityGroupLabel(
    value: String,
    capability: String,
    available: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GroupLabel(value)
        Surface(
            color =
                if (available) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            shape = CircleShape,
        ) {
            Text(
                capability,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color =
                    if (available) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun ConnectedAvailabilityNotice(
    availability: ServerAvailability,
    onRetry: () -> Unit,
) {
    val strings = LocalStrings.current
    val model = ServerFeatureModel.connectedNotice(availability)
    val accent =
        when (model.tone) {
            ConnectedNoticeTone.POSITIVE -> MaterialTheme.colorScheme.secondary
            ConnectedNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.outline
            ConnectedNoticeTone.CAUTION -> MaterialTheme.colorScheme.tertiary
        }
    val container =
        when (model.tone) {
            ConnectedNoticeTone.POSITIVE -> MaterialTheme.colorScheme.secondaryContainer
            ConnectedNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
            ConnectedNoticeTone.CAUTION -> MaterialTheme.colorScheme.tertiaryContainer
        }
    val contentColor =
        when (model.tone) {
            ConnectedNoticeTone.POSITIVE -> MaterialTheme.colorScheme.onSecondaryContainer
            ConnectedNoticeTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
            ConnectedNoticeTone.CAUTION -> MaterialTheme.colorScheme.onTertiaryContainer
        }
    val title =
        when (availability) {
            ServerAvailability.CHECKING -> strings.serverChecking
            ServerAvailability.ONLINE -> strings.serverOnline
            ServerAvailability.OFFLINE -> strings.connectedOffline
        }

    Surface(
        color = container,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, accent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(accent, CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (availability == ServerAvailability.OFFLINE) {
                    Text(
                        strings.connectedOfflineHelp,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (model.retryVisible) {
                OutlinedButton(onClick = onRetry, enabled = model.retryEnabled) {
                    Text(strings.checkAgain)
                }
            }
        }
    }
}

@Composable
fun TypeBadge(label: String) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
