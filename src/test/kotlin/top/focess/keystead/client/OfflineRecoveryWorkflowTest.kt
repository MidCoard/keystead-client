package top.focess.keystead.client

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFailsWith

class OfflineRecoveryWorkflowTest {
    private val client = KeysteadServerClient("http://localhost:1", "alice", "secret")
    private val identity =
        LocalDeviceIdentity(
            "device-1",
            "RSA-OAEP-SHA256",
            byteArrayOf(1),
            byteArrayOf(2),
            "Ed25519",
            byteArrayOf(3),
            byteArrayOf(4),
        )
    private val workflow = OfflineRecoveryWorkflow(client)

    @Test
    fun recoverRejectsBlankRecoveryKit() {
        assertFailsWith<IllegalArgumentException> {
            workflow.recover("alice", "", charArrayOf('x'), identity, Path.of("vault"))
        }
    }

    @Test
    fun recoverRejectsOversizedRecoveryKit() {
        assertFailsWith<IllegalArgumentException> {
            workflow.recover("alice", "x".repeat(513), charArrayOf('x'), identity, Path.of("vault"))
        }
    }
}
