package top.focess.keystead.client

import com.sun.jna.platform.win32.WinDef
import java.nio.file.Path
import java.security.SecureRandom

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
    constructor(
        osName: String = System.getProperty("os.name"),
        windowHandle: () -> WinDef.HWND? = { null },
    ) : this(
        osName,
        { normalized, directory ->
            when {
                normalized.contains("windows") ->
                    WindowsHelloSecretStore(
                        directory.resolve("windows-hello"),
                        JnaWindowsHelloPort(windowHandle),
                    )
                else -> null
            }
        },
        SecureRandom(),
    )

    fun biometric(dataDirectory: Path, instanceId: String): SecureStorageSelection {
        if (!osName.lowercase().contains("windows")) {
            return unavailable(
                "none",
                OsSecretStoreFailure.UNSUPPORTED,
                "biometric-provider-unsupported",
            )
        }
        val provider =
            providers(osName.lowercase(), dataDirectory)
                ?: return unavailable(
                    "none",
                    OsSecretStoreFailure.UNSUPPORTED,
                    "biometric-provider-unsupported",
                )
        val availability =
            try {
                provider.availability()
            } catch (_: Throwable) {
                return unavailable(
                    provider.providerId,
                    OsSecretStoreFailure.UNAVAILABLE,
                    "biometric-provider-check-failed",
                )
            }
        if (availability.status != OsSecretStoreStatus.AVAILABLE) {
            val failure =
                when (availability.status) {
                    OsSecretStoreStatus.UNSUPPORTED -> OsSecretStoreFailure.UNSUPPORTED
                    OsSecretStoreStatus.UNAVAILABLE -> OsSecretStoreFailure.UNAVAILABLE
                    OsSecretStoreStatus.LOCKED -> OsSecretStoreFailure.LOCKED
                    OsSecretStoreStatus.ACCESS_DENIED -> OsSecretStoreFailure.ACCESS_DENIED
                    OsSecretStoreStatus.AVAILABLE -> error("unreachable")
                }
            return unavailable(provider.providerId, failure, availability.diagnosticCode)
        }
        return SecureStorageSelection.Available(
            LazySecureStorage(SecureStorageCapability.OS_BIOMETRIC_GATED) {
                NativeSecureStorage(
                    dataDirectory.resolve("windows-hello-storage.ks2"),
                    instanceId,
                    provider,
                    random,
                )
            },
            provider.providerId,
        )
    }

    private fun unavailable(providerId: String, failure: OsSecretStoreFailure, code: String) =
        SecureStorageSelection.Unavailable(SecureStorageDiagnostic(providerId, failure, code))
}

private class LazySecureStorage(
    override val capability: SecureStorageCapability,
    private val open: () -> SecureStorage,
) : SecureStorage, AutoCloseable {
    private var delegate: SecureStorage? = null

    @Synchronized
    private fun storage(): SecureStorage = delegate ?: open().also { delegate = it }

    override fun save(key: SecureStorageKey, value: ByteArray) = storage().save(key, value)

    override fun load(key: SecureStorageKey): ByteArray? = storage().load(key)

    override fun delete(key: SecureStorageKey) = storage().delete(key)

    override fun listKeys(namespace: String, account: String): Set<String> =
        storage().listKeys(namespace, account)

    @Synchronized
    override fun close() {
        (delegate as? AutoCloseable)?.close()
        delegate = null
    }
}
