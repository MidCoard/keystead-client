package top.focess.keystead.client

import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalVaultSyncTest {
    @Test
    fun selectiveUploadPublishesOnlyTheChosenCurrentRecord() {
        val root = Files.createTempDirectory("keystead-selective-upload-")
        val uploadedIds = CopyOnWriteArrayList<String>()
        withServer { exchange ->
            val request = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
            uploadedIds += request["secretId"].asString
            "{\"serverSequence\":1,\"eventId\":${request["eventId"]},\"fingerprint\":${request["fingerprint"]},\"secretId\":${request["secretId"]},\"revision\":${request["revision"]},\"secretType\":${request["secretType"]},\"encryptedProfile\":${request["encryptedProfile"]},\"envelope\":${request["envelope"]},\"deleted\":${request["deleted"]},\"createdAt\":\"2030-01-01T00:00:00Z\"}"
        }.use { server ->
            LocalVaultSession.openOrCreate(
                root.resolve("selective.kvault"),
                "master-passphrase".toCharArray(),
            ).use { vault ->
                val selected = vault.addLogin("Selected", "alice", "one", "https://one.test")
                vault.addLogin("Not selected", "bob", "two", "https://two.test")

                val count =
                    vault.pushSelectedPersonalRecordsTo(
                        KeysteadServerClient(server.baseUrl, "alice", "secret"),
                        setOf(selected),
                    )

                assertEquals(1, count)
                assertEquals(listOf(selected), uploadedIds)
            }
        }
    }

    @Test
    fun currentRecordInventoryExportsOnlyEncryptedSyncRecords() {
        val root = Files.createTempDirectory("keystead-personal-inventory-")
        LocalVaultSession.openOrCreate(
            root.resolve("inventory.kvault"),
            "master-passphrase".toCharArray(),
        ).use { vault ->
            val secretId = vault.addLogin("Email", "alice", "clear-secret", "https://example.test")

            val records = vault.currentPersonalRecords()

            assertEquals(1, records.size)
            assertEquals(secretId, records.single().secretId())
            assertEquals(vault.fingerprintValue(), records.single().fingerprint())
            assertTrue(records.single().envelope().isNotBlank())
            assertTrue(!records.single().envelope().contains("clear-secret"))
        }
    }

    @Test
    fun pullAdvancesPastRejectedCiphertextWithoutDamagingValidLocalData() {
        val root = Files.createTempDirectory("keystead-personal-sync-")
        val vaultFile = root.resolve("source.kvault")
        val state = SyncStateStore(root.resolve("sync"))
        val appended = AtomicReference<JsonObjectResponse>()
        withServer { exchange ->
            if (exchange.requestMethod == "POST") {
                val request = JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject
                val response =
                    "{\"serverSequence\":1,\"eventId\":${request["eventId"]},\"fingerprint\":${request["fingerprint"]},\"secretId\":${request["secretId"]},\"revision\":${request["revision"]},\"secretType\":${request["secretType"]},\"encryptedProfile\":${request["encryptedProfile"]},\"envelope\":${request["envelope"]},\"deleted\":${request["deleted"]},\"createdAt\":\"${Instant.parse("2030-01-01T00:00:00Z")}\"}"
                appended.set(JsonObjectResponse(response))
                response
            } else {
                val valid = appended.get().body
                val parsed = JsonParser.parseString(valid).asJsonObject
                val bogus =
                    "{\"serverSequence\":2,\"eventId\":\"bogus\",\"fingerprint\":${parsed["fingerprint"]},\"secretId\":\"550e8400-e29b-41d4-a716-446655440000\",\"revision\":999,\"secretType\":\"LOGIN_PASSWORD\",\"encryptedProfile\":\"not-authentic\",\"envelope\":\"not-authentic\",\"deleted\":false,\"createdAt\":\"2030-01-01T00:00:01Z\"}"
                "{\"afterSequence\":0,\"records\":[$valid,$bogus],\"highestSequence\":2,\"hasMore\":false,\"nextSequence\":null}"
            }
        }.use { server ->
            val client = KeysteadServerClient(server.baseUrl, "alice", "secret")
            LocalVaultSession.openOrCreate(vaultFile, "master-passphrase".toCharArray()).use { vault ->
                vault.addLogin("Email", "alice", "correct-secret", "https://example.test")
                assertEquals(1, vault.pushAllPersonalRecordsTo(client))

                val result = vault.pullPendingPersonalRecordsFrom(client, state)

                assertEquals(1, result.rejected.size)
                assertEquals(2, result.highestSequence)
                assertEquals(2, state.lastPulledServerSequence(vault.fingerprintValue()))
                assertTrue(vault.revealPassword(vault.listSecrets().single().id) == "correct-secret")
            }
        }
    }

    private fun withServer(response: (com.sun.net.httpserver.HttpExchange) -> String): TestServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val bytes = response(exchange).encodeToByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        return TestServer(server)
    }

    private data class JsonObjectResponse(val body: String)

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }
}
