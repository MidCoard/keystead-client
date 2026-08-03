package top.focess.keystead.client

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WindowsHelloSecretStoreTest {
    @Test
    fun `windows hello PRF encrypts an arbitrary storage key and decrypts it after verification`() {
        val directory = Files.createTempDirectory("keystead-windows-hello")
        val port = FakeWindowsHelloPort()
        val store = WindowsHelloSecretStore(directory, port)
        val input = ByteArray(32) { (it + 1).toByte() }

        store.save("desktop", input)

        assertContentEquals(input, store.load("desktop"))
        assertEquals(1, port.created)
        assertEquals(2, port.verifications)
        val encoded = Files.list(directory).use { paths -> Files.readAllBytes(paths.findFirst().orElseThrow()) }
        assertFalse(encoded.containsSubsequence(input))
        assertFalse(encoded.decodeToString().contains("desktop"))

        store.delete("desktop")
        assertEquals(1, port.deleted)
        assertNull(store.load("desktop"))
    }

    @Test
    fun `wrong authenticator PRF output fails closed as corrupt data`() {
        val directory = Files.createTempDirectory("keystead-windows-hello-corrupt")
        val port = FakeWindowsHelloPort()
        val store = WindowsHelloSecretStore(directory, port)
        store.save("desktop", ByteArray(32) { 7 })
        port.secretMarker = 99

        val error = assertFailsWith<OsSecretStoreException> { store.load("desktop") }

        assertEquals(OsSecretStoreFailure.CORRUPT, error.failure)
        assertFalse(error.toString().contains("desktop"))
    }

    @Test
    fun `unavailable verification is reported without creating plaintext fallback`() {
        val directory = Files.createTempDirectory("keystead-windows-hello-unavailable")
        val port = FakeWindowsHelloPort(OsSecretStoreStatus.UNAVAILABLE)
        val store = WindowsHelloSecretStore(directory, port)

        assertEquals(OsSecretStoreStatus.UNAVAILABLE, store.availability().status)
        assertFailsWith<OsSecretStoreException> { store.save("desktop", ByteArray(32)) }
        assertTrue(Files.list(directory).use { paths -> paths.findAny().isEmpty })
    }

    private class FakeWindowsHelloPort(
        private val status: OsSecretStoreStatus = OsSecretStoreStatus.AVAILABLE,
    ) : WindowsHelloPort {
        var created = 0
        var verifications = 0
        var deleted = 0
        var secretMarker = 41

        override fun availability() = OsSecretStoreAvailability(status, "fake-windows-hello")

        override fun createCredential(
            relyingPartyId: String,
            userId: ByteArray,
            salt: ByteArray,
        ): WindowsHelloCredential {
            check(status == OsSecretStoreStatus.AVAILABLE)
            created++
            verifications++
            val credentialId = MessageDigest.getInstance("SHA-256").digest(userId)
            val secret =
                MessageDigest.getInstance("SHA-256").digest(
                    byteArrayOf(secretMarker.toByte()) + credentialId + salt,
                )
            return WindowsHelloCredential(credentialId, secret)
        }

        override fun deriveSecret(
            relyingPartyId: String,
            credentialId: ByteArray,
            salt: ByteArray,
        ): ByteArray {
            check(status == OsSecretStoreStatus.AVAILABLE)
            verifications++
            return MessageDigest.getInstance("SHA-256").digest(
                byteArrayOf(secretMarker.toByte()) + credentialId + salt,
            )
        }

        override fun deleteCredential(credentialId: ByteArray) {
            deleted++
        }
    }

    private fun ByteArray.containsSubsequence(value: ByteArray): Boolean =
        value.isNotEmpty() && indices.any { start ->
            start + value.size <= size && value.indices.all { offset -> this[start + offset] == value[offset] }
        }
}
