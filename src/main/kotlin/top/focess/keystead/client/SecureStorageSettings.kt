package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class SecureStorageMode { NATIVE, PASSPHRASE_FILE, MEMORY_ONLY }
data class PersistedSecureStorageSelection(val mode: SecureStorageMode, val providerId: String?)

class SecureStorageSettings(private val file: Path) {
    fun load(): PersistedSecureStorageSelection? {
        if (!Files.exists(file)) return null
        val values = Properties().also { Files.newInputStream(file).use(it::load) }
        val mode = runCatching { SecureStorageMode.valueOf(values.getProperty("mode")) }.getOrNull() ?: return null
        return PersistedSecureStorageSelection(mode, values.getProperty("providerId")?.takeIf(String::isNotBlank))
    }

    fun save(selection: PersistedSecureStorageSelection) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        val text = buildString { append("mode=").append(selection.mode.name).append('\n'); selection.providerId?.let { append("providerId=").append(it).append('\n') } }
        Files.writeString(temporary, text)
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
    }
}
