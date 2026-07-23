package top.focess.keystead.client

import java.nio.file.Path
import java.security.SecureRandom
import top.focess.keystead.memory.Wipe

data class SecureStorageDiagnostic(
    val providerId: String,
    val failure: OsSecretStoreFailure,
    val diagnosticCode: String,
)

sealed interface SecureStorageSelection {
    data class Available(val storage: SecureStorage, val providerId: String) : SecureStorageSelection
    data class Unavailable(val diagnostic: SecureStorageDiagnostic) : SecureStorageSelection
}

class SecureStorageFactory internal constructor(
    private val osName: String,
    private val providers: (String, Path) -> OsSecretStore?,
    private val random: SecureRandom,
) {
    constructor(osName: String = System.getProperty("os.name")) : this(
        osName,
        { normalized, directory ->
            when {
                normalized.contains("windows") -> WindowsDpapiSecretStore(directory.resolve("dpapi"))
                normalized.contains("mac") || normalized.contains("darwin") -> MacOsKeychainSecretStore()
                normalized.contains("linux") -> LinuxSecretServiceStore()
                else -> null
            }
        },
        SecureRandom(),
    )

    fun native(dataDirectory: Path, instanceId: String): SecureStorageSelection {
        val provider = providers(osName.lowercase(), dataDirectory)
            ?: return unavailable("none", OsSecretStoreFailure.UNSUPPORTED, "native-provider-unsupported")
        val availability = try { provider.availability() } catch (_: Throwable) { return unavailable(provider.providerId, OsSecretStoreFailure.UNAVAILABLE, "native-provider-check-failed") }
        if (availability.status != OsSecretStoreStatus.AVAILABLE) {
            val failure = when (availability.status) {
                OsSecretStoreStatus.UNSUPPORTED -> OsSecretStoreFailure.UNSUPPORTED
                OsSecretStoreStatus.UNAVAILABLE -> OsSecretStoreFailure.UNAVAILABLE
                OsSecretStoreStatus.LOCKED -> OsSecretStoreFailure.LOCKED
                OsSecretStoreStatus.ACCESS_DENIED -> OsSecretStoreFailure.ACCESS_DENIED
                OsSecretStoreStatus.AVAILABLE -> error("unreachable")
            }
            return unavailable(provider.providerId, failure, availability.diagnosticCode)
        }
        return try {
            val storage = NativeSecureStorage(dataDirectory.resolve("secure-storage.ks2"), instanceId, provider, random)
            val key = SecureStorageKey("keystead-probe", instanceId, "availability")
            val value = ByteArray(32).also(random::nextBytes)
            try {
                storage.save(key, value)
                if (!storage.load(key).contentEquals(value)) throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-probe-mismatch")
                storage.delete(key)
            } finally { Wipe.wipe(value) }
            SecureStorageSelection.Available(storage, provider.providerId)
        } catch (error: OsSecretStoreException) {
            unavailable(provider.providerId, error.failure, error.diagnosticCode)
        } catch (_: Throwable) {
            unavailable(provider.providerId, OsSecretStoreFailure.UNAVAILABLE, "native-probe-failed")
        }
    }

    private fun unavailable(providerId: String, failure: OsSecretStoreFailure, code: String) =
        SecureStorageSelection.Unavailable(SecureStorageDiagnostic(providerId, failure, code))
}
