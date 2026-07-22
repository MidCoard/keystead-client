package top.focess.keystead.client

import top.focess.keystead.model.SecretType

internal enum class SecretGroupingMode(val label: String) {
    NONE("None"),
    TYPE("Type"),
    CATEGORY("Category"),
    PROVIDER("Provider"),
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
    fun group(secrets: List<SecretListItem>, mode: SecretGroupingMode): List<SecretGroup> {
        if (mode == SecretGroupingMode.NONE || secrets.isEmpty()) {
            return emptyList()
        }
        return secrets
            .groupBy { keyFor(it, mode) }
            .toSortedMap()
            .map { (key, group) -> SecretGroup(key, labelFor(key, mode), group) }
    }

    private fun keyFor(secret: SecretListItem, mode: SecretGroupingMode): String =
        when (mode) {
            SecretGroupingMode.TYPE -> secret.type
            SecretGroupingMode.CATEGORY -> secret.category?.trim().orEmpty()
            SecretGroupingMode.PROVIDER -> secret.provider?.trim().orEmpty()
            SecretGroupingMode.NONE -> ""
        }

    private fun labelFor(key: String, mode: SecretGroupingMode): String {
        if (key.isBlank()) {
            return when (mode) {
                SecretGroupingMode.CATEGORY -> "No category"
                SecretGroupingMode.PROVIDER -> "No provider"
                else -> "Other"
            }
        }
        return when (mode) {
            SecretGroupingMode.TYPE ->
                runCatching { SecretFormModel.specForOrNull(SecretType.valueOf(key))?.label ?: "Login" }
                    .getOrDefault(key)
            else -> key
        }
    }
}
