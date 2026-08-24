package top.focess.keystead.client

import java.nio.file.Files
import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SecureStorageFactoryTest {
    @Test
    fun windowsSelectsOnlyWindowsHelloWithoutCreatingCredential() {
        val result =
            SecureStorageFactory(
                "Windows 11",
                windowHandle = { null },
                touchIdAuthenticationReason = { "test" },
            )
                .biometric(Files.createTempDirectory("keystead-windows-hello-factory"), "desktop")
        val providerId = when (result) {
            is SecureStorageSelection.Available -> result.providerId
            is SecureStorageSelection.Unavailable -> result.diagnostic.providerId
        }
        assertEquals("windows-hello", providerId)
    }

    @Test
    fun macOsSelectsTouchIdWhileLinuxHasNoBiometricProvider() {
        val directory = Files.createTempDirectory("keystead-no-biometric-provider")
        val macResult =
            SecureStorageFactory(
                "Mac OS X",
                { normalized, _ -> if (normalized.contains("mac")) FakeHelloStore(providerId = "mac-touch-id") else null },
                SecureRandom(),
            ).biometric(directory, "desktop")
        assertEquals("mac-touch-id", assertIs<SecureStorageSelection.Available>(macResult).providerId)
        assertEquals(
            "none",
            assertIs<SecureStorageSelection.Unavailable>(
                SecureStorageFactory("Linux", touchIdAuthenticationReason = { "test" })
                    .biometric(directory, "desktop"),
            ).diagnostic.providerId,
        )
    }

    @Test
    fun availabilityCheckDoesNotWriteOrPrompt() {
        val provider = FakeHelloStore()
        val result =
            SecureStorageFactory("windows-test", { _, _ -> provider }, SecureRandom())
                .biometric(Files.createTempDirectory("keystead-biometric-factory"), "desktop")

        assertEquals("fake-hello", assertIs<SecureStorageSelection.Available>(result).providerId)
        assertEquals(0, provider.saves)
        assertEquals(0, provider.loads)
    }

    @Test
    fun lockedProviderReturnsDiagnosticWithoutFallback() {
        val provider = FakeHelloStore(OsSecretStoreStatus.LOCKED)
        val result =
            SecureStorageFactory("windows-test", { _, _ -> provider }, SecureRandom())
                .biometric(Files.createTempDirectory("keystead-biometric-locked"), "desktop")

        val unavailable = assertIs<SecureStorageSelection.Unavailable>(result)
        assertEquals(OsSecretStoreFailure.LOCKED, unavailable.diagnostic.failure)
        assertEquals("fake-status", unavailable.diagnostic.diagnosticCode)
        assertEquals(0, provider.saves)
    }

    private class FakeHelloStore(
        private val status: OsSecretStoreStatus = OsSecretStoreStatus.AVAILABLE,
        override val providerId: String = "fake-hello",
    ) : OsSecretStore {
        var saves = 0
        var loads = 0
        override fun availability() = OsSecretStoreAvailability(status, "fake-status")
        override fun save(instanceId: String, secret: ByteArray) { saves += 1 }
        override fun load(instanceId: String): ByteArray? { loads += 1; return null }
        override fun delete(instanceId: String) = Unit
    }
}
