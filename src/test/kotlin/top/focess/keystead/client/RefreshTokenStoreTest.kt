package top.focess.keystead.client

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshTokenStoreTest {
    @Test
    fun roundTripsPersistedSession() {
        val store = RefreshTokenStore(PersistentTestStorage())
        val session = persistedSession()

        store.save(session)

        assertEquals(session, store.load())
    }

    @Test
    fun loadReturnsNullWhenAbsent() {
        assertNull(RefreshTokenStore(PersistentTestStorage()).load())
    }

    @Test
    fun clearRemovesPersistedSession() {
        val store = RefreshTokenStore(PersistentTestStorage())
        store.save(persistedSession())

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun memoryOnlyStorageDoesNotPersist() {
        val storage = MemorySecureStorage()
        val store = RefreshTokenStore(storage)

        store.save(persistedSession())

        assertNull(store.load())
        assertTrue(storage.listKeys("auth", "keystead-desktop").isEmpty())
    }

    @Test
    fun nullStorageIsNoOp() {
        val store = RefreshTokenStore(null)
        store.save(persistedSession())
        assertNull(store.load())
        store.clear()
    }

    @Test
    fun corruptBytesLoadAsNull() {
        val storage = PersistentTestStorage()
        storage.save(
            SecureStorageKey("auth", "keystead-desktop", "refresh-token"),
            "not-valid-properties-garbage".encodeToByteArray(),
        )
        val store = RefreshTokenStore(storage)

        assertNull(store.load())
    }

    private fun persistedSession(): PersistedAuthSession =
        PersistedAuthSession(
            baseUrl = "https://server.example.com",
            username = "alice",
            refreshToken = "rt-abc",
            refreshTokenExpiresAt = Instant.parse("2026-08-11T00:00:00Z"),
        )
}

/** A [SecureStorage] backed by an in-memory map but advertising a persistent capability. */
private class PersistentTestStorage : SecureStorage {
    override val capability = SecureStorageCapability.OS_BIOMETRIC_GATED
    private val backing = MemorySecureStorage()

    override fun save(key: SecureStorageKey, value: ByteArray) = backing.save(key, value)

    override fun load(key: SecureStorageKey): ByteArray? = backing.load(key)

    override fun delete(key: SecureStorageKey) = backing.delete(key)

    override fun listKeys(namespace: String, account: String): Set<String> =
        backing.listKeys(namespace, account)
}
