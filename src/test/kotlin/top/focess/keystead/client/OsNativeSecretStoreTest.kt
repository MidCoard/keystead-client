package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OsNativeSecretStoreTest {
    @Test
    fun `windows DPAPI store uses current-user port entropy and opaque filenames`() = withOs("Windows 11") {
        val directory = Files.createTempDirectory("keystead-dpapi")
        val port = FakeDpapiPort()
        val store = WindowsDpapiSecretStore(directory, port)
        val input = byteArrayOf(1, 2, 3, 4)

        store.save("account@example.com", input)

        assertContentEquals(input, store.load("account@example.com"))
        assertTrue(port.protectInput.all { it == 0.toByte() })
        assertTrue(port.lastEntropy.isNotEmpty())
        assertFalse(Files.list(directory).use { paths -> paths.anyMatch { it.fileName.toString().contains("account") } })
        store.delete("account@example.com")
        assertNull(store.load("account@example.com"))
    }

    @Test
    fun `macOS Keychain store wipes adapter copies and maps exact service account`() = withOs("Mac OS X") {
        val port = FakeKeychainPort()
        val store = MacOsKeychainSecretStore(port)

        store.save("desktop-1", byteArrayOf(7, 8, 9))
        assertTrue(port.savedReference.all { it == 0.toByte() })
        assertContentEquals(byteArrayOf(7, 8, 9), store.load("desktop-1"))
        assertEquals("top.focess.keystead.desktop", port.service)
        assertEquals("desktop-1", port.account)
        store.delete("desktop-1")
        assertNull(store.load("desktop-1"))
    }

    @Test
    fun `Linux Secret Service uses exact attributes and wipes returned item`() = withOs("Linux") {
        val port = FakeSecretServicePort()
        val store = LinuxSecretServiceStore(port)

        store.save("desktop-1", byteArrayOf(4, 5, 6))
        assertTrue(port.savedReference.all { it == 0.toByte() })
        assertEquals(
            mapOf("application" to "top.focess.keystead.desktop", "instance" to "desktop-1"),
            port.attributes,
        )
        assertContentEquals(byteArrayOf(4, 5, 6), store.load("desktop-1"))
        assertTrue(port.returnedReference.all { it == 0.toByte() })
        store.delete("desktop-1")
        assertNull(store.load("desktop-1"))
    }

    private inline fun withOs(name: String, action: () -> Unit) {
        val previous = System.getProperty("os.name")
        try {
            System.setProperty("os.name", name)
            action()
        } finally {
            System.setProperty("os.name", previous)
        }
    }

    private class FakeDpapiPort : WindowsDpapiPort {
        lateinit var protectInput: ByteArray
        var lastEntropy = ByteArray(0)
        override fun protect(plaintext: ByteArray, entropy: ByteArray): ByteArray {
            protectInput = plaintext
            lastEntropy = entropy.copyOf()
            return plaintext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        }
        override fun unprotect(ciphertext: ByteArray, entropy: ByteArray): ByteArray {
            assertContentEquals(lastEntropy, entropy)
            return ciphertext.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
        }
    }

    private class FakeKeychainPort : MacOsKeychainPort {
        var service = ""
        var account = ""
        lateinit var savedReference: ByteArray
        private var stored: ByteArray? = null
        override fun save(service: String, account: String, secret: ByteArray) {
            this.service = service
            this.account = account
            savedReference = secret
            stored = secret.copyOf()
        }
        override fun load(service: String, account: String) = stored?.copyOf()
        override fun delete(service: String, account: String) { stored?.fill(0); stored = null }
    }

    private class FakeSecretServicePort : SecretServicePort {
        var attributes = emptyMap<String, String>()
        lateinit var savedReference: ByteArray
        var returnedReference = ByteArray(0)
        private var stored: ByteArray? = null
        override fun available() = true
        override fun search(attributes: Map<String, String>): List<SecretServiceItem> {
            val value = stored ?: return emptyList()
            returnedReference = value.copyOf()
            return listOf(SecretServiceItem("/item/1", returnedReference))
        }
        override fun replace(attributes: Map<String, String>, label: String, secret: ByteArray) {
            this.attributes = attributes
            savedReference = secret
            stored = secret.copyOf()
        }
        override fun delete(attributes: Map<String, String>) { stored?.fill(0); stored = null }
    }
}
