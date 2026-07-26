package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class SyncStateStore(private val directory: Path) {
    private val stateFile = directory.resolve("sync-state.properties")

    fun lastPushedRevision(fingerprint: String): Long = revision(fingerprint, "pushed")

    fun lastPulledRevision(fingerprint: String): Long = revision(fingerprint, "pulled")

    fun recordPushed(fingerprint: String, revision: Long) {
        record(fingerprint, "pushed", revision)
    }

    fun recordPulled(fingerprint: String, revision: Long) {
        record(fingerprint, "pulled", revision)
    }

    private fun revision(fingerprint: String, direction: String): Long =
        load().getProperty(key(fingerprint, direction))?.toLongOrNull() ?: 0L

    private fun record(fingerprint: String, direction: String, revision: Long) {
        require(revision >= 0) { "Sync revision must not be negative" }
        val properties = load()
        val current = properties.getProperty(key(fingerprint, direction))?.toLongOrNull() ?: 0L
        if (revision < current) {
            return
        }
        properties.setProperty(key(fingerprint, direction), revision.toString())
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

    private fun key(fingerprint: String, direction: String): String =
        "vault.$fingerprint.last${direction.replaceFirstChar { it.uppercase() }}Revision"
}
