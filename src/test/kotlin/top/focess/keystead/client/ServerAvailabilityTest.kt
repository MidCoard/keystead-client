package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.http.HttpClient
import java.time.Duration
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerAvailabilityTest {
    private val checker =
        ServerAvailabilityChecker(
            http =
                HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(300))
                    .build(),
            requestTimeout = Duration.ofMillis(300),
        )

    @Test
    fun healthyActuatorEndpointMakesServerOnline() {
        withHealthServer(status = 200) { baseUrl ->
            assertEquals(ServerAvailability.ONLINE, checker.check("$baseUrl/"))
        }
    }

    @Test
    fun unhealthyActuatorEndpointMakesServerOffline() {
        withHealthServer(status = 503) { baseUrl ->
            assertEquals(ServerAvailability.OFFLINE, checker.check(baseUrl))
        }
    }

    @Test
    fun malformedAndUnreachableUrlsFailClosed() {
        assertEquals(ServerAvailability.OFFLINE, checker.check("not a URL"))
        assertEquals(ServerAvailability.OFFLINE, checker.check("http://127.0.0.1:1"))
        assertEquals(ServerAvailability.OFFLINE, checker.check("  "))
    }

    @Test
    fun serverActionOutcomeUpdatesAvailabilityWithoutMisclassifyingLocalFailures() {
        assertEquals(
            ServerAvailability.ONLINE,
            ServerAvailabilityTransitions.afterServerAction(
                current = ServerAvailability.CHECKING,
                error = null,
            ),
        )
        assertEquals(
            ServerAvailability.OFFLINE,
            ServerAvailabilityTransitions.afterServerAction(
                current = ServerAvailability.ONLINE,
                error = IOException("connection lost"),
            ),
        )
        assertEquals(
            ServerAvailability.ONLINE,
            ServerAvailabilityTransitions.afterServerAction(
                current = ServerAvailability.OFFLINE,
                error = KeysteadAuthenticationException(401),
            ),
        )
        assertEquals(
            ServerAvailability.CHECKING,
            ServerAvailabilityTransitions.afterServerAction(
                current = ServerAvailability.CHECKING,
                error = IllegalStateException("local validation failed"),
            ),
        )
    }

    private fun withHealthServer(status: Int, action: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/actuator/health") { exchange ->
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        try {
            action("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
