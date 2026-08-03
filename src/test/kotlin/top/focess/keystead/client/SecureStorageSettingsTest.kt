package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecureStorageSettingsTest {
    @Test
    fun `settings persist only mode and provider identifier`() {
        val file = Files.createTempDirectory("keystead-storage-settings").resolve("secure-storage.properties")
        val settings = SecureStorageSettings(file)
        val selection = PersistedSecureStorageSelection(SecureStorageMode.BIOMETRIC, "windows-hello")

        settings.save(selection)

        assertEquals(selection, settings.load())
        val text = Files.readString(file)
        assertEquals(setOf("mode=BIOMETRIC", "providerId=windows-hello"), text.lines().filter(String::isNotBlank).toSet())
        assertFalse(text.contains("passphrase", ignoreCase = true))
        assertFalse(text.contains("probe", ignoreCase = true))
    }

    @Test
    fun `unknown selection is ignored instead of silently reused`() {
        val file = Files.createTempDirectory("keystead-obsolete-storage-settings")
            .resolve("secure-storage.properties")
        Files.writeString(file, "mode=REMOVED\nproviderId=removed-provider\n")

        assertEquals(null, SecureStorageSettings(file).load())
    }
}
