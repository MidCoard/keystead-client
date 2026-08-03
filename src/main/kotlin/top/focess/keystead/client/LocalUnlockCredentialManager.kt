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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.crypto.DeviceKeyPair
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.KeyId

enum class LocalLoginPersistence { BIOMETRIC }

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

    fun loadOrCreate(mode: SecureStorageMode): LocalUnlockCredential {
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
                    loadUnlocked(descriptor.persistence)
                } else {
                    createUnlocked(expected)
                }
            adopt(loaded)
        }
    }

    fun loadExisting(): LocalUnlockCredential =
        withCredentialLock {
            val descriptor =
                descriptorUnlocked() ?: throw IllegalStateException("Local login is not configured")
            adopt(loadUnlocked(descriptor.persistence))
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
        SecureStorageMode.MEMORY_ONLY ->
            throw IllegalArgumentException("Local login cannot be memory-only")
    }

private fun DeviceKeyPair.privateKeyCopy(): ByteArray {
    var copy = ByteArray(0)
    copyPrivateKey { bytes -> copy = bytes.copyOf() }
    return copy
}
