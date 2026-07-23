package top.focess.keystead.client

import top.focess.keystead.memory.Wipe

/** Capability advertised by a secure-storage implementation. */
enum class SecureStorageCapability { MEMORY_ONLY, FILE_PASSPHRASE_PROTECTED, OS_USER_PROTECTED, OS_BIOMETRIC_GATED }

data class SecureStorageKey(val namespace: String, val account: String, val name: String) {
    init {
        require(namespace.isNotBlank() && account.isNotBlank() && name.isNotBlank())
        require(listOf(namespace, account, name).all { it.length <= 128 && it.none { c -> c == '/' || c == '\\' || c.isISOControl() } })
    }
    override fun toString(): String = "$namespace:$account:$name"
}

/**
 * Secret store backed by a capability-appropriate mechanism.
 *
 * Ownership/zeroing contract: [save] copies the supplied value (the caller
 * retains ownership and may zero its array afterwards); [load] returns a fresh
 * copy; [delete] zeroes the removed value. Stored secrets are never aliased by
 * caller-supplied or returned arrays.
 */
interface SecureStorage {
    val capability: SecureStorageCapability
    fun save(key: SecureStorageKey, value: ByteArray)
    fun load(key: SecureStorageKey): ByteArray?
    fun delete(key: SecureStorageKey)
    fun listKeys(namespace: String, account: String): Set<String> = emptySet()
}

/** Deterministic contract implementation for tests and memory-only sessions. */
class MemorySecureStorage : SecureStorage {
    override val capability = SecureStorageCapability.MEMORY_ONLY
    private val values = linkedMapOf<SecureStorageKey, ByteArray>()
    @Synchronized override fun save(key: SecureStorageKey, value: ByteArray) { Wipe.wipe(values[key]); values[key] = value.copyOf() }
    @Synchronized override fun load(key: SecureStorageKey): ByteArray? = values[key]?.copyOf()
    @Synchronized override fun delete(key: SecureStorageKey) { Wipe.wipe(values.remove(key)) }
    @Synchronized override fun listKeys(namespace: String, account: String): Set<String> = values.keys.filter { it.namespace == namespace && it.account == account }.map { it.name }.toSet()
    @Synchronized fun clear() { values.values.forEach { Wipe.wipe(it) }; values.clear() }
}
