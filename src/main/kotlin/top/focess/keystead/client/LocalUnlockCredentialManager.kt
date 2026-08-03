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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import top.focess.keystead.crypto.CryptoException
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.crypto.DeviceKeyPair
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.KeyId

enum class LocalLoginPersistence {
    BIOMETRIC,
    PASSPHRASE_FILE,
}

data class LocalUnlockCredentialDescriptor(
    val persistence: LocalLoginPersistence,
    val keyFingerprint: String,
)

class LocalUnlockCredential internal constructor(
    val persistence: LocalLoginPersistence,
    publicKey: ByteArray,
    privateKey: ByteArray,
) : AutoCloseable {
    private val publicKeyBytes = publicKey.copyOf()
    private val privateKeyBytes = privateKey.copyOf()
    private var closed = false

    internal fun publicKey(): ByteArray = publicKeyBytes.copyOf()

    @Synchronized
    internal fun privateKey(): ByteArray {
        check(!closed) { "Local login credential is closed" }
        return privateKeyBytes.copyOf()
    }

    internal val bindingId: String
        get() = LOCAL_UNLOCK_BINDING_ID

    @Synchronized
    override fun close() {
        if (!closed) {
            Wipe.wipe(privateKeyBytes)
            closed = true
        }
    }

    override fun toString(): String = "LocalUnlockCredential(<redacted>)"
}

