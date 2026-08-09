package top.focess.keystead.client

import java.net.URI
import java.nio.file.Path

/**
 * Remembers the last vault path that completed an open, create, or recovery operation, in the
 * consolidated client settings. The value is stored as a file URI so Windows path separators and
 * spaces survive without custom escaping.
 */
internal class VaultLocationSettings(
    private val store: ClientSettingsStore,
    private val fallback: Path,
) {
    fun load(): Path =
        runCatching {
            val uri = store.load().vaultLocationUri?.takeIf(String::isNotBlank) ?: return@runCatching null
            require(uri.startsWith("file:", ignoreCase = true))
            Path.of(URI.create(uri)).toAbsolutePath().normalize()
        }.getOrNull() ?: fallback.toAbsolutePath().normalize()

    fun rememberSuccessfulVault(vaultFile: Path): Path {
        val normalized = vaultFile.toAbsolutePath().normalize()
        val settings = store.load()
        settings.vaultLocationUri = normalized.toUri().toASCIIString()
        store.save(settings)
        return normalized
    }

    fun clear() {
        val settings = store.load()
        settings.vaultLocationUri = null
        store.save(settings)
    }
}
