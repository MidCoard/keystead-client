package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path

internal data class BackupRestoreSelection(
    val source: Path?,
    val target: Path?,
) {
    val sourceReady: Boolean
        get() =
            source?.let {
                Files.isRegularFile(it) && it.fileName.toString().endsWith(".ksbackup", ignoreCase = true)
            } == true

    val targetExists: Boolean
        get() = target?.let(Files::exists) == true

    val targetReady: Boolean
        get() =
            target?.let {
                it.fileName.toString().endsWith(".kvault", ignoreCase = true) &&
                    !Files.exists(it) &&
                    it.parent?.let(Files::isDirectory) == true
            } == true

    val canReview: Boolean
        get() = sourceReady && targetReady
}
