package top.focess.keystead.client

import java.nio.file.Path

internal object VaultFileSelection {
    fun newTarget(selected: Path): Path {
        val name = selected.fileName.toString()
        if (name.endsWith(".kvault", ignoreCase = true)) return selected
        return selected.resolveSibling("$name.kvault")
    }
}
