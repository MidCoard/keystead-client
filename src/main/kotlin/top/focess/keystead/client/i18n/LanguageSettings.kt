package top.focess.keystead.client.i18n

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/**
 * Persists the chosen [AppLocale] in a tiny properties file so the language survives restarts.
 *
 * Mirrors [top.focess.keystead.client.SecureStorageSettings]: atomic write via a sibling temp
 * file, lenient read (a missing or unreadable file falls back to the system locale rather than
 * throwing on startup).
 */
internal class LanguageSettings(private val file: Path) {
    fun load(): AppLocale? {
        if (!Files.exists(file)) return null
        val values = Properties().also { Files.newInputStream(file).use(it::load) }
        val tag = values.getProperty("locale")?.takeIf(String::isNotBlank) ?: return null
        return runCatching { AppLocale.forLanguageTag(tag) }.getOrNull()
    }

    fun save(locale: AppLocale) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        Files.writeString(temporary, "locale=${locale.languageTag}\n")
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
    }
}
