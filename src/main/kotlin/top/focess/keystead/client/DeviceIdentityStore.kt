package top.focess.keystead.client

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.DestroyFailedException
import top.focess.keystead.crypto.CryptoException
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.crypto.DeviceKeyPair
import top.focess.keystead.model.KeyId

class LocalDeviceIdentity(
    val deviceId: String,
    val keyAlgorithm: String,
    publicKey: ByteArray,
    privateKey: ByteArray,
    val proofKeyAlgorithm: String,
    proofPublicKey: ByteArray,
    proofPrivateKey: ByteArray,
) : AutoCloseable {
    private val publicKeyBytes = publicKey.copyOf()
    private val privateKeyBytes = privateKey.copyOf()
    private val proofPublicKeyBytes = proofPublicKey.copyOf()
    private val proofPrivateKeyBytes = proofPrivateKey.copyOf()
    private var closed = false

    fun publicKey(): ByteArray = publicKeyBytes.copyOf()

    @Synchronized
    fun privateKey(): ByteArray {
        check(!closed) { "Device identity is closed" }
        return privateKeyBytes.copyOf()
    }

    fun proofPublicKey(): ByteArray = proofPublicKeyBytes.copyOf()

    internal fun proofPrivateKey(): ByteArray {
        check(!closed) { "Device identity is closed" }
        return proofPrivateKeyBytes.copyOf()
    }

    @Synchronized
    fun signDeviceChallenge(challengeId: String, nonce: String): String {
        check(!closed) { "Device identity is closed" }
        require(challengeId.isNotBlank()) { "Challenge id must not be blank" }
        require(nonce.isNotBlank()) { "Challenge nonce must not be blank" }
        check(proofKeyAlgorithm == PROOF_KEY_ALGORITHM) {
            "Device proof key algorithm is unsupported"
        }
        return signPayload(proofPayload(challengeId, nonce).toByteArray(StandardCharsets.UTF_8))
    }

    @Synchronized
    internal fun signRecoveryRequest(payload: ByteArray): String = signPayload(payload.copyOf())

    private fun signPayload(payload: ByteArray): String {
        val encodedPrivateKey = proofPrivateKeyBytes.copyOf()
        var privateKey: PrivateKey? = null
        var signatureBytes: ByteArray? = null
        return try {
            privateKey =
                KeyFactory.getInstance(PROOF_JCA_ALGORITHM)
                    .generatePrivate(PKCS8EncodedKeySpec(encodedPrivateKey))
            val signature = Signature.getInstance(PROOF_JCA_ALGORITHM)
            signature.initSign(privateKey)
            signature.update(payload)
            signatureBytes = signature.sign()
            Base64.getEncoder().encodeToString(signatureBytes)
        } catch (error: GeneralSecurityException) {
            throw CryptoException("Could not sign device challenge", error)
        } finally {
            encodedPrivateKey.fill(0)
            payload.fill(0)
            signatureBytes?.fill(0)
            destroy(privateKey)
        }
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            privateKeyBytes.fill(0)
            proofPrivateKeyBytes.fill(0)
            closed = true
        }
    }

    override fun toString(): String = "LocalDeviceIdentity(<redacted>)"

    private fun destroy(privateKey: PrivateKey?) {
        try {
            privateKey?.destroy()
        } catch (_: DestroyFailedException) {
            // The encoded source bytes are still wiped even if the provider cannot destroy its key object.
        }
    }

    companion object {
        const val PROOF_KEY_ALGORITHM = "ED25519"
        private const val PROOF_JCA_ALGORITHM = "Ed25519"

        fun proofPayload(challengeId: String, nonce: String): String =
            "keystead-device-proof:v1:$challengeId:$nonce"
    }
}

