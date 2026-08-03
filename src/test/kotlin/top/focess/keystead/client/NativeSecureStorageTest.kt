package top.focess.keystead.client

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

class NativeSecureStorageTest {
    private val testKey = SecureStorageKey("keystead", "device-1", "refresh-token")

    @Test
    fun nativeStoreRoundTripsWithoutPlaintextNamesAndCopiesValues() {
        val os = FakeOsSecretStore()
        val file = createTempDirectory().resolve("native.ks2")
        val original = byteArrayOf(1, 2, 3)
        NativeSecureStorage(file, "desktop", os).use { storage -> storage.save(testKey, original) }
        original[0] = 9
        NativeSecureStorage(file, "desktop", os).use { storage ->
            val loaded = storage.load(testKey)!!
            assertContentEquals(byteArrayOf(1, 2, 3), loaded)
            loaded[0] = 8
            assertContentEquals(byteArrayOf(1, 2, 3), storage.load(testKey))
            assertEquals(SecureStorageCapability.OS_BIOMETRIC_GATED, storage.capability)
        }
        assertFalse(Files.readAllBytes(file).toString(Charsets.ISO_8859_1).contains("refresh-token"))
    }

    @Test
    fun ciphertextWithoutNativeKeyIsCorruptNotEmpty() {
        val os = FakeOsSecretStore()
        val file = createTempDirectory().resolve("native.ks2")
        NativeSecureStorage(file, "desktop", os).use { it.save(testKey, byteArrayOf(9)) }
        os.delete("desktop")
        val failure = assertFailsWith<OsSecretStoreException> { NativeSecureStorage(file, "desktop", os) }
        assertEquals(OsSecretStoreFailure.CORRUPT, failure.failure)
    }

    @Test
    fun tamperAndWrongInstanceAreRejectedAndDestroyDeletesBothParts() {
        val os = FakeOsSecretStore()
        val directory = createTempDirectory()
        val file = directory.resolve("native.ks2")
        NativeSecureStorage(file, "desktop", os).use { it.save(testKey, byteArrayOf(9)) }
        assertEquals(OsSecretStoreFailure.CORRUPT, assertFailsWith<OsSecretStoreException> { NativeSecureStorage(file, "other", os) }.failure)
        val bytes = Files.readAllBytes(file); bytes[bytes.lastIndex] = (bytes.last() + 1).toByte(); Files.write(file, bytes)
        assertEquals(OsSecretStoreFailure.CORRUPT, assertFailsWith<OsSecretStoreException> { NativeSecureStorage(file, "desktop", os) }.failure)

        Files.delete(file)
        NativeSecureStorage(file, "desktop", os).use { storage -> storage.save(testKey, byteArrayOf(4)); storage.destroy() }
        assertFalse(Files.exists(file))
        assertEquals(null, os.load("desktop"))
    }

    @Test
    fun failedOverwriteRestoresPreviousInMemoryValue() {
        val os = FakeOsSecretStore()
        val file = createTempDirectory().resolve("native.ks2")
        val writer = FailAfterOneWrite()
        NativeSecureStorage(file, "desktop", os, java.security.SecureRandom(), writer).use {
            storage ->
            storage.save(testKey, byteArrayOf(1, 2, 3))

            assertFailsWith<OsSecretStoreException> {
                storage.save(testKey, byteArrayOf(9, 9, 9))
            }

            assertContentEquals(byteArrayOf(1, 2, 3), storage.load(testKey))
        }
    }

    @Test
    fun failedDeleteRestoresRemovedInMemoryValue() {
        val os = FakeOsSecretStore()
        val file = createTempDirectory().resolve("native.ks2")
        val writer = FailAfterOneWrite()
        NativeSecureStorage(file, "desktop", os, java.security.SecureRandom(), writer).use {
            storage ->
            storage.save(testKey, byteArrayOf(4, 5, 6))

            assertFailsWith<OsSecretStoreException> { storage.delete(testKey) }

            assertContentEquals(byteArrayOf(4, 5, 6), storage.load(testKey))
        }
    }

    private class FailAfterOneWrite : SecureStorageFileWriter {
        private var writes = 0

        override fun write(file: java.nio.file.Path, encoded: ByteArray) {
            if (writes++ > 0) throw java.io.IOException("simulated-write-failure")
            Files.createDirectories(file.toAbsolutePath().parent)
            Files.write(file, encoded)
        }
    }
}

private class FakeOsSecretStore : OsSecretStore {
    override val providerId = "fake"
    private val values = mutableMapOf<String, ByteArray>()
    override fun availability() = OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "fake-available")
    override fun save(instanceId: String, secret: ByteArray) { values.put(instanceId, secret.copyOf())?.fill(0) }
    override fun load(instanceId: String): ByteArray? = values[instanceId]?.copyOf()
    override fun delete(instanceId: String) { values.remove(instanceId)?.fill(0) }
}