class LocalUnlockCredentialManager(
    private val directory: Path,
    private val biometricStorage: () -> SecureStorage?,
    private val crypto: DefaultCryptoService = DefaultCryptoService(),
    private val random: SecureRandom = SecureRandom(),
) : AutoCloseable {
    private val metadataFile = directory.resolve("local-login.properties")
    private var current: LocalUnlockCredential? = null

    var currentPersistence: LocalLoginPersistence? = null
        private set

    val canEnrollVaultKey: Boolean
        get() = current != null

    fun currentCredential(): LocalUnlockCredential? = current

    fun descriptor(): LocalUnlockCredentialDescriptor? =
        withCredentialLock {
            if (!Files.exists(metadataFile)) return@withCredentialLock null
            val properties = loadProperties()
            require(properties.getProperty("formatVersion") == FORMAT_VERSION) {
                "Local login format is unsupported"
            }
            val persistence =
                runCatching {
                    LocalLoginPersistence.valueOf(required(properties, "persistence"))
                }.getOrElse { throw IllegalStateException("Local login persistence is invalid") }
            val publicKey = bytes(properties, "publicKey")
            try {
                LocalUnlockCredentialDescriptor(
                    persistence = persistence,
                    keyFingerprint =
                        Base64.getUrlEncoder().withoutPadding().encodeToString(
                            MessageDigest.getInstance("SHA-256").digest(publicKey),
                        ),
                )
            } finally {
                Wipe.wipe(publicKey)
            }
        }

    fun loadOrCreate(
        mode: SecureStorageMode,
        passphrase: CharArray,
    ): LocalUnlockCredential {
        require(mode != SecureStorageMode.MEMORY_ONLY) {
            "Local login must use persistent protected storage"
        }
        return withCredentialLock {
            val expected = mode.toLocalLoginPersistence()
            val loaded =
                if (Files.exists(metadataFile)) {
                    val descriptor = requireNotNull(descriptorUnlocked())
                    check(descriptor.persistence == expected) {
                        "Local login protection cannot be changed after creation"
                    }
                    loadUnlocked(descriptor.persistence, passphrase)
                } else {
                    createUnlocked(expected, passphrase)
                }
            adopt(loaded)
        }
    }

    fun loadExisting(passphrase: CharArray): LocalUnlockCredential =
        withCredentialLock {
            val descriptor =
                descriptorUnlocked() ?: throw IllegalStateException("Local login is not configured")
            adopt(loadUnlocked(descriptor.persistence, passphrase))
        }

    fun unload() {
        current?.close()
        current = null
        currentPersistence = null
    }

    override fun close() = unload()

    private fun adopt(credential: LocalUnlockCredential): LocalUnlockCredential {
        current?.close()
        current = credential
        currentPersistence = credential.persistence
        return credential
    }

    private fun descriptorUnlocked(): LocalUnlockCredentialDescriptor? {
        if (!Files.exists(metadataFile)) return null
        val properties = loadProperties()
        require(properties.getProperty("formatVersion") == FORMAT_VERSION) {
            "Local login format is unsupported"
        }
        val persistence =
            runCatching { LocalLoginPersistence.valueOf(required(properties, "persistence")) }
                .getOrElse { throw IllegalStateException("Local login persistence is invalid") }
        val publicKey = bytes(properties, "publicKey")
        return try {
            LocalUnlockCredentialDescriptor(
                persistence,
                Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(publicKey),
                ),
            )
        } finally {
            Wipe.wipe(publicKey)
        }
    }

    private fun createUnlocked(
        persistence: LocalLoginPersistence,
        passphrase: CharArray,
    ): LocalUnlockCredential {
        crypto.generateDeviceKeyPair().use { pair ->
            val publicKey = pair.publicKey()
            val privateKey = pair.privateKeyCopy()
            try {
                verifyPair(publicKey, privateKey)
                val properties = Properties()
                properties.setProperty("formatVersion", FORMAT_VERSION)
                properties.setProperty("persistence", persistence.name)
                properties.setProperty("keyAlgorithm", pair.keyAlgorithm())
                properties.setProperty("publicKey", b64(publicKey))
                when (persistence) {
                    LocalLoginPersistence.BIOMETRIC -> {
                        val storage = requireBiometricStorage()
                        var stored = false
                        try {
                            storage.save(BIOMETRIC_KEY, privateKey)
                            stored = true
                            val reloaded =
                                storage.load(BIOMETRIC_KEY)
                                    ?: throw IllegalStateException(
                                        "Biometric local login could not be verified",
                                    )
                            try {
                                check(reloaded.contentEquals(privateKey)) {
                                    "Biometric local login could not be verified"
                                }
                            } finally {
                                Wipe.wipe(reloaded)
                            }
                            writeProperties(properties)
                        } catch (error: Throwable) {
                            if (stored) runCatching { storage.delete(BIOMETRIC_KEY) }
                            throw error
                        }
                    }
                    LocalLoginPersistence.PASSPHRASE_FILE -> {
                        require(passphrase.isNotEmpty()) {
                            "Local login passphrase must not be empty"
                        }
                        val salt = randomBytes(SALT_BYTES)
                        val nonce = randomBytes(GCM_NONCE_BYTES)
                        val aad = localLoginAad(pair.keyAlgorithm(), publicKey)
                        var wrappingKey = ByteArray(0)
                        var encryptedPrivateKey = ByteArray(0)
                        try {
                            wrappingKey = deriveWrappingKey(passphrase, salt)
                            encryptedPrivateKey =
                                runCipher(
                                    Cipher.ENCRYPT_MODE,
                                    wrappingKey,
                                    nonce,
                                    privateKey,
                                    aad,
                                    "Could not protect local login credential",
                                )
                            properties.setProperty("kdfSalt", b64(salt))
                            properties.setProperty("privateKeyNonce", b64(nonce))
                            properties.setProperty(
                                "encryptedPrivateKey",
                                b64(encryptedPrivateKey),
                            )
                            writeProperties(properties)
                        } finally {
                            Wipe.wipe(salt)
                            Wipe.wipe(nonce)
                            Wipe.wipe(aad)
                            Wipe.wipe(wrappingKey)
                            Wipe.wipe(encryptedPrivateKey)
                        }
                    }
                }
                return LocalUnlockCredential(persistence, publicKey, privateKey)
            } finally {
                Wipe.wipe(publicKey)
                Wipe.wipe(privateKey)
            }
        }
    }

    private fun loadUnlocked(
        persistence: LocalLoginPersistence,
        passphrase: CharArray,
    ): LocalUnlockCredential {
        val properties = loadProperties()
        val keyAlgorithm = required(properties, "keyAlgorithm")
        val publicKey = bytes(properties, "publicKey")
        var privateKey = ByteArray(0)
        return try {
            privateKey =
                when (persistence) {
                    LocalLoginPersistence.BIOMETRIC ->
                        requireBiometricStorage().load(BIOMETRIC_KEY)
                            ?: throw IllegalStateException(
                                "Biometric local login is incomplete",
                            )
                    LocalLoginPersistence.PASSPHRASE_FILE -> {
                        require(passphrase.isNotEmpty()) {
                            "Local login passphrase must not be empty"
                        }
                        val salt = bytes(properties, "kdfSalt")
                        val nonce = bytes(properties, "privateKeyNonce")
                        val encrypted = bytes(properties, "encryptedPrivateKey")
                        val aad = localLoginAad(keyAlgorithm, publicKey)
                        val wrappingKey = deriveWrappingKey(passphrase, salt)
                        try {
                            runCipher(
                                Cipher.DECRYPT_MODE,
                                wrappingKey,
                                nonce,
                                encrypted,
                                aad,
                                "Could not unlock local login credential",
                            )
                        } finally {
                            Wipe.wipe(salt)
                            Wipe.wipe(nonce)
                            Wipe.wipe(encrypted)
                            Wipe.wipe(aad)
                            Wipe.wipe(wrappingKey)
                        }
                    }
                }
            verifyPair(publicKey, privateKey)
            LocalUnlockCredential(persistence, publicKey, privateKey)
        } finally {
            Wipe.wipe(publicKey)
            Wipe.wipe(privateKey)
        }
    }

    private fun verifyPair(publicKey: ByteArray, privateKey: ByteArray) {
        val context = randomBytes(32)
        crypto.generateVaultKey(KeyId("local-login-verification")).use { original ->
            val wrapped = crypto.wrapVaultKeyForDevice(original, publicKey, context)
            try {
                crypto.unwrapVaultKeyFromDevicePackage(
                    original.keyId(),
                    wrapped,
                    privateKey,
                    context,
                ).use { opened ->
                    var expected = ByteArray(0)
                    var actual = ByteArray(0)
                    try {
                        original.copyBytes { expected = it.copyOf() }
                        opened.copyBytes { actual = it.copyOf() }
                        check(expected.contentEquals(actual)) {
                            "Local login key pair is inconsistent"
                        }
                    } finally {
                        Wipe.wipe(expected)
                        Wipe.wipe(actual)
                    }
                }
            } finally {
                Wipe.wipe(wrapped)
                Wipe.wipe(context)
            }
        }
    }

    private fun requireBiometricStorage(): SecureStorage {
        val storage = biometricStorage()
            ?: throw IllegalStateException("Biometric local login is unavailable")
        check(storage.capability == SecureStorageCapability.OS_BIOMETRIC_GATED) {
            "Biometric local login requires biometric-gated storage"
        }
        return storage
    }

    private fun deriveWrappingKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val passwordCopy = passphrase.copyOf()
        val spec = PBEKeySpec(passwordCopy, salt.copyOf(), KDF_ITERATIONS, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(KDF_ALGORITHM).generateSecret(spec).encoded
        } catch (error: GeneralSecurityException) {
            throw CryptoException("Could not derive local login wrapping key", error)
        } finally {
            Wipe.wipe(passwordCopy)
            spec.clearPassword()
        }
    }

    private fun runCipher(
        mode: Int,
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        aad: ByteArray,
        errorMessage: String,
    ): ByteArray =
        try {
            val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
            cipher.init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(aad)
            cipher.doFinal(input)
        } catch (error: GeneralSecurityException) {
            throw CryptoException(errorMessage, error)
        }

    private fun writeProperties(properties: Properties) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "local-login-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output ->
                properties.store(output, "Keystead local login")
            }
            Files.move(temporary, metadataFile, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun loadProperties(): Properties =
        Properties().also { properties ->
            Files.newInputStream(metadataFile).use { input: InputStream ->
                properties.load(input)
            }
        }

    private fun <T> withCredentialLock(action: () -> T): T {
        Files.createDirectories(directory)
        val canonical = directory.toAbsolutePath().normalize()
        val processLock = PROCESS_LOCKS.computeIfAbsent(canonical) { ReentrantLock(true) }
        processLock.lock()
        try {
            FileChannel.open(directory.resolve(LOCK_FILE_NAME), CREATE, WRITE).use { channel ->
                channel.lock().use { return action() }
            }
        } finally {
            processLock.unlock()
        }
    }

    private fun required(properties: Properties, key: String): String =
        properties.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Local login metadata is missing $key")

    private fun bytes(properties: Properties, key: String): ByteArray =
        try {
            Base64.getDecoder().decode(required(properties, key)).also {
                check(it.isNotEmpty()) { "Local login metadata is missing $key" }
            }
        } catch (error: IllegalArgumentException) {
            throw IllegalStateException("Local login metadata field $key is invalid", error)
        }

    private fun localLoginAad(keyAlgorithm: String, publicKey: ByteArray): ByteArray =
        listOf("keystead-local-login-v1", keyAlgorithm, b64(publicKey))
            .joinToString("|")
            .toByteArray(StandardCharsets.UTF_8)

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)

    private fun b64(value: ByteArray): String = Base64.getEncoder().encodeToString(value)

    private companion object {
        const val FORMAT_VERSION = "1"
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val CIPHER_ALGORITHM = "AES/GCM/NoPadding"
        const val KDF_ITERATIONS = 210_000
        const val KEY_BYTES = 32
        const val SALT_BYTES = 16
        const val GCM_NONCE_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val LOCK_FILE_NAME = ".local-login.lock"
        val BIOMETRIC_KEY =
            SecureStorageKey("local-vault-login", "this-vault", "private-key")
        val PROCESS_LOCKS = ConcurrentHashMap<Path, ReentrantLock>()
    }
}

internal const val LOCAL_UNLOCK_BINDING_ID = "local-vault-login-v1"

private fun SecureStorageMode.toLocalLoginPersistence(): LocalLoginPersistence =
    when (this) {
        SecureStorageMode.BIOMETRIC -> LocalLoginPersistence.BIOMETRIC
        SecureStorageMode.PASSPHRASE_FILE -> LocalLoginPersistence.PASSPHRASE_FILE
        SecureStorageMode.MEMORY_ONLY ->
            throw IllegalArgumentException("Local login cannot be memory-only")
    }

private fun DeviceKeyPair.privateKeyCopy(): ByteArray {
    var copy = ByteArray(0)
    copyPrivateKey { bytes -> copy = bytes.copyOf() }
    return copy
}
