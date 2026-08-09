package top.focess.keystead.client
import top.focess.keystead.client.ClientSettingsStore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecureStorageSettingsTest {
    @Test
    fun `settings persist only mode and provider identifier`() {
        val file = Files.createTempDirectory("keystead-storage-settings").resolve("secure-storage.properties")
        val settings = SecureStorageSettings(ClientSettingsStore(file), false)
        val selection = PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, "windows-hello")

        settings.save(selection)

        assertEquals(selection, settings.load())
    }

    @Test
    fun `unknown selection is ignored instead of silently reused`() {
        val file = Files.createTempDirectory("keystead-obsolete-storage-settings")
            .resolve("secure-storage.properties")
        Files.writeString(file, "mode=REMOVED\nproviderId=removed-provider\n")

        assertEquals(null, SecureStorageSettings(ClientSettingsStore(file), false).load())
    }
}
