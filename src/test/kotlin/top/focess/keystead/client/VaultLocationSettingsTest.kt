package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VaultLocationSettingsTest {
    @Test
    fun missingPreferenceUsesTheApplicationDefault() {
        val root = Files.createTempDirectory("keystead-vault-location-missing")
        val fallback = root.resolve("default").resolve("vault.kvault")

        val loaded =
            VaultLocationSettings(root.resolve("vault-location.properties"), fallback).load()

        assertEquals(fallback.toAbsolutePath().normalize(), loaded)
    }

    @Test
    fun successfulVaultLocationSurvivesRestartWithWindowsSafeEncoding() {
        val root = Files.createTempDirectory("keystead-vault-location-save")
        val settingsFile = root.resolve("settings").resolve("vault-location.properties")
        val fallback = root.resolve("default.kvault")
        val selected = root.resolve("relocated vault").resolve("personal vault.kvault")
        val settings = VaultLocationSettings(settingsFile, fallback)

        val remembered = settings.rememberSuccessfulVault(selected)
        val reloaded = VaultLocationSettings(settingsFile, fallback).load()

        assertEquals(selected.toAbsolutePath().normalize(), remembered)
        assertEquals(selected.toAbsolutePath().normalize(), reloaded)
    }

    @Test
    fun malformedPreferenceFallsBackInsteadOfBreakingStartup() {
        val root = Files.createTempDirectory("keystead-vault-location-malformed")
        val settingsFile = root.resolve("vault-location.properties")
        val fallback = root.resolve("fallback.kvault")
        Files.writeString(settingsFile, "uri=not a file uri\n")

        val loaded = VaultLocationSettings(settingsFile, fallback).load()

        assertEquals(fallback.toAbsolutePath().normalize(), loaded)
    }

    @Test
    fun clearingRememberedLocationRemovesOnlyThePreferenceAndRestoresTheDefault() {
        val root = Files.createTempDirectory("keystead-vault-location-clear")
        val settingsFile = root.resolve("settings").resolve("vault-location.properties")
        val fallback = root.resolve("default.kvault")
        val selected = root.resolve("selected.kvault")
        Files.writeString(selected, "vault bytes")
        val settings = VaultLocationSettings(settingsFile, fallback)
        settings.rememberSuccessfulVault(selected)

        settings.clear()

        assertFalse(Files.exists(settingsFile))
        assertEquals(fallback.toAbsolutePath().normalize(), settings.load())
        assertEquals("vault bytes", Files.readString(selected))
    }
}
