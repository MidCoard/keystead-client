package top.focess.keystead.client.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import top.focess.keystead.client.ShareExchange
import top.focess.keystead.share.ShareContents

@Composable
internal fun SharePanel(
    authenticated: Boolean,
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
    onMint: () -> Unit,
    redeemCode: String,
    onRedeemCodeChange: (String) -> Unit,
    redeemPassphrase: String,
    onRedeemPassphraseChange: (String) -> Unit,
    redeemedContents: ShareContents?,
    onClearRedeemed: () -> Unit,
    onRedeem: () -> Unit,
) {
    val mintReady = authenticated && title.isNotBlank() && payload.isNotEmpty() && passphrase.length >= 12
    val redeemReady = authenticated && redeemCode.isNotBlank() && redeemPassphrase.length >= 12
    DestinationCard {
        SectionHeader("Share")
        if (!authenticated) {
            Text(
                "Sign in to Keystead Server on the Sync tab to mint or redeem shares.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        GroupLabel("Mint a share")
        OutlinedTextField(
            title,
            onTitleChange,
            label = { Text("Title") },
            enabled = authenticated,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            payload,
            onPayloadChange,
            label = { Text("Payload (the secret to share)") },
            enabled = authenticated,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            passphrase,
            onPassphraseChange,
            label = { Text("Temp passphrase (min 12 chars, 3 classes)") },
            enabled = authenticated,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Expires",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ShareExchange.ShareTtl.entries.forEach { option ->
                FilterChip(
                    selected = ttl == option,
                    onClick = { onTtlChange(option) },
                    label = { Text(option.label) },
                    enabled = authenticated,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Switch(checked = burnAfterReading, onCheckedChange = onBurnChange, enabled = authenticated)
            Text("Burn after reading (one-time redeem)", style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = onMint, enabled = mintReady, modifier = Modifier.fillMaxWidth()) {
            Text("Mint share")
        }
        mintedShare?.let { minted ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share ready", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Code: ${minted.code}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Expires: ${minted.expiresAt}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        if (burnAfterReading) {
                            "Share this code and the passphrase out of band. The recipient can redeem it once."
                        } else {
                            "Share this code and the passphrase out of band."
                        },
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onClearMinted, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear")
                    }
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        GroupLabel("Redeem a share")
        OutlinedTextField(
            redeemCode,
            onRedeemCodeChange,
            label = { Text("Share code") },
            enabled = authenticated,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            redeemPassphrase,
            onRedeemPassphraseChange,
            label = { Text("Temp passphrase") },
            enabled = authenticated,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Some shares burn after reading - enter the passphrase carefully, as it can only be redeemed once.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onRedeem, enabled = redeemReady, modifier = Modifier.fillMaxWidth()) {
            Text("Redeem share")
        }
        redeemedContents?.let { contents ->
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share opened", color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.SemiBold)
                    Text("Title: ${contents.title}", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    Text("Type: ${contents.secretType}", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    contents.fields["body"]?.let { body ->
                        Text("Payload:", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                        Text(body, color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                    contents.sharerNote?.let { note ->
                        Text("Note: $note", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Created: ${contents.createdAt}", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onClearRedeemed, modifier = Modifier.fillMaxWidth()) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}
