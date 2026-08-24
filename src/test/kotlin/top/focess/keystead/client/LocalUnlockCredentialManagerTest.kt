package top.focess.keystead.client

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class LocalUnlockCredentialManagerTest {
    @Test
    fun biometricLocalLoginRoundTripsThroughBiometricGatedStorage() {
        val directory = createTempDirectory("keystead-biometric-login")
        val storage = TestBiometricStorage()
        val createdManager =
            LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        val created =
            createdManager.loadOrCreate(
                SecureStorageMode.BIOMETRIC,
            )
        val publicKey = created.publicKey()
        createdManager.close()

        val reloadedManager =
            LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        val reloaded = reloadedManager.loadExisting()
        assertEquals(LocalLoginPersistence.BIOMETRIC, reloaded.persistence)
        assertContentEquals(publicKey, reloaded.publicKey())

        publicKey.fill(0)
        reloadedManager.close()
    }

    @Test
    fun localLoginCannotUseMemoryOnlyStorage() {
        val manager =
            LocalUnlockCredentialManager(
                createTempDirectory("keystead-memory-login"),
                biometricStorage = { null },
            )

        assertFailsWith<IllegalArgumentException> {
            manager.loadOrCreate(SecureStorageMode.MEMORY_ONLY)
        }
    }

    @Test
    fun existingBiometricCredentialIsWipedImmediatelyAfterOneUse() {
        val directory = createTempDirectory("keystead-one-shot-login")
        val storage = TestBiometricStorage()
        LocalUnlockCredentialManager(directory, biometricStorage = { storage }).use { manager ->
            manager.loadOrCreate(SecureStorageMode.BIOMETRIC)
        }
        storage.resetLoadCount()

        val manager = LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        lateinit var consumed: LocalUnlockCredential
        val result =
            manager.useExistingOnce { credential ->
                consumed = credential
                "used"
            }

        assertEquals("used", result)
        assertEquals(1, storage.loadCount)
        assertNull(manager.currentCredential())
        assertFailsWith<IllegalStateException> { consumed.privateKey() }
        manager.close()
    }

    @Test
    fun biometricStorageIsClosedAfterEachOneShotCredentialUse() {
        val directory = createTempDirectory("keystead-one-shot-storage")
        val storage = TestBiometricStorage()
        val manager = LocalUnlockCredentialManager(directory, biometricStorage = { storage })

        manager.useOrCreateOnce(SecureStorageMode.BIOMETRIC) { }
        assertEquals(1, storage.closeCount)

        manager.useExistingOnce { }
        assertEquals(2, storage.closeCount)
    }

    @Test
    fun existingBiometricCredentialIsWipedWhenItsOneShotUseFails() {
        val directory = createTempDirectory("keystead-failed-one-shot-login")
        val storage = TestBiometricStorage()
        LocalUnlockCredentialManager(directory, biometricStorage = { storage }).use { manager ->
            manager.loadOrCreate(SecureStorageMode.BIOMETRIC)
        }
        storage.resetLoadCount()

        val manager = LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        lateinit var consumed: LocalUnlockCredential
        assertFailsWith<IllegalArgumentException> {
            manager.useExistingOnce { credential ->
                consumed = credential
                throw IllegalArgumentException("unlock failed")
            }
        }

        assertEquals(1, storage.loadCount)
        assertNull(manager.currentCredential())
        assertFailsWith<IllegalStateException> { consumed.privateKey() }
        manager.close()
    }

    private class TestBiometricStorage : SecureStorage, AutoCloseable {
        override val capability = SecureStorageCapability.OS_BIOMETRIC_GATED
        private val values = mutableMapOf<SecureStorageKey, ByteArray>()
        var loadCount: Int = 0
            private set
        var closeCount: Int = 0
            private set

        override fun save(key: SecureStorageKey, value: ByteArray) {
            values.put(key, value.copyOf())?.fill(0)
        }

        override fun load(key: SecureStorageKey): ByteArray? {
            loadCount += 1
            return values[key]?.copyOf()
        }

        override fun delete(key: SecureStorageKey) {
            values.remove(key)?.fill(0)
        }

        fun resetLoadCount() {
            loadCount = 0
        }

        override fun close() {
            closeCount += 1
        }
    }
}
