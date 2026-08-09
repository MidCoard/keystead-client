package top.focess.keystead.client

import java.nio.file.Path

enum class SecureStorageUiState {
    CHECKING,
    BIOMETRIC_AVAILABLE,
    BIOMETRIC_UNAVAILABLE,
    MEMORY_SELECTED,
}

enum class BiometricAvailability {
    NOT_CHECKED,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE,
}

data class SecureStorageUiModel(
    val selectedMode: SecureStorageMode?,
    val biometricAvailability: BiometricAvailability,
    val providerId: String? = null,
    val diagnosticCode: String? = null,
    val biometricActive: Boolean = false,
) {
    val state: SecureStorageUiState
        get() =
            when {
                selectedMode == SecureStorageMode.MEMORY_ONLY ->
                    SecureStorageUiState.MEMORY_SELECTED
                biometricAvailability == BiometricAvailability.UNAVAILABLE ->
                    SecureStorageUiState.BIOMETRIC_UNAVAILABLE
                biometricAvailability == BiometricAvailability.AVAILABLE ->
                    SecureStorageUiState.BIOMETRIC_AVAILABLE
                else -> SecureStorageUiState.CHECKING
            }
}

internal class SecureStorageViewModel internal constructor(
    private val settings: SecureStorageSettings,
    private val biometricSelector: (Path, String) -> SecureStorageSelection,
) : AutoCloseable {
    constructor(
        settings: SecureStorageSettings,
        factory: SecureStorageFactory = SecureStorageFactory(),
    ) : this(settings, factory::biometric)

    private var biometricCandidate: SecureStorageSelection.Available? = null
    private var selected: SecureStorage? = null

    var model: SecureStorageUiModel = initialModel(settings.load())
        private set

    fun initialize(dataDirectory: Path, instanceId: String): SecureStorageUiModel {
        if (model.selectedMode == SecureStorageMode.MEMORY_ONLY && selected !is MemorySecureStorage) {
            selected = MemorySecureStorage()
        }
        return checkBiometric(dataDirectory, instanceId)
    }

    fun checkBiometric(dataDirectory: Path, instanceId: String): SecureStorageUiModel {
        val existingCandidate = biometricCandidate
        val existingStorage = existingCandidate?.storage
        val existingActive =
            model.selectedMode == SecureStorageMode.BIOMETRIC &&
                selected === existingStorage &&
                existingStorage != null
        model =
            model.copy(
                biometricAvailability = BiometricAvailability.CHECKING,
                providerId = existingCandidate?.providerId ?: model.providerId,
                diagnosticCode = null,
                biometricActive = existingActive,
            )
        return when (val result = biometricSelector(dataDirectory, instanceId)) {
            is SecureStorageSelection.Available -> {
                if (existingActive) {
                    if (result.storage !== existingStorage) {
                        (result.storage as? AutoCloseable)?.close()
                    }
                    biometricCandidate = existingCandidate
                    selected = existingStorage
                } else {
                    if (selected === existingStorage) selected = null
                    (existingStorage as? AutoCloseable)?.close()
                    biometricCandidate = result
                    if (model.selectedMode == SecureStorageMode.BIOMETRIC) {
                        selected = result.storage
                    }
                }
                model =
                    model.copy(
                        biometricAvailability = BiometricAvailability.AVAILABLE,
                        providerId = result.providerId,
                        diagnosticCode = null,
                        biometricActive =
                            existingActive ||
                                (model.selectedMode == SecureStorageMode.BIOMETRIC &&
                                    selected === result.storage),
                    )
                model
            }
            is SecureStorageSelection.Unavailable -> {
                if (!existingActive) {
                    if (selected === existingStorage) selected = null
                    (existingStorage as? AutoCloseable)?.close()
                    biometricCandidate = null
                }
                model =
                    model.copy(
                        biometricAvailability = BiometricAvailability.UNAVAILABLE,
                        providerId = result.diagnostic.providerId,
                        diagnosticCode = result.diagnostic.diagnosticCode,
                        biometricActive = existingActive,
                    )
                model
            }
        }
    }

    fun selectBiometric(): SecureStorage {
        val available =
            checkNotNull(biometricCandidate) { "Biometric secure storage is not available" }
        clearSelectedMemory()
        selected = available.storage
        settings.save(
            PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, available.providerId),
        )
        model =
            model.copy(
                selectedMode = SecureStorageMode.BIOMETRIC,
                biometricAvailability = BiometricAvailability.AVAILABLE,
                providerId = available.providerId,
                diagnosticCode = null,
                biometricActive = true,
            )
        return available.storage
    }

    fun selectMemory(): SecureStorageUiModel {
        val memory = selected as? MemorySecureStorage ?: MemorySecureStorage()
        selected = memory
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.MEMORY_ONLY, null))
        model = model.copy(selectedMode = SecureStorageMode.MEMORY_ONLY, biometricActive = false)
        return model
    }

    fun adoptExistingLocalLogin(persistence: LocalLoginPersistence): SecureStorageUiModel {
        if (model.selectedMode != null) return model
        return when (persistence) {
            LocalLoginPersistence.BIOMETRIC -> {
                if (model.biometricAvailability == BiometricAvailability.AVAILABLE) {
                    selectBiometric()
                    model
                } else {
                    settings.save(
                        PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, model.providerId),
                    )
                    model = model.copy(selectedMode = SecureStorageMode.BIOMETRIC, biometricActive = false)
                    model
                }
            }
        }
    }

    fun selectedStorage(): SecureStorage? = selected

    override fun close() {
        val biometric = biometricCandidate?.storage
        val active = selected
        biometricCandidate = null
        selected = null
        if (active is MemorySecureStorage) active.clear()
        (biometric as? AutoCloseable)?.close()
    }

    private fun clearSelectedMemory() {
        (selected as? MemorySecureStorage)?.clear()
    }

    private companion object {
        fun initialModel(selection: PersistedSecureStorageSelection?): SecureStorageUiModel =
            SecureStorageUiModel(
                selectedMode = selection?.mode,
                biometricAvailability = BiometricAvailability.NOT_CHECKED,
                providerId = selection?.providerId,
                biometricActive = false,
            )
    }
}
