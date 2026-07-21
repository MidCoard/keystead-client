package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerAuthenticationTest {
    private val clock = Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun registrationWipesPasswordAndSendsNoAuthorizationHeader() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/users" -> TestResponse(201)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val password = "server-password".toCharArray()

            KeysteadServerAuthClient(baseUrl, clock = clock).registerUser("alice", password)

            assertTrue(password.all { it == '\u0000' })
            assertEquals(1, requests.size)
            assertEquals("", requests.single().authorization)
            assertEquals("/api/v1/users", requests.single().path)
            assertTrue(requests.single().body.contains("\"username\":\"alice\""))
            assertTrue(requests.single().body.contains("\"password\":\"server-password\""))
        }

    @Test
    fun registrationConflictStillWipesPassword() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/users" -> TestResponse(409, "duplicate-user-sentinel")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val password = "server-password".toCharArray()

            val error =
                assertFailsWith<KeysteadAccountConflictException> {
                    KeysteadServerAuthClient(baseUrl, clock = clock).registerUser("alice", password)
                }

            assertTrue(password.all { it == '\u0000' })
            assertFalse(error.message.orEmpty().contains("duplicate-user-sentinel"))
        }

    @Test
    fun failedLoginWipesPasswordWithoutExposingServerBody() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> TestResponse(401, "authentication-body-sentinel")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val password = "server-password".toCharArray()

            val error =
                assertFailsWith<KeysteadAuthenticationException> {
                    KeysteadServerAuthClient(baseUrl, clock = clock).login("alice", password)
                }

            assertTrue(password.all { it == '\u0000' })
            assertFalse(error.message.orEmpty().contains("authentication-body-sentinel"))
            assertFalse(error.message.orEmpty().contains("server-password"))
        }

    @Test
    fun loginEscapesEveryJsonControlCharacterInCredentials() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-one", "refresh-one", "2026-07-12T00:05:00Z")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val password = "p\n\t\r\b\u000c\u0001\"\\".toCharArray()

            KeysteadServerAuthClient(baseUrl, clock = clock)
                .login("ali\nce", password)
                .close()

            assertEquals(
                "{\"username\":\"ali\\nce\",\"password\":\"p\\n\\t\\r\\b\\f\\u0001\\\"\\\\\"}",
                requests.single().body,
            )
            assertTrue(password.all { it == '\u0000' })
        }

    @Test
    fun loginWipesPasswordAndUsesBearerTokenForProtectedRequests() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-one", "refresh-one", "2026-07-12T00:05:00Z")
                    "/api/v1/devices" -> TestResponse(200, "[]")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val password = "server-password".toCharArray()
            KeysteadServerAuthClient(baseUrl, clock = clock)
                .login("alice", password)
                .use { session ->
                    assertTrue(password.all { it == '\u0000' })
                    assertEquals("ServerAuthSession(<redacted>)", session.toString())
                    assertFalse(session.toString().contains("access-one"))
                    assertFalse(session.toString().contains("refresh-one"))

                    assertEquals(emptyList(), session.client().listDevices())
                }

            assertEquals(2, requests.size)
            assertEquals("", requests[0].authorization)
            assertEquals("/api/v1/auth/login", requests[0].path)
            assertTrue(requests[0].body.contains("\"username\":\"alice\""))
            assertTrue(requests[0].body.contains("\"password\":\"server-password\""))
            assertEquals("Bearer access-one", requests[1].authorization)
        }

    @Test
    fun expiredAccessTokenRefreshesAndRotatesRefreshTokenBeforeRequest() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-old", "refresh-old", "2026-07-11T23:59:59Z")
                    "/api/v1/auth/refresh" -> tokenResponse("access-new", "refresh-new", "2026-07-12T00:05:00Z")
                    "/api/v1/devices" -> TestResponse(200, "[]")
                    "/api/v1/auth/revoke" -> TestResponse(204)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            KeysteadServerAuthClient(baseUrl, clock = clock)
                .login("alice", "server-password".toCharArray())
                .use { session ->
                    assertEquals(emptyList(), session.client().listDevices())
                    session.revoke()
                }

            assertEquals(
                listOf(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/devices",
                    "/api/v1/auth/revoke",
                ),
                requests.map { it.path },
            )
            assertTrue(requests[1].body.contains("\"refreshToken\":\"refresh-old\""))
            assertEquals("Bearer access-new", requests[2].authorization)
            assertFalse(requests[2].authorization.contains("access-old"))
            assertTrue(requests[3].body.contains("\"refreshToken\":\"refresh-new\""))
            assertFalse(requests[3].body.contains("refresh-old"))
        }

    @Test
    fun simultaneousExpiredRequestsPerformExactlyOneRefresh() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-old", "refresh-old", "2026-07-11T23:59:59Z")
                    "/api/v1/auth/refresh" -> tokenResponse("access-new", "refresh-new", "2026-07-12T00:05:00Z")
                    "/api/v1/devices" -> TestResponse(200, "[]")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            KeysteadServerAuthClient(baseUrl, clock = clock)
                .login("alice", "server-password".toCharArray())
                .use { session ->
                    val client = session.client()
                    val executor = Executors.newFixedThreadPool(2)
                    val ready = CountDownLatch(2)
                    val start = CountDownLatch(1)
                    try {
                        val calls =
                            List(2) {
                                executor.submit<List<ServerDevice>> {
                                    ready.countDown()
                                    check(start.await(5, TimeUnit.SECONDS))
                                    client.listDevices()
                                }
                            }
                        assertTrue(ready.await(5, TimeUnit.SECONDS))
                        start.countDown()
                        calls.forEach { assertEquals(emptyList(), it.get(5, TimeUnit.SECONDS)) }
                    } finally {
                        start.countDown()
                        executor.shutdownNow()
                        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
                    }
                }

            assertEquals(1, requests.count { it.path == "/api/v1/auth/refresh" })
            assertEquals(2, requests.count { it.path == "/api/v1/devices" })
        }

    @Test
    fun revokeUsesRefreshTokenAndClosesSession() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-one", "refresh-one", "2026-07-12T00:05:00Z")
                    "/api/v1/auth/revoke" -> TestResponse(204)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login("alice", "server-password".toCharArray())
            val client = session.client()

            session.revoke()

            assertTrue(requests.last().body.contains("\"refreshToken\":\"refresh-one\""))
            assertEquals("", requests.last().authorization)
            assertFailsWith<IllegalStateException> { session.client() }
            assertFailsWith<IllegalStateException> { client.listDevices() }
            assertEquals(2, requests.size)
        }

    @Test
    fun logoutAllUsesBearerTokenAndClosesSession() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-one", "refresh-one", "2026-07-12T00:05:00Z")
                    "/api/v1/auth/logout-all" -> TestResponse(204)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login("alice", "server-password".toCharArray())
            val client = session.client()

            session.logoutAll()

            assertEquals("Bearer access-one", requests.last().authorization)
            assertFailsWith<IllegalStateException> { session.client() }
            assertFailsWith<IllegalStateException> { client.listDevices() }
            assertEquals(2, requests.size)
        }

    @Test
    fun logoutAllClosesSessionWhenAutomaticRefreshFails() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("access-old", "refresh-old", "2026-07-11T23:59:59Z")
                    "/api/v1/auth/refresh" -> TestResponse(500, "refresh-failure-sentinel")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login("alice", "server-password".toCharArray())

            val error = assertFailsWith<KeysteadServerException> { session.logoutAll() }

            assertEquals(listOf("/api/v1/auth/login", "/api/v1/auth/refresh"), requests.map { it.path })
            assertFalse(error.message.orEmpty().contains("refresh-failure-sentinel"))
            assertFailsWith<IllegalStateException> { session.client() }
        }

    @Test
    fun loginPersistsInitialRefreshTokenViaSink() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("at-1", "rt-login", "2026-07-13T00:00:00Z")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val persisted = mutableListOf<Pair<String, Instant>>()

            KeysteadServerAuthClient(baseUrl, clock = clock)
                .login(
                    "alice",
                    "server-password".toCharArray(),
                    tokenSink = { token, expiresAt -> persisted.add(token to expiresAt) },
                )

            assertEquals(
                listOf("rt-login" to Instant.parse("2026-08-11T00:00:00Z")),
                persisted,
            )
        }

    @Test
    fun refreshPersistsRotatedRefreshToken() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("at-1", "rt-login", "2026-07-13T00:00:00Z")
                    "/api/v1/auth/refresh" -> tokenResponse("at-2", "rt-rotated", "2026-07-13T00:00:00Z")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val persisted = mutableListOf<Pair<String, Instant>>()
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login(
                        "alice",
                        "server-password".toCharArray(),
                        tokenSink = { token, expiresAt -> persisted.add(token to expiresAt) },
                    )

            session.refresh()

            assertEquals(listOf("rt-login", "rt-rotated"), persisted.map { it.first })
        }

    @Test
    fun restoreWithExpiredRefreshTokenClearsStoreAndThrows() =
        withServer(
            responseFor = { request -> error("Unexpected request: ${request.method} ${request.path}") },
        ) { baseUrl, requests ->
            val revoked = mutableListOf<Unit>()

            val error =
                assertFailsWith<KeysteadAuthenticationException> {
                    KeysteadServerAuthClient(baseUrl, clock = clock).restore(
                        refreshToken = "rt-dead",
                        refreshTokenExpiresAt = Instant.parse("2026-07-01T00:00:00Z"),
                        onRevoked = { revoked.add(Unit) },
                    )
                }

            assertEquals(401, error.statusCode)
            assertEquals(1, revoked.size)
            assertTrue(requests.isEmpty())
        }

    @Test
    fun serverRejectedRefreshClearsStoreAndThrows() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("at-1", "rt-login", "2026-07-13T00:00:00Z")
                    "/api/v1/auth/refresh" -> TestResponse(401, "refresh-rejected")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val revoked = mutableListOf<Unit>()
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login(
                        "alice",
                        "server-password".toCharArray(),
                        onRevoked = { revoked.add(Unit) },
                    )

            assertFailsWith<KeysteadAuthenticationException> { session.refresh() }

            assertEquals(1, revoked.size)
            assertFailsWith<IllegalStateException> { session.client() }
        }

    @Test
    fun revokeClearsPersistedStore() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("at-1", "rt-login", "2026-07-13T00:00:00Z")
                    "/api/v1/auth/revoke" -> TestResponse(204)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val revoked = mutableListOf<Unit>()
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login(
                        "alice",
                        "server-password".toCharArray(),
                        onRevoked = { revoked.add(Unit) },
                    )

            session.revoke()

            assertEquals(1, revoked.size)
            assertFailsWith<IllegalStateException> { session.client() }
        }

    @Test
    fun logoutAllClearsPersistedStore() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/login" -> tokenResponse("at-1", "rt-login", "2026-07-13T00:00:00Z")
                    "/api/v1/auth/logout-all" -> TestResponse(204)
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, _ ->
            val revoked = mutableListOf<Unit>()
            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock)
                    .login(
                        "alice",
                        "server-password".toCharArray(),
                        onRevoked = { revoked.add(Unit) },
                    )

            session.logoutAll()

            assertEquals(1, revoked.size)
            assertFailsWith<IllegalStateException> { session.client() }
        }

    @Test
    fun restoreMintsFreshTokensAndPersistsRotatedRefreshToken() =
        withServer(
            responseFor = { request ->
                when (request.path) {
                    "/api/v1/auth/refresh" -> tokenResponse("at-fresh", "rt-rotated", "2026-07-13T00:00:00Z")
                    else -> error("Unexpected request: ${request.method} ${request.path}")
                }
            },
        ) { baseUrl, requests ->
            val persisted = mutableListOf<Pair<String, Instant>>()

            val session =
                KeysteadServerAuthClient(baseUrl, clock = clock).restore(
                    refreshToken = "rt-persisted",
                    refreshTokenExpiresAt = Instant.parse("2026-08-11T00:00:00Z"),
                    tokenSink = { token, expiresAt -> persisted.add(token to expiresAt) },
                )

            assertEquals(listOf("rt-rotated"), persisted.map { it.first })
            assertEquals(1, requests.size)
            assertEquals("/api/v1/auth/refresh", requests.single().path)
            assertTrue(requests.single().body.contains("\"refreshToken\":\"rt-persisted\""))
            assertEquals(Instant.parse("2026-08-11T00:00:00Z"), session.refreshTokenExpiresAt)
        }

    private fun tokenResponse(accessToken: String, refreshToken: String, accessExpiresAt: String): TestResponse =
        TestResponse(
            200,
            """{"accessToken":"$accessToken","refreshToken":"$refreshToken","accessTokenExpiresAt":"$accessExpiresAt","refreshTokenExpiresAt":"2026-08-11T00:00:00Z"}""",
        )

    private fun withServer(
        responseFor: (CapturedRequest) -> TestResponse,
        action: (String, MutableList<CapturedRequest>) -> Unit,
    ) {
        val requests = Collections.synchronizedList(mutableListOf<CapturedRequest>())
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val request =
                CapturedRequest(
                    exchange.requestMethod,
                    exchange.requestURI.toString(),
                    exchange.requestHeaders.getFirst("Authorization").orEmpty(),
                    exchange.requestBody.readBytes().decodeToString(),
                )
            requests += request
            val response = responseFor(request)
            val body = response.body.encodeToByteArray()
            exchange.sendResponseHeaders(response.statusCode, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
            exchange.close()
        }
        server.start()
        try {
            action("http://127.0.0.1:${server.address.port}", requests)
        } finally {
            server.stop(0)
        }
    }

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String,
        val body: String,
    )

    private data class TestResponse(val statusCode: Int, val body: String = "")
}
