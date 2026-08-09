package top.focess.keystead.client

import com.google.gson.Gson
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Whether the app reads/writes its config from the global home dir or from the open vault's dir. */
internal enum class SettingsScope {
    GLOBAL,
    VAULT_LOCAL,
}

/**
 * Consolidated, non-secret client configuration. Serialized as JSON. Sensitive material
 * (local-login keys, secure storage data, refresh tokens, sync cursors, enrollments) stays in
 * its own stores.
 *
 * @param locale the UI locale tag, or null for the system default
 * @param serverUrl the Keystead server base URL, or null for the default
 * @param secureStorageMode the secure-storage backend mode, or null for the default
 * @param localLoginStorageMode the local-login storage backend mode, or null for the default
 * @param vaultLocationUri the active vault file URI (global only), or null
 * @param vaultScopes per-vault config-scope preference: normalized vault path -> GLOBAL/VAULT_LOCAL.
 *   Always global; the vault-local settings.json never carries this. Defaults to GLOBAL per vault.
 */
internal data class ClientSettings(
    var locale: String? = null,
    var serverUrl: String? = null,
    var secureStorageMode: String? = null,
    var secureStorageProviderId: String? = null,
    var localLoginStorageMode: String? = null,
    var localLoginStorageProviderId: String? = null,
    var vaultLocationUri: String? = null,
    var vaultScopes: Map<String, String>? = null,
) {
    /**
     * Returns the config scope recorded for the vault at [vaultPath] (a normalized absolute path
     * string), defaulting to GLOBAL when no preference is recorded.
     */
    fun scopeFor(vaultPath: String): SettingsScope =
        vaultScopes
            ?.get(vaultPath)
            ?.let { runCatching { SettingsScope.valueOf(it) }.getOrNull() }
            ?: SettingsScope.GLOBAL

    /**
     * Returns a copy of this (global) settings overlaid with [vaultLocal]'s non-null fields. Used
     * when the scope is VAULT_LOCAL: the vault-local settings.json overrides the global one for any
     * field it sets. `vaultLocationUri` and `vaultScopes` always come from the global settings.
     */
    fun mergedWith(vaultLocal: ClientSettings): ClientSettings = copy(
        locale = vaultLocal.locale ?: locale,
        serverUrl = vaultLocal.serverUrl ?: serverUrl,
        secureStorageMode = vaultLocal.secureStorageMode ?: secureStorageMode,
        secureStorageProviderId = vaultLocal.secureStorageProviderId ?: secureStorageProviderId,
        localLoginStorageMode = vaultLocal.localLoginStorageMode ?: localLoginStorageMode,
        localLoginStorageProviderId = vaultLocal.localLoginStorageProviderId ?: localLoginStorageProviderId,
    )
}

/**
 * Atomic, lenient JSON store for [ClientSettings]. A missing or unreadable file yields default
 * settings (so a corrupt config never blocks startup). Mirrors the atomic-write pattern used by the
 * previous properties stores.
 */
internal class ClientSettingsStore(private val file: Path) {
    private val gson = Gson()

    fun load(): ClientSettings {
        if (!Files.exists(file)) return ClientSettings()
        return runCatching {
            Files.newInputStream(file).use { input ->
                gson.fromJson(input.reader(), ClientSettings::class.java) ?: ClientSettings()
            }
        }.getOrDefault(ClientSettings())
    }

    fun save(settings: ClientSettings) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        Files.writeString(temporary, gson.toJson(settings))
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
    }
}
