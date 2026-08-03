package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.WRITE
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import top.focess.keystead.memory.Wipe

/** Encrypted file fallback; this is deliberately not advertised as OS-protected. */
class PassphraseFileSecureStorage(
    private val file: Path,
    passphrase: CharArray,
    private val random: SecureRandom = SecureRandom(),
) : SecureStorage, Closeable {
    override val capability = SecureStorageCapability.FILE_PASSPHRASE_PROTECTED
    private val password = passphrase.copyOf()
    private val values = linkedMapOf<SecureStorageKey, ByteArray>()
    private var closed = false

    init {
        try {
            loadFile()
        } catch (error: Throwable) {
            Wipe.wipe(password)
            values.values.forEach(Wipe::wipe)
            values.clear()
            closed = true
            throw error
        }
    }

    @Synchronized
    override fun save(key: SecureStorageKey, value: ByteArray) {
        requireOpen()
        require(value.size <= SecureStorageCodec.MAX_VALUE_BYTES) {
            "Secure storage value is too large"
        }
        val replacement = value.copyOf()
        val previous = values.put(key, replacement)
        try {
            persist()
            Wipe.wipe(previous)
        } catch (error: Throwable) {
            if (previous == null) {
                values.remove(key)
            } else {
                values[key] = previous
            }
            Wipe.wipe(replacement)
            throw error
        }
    }

    @Synchronized
    override fun load(key: SecureStorageKey): ByteArray? {
        requireOpen()
        return values[key]?.copyOf()
    }

    @Synchronized
    override fun delete(key: SecureStorageKey) {
        requireOpen()
        val removed = values.remove(key) ?: return
        try {
            persist()
            Wipe.wipe(removed)
        } catch (error: Throwable) {
            values[key] = removed
            throw error
        }
    }

    @Synchronized
    override fun listKeys(namespace: String, account: String): Set<String> {
        requireOpen()
        return values.keys
            .filter { it.namespace == namespace && it.account == account }
            .mapTo(linkedSetOf()) { it.name }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            values.values.forEach(Wipe::wipe)
            values.clear()
            Wipe.wipe(password)
            closed = true
        }
    }

    private fun loadFile() {
        if (!Files.exists(file)) return
        val size =
            try {
                Files.size(file)
            } catch (error: java.io.IOException) {
                throw ioFailure("passphrase-file-read", error)
            }
        if (size !in 1..MAX_FILE_BYTES.toLong()) corrupt("passphrase-file-size")
        val encoded =
            try {
                Files.readAllBytes(file)
            } catch (error: java.io.IOException) {
                throw ioFailure("passphrase-file-read", error)
            }
        var decoded: LinkedHashMap<SecureStorageKey, ByteArray>? = null
        try {
            decoded =
                if (encoded.size >= MAGIC.size &&
                    encoded.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
                ) {
                    readBinary(encoded)
                } else {
                    readLegacy(encoded)
                }
            values.putAll(decoded)
            decoded = null
        } finally {
            Wipe.wipe(encoded)
            decoded?.values?.forEach(Wipe::wipe)
        }
    }

    private fun readBinary(encoded: ByteArray): LinkedHashMap<SecureStorageKey, ByteArray> {
        var plaintext: ByteArray? = null
        var ciphertext: ByteArray? = null
        return try {
            DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                val magic = ByteArray(MAGIC.size)
                data.readFully(magic)
                if (!magic.contentEquals(MAGIC) || data.readUnsignedByte() != VERSION) {
                    corrupt("passphrase-file-header")
                }
                val salt = ByteArray(SALT_BYTES)
                val nonce = ByteArray(NONCE_BYTES)
                data.readFully(salt)
                data.readFully(nonce)
                val length = data.readInt()
                if (length !in GCM_TAG_BYTES..MAX_FILE_BYTES || length != data.available()) {
                    Wipe.wipe(salt)
                    Wipe.wipe(nonce)
                    corrupt("passphrase-file-length")
                }
                ciphertext = ByteArray(length)
                data.readFully(ciphertext)
                plaintext = crypt(Cipher.DECRYPT_MODE, salt, nonce, ciphertext, useAad = true)
                Wipe.wipe(salt)
                Wipe.wipe(nonce)
                SecureStorageCodec.decode(plaintext)
            }
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw corrupt("passphrase-authentication-failed", error)
        } catch (error: GeneralSecurityException) {
            throw corrupt("passphrase-cipher-invalid", error)
        } catch (error: java.io.IOException) {
            throw corrupt("passphrase-file-invalid", error)
        } finally {
            Wipe.wipe(plaintext)
            Wipe.wipe(ciphertext)
        }
    }

    private fun readLegacy(encoded: ByteArray): LinkedHashMap<SecureStorageKey, ByteArray> {
        val properties =
            try {
                Properties().also { values ->
                    ByteArrayInputStream(encoded).use(values::load)
                }
            } catch (error: RuntimeException) {
                throw corrupt("passphrase-legacy-invalid", error)
            }
        val salt = decodeLegacyProperty(properties, "salt")
        val nonce = decodeLegacyProperty(properties, "nonce")
        val ciphertext = decodeLegacyProperty(properties, "data")
        var plaintext: ByteArray? = null
        return try {
            if (salt.size != SALT_BYTES || nonce.size != NONCE_BYTES) {
                corrupt("passphrase-legacy-invalid")
            }
            plaintext = crypt(Cipher.DECRYPT_MODE, salt, nonce, ciphertext, useAad = false)
            decodeLegacyPlaintext(plaintext)
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw corrupt("passphrase-authentication-failed", error)
        } catch (error: GeneralSecurityException) {
            throw corrupt("passphrase-cipher-invalid", error)
        } finally {
            Wipe.wipe(salt)
            Wipe.wipe(nonce)
            Wipe.wipe(ciphertext)
            Wipe.wipe(plaintext)
        }
    }

    private fun decodeLegacyPlaintext(
        plaintext: ByteArray,
    ): LinkedHashMap<SecureStorageKey, ByteArray> {
        val decoded = linkedMapOf<SecureStorageKey, ByteArray>()
        var start = 0
        try {
            while (start < plaintext.size) {
                var end = start
                while (end < plaintext.size && plaintext[end] != '\n'.code.toByte()) end++
                var contentEnd = end
                if (contentEnd > start && plaintext[contentEnd - 1] == '\r'.code.toByte()) {
                    contentEnd--
                }
                if (contentEnd > start) {
                    var separator = start
                    while (separator < contentEnd &&
                        plaintext[separator] != '|'.code.toByte()
                    ) {
                        separator++
                    }
                    if (separator == start || separator == contentEnd) {
                        corrupt("passphrase-legacy-map-invalid")
                    }
                    val encodedKey = plaintext.copyOfRange(start, separator)
                    val encodedValue = plaintext.copyOfRange(separator + 1, contentEnd)
                    var keyBytes: ByteArray? = null
                    var valueBytes: ByteArray? = null
                    try {
                        keyBytes = Base64.getDecoder().decode(encodedKey)
                        valueBytes = Base64.getDecoder().decode(encodedValue)
                        if (valueBytes.size > SecureStorageCodec.MAX_VALUE_BYTES) {
                            corrupt("passphrase-legacy-map-invalid")
                        }
                        val keyParts =
                            String(keyBytes, StandardCharsets.UTF_8).split(":", limit = 3)
                        if (keyParts.size != 3) corrupt("passphrase-legacy-map-invalid")
                        val key = SecureStorageKey(keyParts[0], keyParts[1], keyParts[2])
                        if (decoded.put(key, valueBytes) != null) {
                            corrupt("passphrase-legacy-map-invalid")
                        }
                        valueBytes = null
                    } catch (error: IllegalArgumentException) {
                        throw corrupt("passphrase-legacy-map-invalid", error)
                    } finally {
                        Wipe.wipe(encodedKey)
                        Wipe.wipe(encodedValue)
                        Wipe.wipe(keyBytes)
                        Wipe.wipe(valueBytes)
                    }
                    if (decoded.size > MAX_ENTRIES) corrupt("passphrase-legacy-map-invalid")
                }
                start = end + 1
            }
            return decoded
        } catch (error: Throwable) {
            decoded.values.forEach(Wipe::wipe)
            throw error
        }
    }

    private fun persist() {
        var plaintext: ByteArray? = null
        var ciphertext: ByteArray? = null
        var encoded: ByteArray? = null
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        try {
            plaintext = SecureStorageCodec.encode(values)
            ciphertext = crypt(Cipher.ENCRYPT_MODE, salt, nonce, plaintext, useAad = true)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data ->
                data.write(MAGIC)
                data.writeByte(VERSION)
                data.write(salt)
                data.write(nonce)
                data.writeInt(ciphertext.size)
                data.write(ciphertext)
            }
            encoded = output.toByteArray()
            if (encoded.size > MAX_FILE_BYTES) {
                throw IllegalArgumentException("Secure storage file is too large")
            }
            writeAtomically(encoded)
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: java.io.IOException) {
            throw ioFailure("passphrase-file-write", error)
        } catch (error: GeneralSecurityException) {
            throw ioFailure("passphrase-encryption-failed", error)
        } finally {
            Wipe.wipe(plaintext)
            Wipe.wipe(ciphertext)
            Wipe.wipe(encoded)
            Wipe.wipe(salt)
            Wipe.wipe(nonce)
        }
    }

    private fun writeAtomically(encoded: ByteArray) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary =
            file.resolveSibling(".${file.fileName}.${java.util.UUID.randomUUID()}.tmp")
        try {
            FileChannel.open(temporary, CREATE_NEW, WRITE).use { channel ->
                writeFully(channel, encoded)
                channel.force(true)
            }
            Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun crypt(
        mode: Int,
        salt: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        useAad: Boolean,
    ): ByteArray {
        val derived = deriveKey(salt)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(derived, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                if (useAad) updateAAD(AAD)
                doFinal(input)
            }
        } finally {
            Wipe.wipe(derived)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun deriveKey(salt: ByteArray): ByteArray {
        val passwordCopy = password.copyOf()
        val saltCopy = salt.copyOf()
        val spec = PBEKeySpec(passwordCopy, saltCopy, KDF_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            Wipe.wipe(passwordCopy)
            Wipe.wipe(saltCopy)
            spec.clearPassword()
        }
    }

    private fun decodeLegacyProperty(properties: Properties, name: String): ByteArray {
        val value =
            properties.getProperty(name)?.takeIf(String::isNotBlank)
                ?: throw corrupt("passphrase-legacy-invalid")
        return try {
            Base64.getDecoder().decode(value)
        } catch (error: IllegalArgumentException) {
            throw corrupt("passphrase-legacy-invalid", error)
        }
    }

    private fun requireOpen() = check(!closed) { "Secure storage is closed" }

    private fun corrupt(
        code: String,
        cause: Throwable? = null,
    ): OsSecretStoreException =
        OsSecretStoreException(OsSecretStoreFailure.CORRUPT, code, cause)

    private fun ioFailure(
        code: String,
        cause: Throwable,
    ): OsSecretStoreException =
        OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, code, cause)

    private companion object {
        val MAGIC =
            byteArrayOf(
                'K'.code.toByte(),
                'S'.code.toByte(),
                'P'.code.toByte(),
                '2'.code.toByte(),
            )
        const val VERSION = 1
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val KDF_ITERATIONS = 120_000
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val MAX_ENTRIES = 4096
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
        val AAD = "keystead-passphrase-storage|v1".toByteArray(StandardCharsets.UTF_8)
    }
}
