package top.focess.keystead.client.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.HorizontalDivider
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
import top.focess.keystead.client.InspectorSecretValue
import top.focess.keystead.client.SecretGrouper
import top.focess.keystead.client.SecretGroupingMode
import top.focess.keystead.client.SecretInspectorField
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

internal enum class InspectorCredentialKind { USERNAME, PASSWORD }

internal data class InspectorCredentialRowModel(
    val kind: InspectorCredentialKind,
    val value: String,
    val copyEnabled: Boolean,
    val revealed: Boolean,
)

internal object InspectorCredentialPresentation {
    fun rows(username: String, revealedPassword: String): List<InspectorCredentialRowModel> =
        listOf(
            InspectorCredentialRowModel(
                kind = InspectorCredentialKind.USERNAME,
                value = username.ifEmpty { "—" },
                copyEnabled = username.isNotEmpty(),
                revealed = true,
            ),
            InspectorCredentialRowModel(
                kind = InspectorCredentialKind.PASSWORD,
                value = InspectorSecretValue.display(revealedPassword),
                copyEnabled = revealedPassword.isNotEmpty(),
                revealed = revealedPassword.isNotEmpty(),
            ),
        )
}

internal enum class InspectorDetailKind {
    URL,
    ACCOUNT,
    PROVIDER,
    SOFTWARE,
    CATEGORY,
    EXPIRY,
    LABELS,
    TAGS,
    ATTRIBUTE,
    CREATED_AT,
    UPDATED_AT,
    REVISION,
}

internal data class InspectorDetailRowModel(
    val kind: InspectorDetailKind,
    val value: String,
    val name: String? = null,
)

internal object InspectorDetailPresentation {
    fun rows(secret: SecretListItem): List<InspectorDetailRowModel> = buildList {
        secret.url?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.URL, it))
        }
        secret.account?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.ACCOUNT, it))
        }
        secret.provider?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.PROVIDER, it))
        }
        secret.software?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.SOFTWARE, it))
        }
        secret.category?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.CATEGORY, it))
        }
        secret.expiry?.takeIf(String::isNotBlank)?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.EXPIRY, it))
        }
        if (secret.labels.isNotEmpty()) {
            add(
                InspectorDetailRowModel(
                    InspectorDetailKind.LABELS,
                    secret.labels.sorted().joinToString(", "),
                )
            )
        }
        if (secret.tags.isNotEmpty()) {
            add(
                InspectorDetailRowModel(
                    InspectorDetailKind.TAGS,
                    secret.tags.sorted().joinToString(", "),
                )
            )
        }
        secret.attributes
            .filterKeys { it != "expiry" }
            .toSortedMap()
            .forEach { (name, value) ->
                add(InspectorDetailRowModel(InspectorDetailKind.ATTRIBUTE, value, name))
            }
        secret.createdAt?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.CREATED_AT, it))
        }
        secret.updatedAt?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.UPDATED_AT, it))
        }
        secret.revision?.let {
            add(InspectorDetailRowModel(InspectorDetailKind.REVISION, it.toString()))
        }
    }
}

internal data class InspectorFieldRowModel(
    val name: String,
    val value: String,
    val secret: Boolean,
    val revealed: Boolean,
    val copyEnabled: Boolean,
)

internal object InspectorFieldPresentation {
    fun rows(
        fields: List<SecretInspectorField>,
        revealedFieldName: String?,
        revealedValue: String,
    ): List<InspectorFieldRowModel> =
        fields.map { field ->
            val revealed = field.secret && field.name == revealedFieldName && revealedValue.isNotEmpty()
            val value =
                when {
                    !field.secret -> field.value.orEmpty().ifEmpty { "—" }
                    revealed -> revealedValue
                    else -> InspectorSecretValue.MASKED
                }
            InspectorFieldRowModel(
                name = field.name,
                value = value,
                secret = field.secret,
                revealed = revealed,
                copyEnabled = if (field.secret) revealed else !field.value.isNullOrEmpty(),
            )
        }
}

