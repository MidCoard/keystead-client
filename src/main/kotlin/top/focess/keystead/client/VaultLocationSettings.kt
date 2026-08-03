package top.focess.keystead.client

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

/**
 * Remembers the last vault path that completed an open, create, or recovery operation.
 *
 * The value is stored as a file URI so Windows path separators and spaces survive a
 * java-properties round trip without custom escaping.
 */
internal class VaultLocationSettings(
    private val file: Path,
    private val fallback: Path,
) {
    fun load(): Path =
        runCatching {
            if (!Files.exists(file)) return@runCatching null
            val values = Properties().also { Files.newInputStream(file).use(it::load) }
            val uri = URI.create(values.getProperty("uri") ?: return@runCatching null)
            require(uri.scheme.equals("file", ignoreCase = true))
            Path.of(uri).toAbsolutePath().normalize()
        }.getOrNull() ?: fallback.toAbsolutePath().normalize()

    fun rememberSuccessfulVault(vaultFile: Path): Path {
        val normalized = vaultFile.toAbsolutePath().normalize()
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        Files.writeString(temporary, "uri=${normalized.toUri().toASCIIString()}\n")
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        return normalized
    }

    fun clear() {
        Files.deleteIfExists(file)
    }
}
