package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.Properties

/**
 * Persisted refresh-token credentials for a single Keystead Server account.
 *
 * Stores only the (rotating) refresh token plus the metadata required to rebuild
 * a [KeysteadServerAuthClient] / [ServerAuthSession] - never the password and never the
 * short-lived access token (the access token is re-minted on restore via `refresh`).
 */
internal data class PersistedAuthSession(
    val baseUrl: String,
    val username: String,
    val deviceId: String?,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
)

/**
 * Persists a [PersistedAuthSession] in the supplied [SecureStorage] so the client can
 * restore an authenticated server session across launches without keeping the password.
 *
 * All persistence is gated on a non-null storage whose capability is not
 * [SecureStorageCapability.MEMORY_ONLY]: when storage is absent or memory-only the store
 * is a no-op so refresh-token persistence never silently downgrades to an in-process map.
 */
internal class RefreshTokenStore(
    private val storage: SecureStorage?,
    private val key: SecureStorageKey =
        SecureStorageKey("auth", "keystead-desktop", "refresh-token"),
) {
    fun save(session: PersistedAuthSession) {
        val store = storage ?: return
        if (store.capability == SecureStorageCapability.MEMORY_ONLY) return
        store.save(key, encode(session))
    }

    fun load(): PersistedAuthSession? {
        val store = storage ?: return null
        if (store.capability == SecureStorageCapability.MEMORY_ONLY) return null
        val bytes = store.load(key) ?: return null
        return decode(bytes)
    }

    fun clear() {
        storage?.delete(key)
    }

    private fun encode(session: PersistedAuthSession): ByteArray {
        val properties = Properties()
        properties.setProperty("baseUrl", session.baseUrl)
        properties.setProperty("username", session.username)
        properties.setProperty("deviceId", session.deviceId.orEmpty())
        properties.setProperty("refreshToken", session.refreshToken)
        properties.setProperty("refreshTokenExpiresAt", session.refreshTokenExpiresAt.toString())
        val out = ByteArrayOutputStream()
        properties.store(out, "keystead-refresh-token")
        return out.toByteArray()
    }

    private fun decode(bytes: ByteArray): PersistedAuthSession? =
        try {
            val properties = Properties()
            ByteArrayInputStream(bytes).use { properties.load(it) }
            val baseUrl = properties.getProperty("baseUrl") ?: return null
            val username = properties.getProperty("username") ?: return null
            val refreshToken = properties.getProperty("refreshToken") ?: return null
            val expiresAt = properties.getProperty("refreshTokenExpiresAt") ?: return null
            val deviceId = properties.getProperty("deviceId")?.takeIf { it.isNotEmpty() }
            PersistedAuthSession(baseUrl, username, deviceId, refreshToken, Instant.parse(expiresAt))
        } catch (_: RuntimeException) {
            null
        }
}
