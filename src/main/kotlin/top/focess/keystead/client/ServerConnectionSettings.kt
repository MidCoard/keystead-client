package top.focess.keystead.client

/** Persists the selected Keystead Server address in the consolidated client settings. */
internal class ServerConnectionSettings(
    private val store: ClientSettingsStore,
    fallback: String,
) {
    private val fallback = normalize(fallback)

    fun load(): String =
        store.load().serverUrl?.let(::normalize)?.takeIf(String::isNotBlank) ?: fallback

    fun remember(serverUrl: String): String {
        val normalized = normalize(serverUrl)
        if (normalized.isBlank()) return fallback
        val settings = store.load()
        settings.serverUrl = normalized
        store.save(settings)
        return normalized
    }

    private fun normalize(value: String): String = value.trim().trimEnd('/')
}
