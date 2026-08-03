package top.focess.keystead.client

import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.Strings
import top.focess.keystead.model.SecretType

internal enum class SecretGroupingMode {
    NONE,
    TYPE,
    CATEGORY,
    PROVIDER,
}

internal data class SecretGroup(val key: String, val label: String, val secrets: List<SecretListItem>)

/**
 * Groups the secret list by a chosen dimension for display.
 *
 * The grouping is display-only: it never reorders or filters the underlying secrets beyond
 * partitioning them into sorted, labeled sections. Blank keys (secrets without a category or
 * provider) collapse into a single "No category" / "No provider" bucket so untagged secrets stay
 * discoverable.
 */
internal object SecretGrouper {
    fun group(secrets: List<SecretListItem>, mode: SecretGroupingMode): List<SecretGroup> =
        group(secrets, mode, EnStrings)

    fun group(
        secrets: List<SecretListItem>,
        mode: SecretGroupingMode,
        strings: Strings,
    ): List<SecretGroup> {
        if (mode == SecretGroupingMode.NONE || secrets.isEmpty()) {
            return emptyList()
        }
        return secrets
            .groupBy { keyFor(it, mode) }
            .toSortedMap()
            .map { (key, group) -> SecretGroup(key, labelFor(key, mode, strings), group) }
    }

    private fun keyFor(secret: SecretListItem, mode: SecretGroupingMode): String =
        when (mode) {
            SecretGroupingMode.TYPE -> secret.type
            SecretGroupingMode.CATEGORY -> secret.category?.trim().orEmpty()
            SecretGroupingMode.PROVIDER -> secret.provider?.trim().orEmpty()
            SecretGroupingMode.NONE -> ""
        }

    private fun labelFor(key: String, mode: SecretGroupingMode, strings: Strings): String {
        if (key.isBlank()) {
            return strings.groupingBucketLabel(mode)
        }
        return when (mode) {
            SecretGroupingMode.TYPE ->
                runCatching { strings.secretTypeLabel(SecretType.valueOf(key)) }
                    .getOrDefault(key)
            else -> key
        }
    }
}
