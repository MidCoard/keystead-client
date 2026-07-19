package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.crypto.CryptoException
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.model.KeyId

class DeviceIdentityStoreTest {
    @Test
    fun nativeFormatStoresOnlyPublicMetadataAndReloadsPrivateKeys() {
        val directory = createTempDirectory("keystead-native-device")
        val storage = TestNativeStorage()
        val store = DeviceIdentityStore(directory, secureStorage = storage)

        val created = store.createOrLoadNative("native-device")
        val privateKey = created.privateKey()
        val proof = created.proofPrivateKey()
        created.close()

        val metadata = directory.resolve("device-identity.properties").readText()
        assertTrue(metadata.contains("formatVersion=3"))
        assertFalse(metadata.contains("encryptedPrivateKey"))
        assertFalse(metadata.contains(Base64.getEncoder().encodeToString(privateKey)))
        store.createOrLoadNative("native-device").use { loaded ->
            assertContentEquals(privateKey, loaded.privateKey())
            assertContentEquals(proof, loaded.proofPrivateKey())
        }
        privateKey.fill(0)
        proof.fill(0)
    }

    @Test
    fun passphraseIdentityMigratesToNativeOnlyAfterVerifiedSave() {
        val directory = createTempDirectory("keystead-native-migration")
        DeviceIdentityStore(directory).createOrLoad("migrated-device", "old-passphrase".toCharArray()).close()
        val storage = TestNativeStorage()
        val passphrase = "old-passphrase".toCharArray()
        val result = DeviceIdentityStore(directory, secureStorage = storage).migrateToNative("migrated-device", passphrase)
        assertEquals(DeviceIdentityMigrationResult.Migrated, result)
        assertTrue(passphrase.all { it == '\u0000' })
        assertTrue(directory.resolve("device-identity.properties").readText().contains("formatVersion=3"))
    }
    @Test
    fun createOrLoadPersistsEncryptedDeviceIdentity() {
        val directory = createTempDirectory("keystead-device-identity-test")
        val store = DeviceIdentityStore(directory)

        val created =
            store.createOrLoad(
                deviceId = "laptop-1",
                passphrase = "device-passphrase".toCharArray(),
            )
        val rawPrivateKey = created.privateKey()
        val rawPublicKey = created.publicKey()
        val rawProofPrivateKey = ownedBytes(created, "proofPrivateKeyBytes").copyOf()
        created.close()

        val file = directory.resolve("device-identity.properties").readText()
        assertTrue(file.contains("formatVersion=2"))
        assertTrue(file.contains("deviceId=laptop-1"))
        assertTrue(file.contains(DefaultCryptoService.DEVICE_KEY_ALGORITHM))
        assertFalse(file.contains(Base64.getEncoder().encodeToString(rawPrivateKey)))
        assertFalse(file.contains(Base64.getEncoder().encodeToString(rawProofPrivateKey)))

        val loaded =
            store.createOrLoad(
                deviceId = "laptop-1",
                passphrase = "device-passphrase".toCharArray(),
            )
        try {
            assertEquals("laptop-1", loaded.deviceId)
            assertEquals(DefaultCryptoService.DEVICE_KEY_ALGORITHM, loaded.keyAlgorithm)
            val loadedPrivateKey = loaded.privateKey()
            try {
                assertTrue(rawPrivateKey.contentEquals(loadedPrivateKey))
            } finally {
                loadedPrivateKey.fill(0)
            }
            assertTrue(rawPublicKey.contentEquals(loaded.publicKey()))
        } finally {
            rawPrivateKey.fill(0)
            rawPublicKey.fill(0)
            rawProofPrivateKey.fill(0)
            loaded.close()
        }
    }

    @Test
    fun createdIdentityUsesSeparateEd25519ProofKeyAndSignsCanonicalChallenge() {
        val directory = createTempDirectory("keystead-device-proof-test")
        val passphrase = "device-passphrase".toCharArray()

        val identity = DeviceIdentityStore(directory).createOrLoad("laptop-1", passphrase)

        try {
            assertTrue(passphrase.all { it == '\u0000' })
            assertEquals("ED25519", identity.proofKeyAlgorithm)
            assertFalse(identity.keyAlgorithm == identity.proofKeyAlgorithm)
            assertEquals("LocalDeviceIdentity(<redacted>)", identity.toString())

            val signature = identity.signDeviceChallenge("challenge-1", "nonce-1")

            assertTrue(
                verifies(
                    identity.proofPublicKey(),
                    "keystead-device-proof:v1:challenge-1:nonce-1",
                    signature,
                ),
            )
            assertFalse(
                verifies(
                    identity.proofPublicKey(),
                    "keystead-device-proof:v1:challenge-1:other-nonce",
                    signature,
                ),
            )
        } finally {
            identity.close()
        }
    }

