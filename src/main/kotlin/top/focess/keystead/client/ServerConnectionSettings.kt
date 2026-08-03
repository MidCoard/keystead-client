package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/** Persists the selected Keystead Server address independently of login state. */
internal class ServerConnectionSettings(
    private val file: Path,
    fallback: String,
) {
    private val fallback = normalize(fallback)

    fun load(): String =
        runCatching {
            if (!Files.exists(file)) return@runCatching null
            val values = Properties().also { Files.newInputStream(file).use(it::load) }
            normalize(values.getProperty("url").orEmpty()).takeIf(String::isNotBlank)
        }.getOrNull() ?: fallback

    fun remember(serverUrl: String): String {
        val normalized = normalize(serverUrl)
        if (normalized.isBlank()) return fallback
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        Files.writeString(temporary, "url=$normalized\n")
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        return normalized
    }

    private fun normalize(value: String): String = value.trim().trimEnd('/')
}
