package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertFailsWith

class RecoveryClientTest {
    @Test
    fun listDeviceRecoveryRequestsRejectsInvalidJson() {
        withServer("{}") { baseUrl ->
            val client = RecoveryClient(KeysteadServerClient(baseUrl, "alice", "secret"))
            assertFailsWith<IllegalStateException> { client.listDeviceRecoveryRequests() }
        }
        withServer("{broken") { baseUrl ->
            val client = RecoveryClient(KeysteadServerClient(baseUrl, "alice", "secret"))
            assertFailsWith<IllegalStateException> { client.listDeviceRecoveryRequests() }
        }
    }

    @Test
    fun claimDeviceRecoveryRejectsInvalidJson() {
        withServer("[]") { baseUrl ->
            val client = RecoveryClient(KeysteadServerClient(baseUrl, "alice", "secret"))
            assertFailsWith<IllegalStateException> { client.claimDeviceRecovery("req-1", "sig") }
        }
        withServer("{broken") { baseUrl ->
            val client = RecoveryClient(KeysteadServerClient(baseUrl, "alice", "secret"))
            assertFailsWith<IllegalStateException> { client.claimDeviceRecovery("req-1", "sig") }
        }
    }

    private fun withServer(responseBody: String, block: (String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val response = responseBody.encodeToByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
            exchange.close()
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
