package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class SyncStateStore(private val directory: Path) {
    private val stateFile = directory.resolve("sync-state.properties")

    fun lastPushedRevision(vaultId: String): Long = revision(vaultId, "pushed")

    fun lastPulledRevision(vaultId: String): Long = revision(vaultId, "pulled")

    fun recordPushed(vaultId: String, revision: Long) {
        record(vaultId, "pushed", revision)
    }

    fun recordPulled(vaultId: String, revision: Long) {
        record(vaultId, "pulled", revision)
    }

    private fun revision(vaultId: String, direction: String): Long =
        load().getProperty(key(vaultId, direction))?.toLongOrNull() ?: 0L

    private fun record(vaultId: String, direction: String, revision: Long) {
        require(revision >= 0) { "Sync revision must not be negative" }
        val properties = load()
        val current = properties.getProperty(key(vaultId, direction))?.toLongOrNull() ?: 0L
        if (revision < current) {
            return
        }
        properties.setProperty(key(vaultId, direction), revision.toString())
        Files.createDirectories(directory)
        Files.newOutputStream(stateFile).use { output ->
            properties.store(output, "Keystead sync state")
        }
    }

    private fun load(): Properties {
        val properties = Properties()
        if (Files.exists(stateFile)) {
            Files.newInputStream(stateFile).use(properties::load)
        }
        return properties
    }

    private fun key(vaultId: String, direction: String): String =
        "vault.$vaultId.last${direction.replaceFirstChar { it.uppercase() }}Revision"
}
