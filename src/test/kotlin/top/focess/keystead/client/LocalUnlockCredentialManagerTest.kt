package top.focess.keystead.client

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LocalUnlockCredentialManagerTest {
    @Test
    fun passphraseLocalLoginRoundTripsWithoutDeviceOrProofIdentityMetadata() {
        val directory = createTempDirectory("keystead-local-login")
        val manager = LocalUnlockCredentialManager(directory, biometricStorage = { null })
        val credential =
            manager.loadOrCreate(
                SecureStorageMode.PASSPHRASE_FILE,
                "local-passphrase".toCharArray(),
            )
        val publicKey = credential.publicKey()
        val descriptor = requireNotNull(manager.descriptor())

        assertEquals(LocalLoginPersistence.PASSPHRASE_FILE, descriptor.persistence)
        assertFalse(descriptor.keyFingerprint.isBlank())
        val metadata = Files.readString(directory.resolve("local-login.properties"))
        assertFalse(metadata.contains("deviceId", ignoreCase = true))
        assertFalse(metadata.contains("deviceName", ignoreCase = true))
        assertFalse(metadata.contains("proof", ignoreCase = true))

        manager.unload()
        assertNull(manager.currentCredential())
        assertFailsWith<RuntimeException> {
            manager.loadExisting("wrong-passphrase".toCharArray())
        }
        val reloaded = manager.loadExisting("local-passphrase".toCharArray())
        assertContentEquals(publicKey, reloaded.publicKey())
        assertEquals(descriptor.keyFingerprint, manager.descriptor()?.keyFingerprint)

        publicKey.fill(0)
        manager.close()
    }

    @Test
    fun biometricLocalLoginRoundTripsThroughBiometricGatedStorage() {
        val directory = createTempDirectory("keystead-biometric-login")
        val storage = TestBiometricStorage()
        val createdManager =
            LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        val created =
            createdManager.loadOrCreate(
                SecureStorageMode.BIOMETRIC,
                charArrayOf(),
            )
        val publicKey = created.publicKey()
        createdManager.close()

        val reloadedManager =
            LocalUnlockCredentialManager(directory, biometricStorage = { storage })
        val reloaded = reloadedManager.loadExisting(charArrayOf())
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
            manager.loadOrCreate(SecureStorageMode.MEMORY_ONLY, charArrayOf())
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
