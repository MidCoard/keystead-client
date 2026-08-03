package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

internal class VaultFileDeletionService {
    fun delete(file: Path): Path {
        val normalized = file.toAbsolutePath().normalize()
        require(normalized.fileName.toString().endsWith(".kvault", ignoreCase = true)) {
            "Only Keystead vault files can be deleted here"
        }
        check(Files.exists(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Vault file no longer exists"
        }
        require(Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            "Vault path must be a regular file"
        }
        Files.delete(normalized)
        return normalized
    }
}
