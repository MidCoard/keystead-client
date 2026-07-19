package top.focess.keystead.client

enum class OsSecretStoreStatus { AVAILABLE, UNSUPPORTED, UNAVAILABLE, LOCKED, ACCESS_DENIED }
enum class OsSecretStoreFailure { UNSUPPORTED, UNAVAILABLE, LOCKED, ACCESS_DENIED, CORRUPT, IO_FAILURE }

data class OsSecretStoreAvailability(val status: OsSecretStoreStatus, val diagnosticCode: String)

class OsSecretStoreException(
    val failure: OsSecretStoreFailure,
    val diagnosticCode: String,
    cause: Throwable? = null,
) : IllegalStateException("OS secure storage failed: $diagnosticCode", cause) {
    override fun toString(): String = "OsSecretStoreException(failure=$failure, diagnosticCode=$diagnosticCode)"
}

interface OsSecretStore {
    val providerId: String
    fun availability(): OsSecretStoreAvailability
    fun save(instanceId: String, secret: ByteArray)
    fun load(instanceId: String): ByteArray?
    fun delete(instanceId: String)
}
