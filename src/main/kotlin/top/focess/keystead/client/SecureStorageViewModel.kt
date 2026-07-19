package top.focess.keystead.client

import java.nio.file.Path

enum class SecureStorageUiState {
    CHECKING,
    NATIVE_AVAILABLE,
    NATIVE_UNAVAILABLE,
    PASSPHRASE_SELECTED,
    MEMORY_SELECTED,
}

data class SecureStorageUiModel(
    val state: SecureStorageUiState,
    val providerId: String? = null,
    val diagnosticCode: String? = null,
)

class SecureStorageViewModel internal constructor(
    private val settings: SecureStorageSettings,
    private val nativeSelector: (Path, String) -> SecureStorageSelection,
) : AutoCloseable {
    constructor(
        settings: SecureStorageSettings,
        factory: SecureStorageFactory = SecureStorageFactory(),
    ) : this(settings, factory::native)

    private var nativeCandidate: SecureStorageSelection.Available? = null
    private var selected: SecureStorage? = null

    var model: SecureStorageUiModel = initialModel(settings.load())
        private set

    fun checkNative(dataDirectory: Path, instanceId: String): SecureStorageUiModel {
        closeNativeCandidate()
        model = SecureStorageUiModel(SecureStorageUiState.CHECKING)
        return when (val result = nativeSelector(dataDirectory, instanceId)) {
            is SecureStorageSelection.Available -> {
                nativeCandidate = result
                model = SecureStorageUiModel(
                    SecureStorageUiState.NATIVE_AVAILABLE,
                    result.providerId,
                )
                model
            }
            is SecureStorageSelection.Unavailable -> {
                model = SecureStorageUiModel(
                    SecureStorageUiState.NATIVE_UNAVAILABLE,
                    result.diagnostic.providerId,
                    result.diagnostic.diagnosticCode,
                )
                model
            }
        }
    }

    fun selectNative(): SecureStorage {
        val available = checkNotNull(nativeCandidate) { "Native secure storage is not available" }
        selected = available.storage
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.NATIVE, available.providerId))
        model = SecureStorageUiModel(SecureStorageUiState.NATIVE_AVAILABLE, available.providerId)
        return available.storage
    }

    fun selectPassphrase(): SecureStorageUiModel {
        selected = null
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.PASSPHRASE_FILE, null))
        model = SecureStorageUiModel(SecureStorageUiState.PASSPHRASE_SELECTED)
        return model
    }

    fun selectMemory(): SecureStorageUiModel {
        selected = MemorySecureStorage()
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.MEMORY_ONLY, null))
        model = SecureStorageUiModel(SecureStorageUiState.MEMORY_SELECTED)
        return model
    }

    fun selectedStorage(): SecureStorage? = selected

    fun migrateIdentity(migration: (SecureStorage) -> DeviceIdentityMigrationResult): DeviceIdentityMigrationResult {
        val available = checkNotNull(nativeCandidate) { "Native secure storage is not available" }
        val result = migration(available.storage)
        selected = available.storage
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.NATIVE, available.providerId))
        model = SecureStorageUiModel(SecureStorageUiState.NATIVE_AVAILABLE, available.providerId)
        return result
    }

    override fun close() {
        val storage = nativeCandidate?.storage
        nativeCandidate = null
        selected = null
        (storage as? AutoCloseable)?.close()
    }

    private fun closeNativeCandidate() {
        val existing = nativeCandidate?.storage
        nativeCandidate = null
        if (selected === existing) selected = null
        (existing as? AutoCloseable)?.close()
    }

    private companion object {
        fun initialModel(selection: PersistedSecureStorageSelection?): SecureStorageUiModel =
            when (selection?.mode) {
                SecureStorageMode.PASSPHRASE_FILE ->
                    SecureStorageUiModel(SecureStorageUiState.PASSPHRASE_SELECTED)
                SecureStorageMode.MEMORY_ONLY ->
                    SecureStorageUiModel(SecureStorageUiState.MEMORY_SELECTED)
                SecureStorageMode.NATIVE, null ->
                    SecureStorageUiModel(SecureStorageUiState.CHECKING, selection?.providerId)
            }
    }
}
