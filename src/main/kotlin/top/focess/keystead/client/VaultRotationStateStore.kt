package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.Properties

enum class LocalRotationStage { PACKAGING, PACKAGED, LOCAL_COMMITTED }

data class LocalRotationState(
    val vaultId: String,
    val generationId: String,
    val sourceVaultKeyId: String,
    val targetVaultKeyId: String,
    val deviceId: String,
    val stage: LocalRotationStage,
)

class VaultRotationStateStore(private val file: Path) {
    @Synchronized
    fun save(state: LocalRotationState) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.tmp")
        val body = "vaultId=${state.vaultId}\ngenerationId=${state.generationId}\nsourceVaultKeyId=${state.sourceVaultKeyId}\ntargetVaultKeyId=${state.targetVaultKeyId}\ndeviceId=${state.deviceId}\nstage=${state.stage.name}\n"
        Files.writeString(temporary, body)
        Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
    }

    @Synchronized
    fun load(): LocalRotationState? {
        if (!Files.exists(file)) return null
        val values = Properties().also { Files.newInputStream(file).use(it::load) }
        return LocalRotationState(
            values.required("vaultId"), values.required("generationId"),
            values.required("sourceVaultKeyId"), values.required("targetVaultKeyId"),
            values.required("deviceId"), LocalRotationStage.valueOf(values.required("stage")),
        )
    }

    @Synchronized fun clear() { Files.deleteIfExists(file) }
    private fun Properties.required(name: String) = getProperty(name)?.takeIf(String::isNotBlank) ?: error("Rotation state is invalid")
}
