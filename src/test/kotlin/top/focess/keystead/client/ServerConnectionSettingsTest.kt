package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConnectionSettingsTest {
    @Test
    fun missingSettingUsesTheApplicationDefault() {
        val root = Files.createTempDirectory("keystead-server-setting-missing")
        val settings =
            ServerConnectionSettings(
                root.resolve("server-connection.properties"),
                "http://localhost:8080",
            )

        assertEquals("http://localhost:8080", settings.load())
    }

    @Test
    fun rememberedServerSurvivesRestartAndNormalizesTrailingSlash() {
        val root = Files.createTempDirectory("keystead-server-setting-save")
        val settingsFile = root.resolve("settings").resolve("server-connection.properties")
        val settings = ServerConnectionSettings(settingsFile, "http://localhost:8080")

        val remembered = settings.remember("  https://vault.example.com:8443/  ")
        val reloaded =
            ServerConnectionSettings(settingsFile, "http://localhost:8080").load()

        assertEquals("https://vault.example.com:8443", remembered)
        assertEquals("https://vault.example.com:8443", reloaded)
    }

    @Test
    fun blankStoredAddressFallsBackInsteadOfBreakingConnectionSetup() {
        val root = Files.createTempDirectory("keystead-server-setting-blank")
        val settingsFile = root.resolve("server-connection.properties")
        Files.writeString(settingsFile, "url=   \n")

        val loaded =
            ServerConnectionSettings(settingsFile, "http://localhost:8080").load()

        assertEquals("http://localhost:8080", loaded)
    }
}