@Composable
internal fun SecretListPanel(
    secrets: List<SecretListItem>,
    breachFindings: Map<String, Int> = emptyMap(),
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
        if (breachFindings.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    strings.passwordBreachAuditFound(breachFindings.size),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (secrets.isEmpty()) {
            EmptyState()
        } else if (groupingMode == SecretGroupingMode.NONE) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(secrets) { secret ->
                    SecretRow(
                        secret,
                        secret.id == selectedSecretId,
                        breachFindings[secret.id],
                    ) { onSelect(secret.id) }
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
                        SecretRow(
                            secret,
                            secret.id == selectedSecretId,
                            breachFindings[secret.id],
                        ) { onSelect(secret.id) }
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
private fun SecretRow(
    secret: SecretListItem,
    selected: Boolean,
    breachCount: Int?,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current
    val expiryState = SecretExpiry.state(secret.expiry)
    val expiryBadge =
        if (expiryState != null && expiryState.status != SecretExpiryStatus.ACTIVE) {
            expiryState
        } else {
            null
        }
    val breachLabel = breachCount?.let(strings::passwordLeakBadge)
    val rowDescription = buildString {
        append(strings.secretRowLabel(secret.title, typeLabel(secret.type, strings)))
        expiryBadge?.let { append(", ${it.label(strings)}") }
        breachLabel?.let { append(", $it") }
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
                if (breachLabel != null) {
                    Text(
                        breachLabel,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
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
    revealedFieldName: String?,
    revealedValue: String,
    showTotpCode: Boolean,
    totpCode: String,
    totpSecondsRemaining: Int,
    onReveal: (String) -> Unit,
    onHide: () -> Unit,
    onCopy: (String) -> Unit,
    onCopyUsername: () -> Unit,
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
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        val type = SecretType.valueOf(selectedSecret.type)
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
        if (type == SecretType.LOGIN_PASSWORD) {
            LoginCredentialsCard(
                rows =
                    InspectorCredentialPresentation.rows(
                        selectedSecret.username.orEmpty(),
                        revealedValue,
                ),
                onCopyUsername = onCopyUsername,
                onReveal = { onReveal("password") },
                onHide = onHide,
                onCopyPassword = { onCopy("password") },
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
        if (type != SecretType.LOGIN_PASSWORD && selectedSecret.fields.isNotEmpty()) {
            SecretFieldsCard(
                rows =
                    InspectorFieldPresentation.rows(
                        selectedSecret.fields,
                        revealedFieldName,
                        revealedValue,
                    ),
                onReveal = onReveal,
                onHide = onHide,
                onCopy = onCopy,
            )
        }
        val detailRows = InspectorDetailPresentation.rows(selectedSecret)
        if (detailRows.isNotEmpty()) {
            SectionHeader(strings.secretDetails)
            InspectorDetailsCard(detailRows)
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
}

@Composable
private fun LoginCredentialsCard(
    rows: List<InspectorCredentialRowModel>,
    onCopyUsername: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onCopyPassword: () -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 68.dp)
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (row.kind == InspectorCredentialKind.USERNAME) {
                                strings.fieldUsername
                            } else {
                                strings.fieldPassword
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            row.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily =
                                if (row.kind == InspectorCredentialKind.PASSWORD) {
                                    FontFamily.Monospace
                                } else {
                                    null
                                },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    when (row.kind) {
                        InspectorCredentialKind.USERNAME ->
                            TextButton(
                                onClick = onCopyUsername,
                                enabled = row.copyEnabled,
                            ) { Text(strings.copy) }
                        InspectorCredentialKind.PASSWORD -> {
                            TextButton(onClick = if (row.revealed) onHide else onReveal) {
                                Text(if (row.revealed) strings.hide else strings.reveal)
                            }
                            TextButton(
                                onClick = onCopyPassword,
                                enabled = row.copyEnabled,
                            ) { Text(strings.copy) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretFieldsCard(
    rows: List<InspectorFieldRowModel>,
    onReveal: (String) -> Unit,
    onHide: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth().heightIn(min = 68.dp)
                            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            strings.secretFieldLabel(row.name),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            row.value,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (row.secret) {
                        TextButton(onClick = { if (row.revealed) onHide() else onReveal(row.name) }) {
                            Text(if (row.revealed) strings.hide else strings.reveal)
                        }
                    }
                    TextButton(
                        onClick = { onCopy(row.name) },
                        enabled = row.copyEnabled,
                    ) { Text(strings.copy) }
                }
            }
        }
    }
}

@Composable
private fun InspectorDetailsCard(rows: List<InspectorDetailRowModel>) {
    val strings = LocalStrings.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        inspectorDetailLabel(row, strings),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        row.value,
                        maxLines = if (row.kind == InspectorDetailKind.URL) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily =
                            if (row.kind in setOf(
                                    InspectorDetailKind.URL,
                                    InspectorDetailKind.CREATED_AT,
                                    InspectorDetailKind.UPDATED_AT,
                                    InspectorDetailKind.REVISION,
                                )
                            ) {
                                FontFamily.Monospace
                            } else {
                                null
                            },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun inspectorDetailLabel(row: InspectorDetailRowModel, strings: Strings): String =
    when (row.kind) {
        InspectorDetailKind.URL -> strings.fieldUrl
        InspectorDetailKind.ACCOUNT -> strings.fieldAccount
        InspectorDetailKind.PROVIDER -> strings.fieldProvider
        InspectorDetailKind.SOFTWARE -> strings.fieldSoftware
        InspectorDetailKind.CATEGORY -> strings.fieldCategory
        InspectorDetailKind.EXPIRY -> strings.fieldExpiry
        InspectorDetailKind.LABELS -> strings.fieldLabels
        InspectorDetailKind.TAGS -> strings.fieldTags
        InspectorDetailKind.ATTRIBUTE -> strings.customAttributeLabel(row.name.orEmpty())
        InspectorDetailKind.CREATED_AT -> strings.fieldCreatedAt
        InspectorDetailKind.UPDATED_AT -> strings.fieldUpdatedAt
        InspectorDetailKind.REVISION -> strings.fieldRevision
    }

@Composable
private fun InspectorValueDisplay(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
) {
    Surface(
        modifier = modifier.heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = Color.Transparent,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (monospace) FontFamily.Monospace else null,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