    @Test
    fun loadingVersionOneIdentityMigratesWithoutChangingWrappingKeyPair() {
        val directory = createTempDirectory("keystead-device-v1-migration-test")
        val store = DeviceIdentityStore(directory)
        val created = store.createOrLoad("laptop-1", "device-passphrase".toCharArray())
        val wrappingPublicKey = created.publicKey()
        val wrappingPrivateKey = created.privateKey()
        val crypto = DefaultCryptoService()
        val context = "pre-migration-package-context".encodeToByteArray()
        val vaultKey = crypto.generateVaultKey(KeyId("pre-migration-key"))
        val wrappedVaultKey =
            crypto.wrapVaultKeyForDevice(vaultKey, wrappingPublicKey, context)
        created.close()
        downgradeToVersionOne(directory)

        val migrated = store.createOrLoad("laptop-1", "device-passphrase".toCharArray())

        try {
            assertTrue(wrappingPublicKey.contentEquals(migrated.publicKey()))
            val migratedWrappingPrivateKey = migrated.privateKey()
            try {
                assertTrue(wrappingPrivateKey.contentEquals(migratedWrappingPrivateKey))
            } finally {
                migratedWrappingPrivateKey.fill(0)
            }
            assertEquals("ED25519", migrated.proofKeyAlgorithm)
            assertTrue(
                verifies(
                    migrated.proofPublicKey(),
                    "keystead-device-proof:v1:challenge-after-upgrade:nonce-after-upgrade",
                    migrated.signDeviceChallenge("challenge-after-upgrade", "nonce-after-upgrade"),
                ),
            )
            assertTrue(
                directory.resolve("device-identity.properties").readText().contains("formatVersion=2"),
            )
            val migratedPrivateKey = migrated.privateKey()
            try {
                crypto.unwrapVaultKeyFromDevicePackage(
                    KeyId("pre-migration-key"),
                    wrappedVaultKey,
                    migratedPrivateKey,
                    context,
                ).use { opened ->
                    var sameKey = false
                    vaultKey.copyBytes { expected ->
                        opened.copyBytes { actual -> sameKey = expected.contentEquals(actual) }
                    }
                    assertTrue(sameKey)
                }
            } finally {
                migratedPrivateKey.fill(0)
            }
        } finally {
            wrappingPublicKey.fill(0)
            wrappingPrivateKey.fill(0)
            wrappedVaultKey.fill(0)
            context.fill(0)
            vaultKey.close()
            migrated.close()
        }
    }

    @Test
    fun checkedInVersionOneFixtureMigratesAndReloadsWithStableDualKeyIdentity() {
        val directory = createTempDirectory("keystead-device-v1-fixture-test")
        val identityFile = directory.resolve("device-identity.properties")
        val fixtureProperties = Properties()
        requireNotNull(javaClass.getResourceAsStream("/device-identity-v1.properties")).use { input ->
            val fixtureBytes = input.readAllBytes()
            try {
                Files.write(identityFile, fixtureBytes)
                fixtureBytes.inputStream().use(fixtureProperties::load)
            } finally {
                fixtureBytes.fill(0)
            }
        }
        val fixtureWrappingPublicKey =
            Base64.getDecoder().decode(fixtureProperties.getProperty("publicKey"))
        var firstWrappingPrivateKey = ByteArray(0)
        var firstProofPublicKey = ByteArray(0)
        var firstSignature = ""
        var first: LocalDeviceIdentity? = null
        var reloaded: LocalDeviceIdentity? = null
        try {
            first =
                DeviceIdentityStore(directory)
                    .createOrLoad("laptop-1", "device-passphrase".toCharArray())
            firstWrappingPrivateKey = first.privateKey()
            firstProofPublicKey = first.proofPublicKey()
            firstSignature =
                first.signDeviceChallenge("fixture-challenge", "fixture-nonce")
            assertTrue(fixtureWrappingPublicKey.contentEquals(first.publicKey()))
            assertTrue(
                verifies(
                    firstProofPublicKey,
                    "keystead-device-proof:v1:fixture-challenge:fixture-nonce",
                    firstSignature,
                ),
            )
            first.close()
            first = null

            reloaded =
                DeviceIdentityStore(directory)
                    .createOrLoad("laptop-1", "device-passphrase".toCharArray())
            assertTrue(fixtureWrappingPublicKey.contentEquals(reloaded.publicKey()))
            val reloadedWrappingPrivateKey = reloaded.privateKey()
            try {
                assertTrue(firstWrappingPrivateKey.contentEquals(reloadedWrappingPrivateKey))
            } finally {
                reloadedWrappingPrivateKey.fill(0)
            }
            assertTrue(firstProofPublicKey.contentEquals(reloaded.proofPublicKey()))
            assertEquals(
                firstSignature,
                reloaded.signDeviceChallenge("fixture-challenge", "fixture-nonce"),
            )
            assertTrue(identityFile.readText().contains("formatVersion=2"))
        } finally {
            fixtureWrappingPublicKey.fill(0)
            firstWrappingPrivateKey.fill(0)
            firstProofPublicKey.fill(0)
            firstSignature = ""
            first?.close()
            reloaded?.close()
        }
    }

