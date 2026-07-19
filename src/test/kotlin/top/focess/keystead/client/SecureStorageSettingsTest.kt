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
        val selection = PersistedSecureStorageSelection(SecureStorageMode.NATIVE, "windows-dpapi")

        settings.save(selection)

        assertEquals(selection, settings.load())
        val text = Files.readString(file)
        assertEquals(setOf("mode=NATIVE", "providerId=windows-dpapi"), text.lines().filter(String::isNotBlank).toSet())
        assertFalse(text.contains("passphrase", ignoreCase = true))
        assertFalse(text.contains("probe", ignoreCase = true))
    }
}
