package top.focess.keystead.client

data class SecretListQuery(
    val text: String = "",
    val type: String? = null,
    val category: String = "",
    val provider: String = "",
    val software: String = "",
)

object SecretListFilter {
    fun apply(secrets: List<SecretListItem>, query: SecretListQuery): List<SecretListItem> =
        secrets.filter { secret ->
            matchesType(secret, query.type)
                && matchesExact(secret.category, query.category)
                && matchesExact(secret.provider, query.provider)
                && matchesExact(secret.software, query.software)
                && matchesText(secret, query.text)
        }

    private fun matchesType(secret: SecretListItem, type: String?): Boolean =
        type.isNullOrBlank() || secret.type.equals(type.trim(), ignoreCase = true)

    private fun matchesExact(actual: String?, expected: String): Boolean =
        expected.isBlank() || actual?.trim()?.equals(expected.trim(), ignoreCase = true) == true

    private fun matchesText(secret: SecretListItem, text: String): Boolean {
        val needle = text.trim().lowercase()
        if (needle.isEmpty()) {
            return true
        }
        return searchableFields(secret).any { it.lowercase().contains(needle) }
    }

    private fun searchableFields(secret: SecretListItem): List<String> =
        listOfNotNull(
            secret.id,
            secret.title,
            secret.type,
            secret.category,
            secret.provider,
            secret.software,
            secret.account,
        )
}