    @Test
    fun closeWipesBothPrivateKeysAndDisablesPrivateOperations() {
        val directory = createTempDirectory("keystead-device-close-test")
        val identity =
            DeviceIdentityStore(directory)
                .createOrLoad("laptop-1", "device-passphrase".toCharArray())
        val wrappingPrivateKey = ownedBytes(identity, "privateKeyBytes")
        val proofPrivateKey = ownedBytes(identity, "proofPrivateKeyBytes")

        identity.close()

        assertTrue(wrappingPrivateKey.all { it == 0.toByte() })
        assertTrue(proofPrivateKey.all { it == 0.toByte() })
        assertFailsWith<IllegalStateException> { identity.privateKey() }
        assertFailsWith<IllegalStateException> {
            identity.signDeviceChallenge("challenge-1", "nonce-1")
        }
    }

    @Test
    fun wrongPassphraseCannotOpenStoredIdentity() {
        val directory = createTempDirectory("keystead-device-identity-test")
        val store = DeviceIdentityStore(directory)

        store.createOrLoad("phone-1", "correct-passphrase".toCharArray()).close()

        assertFailsWith<CryptoException> {
            store.createOrLoad("phone-1", "wrong-passphrase".toCharArray())
        }
    }

    @Test
    fun filesystemSetupFailureStillWipesCallerPassphrase() {
        val parent = createTempDirectory("keystead-device-directory-failure-test")
        val blockingFile = parent.resolve("not-a-directory")
        Files.writeString(blockingFile, "blocking file")
        val passphrase = "device-passphrase".toCharArray()

        assertFailsWith<Exception> {
            DeviceIdentityStore(blockingFile.resolve("device"))
                .createOrLoad("laptop-1", passphrase)
        }

        assertTrue(passphrase.all { it == '\u0000' })
    }

    @Test
    fun concurrentFirstCreationReturnsOnePersistedIdentity() {
        val directory = createTempDirectory("keystead-device-create-race-test")

        assertContendersSharePersistedIdentity(directory)
    }

    @Test
    fun concurrentVersionOneMigrationReturnsOnePersistedProofIdentity() {
        val directory = createTempDirectory("keystead-device-migration-race-test")
        DeviceIdentityStore(directory)
            .createOrLoad("laptop-1", "device-passphrase".toCharArray())
            .close()
        downgradeToVersionOne(directory)

        assertContendersSharePersistedIdentity(directory)
    }

