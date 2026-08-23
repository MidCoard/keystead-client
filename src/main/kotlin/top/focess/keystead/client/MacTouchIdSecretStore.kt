package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import top.focess.keystead.memory.Wipe

internal interface MacTouchIdPort {
    fun availability(): OsSecretStoreAvailability
    fun save(account: String, secret: ByteArray)
    fun load(account: String, authenticationReason: String): ByteArray?
    fun delete(account: String)
}

class MacTouchIdSecretStore internal constructor(
    private val port: MacTouchIdPort,
    private val authenticationReason: () -> String = { "Unlock Keystead with Touch ID." },
) : OsSecretStore {
    override val providerId: String = "mac-touch-id"

    override fun availability(): OsSecretStoreAvailability = port.availability()

    override fun save(instanceId: String, secret: ByteArray) {
        requireInstanceId(instanceId)
        require(secret.isNotEmpty() && secret.size <= MAX_SECRET_BYTES) {
            "Touch ID secret has an invalid size"
        }
        requireAvailable()
        port.save(instanceId, secret)
    }

    override fun load(instanceId: String): ByteArray? {
        requireInstanceId(instanceId)
        requireAvailable()
        return port.load(instanceId, authenticationReason())?.also {
            if (it.isEmpty() || it.size > MAX_SECRET_BYTES) {
                Wipe.wipe(it)
                throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "mac-touch-id-secret-invalid")
            }
        }
    }

    override fun delete(instanceId: String) {
        requireInstanceId(instanceId)
        requireAvailable()
        port.delete(instanceId)
    }

    private fun requireAvailable() {
        val availability = availability()
        if (availability.status == OsSecretStoreStatus.AVAILABLE) return
        throw OsSecretStoreException(availability.status.failure(), availability.diagnosticCode)
    }

    private fun requireInstanceId(instanceId: String) {
        require(instanceId.isNotBlank() && instanceId.length <= 255 && instanceId.none(Char::isISOControl)) {
            "Touch ID account is invalid"
        }
    }

    private companion object {
        const val MAX_SECRET_BYTES = 4096
    }
}

internal class ProcessMacTouchIdPort(
    private val helper: Path,
) : MacTouchIdPort {
    override fun availability(): OsSecretStoreAvailability {
        if (!Files.isRegularFile(helper) || !Files.isExecutable(helper)) {
            return OsSecretStoreAvailability(OsSecretStoreStatus.UNAVAILABLE, "mac-touch-id-helper-missing")
        }
        val result = execute("availability", null, null)
        return try {
            if (result.exitCode == 0) {
                OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "mac-touch-id-available")
            } else {
                OsSecretStoreAvailability(result.status(), result.diagnostic("mac-touch-id-unavailable"))
            }
        } finally {
            result.wipe()
        }
    }

    override fun save(account: String, secret: ByteArray) {
        val result = execute("save", account, secret)
        try {
            result.requireSuccess("mac-touch-id-save-failed")
        } finally {
            result.wipe()
        }
    }

    override fun load(account: String, authenticationReason: String): ByteArray? {
        val result = execute("load", account, null, authenticationReason)
        if (result.exitCode == EXIT_NOT_FOUND) {
            result.wipe()
            return null
        }
        try {
            result.requireSuccess("mac-touch-id-load-failed")
            return result.stdout
        } finally {
            if (result.exitCode != 0) Wipe.wipe(result.stdout)
            Wipe.wipe(result.stderr)
        }
    }

    override fun delete(account: String) {
        val result = execute("delete", account, null)
        try {
            if (result.exitCode != EXIT_NOT_FOUND) result.requireSuccess("mac-touch-id-delete-failed")
        } finally {
            result.wipe()
        }
    }

    private fun execute(
        command: String,
        account: String?,
        input: ByteArray?,
        authenticationReason: String? = null,
    ): CommandResult {
        val arguments = mutableListOf(helper.toString(), command)
        account?.let(arguments::add)
        val process =
            try {
                ProcessBuilder(arguments).also { builder ->
                    authenticationReason?.let {
                        builder.environment()[AUTHENTICATION_REASON_ENV] = it
                    }
                }.start()
            } catch (error: Exception) {
                throw OsSecretStoreException(
                    OsSecretStoreFailure.UNAVAILABLE,
                    "mac-touch-id-helper-start-failed",
                    error,
                )
        }
        try {
            process.outputStream.use { output -> input?.let(output::write) }
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw OsSecretStoreException(OsSecretStoreFailure.UNAVAILABLE, "mac-touch-id-helper-timeout")
            }
            val stdout = process.inputStream.use { it.readAllBytes() }
            val stderr = process.errorStream.use { it.readAllBytes() }
            return CommandResult(process.exitValue(), stdout, stderr)
        } finally {
            runCatching { process.outputStream.close() }
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: ByteArray,
        val stderr: ByteArray,
    ) {
        fun requireSuccess(fallbackCode: String) {
            if (exitCode != 0) {
                throw OsSecretStoreException(status().failure(), diagnostic(fallbackCode))
            }
        }

        fun wipe() {
            Wipe.wipe(stdout)
            Wipe.wipe(stderr)
        }

        fun status(): OsSecretStoreStatus =
            when (exitCode) {
                10 -> OsSecretStoreStatus.UNSUPPORTED
                12 -> OsSecretStoreStatus.LOCKED
                13 -> OsSecretStoreStatus.ACCESS_DENIED
                else -> OsSecretStoreStatus.UNAVAILABLE
            }

        fun diagnostic(fallback: String): String {
            val candidate = stderr.toString(Charsets.US_ASCII).trim()
            return candidate.takeIf {
                it.length in 1..96 && it.all { character -> character.isLetterOrDigit() || character in "-_" }
            } ?: fallback
        }
    }

    private companion object {
        const val EXIT_NOT_FOUND = 44
        const val COMMAND_TIMEOUT_SECONDS = 90L
        const val AUTHENTICATION_REASON_ENV = "KEYSTEAD_TOUCH_ID_REASON"
    }
}

internal object MacTouchIdHelperLocator {
    fun resolve(dataDirectory: Path): Path {
        val packagedResources = System.getProperty("compose.application.resources.dir")
        if (!packagedResources.isNullOrBlank()) {
            val packaged = Path.of(packagedResources).resolve("keystead-mac-secure-store")
            if (Files.isRegularFile(packaged)) return packaged
        }
        val development = Path.of("build/app-resources/macos/keystead-mac-secure-store")
        if (Files.isRegularFile(development)) return development.toAbsolutePath().normalize()
        return dataDirectory.resolve("macos/keystead-mac-secure-store")
    }
}

private fun OsSecretStoreStatus.failure(): OsSecretStoreFailure =
    when (this) {
        OsSecretStoreStatus.UNSUPPORTED -> OsSecretStoreFailure.UNSUPPORTED
        OsSecretStoreStatus.UNAVAILABLE -> OsSecretStoreFailure.UNAVAILABLE
        OsSecretStoreStatus.LOCKED -> OsSecretStoreFailure.LOCKED
        OsSecretStoreStatus.ACCESS_DENIED -> OsSecretStoreFailure.ACCESS_DENIED
        OsSecretStoreStatus.AVAILABLE -> error("Available status has no failure")
    }
