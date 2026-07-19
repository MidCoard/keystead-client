package top.focess.keystead.client

import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SecureStorageFactoryTest {
    @Test
    fun `native selection probes write read and delete`() {
        val port = FakeOsStore()
        val directory = Files.createTempDirectory("keystead-native-factory")
        val result = SecureStorageFactory("test", { _, _ -> port }, SecureRandom())
            .native(directory, "desktop")

        val available = assertIs<SecureStorageSelection.Available>(result)
        assertEquals("fake-native", available.providerId)
        assertEquals(1, port.saves)
        assertEquals(emptySet(), available.storage.listKeys("keystead-probe", "desktop"))
        assertEquals(null, available.storage.load(SecureStorageKey("keystead-probe", "desktop", "availability")))
    }

    @Test
    fun `locked provider returns stable diagnostic and no fallback`() {
        val port = FakeOsStore(OsSecretStoreStatus.LOCKED)
        val result = SecureStorageFactory("test", { _, _ -> port }, SecureRandom())
            .native(Files.createTempDirectory("keystead-native-factory"), "desktop")

        val unavailable = assertIs<SecureStorageSelection.Unavailable>(result)
        assertEquals(OsSecretStoreFailure.LOCKED, unavailable.diagnostic.failure)
        assertEquals("fake-status", unavailable.diagnostic.diagnosticCode)
        assertEquals(0, port.saves)
    }

    private class FakeOsStore(
        private val status: OsSecretStoreStatus = OsSecretStoreStatus.AVAILABLE,
    ) : OsSecretStore {
        override val providerId = "fake-native"
        private val values = mutableMapOf<String, ByteArray>()
        var saves = 0
        override fun availability() = OsSecretStoreAvailability(status, "fake-status")
        override fun save(instanceId: String, secret: ByteArray) {
            saves++
            values[instanceId] = secret.copyOf()
        }
        override fun load(instanceId: String) = values[instanceId]?.copyOf()
        override fun delete(instanceId: String) {
            values.remove(instanceId)?.fill(0)
        }
    }
}
