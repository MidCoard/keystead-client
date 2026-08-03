package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
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

    @Test fun passphraseFileStorageWritesBinaryAndRoundTripsWithoutPlaintext() {
        val file = Files.createTempDirectory("keystead-secure-storage").resolve("secrets.properties")
        val secret = "opaque-refresh-token".toByteArray()
        PassphraseFileSecureStorage(file, "passphrase".toCharArray()).use { storage ->
            storage.save(key, secret)
            assertEquals(SecureStorageCapability.FILE_PASSPHRASE_PROTECTED, storage.capability)
        }
        val encoded = Files.readAllBytes(file)
        assertContentEquals(byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), '2'.code.toByte()), encoded.copyOf(4))
        assertFalse(String(encoded, StandardCharsets.ISO_8859_1).contains("opaque-refresh-token"))
        PassphraseFileSecureStorage(file, "passphrase".toCharArray()).use { storage ->
            assertContentEquals(secret, storage.load(key))
        }
    }

    @Test fun passphraseFileStorageReadsLegacyFormatAndUpgradesOnMutation() {
        val file = Files.createTempDirectory("keystead-secure-storage-legacy").resolve("secrets.properties")
        writeLegacyPassphraseFile(file, "passphrase".toCharArray(), key, byteArrayOf(7, 8, 9))

        PassphraseFileSecureStorage(file, "passphrase".toCharArray()).use { storage ->
            assertContentEquals(byteArrayOf(7, 8, 9), storage.load(key))
            storage.save(SecureStorageKey("keystead", "device-1", "second"), byteArrayOf(4))
        }

        assertContentEquals(
            byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'P'.code.toByte(), '2'.code.toByte()),
            Files.readAllBytes(file).copyOf(4),
        )
    }

    @Test fun passphraseFileStorageReadsLegacyEmptyValue() {
        val file =
            Files.createTempDirectory("keystead-secure-storage-legacy-empty")
                .resolve("secrets.properties")
        writeLegacyPassphraseFile(file, "passphrase".toCharArray(), key, byteArrayOf())

        PassphraseFileSecureStorage(file, "passphrase".toCharArray()).use { storage ->
            assertContentEquals(byteArrayOf(), storage.load(key))
        }
    }

    @Test fun closedOrTruncatedPassphraseStorageFailsClosed() {
        val directory = Files.createTempDirectory("keystead-secure-storage-closed")
        val file = directory.resolve("secrets.ks2")
        val storage = PassphraseFileSecureStorage(file, "passphrase".toCharArray())
        storage.save(key, byteArrayOf(1, 2, 3))
        storage.close()
        assertFailsWith<IllegalStateException> { storage.load(key) }

        val encoded = Files.readAllBytes(file)
        Files.write(file, encoded.copyOf(8))
        val error =
            assertFailsWith<OsSecretStoreException> {
                PassphraseFileSecureStorage(file, "passphrase".toCharArray())
            }
        assertEquals(OsSecretStoreFailure.CORRUPT, error.failure)
    }

    private fun writeLegacyPassphraseFile(
        file: java.nio.file.Path,
        passphrase: CharArray,
        key: SecureStorageKey,
        value: ByteArray,
    ) {
        val salt = ByteArray(16) { it.toByte() }
        val nonce = ByteArray(12) { (it + 16).toByte() }
        val encodedKey =
            Base64.getEncoder().encodeToString(key.toString().toByteArray(StandardCharsets.UTF_8))
        val body =
            "$encodedKey|${Base64.getEncoder().encodeToString(value)}"
                .toByteArray(StandardCharsets.UTF_8)
        val spec = PBEKeySpec(passphrase, salt, 120_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        val ciphertext =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(128, nonce))
                doFinal(body)
            }
        val text =
            listOf(
                "salt=${Base64.getEncoder().encodeToString(salt)}",
                "nonce=${Base64.getEncoder().encodeToString(nonce)}",
                "data=${Base64.getEncoder().encodeToString(ciphertext)}",
            ).joinToString("\n")
        Files.writeString(file, text, StandardCharsets.US_ASCII)
        passphrase.fill('\u0000')
        spec.clearPassword()
        derived.fill(0)
        body.fill(0)
        ciphertext.fill(0)
    }
}
