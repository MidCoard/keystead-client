package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

class SyncStateStore(private val directory: Path) {
    private val stateFile = directory.resolve("sync-state.properties")

    /** A new local snapshot must never inherit the previous snapshot's watermarks. */
    fun reset(fingerprint: String) {
        val properties = load()
        properties.remove(key(fingerprint, "pushed"))
        properties.remove(serverSequenceKey(fingerprint))
        persist(properties)
    }

    companion object {
        private fun instanceFile(vaultFile: Path): Path {
            val canonical = vaultFile.toAbsolutePath().normalize()
            return canonical.parent.resolve("sync").resolve(RecordDisplayHash.of(canonical.toString()) + ".instance")
        }

        @Synchronized
        fun startNewLocalInstance(vaultFile: Path) {
            val marker = instanceFile(vaultFile)
            Files.createDirectories(marker.parent)
            val temporary = Files.createTempFile(marker.parent, "instance-", ".tmp")
            try {
                Files.writeString(temporary, java.util.UUID.randomUUID().toString())
                Files.move(temporary, marker, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally { Files.deleteIfExists(temporary) }
        }

        @Synchronized
        fun forVault(vaultFile: Path, serverOrigin: String, account: String): SyncStateStore {
            val marker = instanceFile(vaultFile)
            if (!Files.exists(marker)) startNewLocalInstance(vaultFile)
            val instance = Files.readString(marker)
            val scope = listOf(instance, serverOrigin.trim().trimEnd('/'), account)
                .joinToString("") { "${it.length}:$it" }
            return SyncStateStore(marker.parent.resolve(RecordDisplayHash.of(scope)))
        }
    }

    fun lastPushedRevision(fingerprint: String): Long = revision(fingerprint, "pushed")

    fun lastPulledServerSequence(fingerprint: String): Long =
        load().getProperty(serverSequenceKey(fingerprint))?.toLongOrNull() ?: 0L

    fun recordPushed(fingerprint: String, revision: Long) {
        record(fingerprint, "pushed", revision)
    }

    fun recordPulledServerSequence(fingerprint: String, sequence: Long) {
        require(sequence >= 0) { "Server sequence must not be negative" }
        val properties = load()
        val key = serverSequenceKey(fingerprint)
        val current = properties.getProperty(key)?.toLongOrNull() ?: 0L
        if (sequence < current) return
        properties.setProperty(key, sequence.toString())
        persist(properties)
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
        persist(properties)
    }

    private fun persist(properties: Properties) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "sync-state-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output -> properties.store(output, "Keystead sync state") }
            Files.move(temporary, stateFile, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
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

    private fun serverSequenceKey(fingerprint: String): String =
        "vault.$fingerprint.lastPulledServerSequence"
}
