package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.Properties

class LocalLoginEnrollmentStore(
    private val file: Path,
) {
    @Synchronized
    fun remember(
        vaultFingerprint: String,
        slotKeyId: String,
        credentialFingerprint: String,
    ) {
        require(vaultFingerprint.isNotBlank()) { "Vault fingerprint must not be blank" }
        require(slotKeyId.isNotBlank()) { "Local-login slot id must not be blank" }
        require(credentialFingerprint.isNotBlank()) {
            "Local-login credential fingerprint must not be blank"
        }
        val properties = load()
        val prefix = prefix(vaultFingerprint)
        properties.setProperty("$prefix.slot", slotKeyId)
        properties.setProperty("$prefix.credential", credentialFingerprint)
        save(properties)
    }

    @Synchronized
    fun isEnrolled(
        vaultFingerprint: String,
        slotKeyIds: Set<String>,
        credentialFingerprint: String,
    ): Boolean {
        if (vaultFingerprint.isBlank() || credentialFingerprint.isBlank()) return false
        val properties = load()
        val prefix = prefix(vaultFingerprint)
        val slot = properties.getProperty("$prefix.slot") ?: return false
        val credential = properties.getProperty("$prefix.credential") ?: return false
        return slot in slotKeyIds && credential == credentialFingerprint
    }

    @Synchronized
    fun clear(vaultFingerprint: String) {
        if (vaultFingerprint.isBlank() || !Files.exists(file)) return
        val properties = load()
        val prefix = prefix(vaultFingerprint)
        properties.remove("$prefix.slot")
        properties.remove("$prefix.credential")
        save(properties)
    }

    private fun prefix(vaultFingerprint: String): String = "vault.$vaultFingerprint"

    private fun load(): Properties =
        Properties().also { properties ->
            if (Files.exists(file)) {
                Files.newInputStream(file).use(properties::load)
            }
        }

    private fun save(properties: Properties) {
        file.parent?.let(Files::createDirectories)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        Files.newOutputStream(temporary, CREATE, TRUNCATE_EXISTING, WRITE).use { output ->
            properties.store(output, "Keystead local login enrollments")
        }
        try {
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary, file, REPLACE_EXISTING)
        }
    }
}
