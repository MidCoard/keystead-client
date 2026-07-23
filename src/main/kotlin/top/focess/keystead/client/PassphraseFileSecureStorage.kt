package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
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
) : SecureStorage {
    override val capability = SecureStorageCapability.FILE_PASSPHRASE_PROTECTED
    private val password = passphrase.copyOf()
    private val values = linkedMapOf<SecureStorageKey, ByteArray>()

    init { loadFile() }

    @Synchronized override fun save(key: SecureStorageKey, value: ByteArray) { Wipe.wipe(values[key]); values[key] = value.copyOf(); persist() }
    @Synchronized override fun load(key: SecureStorageKey): ByteArray? = values[key]?.copyOf()
    @Synchronized override fun delete(key: SecureStorageKey) { Wipe.wipe(values.remove(key)); persist() }
    @Synchronized override fun listKeys(namespace: String, account: String): Set<String> = values.keys.filter { it.namespace == namespace && it.account == account }.map { it.name }.toSet()
    @Synchronized fun close() { values.values.forEach { Wipe.wipe(it) }; values.clear(); Wipe.wipe(password) }

    private fun loadFile() {
        if (!Files.exists(file)) return
        val p = Properties().also { Files.newInputStream(file).use(it::load) }
        val salt = Base64.getDecoder().decode(p.getProperty("salt")); val nonce = Base64.getDecoder().decode(p.getProperty("nonce"))
        val plain = crypt(Cipher.DECRYPT_MODE, salt, nonce, Base64.getDecoder().decode(p.getProperty("data")))
        plain.toString(StandardCharsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.forEach { val i = it.indexOf('|'); if (i > 0) values[decodeKey(it.substring(0, i))] = Base64.getDecoder().decode(it.substring(i + 1)) }
        Wipe.wipe(plain)
    }

    private fun persist() {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes); val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val body = values.entries.joinToString("\n") { Base64.getEncoder().encodeToString(it.key.toString().toByteArray()) + "|" + Base64.getEncoder().encodeToString(it.value) }.toByteArray(StandardCharsets.UTF_8)
        val encoded = listOf("salt=" + Base64.getEncoder().encodeToString(salt), "nonce=" + Base64.getEncoder().encodeToString(nonce), "data=" + Base64.getEncoder().encodeToString(crypt(Cipher.ENCRYPT_MODE, salt, nonce, body))).joinToString("\n")
        Files.createDirectories(file.parent); val tmp = file.resolveSibling(".${file.fileName}.tmp"); Files.writeString(tmp, encoded, StandardCharsets.US_ASCII); Files.move(tmp, file, ATOMIC_MOVE, REPLACE_EXISTING); Wipe.wipe(body)
    }

    private fun crypt(mode: Int, salt: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray { val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS); val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES"); return Cipher.getInstance("AES/GCM/NoPadding").run { init(mode, key, GCMParameterSpec(GCM_TAG_BITS, nonce)); doFinal(input) } }
    private fun decodeKey(encoded: String): SecureStorageKey { val p = String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8).split(":", limit = 3); return SecureStorageKey(p[0], p[1], p[2]) }

    private companion object {
        const val SALT_BYTES = 16
        const val NONCE_BYTES = 12
        const val KDF_ITERATIONS = 120_000
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
