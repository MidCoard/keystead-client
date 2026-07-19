package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultRotationClientTest {
    @Test
    fun `list memberships parses nullable current key and exact lifecycle fields`() = withServer(
        200,
        """[{"vaultId":"vault-1","ownerId":"owner","encryptedMetadata":"opaque","role":"EDITOR","membershipState":"ACCEPTED_PENDING_KEY","currentVaultKeyId":null,"keyLifecycleState":"STABLE","lifecycleVersion":4}]""",
    ) { client, requests ->
        val value = VaultRotationClient(client).listMemberships().single()
        assertEquals(ServerVaultMemberState.ACCEPTED_PENDING_KEY, value.membershipState)
        assertEquals(null, value.currentVaultKeyId)
        assertEquals(4, value.lifecycleVersion)
        assertEquals("GET", requests.single().method)
        assertEquals("/api/v1/vaults", requests.single().path)
    }

    @Test
    fun `unknown response fields are rejected`() = withServer(
        200,
        """[{"vaultId":"vault-1","ownerId":"owner","encryptedMetadata":"opaque","role":"OWNER","membershipState":"ACTIVE","currentVaultKeyId":"key-1","keyLifecycleState":"STABLE","lifecycleVersion":1,"unexpected":"value"}]""",
    ) { client, _ ->
        assertFailsWith<IllegalStateException> { VaultRotationClient(client).listMemberships() }
    }

    @Test
    fun `package coverage parses server envelope and recipient devices`() = withServer(
        200,
        """{"currentVaultKeyId":"key-1","keyLifecycleState":"STABLE","lifecycleVersion":3,"devices":[{"userId":"member","role":"EDITOR","memberState":"ACTIVE","deviceId":"member-device","keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","publicKey":"public","covered":false}]}""",
    ) { client, _ ->
        val device = VaultRotationClient(client).packageRecipients("vault-1").single()

        assertEquals("member", device.userId)
        assertEquals("member-device", device.deviceId)
        assertFalse(device.covered)
    }

    @Test
    fun `begin serializes lifecycle precondition and selected pending members`() = withServer(
        201,
        rotationJson(),
    ) { client, requests ->
        VaultRotationClient(client).begin("vault 1", "key-old", "key-new", 7, setOf("z-user", "a-user"))

        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/vaults/vault%201/rotations", request.path)
        assertEquals(
            """{"expectedCurrentVaultKeyId":"key-old","targetVaultKeyId":"key-new","expectedLifecycleVersion":7,"selectedPendingUsers":["a-user","z-user"]}""",
            request.body,
        )
    }

    @Test
    fun `self package query is encoded and ciphertext remains redacted`() = withServer(
        200,
        """{"targetId":"target-1","vaultKeyId":"key-new","keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","encryptedVaultKey":"ciphertext-secret-marker"}""",
    ) { client, requests ->
        val value = VaultRotationClient(client).selfPackage("vault-1", "generation-1", "device + one")

        assertEquals("/api/v1/vaults/vault-1/rotations/generation-1/self-package?deviceId=device%20%2B%20one", requests.single().path)
        assertFalse(value.toString().contains("ciphertext-secret-marker"))
    }

    @Test
    fun `lifecycle conflict exposes only lifecycle state`() = withServer(
        409,
        """{"lifecycleState":"ROTATION_REQUIRED"}""",
    ) { client, _ ->
        val error = assertFailsWith<KeysteadVaultLifecycleConflictException> {
            VaultRotationClient(client).commit("vault-1", "generation-1")
        }
        assertEquals(ServerVaultKeyLifecycleState.ROTATION_REQUIRED, error.lifecycleState)
        assertFalse(error.toString().contains("encryptedVaultKey"))
    }

    @Test
    fun `upload binds all target identifiers without logging ciphertext`() = withServer(
        200,
        rotationJson(covered = true, state = "READY"),
    ) { client, requests ->
        val target = ServerVaultRotationTarget(
            "target-1", ServerVaultRotationTargetType.RECOVERY, null, null, null,
            "enrollment-1", 3, "TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM", "public", true, false,
        )
        val result = VaultRotationClient(client).upload(
            "vault-1", "generation-1", target, "key-new", "ciphertext-secret-marker",
        )

        assertTrue(requests.single().body.contains("\"enrollmentId\":\"enrollment-1\""))
        assertTrue(requests.single().body.contains("\"recoveryGeneration\":3"))
        assertFalse(result.toString().contains("ciphertext-secret-marker"))
    }

    private fun rotationJson(covered: Boolean = false, state: String = "PACKAGING") =
        """{"generationId":"generation-1","vaultId":"vault 1","sourceVaultKeyId":"key-old","targetVaultKeyId":"key-new","state":"$state","lifecycleVersion":8,"targets":[{"targetId":"target-1","targetType":"RECOVERY","recipientId":null,"deviceId":null,"principalId":null,"enrollmentId":"enrollment-1","recoveryGeneration":3,"keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","publicKey":"public","required":true,"covered":$covered}]}"""

    private fun withServer(
        status: Int,
        response: String,
        action: (KeysteadServerClient, MutableList<Request>) -> Unit,
    ) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            requests += Request(
                exchange.requestMethod,
                exchange.requestURI.toString(),
                exchange.requestBody.readBytes().decodeToString(),
            )
            val bytes = response.encodeToByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            action(
                KeysteadServerClient("http://127.0.0.1:${server.address.port}", "alice", "password"),
                requests,
            )
        } finally {
            server.stop(0)
        }
    }

    private data class Request(val method: String, val path: String, val body: String)
}
