package top.focess.keystead.client.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.focess.keystead.client.ServerAvailability
import top.focess.keystead.client.ServerFeatureModel
import top.focess.keystead.client.ServerShareSummary
import top.focess.keystead.client.ShareExchange
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.share.ShareContents

@Composable
internal fun SharePanel(
    authenticated: Boolean,
    serverAvailability: ServerAvailability,
    onCheckServer: () -> Unit,
    title: String,
    onTitleChange: (String) -> Unit,
    payload: String,
    onPayloadChange: (String) -> Unit,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    ttl: ShareExchange.ShareTtl,
    onTtlChange: (ShareExchange.ShareTtl) -> Unit,
    burnAfterReading: Boolean,
    onBurnChange: (Boolean) -> Unit,
    mintedShare: ShareExchange.MintedShare?,
    onClearMinted: () -> Unit,
    onCopyCode: (String) -> Unit,
    onMint: () -> Unit,
    redeemCode: String,
    onRedeemCodeChange: (String) -> Unit,
    redeemPassphrase: String,
    onRedeemPassphraseChange: (String) -> Unit,
    redeemedContents: ShareContents?,
    onClearRedeemed: () -> Unit,
    onRedeem: () -> Unit,
    outstandingShares: List<ServerShareSummary>,
    onRefreshShares: () -> Unit,
    onDeleteShare: (String) -> Unit,
) {
    val strings = LocalStrings.current
    val serverAvailable = serverAvailability.isOnline
    val mintReady =
        ServerFeatureModel.canMintShare(
            serverAvailability,
            authenticated,
            title,
            payload,
            passphrase,
        )
    val redeemReady =
        ServerFeatureModel.canRedeemShare(
            serverAvailability,
            redeemCode,
            redeemPassphrase,
        )
    DestinationCard {
        SectionHeader(strings.shareTitle)
        ConnectedAvailabilityNotice(serverAvailability, onCheckServer)
        if (!authenticated) {
            Text(
                strings.shareNotSignedInHelp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        CapabilityGroupLabel(
            strings.groupMintShare,
            strings.loginRequired,
            serverAvailable && authenticated,
        )
        OutlinedTextField(
            title,
            onTitleChange,
            label = { Text(strings.fieldTitle) },
            enabled = authenticated,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            payload,
            onPayloadChange,
            label = { Text(strings.payloadLabel) },
            enabled = authenticated,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            passphrase,
            onPassphraseChange,
            label = { Text(strings.tempPassphraseLabel) },
            enabled = authenticated,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = passphrase.isNotEmpty() && !ShareExchange.meetsPassphrasePolicy(passphrase),
            supportingText = {
                if (passphrase.isNotEmpty() && !ShareExchange.meetsPassphrasePolicy(passphrase)) {
                    Text(strings.passphrasePolicyHint)
                }
            },
            keyboardOptions = SubmitKeyboardOptions,
            keyboardActions = submitKeyboardActions(mintReady, onMint),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            strings.expires,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShareExchange.ShareTtl.entries.forEach { option ->
                KeysteadChoiceChip(
                    selected = ttl == option,
                    onClick = { onTtlChange(option) },
                    label = { Text(strings.shareTtlLabel(option)) },
                    enabled = authenticated,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Switch(checked = burnAfterReading, onCheckedChange = onBurnChange, enabled = authenticated)
            Text(strings.burnAfterReading, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onMint, enabled = mintReady, modifier = Modifier.fillMaxWidth()) {
            Text(strings.mintShare)
        }
        mintedShare?.let { minted ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.shareReady, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(
                        strings.shareCode(minted.code),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        strings.shareExpires(formatInstant(minted.expiresAt)),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (burnAfterReading) strings.shareOutOfBandOnce else strings.shareOutOfBand,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onCopyCode(minted.code) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(strings.copyCode)
                        }
                        OutlinedButton(onClick = onClearMinted, modifier = Modifier.weight(1f)) {
                            Text(strings.clear)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        CapabilityGroupLabel(
            strings.groupRedeemShare,
            strings.serverRequired,
            serverAvailable,
        )
        OutlinedTextField(
            redeemCode,
            onRedeemCodeChange,
            label = { Text(strings.shareCodeField) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            redeemPassphrase,
            onRedeemPassphraseChange,
            label = { Text(strings.tempPassphraseShort) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            isError = redeemPassphrase.isNotEmpty() && !ShareExchange.meetsPassphrasePolicy(redeemPassphrase),
            supportingText = {
                if (redeemPassphrase.isNotEmpty() && !ShareExchange.meetsPassphrasePolicy(redeemPassphrase)) {
                    Text(strings.passphrasePolicyHint)
                }
            },
            keyboardOptions = SubmitKeyboardOptions,
            keyboardActions = submitKeyboardActions(redeemReady, onRedeem),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            strings.someSharesBurnNote,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onRedeem, enabled = redeemReady, modifier = Modifier.fillMaxWidth()) {
            Text(strings.redeemShare)
        }
        redeemedContents?.let { contents ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(strings.shareOpened, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(strings.shareOpenedTitle(contents.title), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    Text(strings.shareOpenedType(strings.secretTypeLabel(contents.secretType)), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    contents.fields["body"]?.let { body ->
                        Text(strings.payloadLabelShort, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                        Text(body, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    contents.sharerNote?.let { note ->
                        Text(strings.shareOpenedNote(note), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(strings.shareOpenedCreated(formatInstant(contents.createdAt)), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onClearRedeemed, modifier = Modifier.fillMaxWidth()) {
                        Text(strings.clear)
                    }
                }
            }
        }

        if (authenticated) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CapabilityGroupLabel(
                strings.groupYourShares,
                strings.loginRequired,
                serverAvailable && authenticated,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRefreshShares,
                    enabled = serverAvailable,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(strings.refresh)
                }
            }
            if (outstandingShares.isEmpty()) {
                Text(
                    strings.noOutstandingShares,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val pageSize = SHARE_PAGE_SIZE
                val totalPages = (outstandingShares.size + pageSize - 1) / pageSize
                var page by remember(outstandingShares) { mutableStateOf(0) }
                val currentPage = page.coerceIn(0, maxOf(0, totalPages - 1))
                outstandingShares
                    .drop(currentPage * pageSize)
                    .take(pageSize)
                    .forEach { summary ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    summary.code,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    strings.shareCreatedExpires(formatInstant(summary.createdAt), formatInstant(summary.expiresAt)),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (summary.burnAfterReading) {
                                    Text(
                                        strings.burnsAfterReading,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onDeleteShare(summary.code) },
                                    enabled = serverAvailable,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors =
                                        ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                ) {
                                    Text(strings.delete)
                                }
                            }
                        }
                    }
                if (totalPages > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { page = currentPage - 1 },
                            enabled = currentPage > 0,
                        ) {
                            Text(strings.previous)
                        }
                        Text(
                            strings.pageOf(currentPage + 1, totalPages),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(
                            onClick = { page = currentPage + 1 },
                            enabled = currentPage < totalPages - 1,
                        ) {
                            Text(strings.next)
                        }
                    }
                }
            }
        }
    }
}

private const val SHARE_PAGE_SIZE = 10

private fun formatInstant(instant: Instant): String =
    instant
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
