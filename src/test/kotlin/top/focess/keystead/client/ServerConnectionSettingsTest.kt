package top.focess.keystead.client
import top.focess.keystead.client.ClientSettingsStore

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerConnectionSettingsTest {
    @Test
    fun missingSettingUsesTheApplicationDefault() {
        val root = Files.createTempDirectory("keystead-server-setting-missing")
        val settings =
            ServerConnectionSettings(
                ClientSettingsStore(root.resolve("server-connection.properties")),
                "http://localhost:22144",
            )

        assertEquals("http://localhost:22144", settings.load())
    }

    @Test
    fun rememberedServerSurvivesRestartAndNormalizesTrailingSlash() {
        val root = Files.createTempDirectory("keystead-server-setting-save")
        val settingsFile = root.resolve("settings").resolve("server-connection.properties")
        val settings = ServerConnectionSettings(ClientSettingsStore(settingsFile), "http://localhost:22144")

        val remembered = settings.remember("  https://vault.example.com:8443/  ")
        val reloaded =
            ServerConnectionSettings(ClientSettingsStore(settingsFile), "http://localhost:22144").load()

        assertEquals("https://vault.example.com:8443", remembered)
        assertEquals("https://vault.example.com:8443", reloaded)
    }

    @Test
    fun blankStoredAddressFallsBackInsteadOfBreakingConnectionSetup() {
        val root = Files.createTempDirectory("keystead-server-setting-blank")
        val settingsFile = root.resolve("server-connection.properties")
        Files.writeString(settingsFile, "url=   \n")

        val loaded =
            ServerConnectionSettings(ClientSettingsStore(settingsFile), "http://localhost:22144").load()

        assertEquals("http://localhost:22144", loaded)
    }
}
