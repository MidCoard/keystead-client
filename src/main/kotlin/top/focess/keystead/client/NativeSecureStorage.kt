package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import top.focess.keystead.memory.Wipe

class NativeSecureStorage(
    private val file: Path,
    private val instanceId: String,
    private val secretStore: OsSecretStore,
    private val random: SecureRandom = SecureRandom(),
) : SecureStorage, AutoCloseable {
    override val capability = SecureStorageCapability.OS_USER_PROTECTED
    private val storageKey: ByteArray
    private val values = linkedMapOf<SecureStorageKey, ByteArray>()
    private var closed = false

    init {
        require(instanceId.isNotBlank() && instanceId.length <= INSTANCE_ID_MAX_LENGTH && instanceId.none(Char::isISOControl))
        val existing = secretStore.load(instanceId)
        storageKey =
            when {
                existing != null -> existing.copyOf().also { Wipe.wipe(existing) }
                Files.exists(file) -> throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-key-missing")
                else -> ByteArray(AES_KEY_BYTES).also { random.nextBytes(it); secretStore.save(instanceId, it.copyOf()) }
            }
        if (storageKey.size != AES_KEY_BYTES) {
            Wipe.wipe(storageKey)
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-key-invalid")
        }
        if (Files.exists(file)) values.putAll(readFile())
    }

    @Synchronized
    override fun save(key: SecureStorageKey, value: ByteArray) {
        requireOpen()
        require(value.size <= SecureStorageCodec.MAX_VALUE_BYTES) { "Secure storage value is too large" }
        Wipe.wipe(values.put(key, value.copyOf()))
        persist()
    }

    @Synchronized override fun load(key: SecureStorageKey): ByteArray? { requireOpen(); return values[key]?.copyOf() }

    @Synchronized
    override fun delete(key: SecureStorageKey) {
        requireOpen()
        val removed = values.remove(key) ?: return
        Wipe.wipe(removed)
        persist()
    }

    @Synchronized
    override fun listKeys(namespace: String, account: String): Set<String> {
        requireOpen()
        return values.keys.filter { it.namespace == namespace && it.account == account }.mapTo(linkedSetOf()) { it.name }
    }

    @Synchronized
    fun destroy() {
        var failure: Throwable? = null
        try {
            Files.deleteIfExists(file)
        } catch (error: Throwable) {
            failure = error
        }
        try {
            secretStore.delete(instanceId)
        } catch (error: Throwable) {
            if (failure == null) failure = error else failure.addSuppressed(error)
        }
        close()
        if (failure != null) throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "native-destroy-partial", failure)
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            values.values.forEach { Wipe.wipe(it) }
            values.clear()
            Wipe.wipe(storageKey)
            closed = true
        }
    }

    private fun readFile(): LinkedHashMap<SecureStorageKey, ByteArray> {
        val encoded = try { Files.readAllBytes(file) } catch (error: java.io.IOException) { throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "native-file-read", error) }
        var plaintext: ByteArray? = null
        try {
            DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                val magic = ByteArray(4); data.readFully(magic)
                if (!magic.contentEquals(MAGIC) || data.readUnsignedByte() != VERSION) corrupt()
                val nonce = ByteArray(GCM_NONCE_BYTES); data.readFully(nonce)
                val length = data.readInt()
                if (length !in GCM_TAG_BYTES..MAX_CIPHERTEXT || length != data.available()) corrupt()
                val ciphertext = ByteArray(length); data.readFully(ciphertext)
                val plain = crypt(Cipher.DECRYPT_MODE, nonce, ciphertext)
                plaintext = plain
                return SecureStorageCodec.decode(plain)
            }
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-authentication-failed", error)
        } catch (error: GeneralSecurityException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-cipher-invalid", error)
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-file-invalid", error)
        } finally {
            Wipe.wipe(encoded)
            Wipe.wipe(plaintext)
        }
    }

    private fun persist() {
        var plaintext: ByteArray? = null
        var ciphertext: ByteArray? = null
        var encoded: ByteArray? = null
        try {
            plaintext = SecureStorageCodec.encode(values)
            val nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
            ciphertext = crypt(Cipher.ENCRYPT_MODE, nonce, plaintext)
            val output = ByteArrayOutputStream()
            DataOutputStream(output).use { data -> data.write(MAGIC); data.writeByte(VERSION); data.write(nonce); data.writeInt(ciphertext.size); data.write(ciphertext) }
            encoded = output.toByteArray()
            Files.createDirectories(file.toAbsolutePath().parent)
            val temporary = file.resolveSibling(".${file.fileName}.${java.util.UUID.randomUUID()}.tmp")
            try {
                FileChannel.open(temporary, CREATE_NEW, WRITE).use { channel -> channel.write(java.nio.ByteBuffer.wrap(encoded)); channel.force(true) }
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                Files.deleteIfExists(temporary)
            }
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "native-file-write", error)
        } catch (error: GeneralSecurityException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "native-encryption-failed", error)
        } finally {
            Wipe.wipe(plaintext); Wipe.wipe(ciphertext); Wipe.wipe(encoded)
        }
    }

    @Throws(GeneralSecurityException::class)
    private fun crypt(mode: Int, nonce: ByteArray, input: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(mode, SecretKeySpec(storageKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            updateAAD("keystead-native-storage|v1|$instanceId".toByteArray(StandardCharsets.UTF_8))
            doFinal(input)
        }

    private fun requireOpen() = check(!closed) { "Secure storage is closed" }
    private fun corrupt(): Nothing = throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-file-invalid")

    private companion object {
        const val INSTANCE_ID_MAX_LENGTH = 255
        const val AES_KEY_BYTES = 32
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val GCM_NONCE_BYTES = 12
        val MAGIC = byteArrayOf('K'.code.toByte(), 'S'.code.toByte(), 'S'.code.toByte(), '2'.code.toByte())
        const val VERSION = 1
        const val MAX_CIPHERTEXT = 8 * 1024 * 1024
    }
}
