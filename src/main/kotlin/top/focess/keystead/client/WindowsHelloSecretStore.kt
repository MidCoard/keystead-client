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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import top.focess.keystead.memory.Wipe

internal interface WindowsHelloPort {
    fun availability(): OsSecretStoreAvailability

    fun createCredential(
        relyingPartyId: String,
        userId: ByteArray,
        salt: ByteArray,
    ): WindowsHelloCredential

    fun deriveSecret(
        relyingPartyId: String,
        credentialId: ByteArray,
        salt: ByteArray,
    ): ByteArray

    fun deleteCredential(credentialId: ByteArray)
}

internal data class WindowsHelloCredential(
    val credentialId: ByteArray,
    val prfSecret: ByteArray,
)

class WindowsHelloSecretStore internal constructor(
    private val directory: Path,
    private val port: WindowsHelloPort,
    private val random: SecureRandom = SecureRandom(),
) : OsSecretStore {
    override val providerId: String = "windows-hello"

    override fun availability(): OsSecretStoreAvailability = port.availability()

    override fun save(instanceId: String, secret: ByteArray) {
        requireAvailable()
        require(secret.isNotEmpty() && secret.size <= MAX_SECRET_BYTES) {
            "Windows Hello secret has an invalid size"
        }
        val userId = digest("keystead-windows-hello-user|$instanceId".toByteArray(StandardCharsets.UTF_8))
        var credentialId = ByteArray(0)
        var salt = ByteArray(0)
        var prfSecret = ByteArray(0)
        var wrappingKey = ByteArray(0)
        var nonce = ByteArray(0)
        var ciphertext = ByteArray(0)
        var encoded = ByteArray(0)
        var installed = false
        try {
            salt = ByteArray(PRF_SALT_BYTES).also(random::nextBytes)
            val created = port.createCredential(RELYING_PARTY_ID, userId, salt)
            credentialId = created.credentialId
            prfSecret = created.prfSecret
            require(credentialId.isNotEmpty() && credentialId.size <= MAX_CREDENTIAL_ID_BYTES) {
                "Windows Hello returned an invalid credential"
            }
            if (prfSecret.size != PRF_SECRET_BYTES) {
                throw OsSecretStoreException(
                    OsSecretStoreFailure.CORRUPT,
                    "windows-hello-prf-invalid",
                )
            }
            wrappingKey = wrappingKey(prfSecret)
            nonce = ByteArray(GCM_NONCE_BYTES).also(random::nextBytes)
            ciphertext = crypt(Cipher.ENCRYPT_MODE, wrappingKey, nonce, secret, aad(instanceId, credentialId))
            encoded = encode(credentialId, salt, nonce, ciphertext)
            writeAtomically(blob(instanceId), encoded)
            installed = true
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: GeneralSecurityException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-hello-encryption-failed", error)
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-hello-write-failed", error)
        } finally {
            if (!installed && credentialId.isNotEmpty()) {
                runCatching { port.deleteCredential(credentialId) }
            }
            Wipe.wipe(userId)
            Wipe.wipe(credentialId)
            Wipe.wipe(salt)
            Wipe.wipe(prfSecret)
            Wipe.wipe(wrappingKey)
            Wipe.wipe(nonce)
            Wipe.wipe(ciphertext)
            Wipe.wipe(encoded)
        }
    }

    override fun load(instanceId: String): ByteArray? {
        requireAvailable()
        val file = blob(instanceId)
        if (!Files.exists(file)) return null
        var encoded = ByteArray(0)
        var credentialId = ByteArray(0)
        var salt = ByteArray(0)
        var nonce = ByteArray(0)
        var ciphertext = ByteArray(0)
        var prfSecret = ByteArray(0)
        var wrappingKey = ByteArray(0)
        return try {
            encoded = Files.readAllBytes(file)
            val decoded = decode(encoded)
            credentialId = decoded.credentialId
            salt = decoded.salt
            nonce = decoded.nonce
            ciphertext = decoded.ciphertext
            prfSecret = verifiedPrf(credentialId, salt)
            wrappingKey = wrappingKey(prfSecret)
            crypt(Cipher.DECRYPT_MODE, wrappingKey, nonce, ciphertext, aad(instanceId, credentialId))
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: AEADBadTagException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-authentication-failed", error)
        } catch (error: GeneralSecurityException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-cipher-invalid", error)
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-file-invalid", error)
        } finally {
            Wipe.wipe(encoded)
            Wipe.wipe(credentialId)
            Wipe.wipe(salt)
            Wipe.wipe(nonce)
            Wipe.wipe(ciphertext)
            Wipe.wipe(prfSecret)
            Wipe.wipe(wrappingKey)
        }
    }

    override fun delete(instanceId: String) {
        requireAvailable()
        val file = blob(instanceId)
        if (!Files.exists(file)) return
        var encoded = ByteArray(0)
        var credentialId = ByteArray(0)
        try {
            encoded = Files.readAllBytes(file)
            credentialId = decode(encoded).credentialId
            port.deleteCredential(credentialId)
            Files.deleteIfExists(file)
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.IO_FAILURE, "windows-hello-delete-failed", error)
        } finally {
            Wipe.wipe(encoded)
            Wipe.wipe(credentialId)
        }
    }

    private fun verifiedPrf(credentialId: ByteArray, salt: ByteArray): ByteArray {
        val value = port.deriveSecret(RELYING_PARTY_ID, credentialId, salt)
        if (value.size != PRF_SECRET_BYTES) {
            Wipe.wipe(value)
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-prf-invalid")
        }
        return value
    }

    private fun requireAvailable() {
        val availability = availability()
        if (availability.status == OsSecretStoreStatus.AVAILABLE) return
        val failure =
            when (availability.status) {
                OsSecretStoreStatus.UNSUPPORTED -> OsSecretStoreFailure.UNSUPPORTED
                OsSecretStoreStatus.UNAVAILABLE -> OsSecretStoreFailure.UNAVAILABLE
                OsSecretStoreStatus.LOCKED -> OsSecretStoreFailure.LOCKED
                OsSecretStoreStatus.ACCESS_DENIED -> OsSecretStoreFailure.ACCESS_DENIED
                OsSecretStoreStatus.AVAILABLE -> error("unreachable")
            }
        throw OsSecretStoreException(failure, availability.diagnosticCode)
    }

    private fun blob(instanceId: String): Path =
        directory.resolve(
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                digest(instanceId.toByteArray(StandardCharsets.UTF_8)),
            ) + ".hello",
        )

    private fun aad(instanceId: String, credentialId: ByteArray): ByteArray =
        "keystead-windows-hello|v1|$instanceId|${Base64.getEncoder().encodeToString(credentialId)}"
            .toByteArray(StandardCharsets.UTF_8)

    private fun wrappingKey(prfSecret: ByteArray): ByteArray {
        val context = "keystead-windows-hello-wrapping-key|v1".toByteArray(StandardCharsets.UTF_8)
        return try {
            digest(context + prfSecret)
        } finally {
            Wipe.wipe(context)
        }
    }

    private fun crypt(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
    ): ByteArray =
        try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
                updateAAD(aad)
                doFinal(input)
            }
        } finally {
            Wipe.wipe(aad)
        }

    private fun encode(
        credentialId: ByteArray,
        salt: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeByte(VERSION)
            data.writeInt(credentialId.size)
            data.write(credentialId)
            data.write(salt)
            data.write(nonce)
            data.writeInt(ciphertext.size)
            data.write(ciphertext)
        }
        return output.toByteArray()
    }

    private fun decode(encoded: ByteArray): EncodedSecret =
        try {
            DataInputStream(ByteArrayInputStream(encoded)).use { data ->
                val magic = ByteArray(MAGIC.size).also(data::readFully)
                if (!magic.contentEquals(MAGIC) || data.readUnsignedByte() != VERSION) corrupt()
                val credentialLength = data.readInt()
                if (credentialLength !in 1..MAX_CREDENTIAL_ID_BYTES) corrupt()
                val credentialId = ByteArray(credentialLength).also(data::readFully)
                val salt = ByteArray(PRF_SALT_BYTES).also(data::readFully)
                val nonce = ByteArray(GCM_NONCE_BYTES).also(data::readFully)
                val ciphertextLength = data.readInt()
                if (ciphertextLength !in GCM_TAG_BYTES..(MAX_SECRET_BYTES + GCM_TAG_BYTES) ||
                    ciphertextLength != data.available()
                ) {
                    Wipe.wipe(credentialId)
                    Wipe.wipe(salt)
                    Wipe.wipe(nonce)
                    corrupt()
                }
                EncodedSecret(
                    credentialId,
                    salt,
                    nonce,
                    ByteArray(ciphertextLength).also(data::readFully),
                )
            }
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-file-invalid", error)
        }

    private fun writeAtomically(file: Path, encoded: ByteArray) {
        Files.createDirectories(file.toAbsolutePath().parent)
        val temporary = file.resolveSibling(".${file.fileName}.${java.util.UUID.randomUUID()}.tmp")
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

    private fun digest(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private fun corrupt(): Nothing =
        throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "windows-hello-file-invalid")

    private data class EncodedSecret(
        val credentialId: ByteArray,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
    )

    private companion object {
        const val RELYING_PARTY_ID = "keystead.local"
        const val VERSION = 1
        const val PRF_SALT_BYTES = 32
        const val PRF_SECRET_BYTES = 32
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = 16
        const val MAX_CREDENTIAL_ID_BYTES = 4096
        const val MAX_SECRET_BYTES = 64 * 1024
        val MAGIC = byteArrayOf('K'.code.toByte(), 'W'.code.toByte(), 'H'.code.toByte(), '1'.code.toByte())
    }
}
