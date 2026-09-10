package top.focess.keystead.client

import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import top.focess.keystead.client.i18n.AppLocale

@EnabledOnOs(OS.WINDOWS)
class WindowsTrayTest {
    @Test
    fun nativeAwtPreservesChineseAndEnglishWithUtf8AndPackagedModules() {
        val config = requireNotNull(System.getProperty("keystead.test.awtFontConfig"))
        for (language in listOf("en", "zh")) {
            val process = ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dfile.encoding=UTF-8",
                "-Duser.language=$language",
                "-Duser.country=${if (language == "zh") "CN" else "US"}",
                "-Dsun.awt.fontconfig=$config",
                "--limit-modules=java.desktop,java.logging,java.net.http,jdk.crypto.ec",
                "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
                "--add-exports=java.desktop/sun.awt=ALL-UNNAMED",
                "-cp",
                Path.of(WindowsTrayFontProbe::class.java.protectionDomain.codeSource.location.toURI()).toString(),
                WindowsTrayFontProbe::class.java.name,
                *AppLocale.entries.flatMap { locale ->
                    with(locale.strings) { listOf(openApplication, lock, quit) }
                }.toTypedArray(),
            ).redirectErrorStream(true).start()
            try {
                assertTrue(process.waitFor(30, TimeUnit.SECONDS), "AWT font probe timed out for $language")
                val output = process.inputStream.bufferedReader().readText()
                assertEquals(0, process.exitValue(), output)
            } finally {
                process.destroy()
            }
        }
    }
}
