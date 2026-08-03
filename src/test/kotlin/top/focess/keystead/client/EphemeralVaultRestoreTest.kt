package top.focess.keystead.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.focess.keystead.access.VaultAccessRequest
import top.focess.keystead.access.VaultAccessRequestCodec

class EphemeralVaultRestoreTest {
    @Test
    fun approvedMemoryOnlyExchangeReconstructsTheSamePersonalVault() {
        val root = Files.createTempDirectory("keystead-ephemeral-restore-")
        val events = CopyOnWriteArrayList<JsonObject>()
        val approvedPackage = AtomicReference<JsonObject>()
        withServer(events, approvedPackage).use { server ->
            val client = KeysteadServerClient(server.baseUrl, "alice", "secret")
            val sourceFile = root.resolve("source.kvault")
            val targetFile = root.resolve("target.kvault")
            EphemeralVaultAccessSession.create(server.baseUrl).use { exchange ->
                LocalVaultSession.openOrCreate(sourceFile, "source-master".toCharArray()).use { source ->
                    val secretId =
                        source.addLogin(
                            "Email",
                            "alice@example.test",
                            "correct-secret",
                            "https://example.test",
                        )
                    val pending = pendingRequest(exchange)

                    VaultAccessWorkflow(client).approve(pending, source)

                    val packageJson = approvedPackage.get()
                    val approved =
                        pending.copy(
                            state = ServerVaultAccessRequestState.APPROVED,
                            approvedPackage =
                                VaultAccessKeyPackage(
                                    fingerprint = packageJson["vaultFingerprint"].asString,
                                    vaultKeyId = packageJson["vaultKeyId"].asString,
                                    keyAlgorithm = packageJson["keyAlgorithm"].asString,
                                    encryptedVaultKey = packageJson["encryptedVaultKey"].asString,
                                ),
                            approvedAt = Instant.now(),
                        )
                    val restored =
                        ServerVaultProvisioningService().restore(
                            file = targetFile,
                            request = approved,
                            exchangeSession = exchange,
                            newMasterPassphrase = "target-master".toCharArray(),
                            client = client,
                            stateStore = SyncStateStore(root.resolve("target-sync")),
                        )
                    restored.session.use { target ->
                        assertEquals(source.fingerprintValue(), target.fingerprintValue())
                        assertEquals(1, restored.pulledRecords)
                        assertEquals(0, restored.rejectedRecords)
                        assertEquals(secretId, target.listSecrets().single().id)
                        assertTrue(target.revealPassword(secretId) == "correct-secret")
                        assertTrue(target.deviceSlots().isEmpty())
                    }
                }
            }
            LocalVaultSession.openOrCreate(targetFile, "target-master".toCharArray()).use { reopened ->
                assertEquals("Email", reopened.listSecrets().single().title)
            }
        }
    }

    private fun pendingRequest(exchange: EphemeralVaultAccessSession): ServerVaultAccessRequest {
        val expires = Instant.ofEpochSecond(Instant.now().epochSecond + 600)
        val canonicalValue =
            VaultAccessRequest(
                VaultAccessRequest.FORMAT_VERSION,
                exchange.requestId,
                "alice",
                exchange.serverOrigin,
                expires,
                exchange.keyAlgorithm,
                exchange.publicKey,
            )
        return ServerVaultAccessRequest(
            requestId = exchange.requestId,
            accountId = "alice",
            serverOrigin = exchange.serverOrigin,
            fingerprint = VaultAccessRequestCodec.fingerprint(canonicalValue),
            keyAlgorithm = exchange.keyAlgorithm,
            exchangePublicKey = Base64.getEncoder().encodeToString(exchange.publicKey),
            state = ServerVaultAccessRequestState.PENDING,
            expiresAt = expires,
            canonicalRequest =
                Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(VaultAccessRequestCodec.encode(canonicalValue)),
            approvedPackage = null,
            approvedAt = null,
        )
    }

    private fun withServer(
        events: CopyOnWriteArrayList<JsonObject>,
        approvedPackage: AtomicReference<JsonObject>,
    ): TestServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val response =
                when {
                    exchange.requestMethod == "POST" && path == "/api/v1/vault/records" -> {
                        val event = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                        events += event
                        recordResponse(events.size.toLong(), event)
                    }
                    exchange.requestMethod == "POST" && path.endsWith("/approve") -> {
                        approvedPackage.set(
                            JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject,
                        )
                        ""
                    }
                    exchange.requestMethod == "GET" && path == "/api/v1/vault/records" -> {
                        val records = events.mapIndexed { index, event -> recordResponse(index + 1L, event) }
                        "{\"afterSequence\":0,\"records\":[${records.joinToString(",")}],\"highestSequence\":${records.size},\"hasMore\":false,\"nextSequence\":null}"
                    }
                    else -> error("Unexpected request ${exchange.requestMethod} $path")
                }
            val bytes = response.encodeToByteArray()
            if (bytes.isEmpty()) {
                exchange.sendResponseHeaders(204, -1)
            } else {
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            exchange.close()
        }
        server.start()
        return TestServer(server)
    }

    private fun recordResponse(sequence: Long, event: JsonObject): String =
        "{\"serverSequence\":$sequence,\"eventId\":${event["eventId"]},\"fingerprint\":${event["fingerprint"]},\"secretId\":${event["secretId"]},\"revision\":${event["revision"]},\"secretType\":${event["secretType"]},\"encryptedProfile\":${event["encryptedProfile"]},\"envelope\":${event["envelope"]},\"deleted\":${event["deleted"]},\"createdAt\":\"2030-01-01T00:00:00Z\"}"

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }
}
