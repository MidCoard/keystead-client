package top.focess.keystead.client

/**
 * Persists the selected secure-storage backend in the consolidated client settings. Two instances
 * exist: one for the vault secure storage ([isLocalLogin] = false) and one for the local-login
 * storage ([isLocalLogin] = true).
 */
internal class SecureStorageSettings(
    private val store: ClientSettingsStore,
    private val isLocalLogin: Boolean,
) {
    fun load(): PersistedSecureStorageSelection? {
        val settings = store.load()
        val mode = if (isLocalLogin) settings.localLoginStorageMode else settings.secureStorageMode
        val providerId =
            if (isLocalLogin) settings.localLoginStorageProviderId else settings.secureStorageProviderId
        val parsed = runCatching { SecureStorageMode.valueOf(mode ?: return null) }.getOrNull() ?: return null
        return PersistedSecureStorageSelection(parsed, providerId?.takeIf(String::isNotBlank))
    }

    fun save(selection: PersistedSecureStorageSelection) {
        val settings = store.load()
        if (isLocalLogin) {
            settings.localLoginStorageMode = selection.mode.name
            settings.localLoginStorageProviderId = selection.providerId
        } else {
            settings.secureStorageMode = selection.mode.name
            settings.secureStorageProviderId = selection.providerId
        }
        store.save(settings)
    }
}

enum class SecureStorageMode { BIOMETRIC, MEMORY_ONLY }
data class PersistedSecureStorageSelection(val mode: SecureStorageMode, val providerId: String?)
