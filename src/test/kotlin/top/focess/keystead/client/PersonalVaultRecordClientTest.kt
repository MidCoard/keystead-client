package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersonalVaultRecordClientTest {
    @Test
    fun eventIdentityIsStableButBindsTheEncryptedEvent() {
        val event =
            PersonalVaultRecordEvent(
                eventId = "ignored",
                fingerprint = "6000000000000001",
                secretId = "550e8400-e29b-41d4-a716-446655440000",
                revision = 7,
                secretType = "LOGIN_PASSWORD",
                encryptedProfile = "profile",
                envelope = "payload",
                deleted = false,
            )

        assertEquals(PersonalRecordEventId.of(event), PersonalRecordEventId.of(event.copy(eventId = "else")))
        assertNotEquals(PersonalRecordEventId.of(event), PersonalRecordEventId.of(event.copy(envelope = "changed")))
        assertNotEquals(PersonalRecordEventId.of(event), PersonalRecordEventId.of(event.copy(deleted = true, envelope = "")))
    }

    @Test
    fun appendUsesTheSinglePersonalVaultStream() {
        val method = AtomicReference("")
        val uri = AtomicReference("")
        val body = AtomicReference("")
        val response =
            """{"serverSequence":1,"eventId":"event-1","fingerprint":"6000000000000001","secretId":"550e8400-e29b-41d4-a716-446655440000","revision":1,"secretType":"LOGIN_PASSWORD","encryptedProfile":"profile","envelope":"payload","deleted":false,"createdAt":"2030-01-01T00:00:00Z"}"""
        withServer(response) { exchange ->
            method.set(exchange.requestMethod)
            uri.set(exchange.requestURI.toString())
            body.set(exchange.requestBody.bufferedReader().readText())
        }.use { server ->
            val appended =
                KeysteadServerClient(server.baseUrl, "alice", "secret")
                    .appendPersonalRecord(
                        PersonalVaultRecordEvent(
                            eventId = "event-1",
                            fingerprint = "6000000000000001",
                            secretId = "550e8400-e29b-41d4-a716-446655440000",
                            revision = 1,
                            secretType = "LOGIN_PASSWORD",
                            encryptedProfile = "profile",
                            envelope = "payload",
                            deleted = false,
                        ),
                    )
            assertEquals(1, appended.serverSequence)
        }

        assertEquals("POST", method.get())
        assertEquals("/api/v1/vault/records", uri.get())
        assertTrue(body.get().contains("\"eventId\":\"event-1\""))
    }

    @Test
    fun pageUsesServerSequenceRatherThanLocalRevision() {
        val uri = AtomicReference("")
        val response =
            """{"afterSequence":41,"records":[],"highestSequence":41,"hasMore":false,"nextSequence":null}"""
        withServer(response) { exchange -> uri.set(exchange.requestURI.toString()) }.use { server ->
            val page =
                KeysteadServerClient(server.baseUrl, "alice", "secret")
                    .listPersonalRecordPage(afterSequence = 41, limit = 25)
            assertEquals(41, page.highestSequence)
        }
        assertEquals("/api/v1/vault/records?afterSequence=41&limit=25", uri.get())
    }

    @Test
    fun deletingRecordHistoryUsesTheOwnerScopedRecordEndpoint() {
        val method = AtomicReference("")
        val uri = AtomicReference("")
        val response = """{"secretId":"secret-selected","deletedEvents":3}"""
        withServer(response, statusCode = 200, inspect = { exchange ->
            method.set(exchange.requestMethod)
            uri.set(exchange.requestURI.toString())
        }).use { server ->
            val deleted =
                KeysteadServerClient(server.baseUrl, "alice", "secret")
                    .deletePersonalRecordHistory("secret-selected")

            assertEquals("secret-selected", deleted.secretId)
            assertEquals(3, deleted.deletedEvents)
        }

        assertEquals("DELETE", method.get())
        assertEquals("/api/v1/vault/records/secret-selected", uri.get())
    }

    @Test
    fun appendExplainsWhenTheAccountRecordsBelongToAnotherVault() {
        val response =
            """{"code":"PERSONAL_VAULT_MISMATCH","serverFingerprint":"server-vault","submittedFingerprint":"local-vault"}"""
        withServer(response, statusCode = 409) {}.use { server ->
            val error =
                assertFailsWith<PersonalVaultMismatchException> {
                    KeysteadServerClient(server.baseUrl, "alice", "secret")
                        .appendPersonalRecord(
                            PersonalVaultRecordEvent(
                                eventId = "event-1",
                                fingerprint = "local-vault",
                                secretId = "secret-1",
                                revision = 1,
                                secretType = "LOGIN_PASSWORD",
                                encryptedProfile = "profile",
                                envelope = "payload",
                                deleted = false,
                            ),
                        )
                }
            assertEquals("server-vault", error.serverFingerprint)
            assertEquals("local-vault", error.localFingerprint)
        }
    }

    @Test
    fun completeInventoryFollowsServerSequencePagesUntilTheStreamEnds() {
        val queries = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val query = exchange.requestURI.query
            queries += query
            val body =
                if (query.contains("afterSequence=0")) {
                    """{"afterSequence":0,"records":[${recordJson(1)},${recordJson(2)}],"highestSequence":2,"hasMore":true,"nextSequence":2}"""
                } else {
                    """{"afterSequence":2,"records":[${recordJson(3)}],"highestSequence":3,"hasMore":false,"nextSequence":null}"""
                }
            val bytes = body.encodeToByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        TestServer(server).use { running ->
            val records =
                KeysteadServerClient(running.baseUrl, "alice", "secret")
                    .listAllPersonalRecords(pageLimit = 2)
            assertEquals(listOf(1L, 2L, 3L), records.map { it.serverSequence })
        }
        assertEquals(
            listOf("afterSequence=0&limit=2", "afterSequence=2&limit=2"),
            queries,
        )
    }

    private fun withServer(
        responseBody: String,
        statusCode: Int = 201,
        inspect: (com.sun.net.httpserver.HttpExchange) -> Unit,
    ): TestServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            inspect(exchange)
            val response = responseBody.encodeToByteArray()
            exchange.sendResponseHeaders(statusCode, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        return TestServer(server)
    }

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }

    private fun recordJson(sequence: Long): String =
        """{"serverSequence":$sequence,"eventId":"event-$sequence","fingerprint":"6000000000000001","secretId":"secret-$sequence","revision":$sequence,"secretType":"SECURE_NOTE","encryptedProfile":"profile-$sequence","envelope":"payload-$sequence","deleted":false,"createdAt":"2030-01-01T00:00:0${sequence}Z"}"""
}
