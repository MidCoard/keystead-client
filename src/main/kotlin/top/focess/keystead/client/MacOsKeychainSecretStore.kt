package top.focess.keystead.client

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.charset.StandardCharsets

internal interface MacOsKeychainPort {
    fun save(service: String, account: String, secret: ByteArray)
    fun load(service: String, account: String): ByteArray?
    fun delete(service: String, account: String)
}

private class MacStatusException(val status: Int) : RuntimeException("Keychain operation failed")

internal class JnaMacOsKeychainPort : MacOsKeychainPort {
    private interface Security : Library {
        fun SecKeychainAddGenericPassword(keychain: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray, passwordLength: Int, password: ByteArray, item: PointerByReference?): Int
        fun SecKeychainFindGenericPassword(keychainOrArray: Pointer?, serviceLength: Int, service: ByteArray, accountLength: Int, account: ByteArray, passwordLength: IntByReference?, passwordData: PointerByReference?, item: PointerByReference?): Int
        fun SecKeychainItemModifyAttributesAndData(item: Pointer, attributes: Pointer?, length: Int, data: ByteArray): Int
        fun SecKeychainItemDelete(item: Pointer): Int
        fun SecKeychainItemFreeContent(attributes: Pointer?, data: Pointer?): Int
    }

    private interface CoreFoundation : Library {
        fun CFRelease(value: Pointer)
    }

    private val security by lazy { Native.load("Security", Security::class.java) }
    private val coreFoundation by lazy { Native.load("CoreFoundation", CoreFoundation::class.java) }

    override fun save(service: String, account: String, secret: ByteArray) {
        val serviceBytes = service.toByteArray(StandardCharsets.UTF_8); val accountBytes = account.toByteArray(StandardCharsets.UTF_8)
        val item = PointerByReference(); val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, null, null, item)
        try {
            val result = if (status == ITEM_NOT_FOUND) security.SecKeychainAddGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, secret.size, secret, null) else if (status == SUCCESS) security.SecKeychainItemModifyAttributesAndData(item.value, null, secret.size, secret) else status
            if (result != SUCCESS) throw MacStatusException(result)
        } finally {
            if (status == SUCCESS && item.value != null) coreFoundation.CFRelease(item.value)
        }
    }

    override fun load(service: String, account: String): ByteArray? {
        val serviceBytes = service.toByteArray(StandardCharsets.UTF_8); val accountBytes = account.toByteArray(StandardCharsets.UTF_8)
        val length = IntByReference(); val data = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, length, data, null)
        if (status == ITEM_NOT_FOUND) return null
        if (status != SUCCESS) throw MacStatusException(status)
        return try { data.value.getByteArray(0, length.value) } finally { security.SecKeychainItemFreeContent(null, data.value) }
    }

    override fun delete(service: String, account: String) {
        val serviceBytes = service.toByteArray(StandardCharsets.UTF_8); val accountBytes = account.toByteArray(StandardCharsets.UTF_8); val item = PointerByReference()
        val status = security.SecKeychainFindGenericPassword(null, serviceBytes.size, serviceBytes, accountBytes.size, accountBytes, null, null, item)
        if (status == ITEM_NOT_FOUND) return
        if (status != SUCCESS) throw MacStatusException(status)
        try {
            val deleted = security.SecKeychainItemDelete(item.value); if (deleted != SUCCESS) throw MacStatusException(deleted)
        } finally {
            if (item.value != null) coreFoundation.CFRelease(item.value)
        }
    }

    private companion object { const val SUCCESS = 0; const val ITEM_NOT_FOUND = -25300 }
}

class MacOsKeychainSecretStore internal constructor(private val port: MacOsKeychainPort) : OsSecretStore {
    constructor() : this(JnaMacOsKeychainPort())
    override val providerId = "macos-keychain"
    override fun availability() = if (isMac()) OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "macos-keychain-available") else OsSecretStoreAvailability(OsSecretStoreStatus.UNSUPPORTED, "macos-keychain-unsupported")
    override fun save(instanceId: String, secret: ByteArray) {
        val copy = secret.copyOf()
        try { call("macos-keychain-save") { port.save(SERVICE, instanceId, copy) } } finally { copy.fill(0) }
    }
    override fun load(instanceId: String): ByteArray? = call("macos-keychain-load") {
        val loaded = port.load(SERVICE, instanceId) ?: return@call null
        try { loaded.copyOf() } finally { loaded.fill(0) }
    }
    override fun delete(instanceId: String) = call("macos-keychain-delete") { port.delete(SERVICE, instanceId) }
    private fun isMac() = System.getProperty("os.name").lowercase().contains("mac")
    private fun <T> call(code: String, action: () -> T): T { if (!isMac()) throw OsSecretStoreException(OsSecretStoreFailure.UNSUPPORTED, "macos-keychain-unsupported"); return try { action() } catch (error: MacStatusException) { val failure = when (error.status) { -25293 -> OsSecretStoreFailure.LOCKED; -25308 -> OsSecretStoreFailure.ACCESS_DENIED; else -> OsSecretStoreFailure.IO_FAILURE }; throw OsSecretStoreException(failure, code, error) } }
    private companion object { const val SERVICE = "top.focess.keystead.desktop" }
}
