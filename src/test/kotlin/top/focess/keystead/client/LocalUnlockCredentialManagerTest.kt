package top.focess.keystead.client

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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

    private class TestBiometricStorage : SecureStorage {
        override val capability = SecureStorageCapability.OS_BIOMETRIC_GATED
        private val values = mutableMapOf<SecureStorageKey, ByteArray>()

        override fun save(key: SecureStorageKey, value: ByteArray) {
            values.put(key, value.copyOf())?.fill(0)
        }

        override fun load(key: SecureStorageKey): ByteArray? = values[key]?.copyOf()

        override fun delete(key: SecureStorageKey) {
            values.remove(key)?.fill(0)
        }
    }
}