class DeviceIdentityStore(
    private val directory: Path,
    private val crypto: DefaultCryptoService = DefaultCryptoService(),
    private val random: SecureRandom = SecureRandom(),
    private val secureStorage: SecureStorage? = null,
) {
    private val identityFile = directory.resolve("device-identity.properties")

    /** Capability used for optional token/credential persistence; identity files remain passphrase protected fallback. */
    val secureStorageCapability: SecureStorageCapability
        get() = secureStorage?.capability ?: SecureStorageCapability.FILE_PASSPHRASE_PROTECTED

    fun saveSecureSecret(key: SecureStorageKey, value: ByteArray) {
        secureStorage?.save(key, value) ?: throw IllegalStateException("Secure storage is unavailable; keep this secret in memory")
    }

    fun loadSecureSecret(key: SecureStorageKey): ByteArray? = secureStorage?.load(key)

    fun deleteSecureSecret(key: SecureStorageKey) { secureStorage?.delete(key) }

    fun createOrLoad(deviceId: String, passphrase: CharArray): LocalDeviceIdentity {
        val password = passphrase.copyOf()
        return try {
            Files.createDirectories(directory)
            withIdentityLock {
                if (Files.exists(identityFile)) {
                    load(deviceId, password)
                } else {
                    create(deviceId, password)
                }
            }
        } finally {
            password.fill('\u0000')
            passphrase.fill('\u0000')
        }
    }

    fun createOrLoadNative(deviceId: String): LocalDeviceIdentity {
        val storage = secureStorage ?: throw IllegalStateException("OS secure storage is unavailable")
        require(storage.capability == SecureStorageCapability.OS_USER_PROTECTED) {
            "Device identity requires OS-user-protected storage"
        }
        Files.createDirectories(directory)
        return withIdentityLock {
            if (Files.exists(identityFile)) loadNative(deviceId, storage) else createNative(deviceId, storage)
        }
    }

    fun createMemoryOnly(deviceId: String): LocalDeviceIdentity {
        require(deviceId.isNotBlank()) { "Device id must not be blank" }
        crypto.generateDeviceKeyPair().use { device ->
            val publicKey = device.publicKey()
            val privateKey = device.privateKeyCopy()
            val proof = generateProofKeyPair()
            return try {
                identity(deviceId, device.keyAlgorithm(), publicKey, privateKey, proof)
            } finally {
                publicKey.fill(0)
                privateKey.fill(0)
                proof.close()
            }
        }
    }

    fun migrateToNative(deviceId: String, passphrase: CharArray): DeviceIdentityMigrationResult {
        val properties = if (Files.exists(identityFile)) loadProperties() else null
        if (properties?.getProperty("formatVersion") == FORMAT_VERSION_3) {
            passphrase.fill('\u0000')
            return DeviceIdentityMigrationResult.AlreadyNative
        }
        val identity = createOrLoad(deviceId, passphrase)
        identity.use {
            val storage = secureStorage ?: throw IllegalStateException("OS secure storage is unavailable")
            require(storage.capability == SecureStorageCapability.OS_USER_PROTECTED) {
                "Device identity requires OS-user-protected storage"
            }
            saveNativeAndVerify(identity, storage)
        }
        return DeviceIdentityMigrationResult.Migrated
    }

    private fun createNative(deviceId: String, storage: SecureStorage): LocalDeviceIdentity {
        crypto.generateDeviceKeyPair().use { device ->
            val publicKey = device.publicKey()
            val privateKey = device.privateKeyCopy()
            val proof = generateProofKeyPair()
            return try {
                val identity = identity(deviceId, device.keyAlgorithm(), publicKey, privateKey, proof)
                try {
                    saveNativeAndVerify(identity, storage)
                    identity
                } catch (error: Throwable) {
                    identity.close()
                    throw error
                }
            } finally { publicKey.fill(0); privateKey.fill(0); proof.close() }
        }
    }

    private fun loadNative(deviceId: String, storage: SecureStorage): LocalDeviceIdentity {
        val properties = loadProperties()
        if (required(properties, "formatVersion") != FORMAT_VERSION_3) {
            throw IllegalStateException("Device identity requires migration to OS secure storage")
        }
        val storedDeviceId = required(properties, "deviceId")
        if (storedDeviceId != deviceId) throw IllegalStateException("Stored device identity belongs to $storedDeviceId")
        val privateKey = storage.load(wrappingPrivateKey(deviceId)) ?: throw IllegalStateException("Native device identity is incomplete")
        val proofPrivateKey = storage.load(proofPrivateKey(deviceId)) ?: throw IllegalStateException("Native device identity is incomplete")
        val publicKey = bytes(properties, "publicKey")
        val proofPublicKey = bytes(properties, "proofPublicKey")
        return try {
            LocalDeviceIdentity(deviceId, required(properties, "keyAlgorithm"), publicKey, privateKey, required(properties, "proofKeyAlgorithm"), proofPublicKey, proofPrivateKey)
        } finally { privateKey.fill(0); proofPrivateKey.fill(0); publicKey.fill(0); proofPublicKey.fill(0) }
    }

    private fun saveNativeAndVerify(identity: LocalDeviceIdentity, storage: SecureStorage) {
        val wrapping = identity.privateKey()
        val proof = identity.proofPrivateKey()
        var savedWrapping = false
        var savedProof = false
        try {
            storage.save(wrappingPrivateKey(identity.deviceId), wrapping); savedWrapping = true
            storage.save(proofPrivateKey(identity.deviceId), proof); savedProof = true
            val reloadedWrapping = storage.load(wrappingPrivateKey(identity.deviceId)) ?: throw IllegalStateException("Native device identity verification failed")
            val reloadedProof = storage.load(proofPrivateKey(identity.deviceId)) ?: throw IllegalStateException("Native device identity verification failed")
            try {
                if (!reloadedWrapping.contentEquals(wrapping) || !reloadedProof.contentEquals(proof)) throw IllegalStateException("Native device identity verification failed")
                LocalDeviceIdentity(identity.deviceId, identity.keyAlgorithm, identity.publicKey(), reloadedWrapping, identity.proofKeyAlgorithm, identity.proofPublicKey(), reloadedProof).use { candidate ->
                    candidate.signDeviceChallenge("native-migration", "verification")
                    verifyWrappingKeyPair(candidate)
                }
            } finally { reloadedWrapping.fill(0); reloadedProof.fill(0) }
            val properties = Properties()
            properties.setProperty("formatVersion", FORMAT_VERSION_3)
            properties.setProperty("deviceId", identity.deviceId)
            properties.setProperty("keyAlgorithm", identity.keyAlgorithm)
            properties.setProperty("publicKey", b64(identity.publicKey()))
            properties.setProperty("proofKeyAlgorithm", identity.proofKeyAlgorithm)
            properties.setProperty("proofPublicKey", b64(identity.proofPublicKey()))
            properties.setProperty("secureStorageCapability", SecureStorageCapability.OS_USER_PROTECTED.name)
            writePropertiesAtomically(properties)
        } catch (error: Throwable) {
            if (savedProof) runCatching { storage.delete(proofPrivateKey(identity.deviceId)) }
            if (savedWrapping) runCatching { storage.delete(wrappingPrivateKey(identity.deviceId)) }
            throw error
        } finally { wrapping.fill(0); proof.fill(0) }
    }

    private fun wrappingPrivateKey(deviceId: String) = SecureStorageKey("keystead-device", deviceId, "wrapping-private-key")
    private fun proofPrivateKey(deviceId: String) = SecureStorageKey("keystead-device", deviceId, "proof-private-key")

    private fun verifyWrappingKeyPair(identity: LocalDeviceIdentity) {
        val context = ByteArray(32).also(random::nextBytes)
        val publicKey = identity.publicKey()
        val privateKey = identity.privateKey()
        crypto.generateVaultKey(KeyId("native-migration-verification")).use { original ->
            val wrapped = crypto.wrapVaultKeyForDevice(original, publicKey, context)
            try {
                crypto.unwrapVaultKeyFromDevicePackage(original.keyId(), wrapped, privateKey, context).use { opened ->
                    var expected = ByteArray(0)
                    var actual = ByteArray(0)
                    try {
                        original.copyBytes { expected = it.copyOf() }
                        opened.copyBytes { actual = it.copyOf() }
                        check(expected.contentEquals(actual)) { "Native device identity verification failed" }
                    } finally {
                        expected.fill(0)
                        actual.fill(0)
                    }
                }
            } finally {
                wrapped.fill(0)
                publicKey.fill(0)
                privateKey.fill(0)
                context.fill(0)
            }
        }
    }

    private fun <T> withIdentityLock(action: () -> T): T {
        val canonicalDirectory = directory.toRealPath()
        val processLock = PROCESS_LOCKS.computeIfAbsent(canonicalDirectory) { ReentrantLock(true) }
        processLock.lock()
        try {
            FileChannel.open(canonicalDirectory.resolve(LOCK_FILE_NAME), CREATE, WRITE).use { channel ->
                channel.lock().use { return action() }
            }
        } finally {
            processLock.unlock()
        }
    }

    private fun create(deviceId: String, passphrase: CharArray): LocalDeviceIdentity {
        crypto.generateDeviceKeyPair().use { device ->
            var publicKey = ByteArray(0)
            var privateKey = ByteArray(0)
            var proof: ProofKeyMaterial? = null
            try {
                publicKey = device.publicKey()
                privateKey = device.privateKeyCopy()
                proof = generateProofKeyPair()
                writeNew(
                    deviceId,
                    device.keyAlgorithm(),
                    publicKey,
                    privateKey,
                    proof.publicKey,
                    proof.privateKey,
                    passphrase,
                )
                return identity(deviceId, device.keyAlgorithm(), publicKey, privateKey, proof)
            } finally {
                publicKey.fill(0)
                privateKey.fill(0)
                proof?.close()
            }
        }
    }

    private fun load(deviceId: String, passphrase: CharArray): LocalDeviceIdentity {
        val properties = loadProperties()
        val formatVersion = required(properties, "formatVersion")
        if (formatVersion != FORMAT_VERSION_1 && formatVersion != FORMAT_VERSION_2) {
            throw IllegalStateException("Device identity format is unsupported")
        }
        val storedDeviceId = required(properties, "deviceId")
        if (storedDeviceId != deviceId) {
            throw IllegalStateException("Stored device identity belongs to $storedDeviceId")
        }
        val keyAlgorithm = required(properties, "keyAlgorithm")
        val publicKey = bytes(properties, "publicKey")
        val salt = bytes(properties, "kdfSalt")
        val nonce = bytes(properties, "privateKeyNonce")
        val encryptedPrivateKey = bytes(properties, "encryptedPrivateKey")
        val wrappingAad = aad(storedDeviceId, keyAlgorithm, publicKey)
        val wrappingKey = deriveWrappingKey(passphrase, salt)
        var privateKey: ByteArray? = null
        var proof: ProofKeyMaterial? = null
        return try {
            privateKey = decrypt(wrappingKey, nonce, encryptedPrivateKey, wrappingAad)
            proof =
                if (formatVersion == FORMAT_VERSION_2) {
                    loadProof(properties, storedDeviceId, wrappingKey)
                } else {
                    generateProofKeyPair().also {
                        migrateVersionOne(properties, storedDeviceId, wrappingKey, it)
                    }
                }
            identity(storedDeviceId, keyAlgorithm, publicKey, privateKey, proof)
        } finally {
            publicKey.fill(0)
            salt.fill(0)
            nonce.fill(0)
            encryptedPrivateKey.fill(0)
            wrappingAad.fill(0)
            wrappingKey.fill(0)
            privateKey?.fill(0)
            proof?.close()
        }
    }

    private fun loadProof(
        properties: Properties,
        deviceId: String,
        wrappingKey: ByteArray,
    ): ProofKeyMaterial {
        val proofKeyAlgorithm = required(properties, "proofKeyAlgorithm")
        if (proofKeyAlgorithm != LocalDeviceIdentity.PROOF_KEY_ALGORITHM) {
            throw IllegalStateException("Device proof key algorithm is unsupported")
        }
        val publicKey = bytes(properties, "proofPublicKey")
        val nonce = bytes(properties, "proofPrivateKeyNonce")
        val encryptedPrivateKey = bytes(properties, "encryptedProofPrivateKey")
        val proofAad = proofAad(deviceId, proofKeyAlgorithm, publicKey)
        return try {
            val privateKey = decrypt(wrappingKey, nonce, encryptedPrivateKey, proofAad)
            try {
                ProofKeyMaterial(publicKey, privateKey)
            } finally {
                privateKey.fill(0)
            }
        } finally {
            publicKey.fill(0)
            nonce.fill(0)
            encryptedPrivateKey.fill(0)
            proofAad.fill(0)
        }
    }

    private fun writeNew(
        deviceId: String,
        keyAlgorithm: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
        proofPublicKey: ByteArray,
        proofPrivateKey: ByteArray,
        passphrase: CharArray,
    ) {
        val salt = randomBytes(SALT_BYTES)
        val nonce = randomBytes(GCM_NONCE_BYTES)
        val proofNonce = randomBytes(GCM_NONCE_BYTES)
        val wrappingAad = aad(deviceId, keyAlgorithm, publicKey)
        val proofAad =
            proofAad(deviceId, LocalDeviceIdentity.PROOF_KEY_ALGORITHM, proofPublicKey)
        var wrappingKey = ByteArray(0)
        var encryptedPrivateKey = ByteArray(0)
        var encryptedProofPrivateKey = ByteArray(0)
        try {
            wrappingKey = deriveWrappingKey(passphrase, salt)
            encryptedPrivateKey = encrypt(wrappingKey, nonce, privateKey, wrappingAad)
            encryptedProofPrivateKey =
                encrypt(wrappingKey, proofNonce, proofPrivateKey, proofAad)
            val properties = Properties()
            properties.setProperty("formatVersion", FORMAT_VERSION_2)
            properties.setProperty("deviceId", deviceId)
            properties.setProperty("keyAlgorithm", keyAlgorithm)
            properties.setProperty("publicKey", b64(publicKey))
            properties.setProperty("kdfAlgorithm", KDF_ALGORITHM)
            properties.setProperty("kdfIterations", KDF_ITERATIONS.toString())
            properties.setProperty("kdfSalt", b64(salt))
            properties.setProperty("cipherAlgorithm", CIPHER_ALGORITHM)
            properties.setProperty("privateKeyNonce", b64(nonce))
            properties.setProperty("encryptedPrivateKey", b64(encryptedPrivateKey))
            properties.setProperty(
                "proofKeyAlgorithm",
                LocalDeviceIdentity.PROOF_KEY_ALGORITHM,
            )
            properties.setProperty("proofPublicKey", b64(proofPublicKey))
            properties.setProperty("proofPrivateKeyNonce", b64(proofNonce))
            properties.setProperty(
                "encryptedProofPrivateKey",
                b64(encryptedProofPrivateKey),
            )
            writePropertiesAtomically(properties)
        } finally {
            salt.fill(0)
            nonce.fill(0)
            proofNonce.fill(0)
            wrappingAad.fill(0)
            proofAad.fill(0)
            wrappingKey.fill(0)
            encryptedPrivateKey.fill(0)
            encryptedProofPrivateKey.fill(0)
        }
    }

    private fun migrateVersionOne(
        properties: Properties,
        deviceId: String,
        wrappingKey: ByteArray,
        proof: ProofKeyMaterial,
    ) {
        val nonce = randomBytes(GCM_NONCE_BYTES)
        val proofAad =
            proofAad(
                deviceId,
                LocalDeviceIdentity.PROOF_KEY_ALGORITHM,
                proof.publicKey,
            )
        var encryptedProofPrivateKey = ByteArray(0)
        try {
            encryptedProofPrivateKey =
                encrypt(wrappingKey, nonce, proof.privateKey, proofAad)
            properties.setProperty("formatVersion", FORMAT_VERSION_2)
            properties.setProperty(
                "proofKeyAlgorithm",
                LocalDeviceIdentity.PROOF_KEY_ALGORITHM,
            )
            properties.setProperty("proofPublicKey", b64(proof.publicKey))
            properties.setProperty("proofPrivateKeyNonce", b64(nonce))
            properties.setProperty(
                "encryptedProofPrivateKey",
                b64(encryptedProofPrivateKey),
            )
            writePropertiesAtomically(properties)
        } finally {
            nonce.fill(0)
            proofAad.fill(0)
            encryptedProofPrivateKey.fill(0)
        }
    }

    private fun generateProofKeyPair(): ProofKeyMaterial =
        try {
            val pair = KeyPairGenerator.getInstance(PROOF_JCA_ALGORITHM).generateKeyPair()
            val publicKey = pair.public.encoded
            val privateKey = pair.private.encoded
            try {
                ProofKeyMaterial(publicKey, privateKey)
            } finally {
                publicKey.fill(0)
                privateKey.fill(0)
                try {
                    pair.private.destroy()
                } catch (_: DestroyFailedException) {
                    // The provider-owned key is left for collection after encoded copies are wiped.
                }
            }
        } catch (error: GeneralSecurityException) {
            throw CryptoException("Could not generate device proof key", error)
        }

    private fun identity(
        deviceId: String,
        keyAlgorithm: String,
        publicKey: ByteArray,
        privateKey: ByteArray,
        proof: ProofKeyMaterial,
    ): LocalDeviceIdentity =
        LocalDeviceIdentity(
            deviceId,
            keyAlgorithm,
            publicKey,
            privateKey,
            LocalDeviceIdentity.PROOF_KEY_ALGORITHM,
            proof.publicKey,
            proof.privateKey,
        )

    private fun writePropertiesAtomically(properties: Properties) {
        Files.createDirectories(identityFile.parent)
        val temporary = Files.createTempFile(identityFile.parent, "device-identity-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output ->
                properties.store(output, "Keystead device identity")
            }
            Files.move(temporary, identityFile, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun loadProperties(): Properties {
        val properties = Properties()
        Files.newInputStream(identityFile).use { input: InputStream -> properties.load(input) }
        return properties
    }

    private fun deriveWrappingKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val passwordCopy = passphrase.copyOf()
        val spec = PBEKeySpec(passwordCopy, salt.copyOf(), KDF_ITERATIONS, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } catch (error: GeneralSecurityException) {
            throw CryptoException("Could not derive device identity wrapping key", error)
        } finally {
            passwordCopy.fill('\u0000')
            spec.clearPassword()
        }
    }

    private fun encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray =
        runCipher(Cipher.ENCRYPT_MODE, key, nonce, plaintext, aad, "Could not encrypt device identity")

    private fun decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
        aad: ByteArray,
    ): ByteArray =
        runCipher(Cipher.DECRYPT_MODE, key, nonce, ciphertext, aad, "Could not decrypt device identity")

    private fun runCipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
        errorMessage: String,
    ): ByteArray {
        return try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(input)
        } catch (error: GeneralSecurityException) {
            throw CryptoException(errorMessage, error)
        }
    }

    private fun aad(deviceId: String, keyAlgorithm: String, publicKey: ByteArray): ByteArray =
        listOf("keystead-device-identity-v1", deviceId, keyAlgorithm, b64(publicKey))
            .joinToString("|")
            .toByteArray(StandardCharsets.UTF_8)

    private fun proofAad(
        deviceId: String,
        keyAlgorithm: String,
        publicKey: ByteArray,
    ): ByteArray =
        listOf("keystead-device-proof-identity-v1", deviceId, keyAlgorithm, b64(publicKey))
            .joinToString("|")
            .toByteArray(StandardCharsets.UTF_8)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Device identity is missing $key")

    private fun bytes(properties: Properties, key: String): ByteArray =
        Base64.getDecoder().decode(required(properties, key)).also {
            if (it.isEmpty()) {
                throw IllegalStateException("Device identity is missing $key")
            }
        }

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private class ProofKeyMaterial(
        publicKey: ByteArray,
        privateKey: ByteArray,
    ) : AutoCloseable {
        val publicKey = publicKey.copyOf()
        val privateKey = privateKey.copyOf()

        override fun close() {
            privateKey.fill(0)
        }

        override fun toString(): String = "ProofKeyMaterial(<redacted>)"
    }

    companion object {
        private const val FORMAT_VERSION_1 = "1"
        private const val FORMAT_VERSION_2 = "2"
        private const val FORMAT_VERSION_3 = "3"
        private const val PROOF_JCA_ALGORITHM = "Ed25519"
        private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        private const val KDF_ITERATIONS = 120_000
        private const val KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val GCM_NONCE_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val LOCK_FILE_NAME = ".device-identity.lock"
        private val PROCESS_LOCKS = ConcurrentHashMap<Path, ReentrantLock>()
    }
}

sealed interface DeviceIdentityMigrationResult {
    data object Migrated : DeviceIdentityMigrationResult
    data object AlreadyNative : DeviceIdentityMigrationResult
}

/** Copies the private key out of protected memory; the caller owns and must wipe the result. */
private fun DeviceKeyPair.privateKeyCopy(): ByteArray {
    var copy = ByteArray(0)
    copyPrivateKey { bytes -> copy = bytes.clone() }
    return copy
}
