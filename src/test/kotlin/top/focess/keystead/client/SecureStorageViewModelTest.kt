package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame

class SecureStorageViewModelTest {
    @Test
    fun `native success becomes available and can be selected`() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        val storage = MemorySecureStorage()
        val viewModel = SecureStorageViewModel(
            settings,
            nativeSelector = { _, _ -> SecureStorageSelection.Available(storage, "test-native") },
        )

        val checked = viewModel.checkNative(directory, "desktop")
        assertEquals(SecureStorageUiState.NATIVE_AVAILABLE, checked.state)
        assertEquals("test-native", checked.providerId)
        assertSame(storage, viewModel.selectNative())
        assertEquals(
            PersistedSecureStorageSelection(SecureStorageMode.NATIVE, "test-native"),
            settings.load(),
        )
    }

    @Test
    fun `locked native provider is reported without implicit fallback`() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val viewModel = SecureStorageViewModel(
            SecureStorageSettings(directory.resolve("selection.properties")),
            nativeSelector = { _, _ ->
                SecureStorageSelection.Unavailable(
                    SecureStorageDiagnostic("secret-service", OsSecretStoreFailure.LOCKED, "collection-locked"),
                )
            },
        )

        val result = viewModel.checkNative(directory, "desktop")
        assertEquals(SecureStorageUiState.NATIVE_UNAVAILABLE, result.state)
        assertEquals("collection-locked", result.diagnosticCode)
        assertEquals(null, viewModel.selectedStorage())
    }

    @Test
    fun `passphrase and memory fallback require explicit selection`() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        val viewModel = SecureStorageViewModel(settings) { _, _ ->
            SecureStorageSelection.Unavailable(
                SecureStorageDiagnostic("none", OsSecretStoreFailure.UNSUPPORTED, "unsupported"),
            )
        }

        viewModel.checkNative(directory, "desktop")
        assertEquals(SecureStorageUiState.PASSPHRASE_SELECTED, viewModel.selectPassphrase().state)
        assertEquals(PersistedSecureStorageSelection(SecureStorageMode.PASSPHRASE_FILE, null), settings.load())
        assertEquals(SecureStorageUiState.MEMORY_SELECTED, viewModel.selectMemory().state)
        assertIs<MemorySecureStorage>(viewModel.selectedStorage())
        assertEquals(PersistedSecureStorageSelection(SecureStorageMode.MEMORY_ONLY, null), settings.load())
    }

    @Test
    fun `migration selects native only after verified migration succeeds`() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.PASSPHRASE_FILE, null))
        val storage = MemorySecureStorage()
        val viewModel = SecureStorageViewModel(settings) { _, _ ->
            SecureStorageSelection.Available(storage, "test-native")
        }
        viewModel.checkNative(directory, "desktop")

        val result = viewModel.migrateIdentity { candidate ->
            assertSame(storage, candidate)
            DeviceIdentityMigrationResult.Migrated
        }

        assertEquals(DeviceIdentityMigrationResult.Migrated, result)
        assertEquals(PersistedSecureStorageSelection(SecureStorageMode.NATIVE, "test-native"), settings.load())
    }

    @Test
    fun `failed migration retains old selection and diagnostics redact secrets`() {
        val directory = Files.createTempDirectory("keystead-storage-vm")
        val settings = SecureStorageSettings(directory.resolve("selection.properties"))
        settings.save(PersistedSecureStorageSelection(SecureStorageMode.PASSPHRASE_FILE, null))
        val viewModel = SecureStorageViewModel(settings) { _, _ ->
            SecureStorageSelection.Available(MemorySecureStorage(), "test-native")
        }
        viewModel.checkNative(directory, "desktop")

        runCatching { viewModel.migrateIdentity { error("migration-failed") } }

        assertEquals(PersistedSecureStorageSelection(SecureStorageMode.PASSPHRASE_FILE, null), settings.load())
        val diagnostic = SecureStorageUiModel(
            SecureStorageUiState.NATIVE_UNAVAILABLE,
            "test-native",
            "native-probe-failed",
        ).toString()
        assertFalse(diagnostic.contains("private", ignoreCase = true))
        assertFalse(diagnostic.contains("secret-value"))
    }
}
