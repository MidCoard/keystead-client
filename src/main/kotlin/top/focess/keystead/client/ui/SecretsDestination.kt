package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.SecretExpiry
import top.focess.keystead.client.SecretExpiryStatus
import top.focess.keystead.client.SecretFormModel
import top.focess.keystead.client.SecretGrouper
import top.focess.keystead.client.SecretGroupingMode
import top.focess.keystead.client.SecretListItem
import top.focess.keystead.client.SecretListQuery
import top.focess.keystead.model.SecretType

internal fun typeLabel(type: String): String =
    SecretFormModel.specForOrNull(SecretType.valueOf(type))?.label ?: "Login"

internal fun shortTypeLabel(type: String): String =
    when (SecretType.valueOf(type)) {
        SecretType.LOGIN_PASSWORD -> "Login"
        SecretType.SSH_KEY -> "SSH"
        SecretType.API_TOKEN -> "API"
        SecretType.GPG_KEY -> "GPG"
        SecretType.MFA_SECRET -> "MFA"
        SecretType.CERTIFICATE -> "Cert"
        SecretType.GENERIC_SECRET -> "Generic"
        SecretType.SECURE_NOTE -> "Note"
    }

internal fun SecretListQuery.hasFilters(): Boolean =
    text.isNotBlank() ||
        !type.isNullOrBlank() ||
        category.isNotBlank() ||
        provider.isNotBlank() ||
        software.isNotBlank()

@Composable
internal fun SecretListPanel(
    secrets: List<SecretListItem>,
    totalSecretCount: Int,
    query: SecretListQuery,
    onQueryTextChange: (String) -> Unit,
    onTypeChange: (String?) -> Unit,
    onCategoryChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onSoftwareChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    groupingMode: SecretGroupingMode,
    onGroupingChange: (SecretGroupingMode) -> Unit,
    selectedSecretId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var filtersExpanded by remember { mutableStateOf(false) }
    DestinationCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Secrets",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "${secrets.size} of $totalSecretCount shown",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                Icon(
                    if (filtersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("Filters")
            }
        }
        OutlinedTextField(
            query.text,
            onQueryTextChange,
            label = { Text("Search") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TypeFilterChips(query.type, onTypeChange)
        if (filtersExpanded) {
            AdvancedFilters(
                query = query,
                onCategoryChange = onCategoryChange,
                onProviderChange = onProviderChange,
                onSoftwareChange = onSoftwareChange,
                onClearFilters = onClearFilters,
                groupingMode = groupingMode,
                onGroupingChange = onGroupingChange,
            )
        }
        val expiryStates = remember(secrets) { secrets.map { SecretExpiry.state(it.expiry) } }
        val expiredCount = expiryStates.count { it?.status == SecretExpiryStatus.EXPIRED }
        val dueSoonCount = expiryStates.count { it?.status == SecretExpiryStatus.DUE_SOON }
        if (expiredCount > 0 || dueSoonCount > 0) {
            ExpiryReminderBanner(expiredCount = expiredCount, dueSoonCount = dueSoonCount)
        }
        if (secrets.isEmpty()) {
            EmptyState()
        } else if (groupingMode == SecretGroupingMode.NONE) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(secrets) { secret ->
                    SecretRow(secret, secret.id == selectedSecretId) { onSelect(secret.id) }
                }
            }
        } else {
            val groups = remember(secrets, groupingMode) { SecretGrouper.group(secrets, groupingMode) }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                groups.forEach { group ->
                    item(key = "header-${group.key}-${group.label}") {
                        GroupHeader(group.label, group.secrets.size)
                    }
                    items(group.secrets, key = { "secret-${it.id}" }) { secret ->
                        SecretRow(secret, secret.id == selectedSecretId) { onSelect(secret.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeFilterChips(selectedType: String?, onTypeChange: (String?) -> Unit) {
    val typeNames = listOf<String?>(null) + SecretFormModel.supportedTypes.map { it.name }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        typeNames.forEach { typeName ->
            FilterChip(
                selected = selectedType == typeName,
                onClick = { onTypeChange(typeName) },
                label = { Text(typeName?.let(::shortTypeLabel) ?: "All") },
            )
        }
    }
}

@Composable
private fun AdvancedFilters(
    query: SecretListQuery,
    onCategoryChange: (String) -> Unit,
    onProviderChange: (String) -> Unit,
    onSoftwareChange: (String) -> Unit,
    onClearFilters: () -> Unit,
    groupingMode: SecretGroupingMode,
    onGroupingChange: (SecretGroupingMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                query.category,
                onCategoryChange,
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                query.provider,
                onProviderChange,
                label = { Text("Provider") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            query.software,
            onSoftwareChange,
            label = { Text("Software") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretGroupingSelector(groupingMode, onGroupingChange)
        OutlinedButton(
            onClick = onClearFilters,
            enabled = query.hasFilters(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear filters")
        }
    }
}

@Composable
private fun SecretGroupingSelector(
    selected: SecretGroupingMode,
    onGroupingChange: (SecretGroupingMode) -> Unit,
) {
    val modes = SecretGroupingMode.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selected == mode,
                onClick = { onGroupingChange(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
            ) {
                Text(mode.label)
            }
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text("($count)", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SecretRow(secret: SecretListItem, selected: Boolean, onClick: () -> Unit) {
    val expiryState = SecretExpiry.state(secret.expiry)
    val expiryBadge =
        if (expiryState != null && expiryState.status != SecretExpiryStatus.ACTIVE) {
            expiryState
        } else {
            null
        }
    val rowDescription = buildString {
        append("Secret: ${secret.title}, ${typeLabel(secret.type)}")
        expiryBadge?.let { append(", ${it.label()}") }
    }
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = rowDescription
                }
                .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surface
            ),
        border =
            BorderStroke(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier.width(4.dp).height(42.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            MaterialTheme.shapes.small,
                        )
            )
            Spacer(Modifier.width(12.dp))
            TypeBadge(shortTypeLabel(secret.type))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    secret.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${typeLabel(secret.type)} · ${secret.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expiryBadge != null) {
                    Text(
                        expiryBadge.label(),
                        color =
                            if (expiryBadge.status == SecretExpiryStatus.EXPIRED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.tertiary
                            },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No saved secrets", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text("Saved secrets will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpiryReminderBanner(expiredCount: Int, dueSoonCount: Int) {
    val parts = buildList {
        if (expiredCount > 0) add("$expiredCount expired")
        if (dueSoonCount > 0) add("$dueSoonCount expiring soon")
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "Expiry reminders: ${parts.joinToString(", ")}",
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Review and rotate these secrets.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
