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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.client.i18n.Strings
import top.focess.keystead.model.SecretType

internal fun typeLabel(type: String, strings: Strings): String =
    strings.secretTypeLabel(SecretType.valueOf(type))

internal fun shortTypeLabel(type: String, strings: Strings): String =
    strings.shortSecretTypeLabel(SecretType.valueOf(type))

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
    onAddSecret: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var filtersExpanded by remember { mutableStateOf(false) }
    DestinationCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    strings.secretsTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    strings.secretsShown(secrets.size, totalSecretCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                    Icon(
                        if (filtersExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(strings.filters)
                }
                Button(onClick = onAddSecret) {
                    Text(strings.newSecret)
                }
            }
        }
        OutlinedTextField(
            query.text,
            onQueryTextChange,
            label = { Text(strings.search) },
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
            val groups = remember(secrets, groupingMode) { SecretGrouper.group(secrets, groupingMode, strings) }
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
    val strings = LocalStrings.current
    val typeNames = listOf<String?>(null) + SecretFormModel.supportedTypes.map { it.name }
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        typeNames.forEach { typeName ->
            KeysteadChoiceChip(
                selected = selectedType == typeName,
                onClick = { onTypeChange(typeName) },
                label = { Text(typeName?.let { shortTypeLabel(it, strings) } ?: strings.all) },
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
    val strings = LocalStrings.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                query.category,
                onCategoryChange,
                label = { Text(strings.fieldCategory) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                query.provider,
                onProviderChange,
                label = { Text(strings.fieldProvider) },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            query.software,
            onSoftwareChange,
            label = { Text(strings.fieldSoftware) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretGroupingSelector(groupingMode, onGroupingChange)
        OutlinedButton(
            onClick = onClearFilters,
            enabled = query.hasFilters(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.clearFilters)
        }
    }
}

@Composable
private fun SecretGroupingSelector(
    selected: SecretGroupingMode,
    onGroupingChange: (SecretGroupingMode) -> Unit,
) {
    val strings = LocalStrings.current
    val modes = SecretGroupingMode.entries
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        modes.forEach { mode ->
            KeysteadChoiceChip(
                selected = selected == mode,
                onClick = { onGroupingChange(mode) },
                modifier = Modifier.weight(1f),
            ) {
                Text(strings.groupingLabel(mode))
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
    val strings = LocalStrings.current
    val expiryState = SecretExpiry.state(secret.expiry)
    val expiryBadge =
        if (expiryState != null && expiryState.status != SecretExpiryStatus.ACTIVE) {
            expiryState
        } else {
            null
        }
    val rowDescription = buildString {
        append(strings.secretRowLabel(secret.title, typeLabel(secret.type, strings)))
        expiryBadge?.let { append(", ${it.label(strings)}") }
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
            TypeBadge(shortTypeLabel(secret.type, strings))
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
                    "${typeLabel(secret.type, strings)} · ${secret.id.take(8)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expiryBadge != null) {
                    Text(
                        expiryBadge.label(strings),
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
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(strings.noSavedSecrets, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(strings.savedSecretsAppearHere, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExpiryReminderBanner(expiredCount: Int, dueSoonCount: Int) {
    val strings = LocalStrings.current
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
                strings.expiryReminders(expiredCount, dueSoonCount),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                strings.expiryReviewRotate,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun InspectorPanel(
    selectedSecret: SecretListItem?,
    revealedValue: String,
    showTotpCode: Boolean,
    totpCode: String,
    totpSecondsRemaining: Int,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCopy: () -> Unit,
    onToggleTotpCode: () -> Unit,
    onCopyTotpCode: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    DestinationCard(modifier = modifier.fillMaxHeight()) {
        SectionHeader(strings.selectedSecret)
        if (selectedSecret == null) {
            EmptyInspector()
            return@DestinationCard
        }
        val type = SecretType.valueOf(selectedSecret.type)
        val revealLabel =
            if (type == SecretType.LOGIN_PASSWORD) strings.fieldPassword
            else strings.secretFieldLabel(SecretFormModel.specFor(type).revealFieldName)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TypeBadge(shortTypeLabel(selectedSecret.type, strings))
            Text(
                typeLabel(selectedSecret.type, strings),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            selectedSecret.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(
            selectedSecret.id,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectedSecret.category != null ||
            selectedSecret.provider != null ||
            selectedSecret.software != null ||
            selectedSecret.account != null
        ) {
            Text(
                listOfNotNull(
                    selectedSecret.category,
                    selectedSecret.provider,
                    selectedSecret.software,
                    selectedSecret.account,
                )
                    .joinToString(" / "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (type == SecretType.MFA_SECRET) {
            SectionHeader(strings.currentCode)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (showTotpCode) totpCode.ifEmpty { "…" } else "••••••",
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (showTotpCode) strings.authCodeShown else strings.authCodeHidden
                        },
                )
                Text(
                    "${totpSecondsRemaining}s",
                    color =
                        if (showTotpCode && totpSecondsRemaining <= 5) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onToggleTotpCode, modifier = Modifier.weight(1f)) {
                    Text(if (showTotpCode) strings.hideCode else strings.showCode)
                }
                OutlinedButton(
                    onClick = onCopyTotpCode,
                    enabled = showTotpCode && totpCode.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text(strings.copyCode) }
            }
        }
        OutlinedTextField(
            value = revealedValue,
            onValueChange = {},
            label = { Text(revealLabel) },
            placeholder = { Text("••••••") },
            readOnly = true,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val revealed = revealedValue.isNotEmpty()
            Button(
                onClick = if (revealed) onHide else onReveal,
                modifier = Modifier.weight(1f),
            ) { Text(if (revealed) strings.hide else strings.reveal) }
            OutlinedButton(
                onClick = onCopy,
                enabled = revealed,
                modifier = Modifier.weight(1f),
            ) { Text(strings.copy) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text(strings.edit) }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) { Text(strings.delete) }
        }
    }
}

@Composable
private fun EmptyInspector() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            KeyIcon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp),
        )
        Text(strings.noSecretSelected, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        Text(strings.selectASecret, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
