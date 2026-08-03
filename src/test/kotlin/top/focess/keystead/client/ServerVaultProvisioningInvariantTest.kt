package top.focess.keystead.client

import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import top.focess.keystead.crypto.DefaultCryptoService

class ServerVaultProvisioningInvariantTest {
    @Test
    fun restoreRequiresANewKvaultTargetBeforeAnyServerCall() {
        val root = createTempDirectory("keystead-restore-invariant-")
        EphemeralVaultAccessSession.create("https://vault.example").use { exchange ->
            val request = approvedRequest(exchange)
            val service = ServerVaultProvisioningService()
            val client = KeysteadServerClient("http://127.0.0.1:1", "alice", "password")
            assertFailsWith<IllegalArgumentException> {
                service.restore(
                    file = root.resolve("not-a-vault.txt"),
                    request = request,
                    exchangeSession = exchange,
                    newMasterPassphrase = "new-master-password".toCharArray(),
                    client = client,
                    stateStore = SyncStateStore(root.resolve("sync")),
                )
            }

            val existing = root.resolve("existing.kvault")
            Files.writeString(existing, "sentinel")
            assertFailsWith<IllegalStateException> {
                service.restore(
                    file = existing,
                    request = request,
                    exchangeSession = exchange,
                    newMasterPassphrase = "new-master-password".toCharArray(),
                    client = client,
                    stateStore = SyncStateStore(root.resolve("sync")),
                )
            }
            assertEquals("sentinel", Files.readString(existing))
        }
    }

    private fun approvedRequest(exchange: EphemeralVaultAccessSession) =
        ServerVaultAccessRequest(
            requestId = exchange.requestId,
            accountId = "alice",
            serverOrigin = exchange.serverOrigin,
            fingerprint = "request-fingerprint",
            keyAlgorithm = exchange.keyAlgorithm,
            exchangePublicKey = java.util.Base64.getEncoder().encodeToString(exchange.publicKey),
            state = ServerVaultAccessRequestState.APPROVED,
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
            canonicalRequest = "not-needed-for-path-validation",
            approvedPackage =
                VaultAccessKeyPackage(
                    fingerprint = "6000000000000001",
                    vaultKeyId = "vault-key-1",
                    keyAlgorithm = DefaultCryptoService.DEVICE_KEY_ALGORITHM,
                    encryptedVaultKey = "not-needed-for-path-validation",
                ),
            approvedAt = Instant.parse("2029-01-01T00:00:00Z"),
        )
}
