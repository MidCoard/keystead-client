package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class SecureStorageViewModelTest {
    @Test
    fun successfulCheckDoesNotImplicitlySelectBiometricStorage() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        val storage = MemorySecureStorage()
        val viewModel =
            SecureStorageViewModel(
                settings,
                biometricSelector = { _, _ ->
                    SecureStorageSelection.Available(storage, "windows-hello")
                },
            )

        val model = viewModel.initialize(directory, "desktop")

        assertEquals(null, model.selectedMode)
        assertEquals(BiometricAvailability.AVAILABLE, model.biometricAvailability)
        assertEquals("windows-hello", model.providerId)
        assertFalse(model.biometricActive)
        assertEquals(null, viewModel.selectedStorage())
        assertEquals(null, settings.load())
    }

    @Test
    fun persistedBiometricSelectionActivatesAvailableStorage() {
        val directory = Files.createTempDirectory("keystead-storage-biometric")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, "windows-hello"))
        val storage = MemorySecureStorage()
        val viewModel =
            SecureStorageViewModel(settings) { _, _ ->
                SecureStorageSelection.Available(storage, "windows-hello")
            }

        val model = viewModel.initialize(directory, "desktop")

        assertEquals(SecureStorageMode.BIOMETRIC, model.selectedMode)
        assertEquals(BiometricAvailability.AVAILABLE, model.biometricAvailability)
        assertSame(storage, viewModel.selectedStorage())
        assertEquals(true, model.biometricActive)
    }

    @Test
    fun recheckDoesNotCloseAlreadyActiveStorage() {
        val directory = Files.createTempDirectory("keystead-storage-recheck")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, "windows-hello"))
        val storage = CloseTrackingStorage()
        var checks = 0
        val viewModel =
            SecureStorageViewModel(settings) { _, _ ->
                if (checks++ == 0) {
                    SecureStorageSelection.Available(storage, "windows-hello")
                } else {
                    SecureStorageSelection.Unavailable(
                        SecureStorageDiagnostic(
                            "windows-hello",
                            OsSecretStoreFailure.LOCKED,
                            "windows-hello-cancelled",
                        ),
                    )
                }
            }
        viewModel.initialize(directory, "desktop")

        val rechecked = viewModel.checkBiometric(directory, "desktop")

        assertSame(storage, viewModel.selectedStorage())
        assertFalse(storage.closed)
        assertEquals(BiometricAvailability.UNAVAILABLE, rechecked.biometricAvailability)
        assertEquals(true, rechecked.biometricActive)
    }

    @Test
    fun memoryFallbackSelectionRemainsAvailable() {
        val directory = Files.createTempDirectory("keystead-storage-fallback")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        val viewModel = unavailableViewModel(settings)
        viewModel.initialize(directory, "desktop")

        assertEquals(SecureStorageMode.MEMORY_ONLY, viewModel.selectMemory().selectedMode)
        assertIs<MemorySecureStorage>(viewModel.selectedStorage())
    }

    private fun unavailableViewModel(settings: SecureStorageSettings) =
        SecureStorageViewModel(settings) { _, _ ->
            SecureStorageSelection.Unavailable(
                SecureStorageDiagnostic(
                    "none",
                    OsSecretStoreFailure.UNSUPPORTED,
                    "biometric-provider-unsupported",
                ),
            )
        }

    private class CloseTrackingStorage : SecureStorage, AutoCloseable {
        override val capability = SecureStorageCapability.OS_BIOMETRIC_GATED
        var closed = false
        override fun save(key: SecureStorageKey, value: ByteArray) = Unit
        override fun load(key: SecureStorageKey): ByteArray? = null
        override fun delete(key: SecureStorageKey) = Unit
        override fun close() { closed = true }
    }
}