    private fun assertContendersSharePersistedIdentity(directory: Path) {
        val firstRandom = FirstCallSecureRandom(blockFirstCall = true)
        val secondRandom = FirstCallSecureRandom(blockFirstCall = false)
        val firstPassphrase = "device-passphrase".toCharArray()
        val secondPassphrase = "device-passphrase".toCharArray()
        val persistedPassphrase = "device-passphrase".toCharArray()
        val executor = Executors.newFixedThreadPool(2)
        var firstFuture: Future<LocalDeviceIdentity>? = null
        var secondFuture: Future<LocalDeviceIdentity>? = null
        var firstIdentity: LocalDeviceIdentity? = null
        var secondIdentity: LocalDeviceIdentity? = null
        var persistedIdentity: LocalDeviceIdentity? = null
        try {
            firstFuture =
                executor.submit<LocalDeviceIdentity> {
                    DeviceIdentityStore(directory, random = firstRandom)
                        .createOrLoad("laptop-1", firstPassphrase)
                }
            assertTrue(
                firstRandom.entered.await(5, TimeUnit.SECONDS),
                "First contender did not reach identity persistence",
            )

            val secondStarted = CountDownLatch(1)
            secondFuture =
                executor.submit<LocalDeviceIdentity> {
                    secondStarted.countDown()
                    DeviceIdentityStore(directory, random = secondRandom)
                        .createOrLoad("laptop-1", secondPassphrase)
                }
            assertTrue(
                secondStarted.await(5, TimeUnit.SECONDS),
                "Second contender did not start",
            )
            val secondEnteredPersistenceBeforeRelease =
                secondRandom.entered.await(2, TimeUnit.SECONDS)

            firstRandom.release()
            firstIdentity = firstFuture.get(10, TimeUnit.SECONDS)
            secondIdentity = secondFuture.get(10, TimeUnit.SECONDS)
            persistedIdentity =
                DeviceIdentityStore(directory)
                    .createOrLoad("laptop-1", persistedPassphrase)

            assertFalse(
                secondEnteredPersistenceBeforeRelease,
                "Concurrent contender entered identity persistence before the winner completed",
            )
            assertSameIdentity(firstIdentity, secondIdentity)
            assertSameIdentity(firstIdentity, persistedIdentity)
            assertTrue(firstPassphrase.all { it == '\u0000' })
            assertTrue(secondPassphrase.all { it == '\u0000' })
            assertTrue(persistedPassphrase.all { it == '\u0000' })
        } finally {
            firstRandom.release()
            firstIdentity?.close()
            secondIdentity?.close()
            persistedIdentity?.close()
            closeCompleted(firstFuture, firstIdentity)
            closeCompleted(secondFuture, secondIdentity)
            executor.shutdownNow()
            executor.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun assertSameIdentity(expected: LocalDeviceIdentity, actual: LocalDeviceIdentity) {
        val expectedPublicKey = expected.publicKey()
        val actualPublicKey = actual.publicKey()
        val expectedPrivateKey = expected.privateKey()
        val actualPrivateKey = actual.privateKey()
        val expectedProofPublicKey = expected.proofPublicKey()
        val actualProofPublicKey = actual.proofPublicKey()
        try {
            assertTrue(expectedPublicKey.contentEquals(actualPublicKey))
            assertTrue(expectedPrivateKey.contentEquals(actualPrivateKey))
            assertTrue(expectedProofPublicKey.contentEquals(actualProofPublicKey))
            assertEquals(
                expected.signDeviceChallenge("race-challenge", "race-nonce"),
                actual.signDeviceChallenge("race-challenge", "race-nonce"),
            )
        } finally {
            expectedPublicKey.fill(0)
            actualPublicKey.fill(0)
            expectedPrivateKey.fill(0)
            actualPrivateKey.fill(0)
            expectedProofPublicKey.fill(0)
            actualProofPublicKey.fill(0)
        }
    }

    private fun closeCompleted(
        future: Future<LocalDeviceIdentity>?,
        alreadyClosed: LocalDeviceIdentity?,
    ) {
        if (future != null && future.isDone && !future.isCancelled) {
            runCatching { future.get() }
                .getOrNull()
                ?.takeIf { it !== alreadyClosed }
                ?.close()
        }
    }

    private fun downgradeToVersionOne(directory: java.nio.file.Path) {
        val identityFile = directory.resolve("device-identity.properties")
        val properties = Properties()
        Files.newInputStream(identityFile).use(properties::load)
        properties.setProperty("formatVersion", "1")
        properties.remove("proofKeyAlgorithm")
        properties.remove("proofPublicKey")
        properties.remove("proofPrivateKeyNonce")
        properties.remove("encryptedProofPrivateKey")
        Files.newOutputStream(identityFile).use { properties.store(it, "Version-one fixture") }
    }

    private fun verifies(publicKeyBytes: ByteArray, payload: String, encodedSignature: String): Boolean {
        val publicKey =
            KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(publicKeyBytes))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        return verifier.verify(Base64.getDecoder().decode(encodedSignature))
    }

    private fun ownedBytes(identity: LocalDeviceIdentity, fieldName: String): ByteArray {
        val field = LocalDeviceIdentity::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(identity) as ByteArray
    }

    private class FirstCallSecureRandom(
        private val blockFirstCall: Boolean,
    ) : SecureRandom() {
        private val firstCall = AtomicBoolean(true)
        val entered = CountDownLatch(1)
        private val release = CountDownLatch(if (blockFirstCall) 1 else 0)

        override fun nextBytes(bytes: ByteArray) {
            if (firstCall.compareAndSet(true, false)) {
                entered.countDown()
                if (blockFirstCall) {
                    check(release.await(10, TimeUnit.SECONDS)) {
                        "Timed out waiting to release identity persistence"
                    }
                }
            }
            super.nextBytes(bytes)
        }

        fun release() {
            release.countDown()
        }
    }

    private class TestNativeStorage : SecureStorage {
        override val capability = SecureStorageCapability.OS_USER_PROTECTED
        private val values = mutableMapOf<SecureStorageKey, ByteArray>()
        override fun save(key: SecureStorageKey, value: ByteArray) { values.put(key, value.copyOf())?.fill(0) }
        override fun load(key: SecureStorageKey): ByteArray? = values[key]?.copyOf()
        override fun delete(key: SecureStorageKey) { values.remove(key)?.fill(0) }
    }
}
