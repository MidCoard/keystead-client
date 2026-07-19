package top.focess.keystead.client

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class VaultRotationWorkflowTest {
    @Test
    fun `resume cancels and restarts when crash happened before self package upload`() {
        val directory = Files.createTempDirectory("keystead-rotation-workflow")
        DeviceIdentityStore(directory.resolve("identity")).createMemoryOnly("device-1").use { identity ->
            LocalVaultSession.openOrCreate(
                directory.resolve("vault"), UUID.randomUUID(), "master-password".toCharArray(),
            ).use { vault ->
                RotationServer(identity).use { server ->
                    server.failNextUpload = true
                    val state = VaultRotationStateStore(directory.resolve("rotation.properties"))
                    val workflow = VaultRotationWorkflow(server.client(), state)

                    assertFailsWith<KeysteadServerException> { workflow.rotate(vault, identity, 1) }
                    assertEquals(LocalRotationStage.PACKAGING, state.load()?.stage)

                    val committed = workflow.resume(vault, identity)

                    assertEquals(ServerVaultRotationState.COMMITTED, committed.state)
                    assertEquals(2, server.beginCount)
                    assertEquals(1, server.cancelCount)
                    assertEquals(null, state.load())
                }
            }
        }
    }

    @Test
    fun `resume after local commit retries only server commit`() {
        val directory = Files.createTempDirectory("keystead-rotation-workflow")
        DeviceIdentityStore(directory.resolve("identity")).createMemoryOnly("device-1").use { identity ->
            LocalVaultSession.openOrCreate(
                directory.resolve("vault"), UUID.randomUUID(), "master-password".toCharArray(),
            ).use { vault ->
                RotationServer(identity).use { server ->
                    server.failNextCommit = true
                    val state = VaultRotationStateStore(directory.resolve("rotation.properties"))
                    val workflow = VaultRotationWorkflow(server.client(), state)
                    val originalKeyId = vault.vaultKeyIdValue()

                    assertFailsWith<KeysteadServerException> { workflow.rotate(vault, identity, 1) }
                    assertEquals(LocalRotationStage.LOCAL_COMMITTED, state.load()?.stage)
                    assertNotEquals(originalKeyId, vault.vaultKeyIdValue())
                    val locallyCommittedKeyId = vault.vaultKeyIdValue()

                    val committed = workflow.resume(vault, identity)

                    assertEquals(ServerVaultRotationState.COMMITTED, committed.state)
                    assertEquals(locallyCommittedKeyId, vault.vaultKeyIdValue())
                    assertEquals(1, server.beginCount)
                    assertEquals(1, server.uploadCount)
                    assertEquals(2, server.commitCount)
                    assertEquals(null, state.load())
                }
            }
        }
    }

    @Test
    fun `resume after partial upload reuses self package and completes uncovered targets`() {
        val directory = Files.createTempDirectory("keystead-rotation-workflow")
        DeviceIdentityStore(directory.resolve("identity")).createMemoryOnly("device-1").use { identity ->
            LocalVaultSession.openOrCreate(
                directory.resolve("vault"), UUID.randomUUID(), "master-password".toCharArray(),
            ).use { vault ->
                RotationServer(identity).use { server ->
                    server.includeOtherTarget = true
                    server.failNextOtherUpload = true
                    val state = VaultRotationStateStore(directory.resolve("rotation.properties"))
                    val workflow = VaultRotationWorkflow(server.client(), state)

                    assertFailsWith<KeysteadServerException> { workflow.rotate(vault, identity, 1) }
                    assertEquals(LocalRotationStage.PACKAGING, state.load()?.stage)

                    val committed = workflow.resume(vault, identity)

                    assertEquals(ServerVaultRotationState.COMMITTED, committed.state)
                    assertEquals(1, server.beginCount)
                    assertEquals(0, server.cancelCount)
                    assertEquals(3, server.uploadCount)
                    assertEquals(null, state.load())
                }
            }
        }
    }

    private class RotationServer(identity: LocalDeviceIdentity) : AutoCloseable {
        private val publicKey = Base64.getEncoder().encodeToString(identity.publicKey())
        private val server = HttpServer.create(InetSocketAddress(0), 0)
        private var generationId = ""
        private var sourceKeyId = ""
        private var targetKeyId = ""
        private var encryptedSelfPackage: String? = null
        private var encryptedOtherPackage: String? = null
        private var rotationState = "PACKAGING"
        private var lifecycleVersion = 1L
        var failNextUpload = false
        var failNextOtherUpload = false
        var failNextCommit = false
        var includeOtherTarget = false
        var beginCount = 0
        var uploadCount = 0
        var commitCount = 0
        var cancelCount = 0

        init {
            server.createContext("/") { exchange -> handle(exchange) }
            server.start()
        }

        fun client() = KeysteadServerClient(
            "http://127.0.0.1:${server.address.port}", "owner", "password",
        )

        private fun handle(exchange: HttpExchange) {
            val path = exchange.requestURI.path
            val method = exchange.requestMethod
            val body = exchange.requestBody.readBytes().decodeToString()
            if (lastVaultId.isEmpty()) {
                Regex("/api/v1/vaults/([^/]+)").find(path)
                    ?.groupValues?.get(1)?.let {
                        lastVaultId = java.net.URLDecoder.decode(it, Charsets.UTF_8)
                    }
            }
            when {
                method == "POST" && path.matches(Regex("/api/v1/vaults/[^/]+/rotations")) -> {
                    val request = JsonParser.parseString(body).asJsonObject
                    beginCount++
                    generationId = "generation-$beginCount"
                    sourceKeyId = request.get("expectedCurrentVaultKeyId").asString
                    targetKeyId = request.get("targetVaultKeyId").asString
                    encryptedSelfPackage = null
                    encryptedOtherPackage = null
                    rotationState = "PACKAGING"
                    respond(exchange, 201, rotation())
                }
                method == "PUT" && path.endsWith("/targets/target-device/package") -> {
                    uploadCount++
                    if (failNextUpload) {
                        failNextUpload = false
                        respond(exchange, 500, "{}")
                    } else {
                        encryptedSelfPackage = JsonParser.parseString(body).asJsonObject
                            .get("encryptedVaultKey").asString
                        rotationState = if (!includeOtherTarget || encryptedOtherPackage != null) "READY" else "PACKAGING"
                        respond(exchange, 200, rotation())
                    }
                }
                method == "PUT" && path.endsWith("/targets/target-other/package") -> {
                    uploadCount++
                    if (failNextOtherUpload) {
                        failNextOtherUpload = false
                        respond(exchange, 500, "{}")
                    } else {
                        encryptedOtherPackage = JsonParser.parseString(body).asJsonObject
                            .get("encryptedVaultKey").asString
                        rotationState = if (encryptedSelfPackage != null) "READY" else "PACKAGING"
                        respond(exchange, 200, rotation())
                    }
                }
                method == "GET" && path.endsWith("/self-package") -> {
                    val encrypted = encryptedSelfPackage
                    if (encrypted == null) {
                        respond(exchange, 404, "{}")
                    } else {
                        respond(
                            exchange,
                            200,
                            """{"targetId":"target-device","vaultKeyId":"$targetKeyId","keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","encryptedVaultKey":"$encrypted"}""",
                        )
                    }
                }
                method == "GET" && path.matches(Regex("/api/v1/vaults/[^/]+/rotations/[^/]+")) ->
                    respond(exchange, 200, rotation())
                method == "DELETE" && path.matches(Regex("/api/v1/vaults/[^/]+/rotations/[^/]+")) -> {
                    cancelCount++
                    lifecycleVersion++
                    respond(exchange, 204, "")
                }
                method == "GET" && path == "/api/v1/vaults" ->
                    respond(
                        exchange,
                        200,
                        """[{"vaultId":"$lastVaultId","ownerId":"owner","encryptedMetadata":"opaque","role":"OWNER","membershipState":"ACTIVE","currentVaultKeyId":"$sourceKeyId","keyLifecycleState":"STABLE","lifecycleVersion":$lifecycleVersion}]""",
                    )
                method == "POST" && path.endsWith("/commit") -> {
                    commitCount++
                    if (failNextCommit) {
                        failNextCommit = false
                        respond(exchange, 500, "{}")
                    } else {
                        rotationState = "COMMITTED"
                        respond(exchange, 200, rotation())
                    }
                }
                else -> respond(exchange, 404, "{}")
            }
        }

        private fun rotation(): String {
            val self = """{"targetId":"target-device","targetType":"DEVICE","recipientId":"owner","deviceId":"device-1","principalId":null,"enrollmentId":null,"recoveryGeneration":null,"keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","publicKey":"$publicKey","required":true,"covered":${encryptedSelfPackage != null}}"""
            val other = if (includeOtherTarget) {
                """,{"targetId":"target-other","targetType":"DEVICE","recipientId":"owner","deviceId":"device-2","principalId":null,"enrollmentId":null,"recoveryGeneration":null,"keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","publicKey":"$publicKey","required":true,"covered":${encryptedOtherPackage != null}}"""
            } else ""
            return """{"generationId":"$generationId","vaultId":"${currentVaultId()}","sourceVaultKeyId":"$sourceKeyId","targetVaultKeyId":"$targetKeyId","state":"$rotationState","lifecycleVersion":$lifecycleVersion,"targets":[$self$other]}"""
        }

        private var lastVaultId = ""
        private fun currentVaultId(): String = lastVaultId

        private fun respond(exchange: HttpExchange, status: Int, body: String) {
            val bytes = body.encodeToByteArray()
            if (status == 204) exchange.sendResponseHeaders(status, -1)
            else exchange.sendResponseHeaders(status, bytes.size.toLong())
            if (status != 204) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }

        override fun close() {
            server.stop(0)
        }
    }
}
