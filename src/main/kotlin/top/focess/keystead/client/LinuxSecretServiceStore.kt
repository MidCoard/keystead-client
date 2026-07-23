package top.focess.keystead.client

import de.swiesend.secretservice.simple.SimpleCollection
import java.nio.charset.StandardCharsets
import java.util.Base64
import top.focess.keystead.memory.Wipe

internal data class SecretServiceItem(val path: String, val secret: ByteArray)
internal interface SecretServicePort {
    fun available(): Boolean
    fun search(attributes: Map<String, String>): List<SecretServiceItem>
    fun replace(attributes: Map<String, String>, label: String, secret: ByteArray)
    fun delete(attributes: Map<String, String>)
}

internal class DbusSecretServicePort : SecretServicePort {
    override fun available(): Boolean = try { SimpleCollection.isAvailable() } catch (_: RuntimeException) { false }
    override fun search(attributes: Map<String, String>): List<SecretServiceItem> =
        SimpleCollection().use { collection ->
            collection.getItems(attributes).map { path ->
                val chars = collection.getSecret(path)
                try { SecretServiceItem(path, Base64.getDecoder().decode(String(chars))) } finally { Wipe.wipe(chars) }
            }
        }
    override fun replace(attributes: Map<String, String>, label: String, secret: ByteArray) {
        SimpleCollection().use { collection ->
            if (collection.isLocked) collection.unlockWithUserPermission()
            val existing = collection.getItems(attributes)
            if (existing.size > 1) throw IllegalStateException("Secret Service returned duplicate items")
            val encoded = Base64.getEncoder().encodeToString(secret)
            if (existing.isEmpty()) collection.createItem(label, encoded, attributes) else collection.updateItem(existing.single(), label, encoded, attributes)
        }
    }
    override fun delete(attributes: Map<String, String>) { SimpleCollection().use { it.deleteItems(it.getItems(attributes)) } }
}

class LinuxSecretServiceStore internal constructor(private val port: SecretServicePort) : OsSecretStore {
    constructor() : this(DbusSecretServicePort())
    override val providerId = "linux-secret-service"
    override fun availability(): OsSecretStoreAvailability = when { !isLinux() -> OsSecretStoreAvailability(OsSecretStoreStatus.UNSUPPORTED, "linux-secret-service-unsupported"); !port.available() -> OsSecretStoreAvailability(OsSecretStoreStatus.UNAVAILABLE, "linux-secret-service-unavailable"); else -> OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "linux-secret-service-available") }
    override fun save(instanceId: String, secret: ByteArray) {
        val copy = secret.copyOf()
        try { call("linux-secret-service-save") { port.replace(attributes(instanceId), "Keystead Desktop", copy) } } finally { Wipe.wipe(copy) }
    }
    override fun load(instanceId: String): ByteArray? = call("linux-secret-service-load") {
        val items = port.search(attributes(instanceId))
        try {
            if (items.size > 1) throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "linux-secret-service-duplicate")
            items.singleOrNull()?.secret?.copyOf()
        } finally { items.forEach { Wipe.wipe(it.secret) } }
    }
    override fun delete(instanceId: String) = call("linux-secret-service-delete") { port.delete(attributes(instanceId)) }
    private fun attributes(instanceId: String) = mapOf("application" to "top.focess.keystead.desktop", "instance" to instanceId)
    private fun isLinux() = System.getProperty("os.name").lowercase().contains("linux")
    private fun <T> call(code: String, action: () -> T): T { if (!isLinux()) throw OsSecretStoreException(OsSecretStoreFailure.UNSUPPORTED, "linux-secret-service-unsupported"); return try { action() } catch (error: OsSecretStoreException) { throw error } catch (error: SecurityException) { throw OsSecretStoreException(OsSecretStoreFailure.ACCESS_DENIED, code, error) } catch (error: Throwable) { throw OsSecretStoreException(OsSecretStoreFailure.UNAVAILABLE, code, error) } }
}
