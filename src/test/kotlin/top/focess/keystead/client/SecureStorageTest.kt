package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SecureStorageTest {
    private val key = SecureStorageKey("keystead", "device-1", "refresh-token")

    @Test fun osStoreFailuresAreStableAndRedacted() {
        val error = OsSecretStoreException(OsSecretStoreFailure.LOCKED, "native-provider-locked")
        assertEquals(OsSecretStoreFailure.LOCKED, error.failure)
        assertFalse(error.toString().contains("secret-value"))
    }

    @Test fun secureStorageKeyRejectsPathAndControlCharacters() {
        assertFailsWith<IllegalArgumentException> { SecureStorageKey("keystead", "a/b", "token") }
        assertFailsWith<IllegalArgumentException> { SecureStorageKey("keystead", "a\u0000b", "token") }
    }

    @Test fun memoryStorageCopiesValuesAndClearsOnDelete() {
        val storage = MemorySecureStorage(); val original = byteArrayOf(1, 2, 3)
        storage.save(key, original); original[0] = 9
        assertContentEquals(byteArrayOf(1, 2, 3), storage.load(key)); assertEquals(setOf("refresh-token"), storage.listKeys("keystead", "device-1"))
        storage.delete(key); assertNull(storage.load(key)); assertEquals(emptySet(), storage.listKeys("keystead", "device-1"))
    }

    @Test fun passphraseFileStorageRoundTripsWithoutPlaintext() {
        val file = Files.createTempDirectory("keystead-secure-storage").resolve("secrets.properties")
        val secret = "opaque-refresh-token".toByteArray()
        PassphraseFileSecureStorage(file, "passphrase".toCharArray()).save(key, secret)
        assertEquals(SecureStorageCapability.FILE_PASSPHRASE_PROTECTED, PassphraseFileSecureStorage(file, "passphrase".toCharArray()).capability)
        assertContentEquals(secret, PassphraseFileSecureStorage(file, "passphrase".toCharArray()).load(key))
        kotlin.test.assertFalse(String(Files.readAllBytes(file)).contains("opaque-refresh-token"))
    }

    @Test fun identityStoreUsesOptionalSecureStorageAndReportsFallback() {
        val fallback = DeviceIdentityStore(Files.createTempDirectory("keystead-identity-fallback"))
        assertEquals(SecureStorageCapability.FILE_PASSPHRASE_PROTECTED, fallback.secureStorageCapability)
        val memory = MemorySecureStorage()
        val store = DeviceIdentityStore(Files.createTempDirectory("keystead-identity-memory"), secureStorage = memory)
        assertEquals(SecureStorageCapability.MEMORY_ONLY, store.secureStorageCapability)
        val value = byteArrayOf(7, 8); store.saveSecureSecret(key, value); value[0] = 0
        assertContentEquals(byteArrayOf(7, 8), store.loadSecureSecret(key)); store.deleteSecureSecret(key); assertNull(store.loadSecureSecret(key))
    }
}
