package top.focess.keystead.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CollaborativeVaultServiceTest {
    @Test
    fun `publishes every uncovered recipient device without current-user assumption`() {
        val directory = Files.createTempDirectory("keystead-collaborative-publish")
        DeviceIdentityStore(directory.resolve("first")).createMemoryOnly("alice-device").use { first ->
            DeviceIdentityStore(directory.resolve("second")).createMemoryOnly("bob-device").use { second ->
                DeviceIdentityStore(directory.resolve("covered")).createMemoryOnly("covered-device").use { covered ->
                    LocalVaultSession.openOrCreate(
                        directory.resolve("vault"), UUID.randomUUID(), "master-password".toCharArray(),
                    ).use { vault ->
                        val recipients = coverage(
                            first.deviceId to Base64.getEncoder().encodeToString(first.publicKey()),
                            second.deviceId to Base64.getEncoder().encodeToString(second.publicKey()),
                            covered.deviceId to Base64.getEncoder().encodeToString(covered.publicKey()),
                        )
                        withServer(recipients) { client, requests ->
                            val published = CollaborativeVaultService(client)
                                .publishUncoveredRecipientPackages(vault)

                            assertEquals(2, published)
                            assertEquals(2, requests.size)
                            assertTrue(requests.any { it.path.contains("/recipients/alice/devices/alice-device") })
                            assertTrue(requests.any { it.path.contains("/recipients/bob/devices/bob-device") })
                            assertFalse(requests.any { it.path.contains("covered-device") })
                            assertTrue(requests.all { it.body.contains("TINK_DEVICE_KEY_PACKAGE") })
                            assertFalse(requests.joinToString().contains("private"))
                        }
                    }
                }
            }
        }
    }

    private fun coverage(
        first: Pair<String, String>,
        second: Pair<String, String>,
        covered: Pair<String, String>,
    ) = """{"currentVaultKeyId":"key-1","keyLifecycleState":"STABLE","lifecycleVersion":1,"devices":[${device("alice", first, false)},${device("bob", second, false)},${device("owner", covered, true)}]}"""

    private fun device(userId: String, device: Pair<String, String>, covered: Boolean) =
        """{"userId":"$userId","role":"EDITOR","memberState":"ACTIVE","deviceId":"${device.first}","keyAlgorithm":"TINK_ECIES_P256_HKDF_HMAC_SHA256_AES128_GCM","publicKey":"${device.second}","covered":$covered}"""

    private fun withServer(
        coverage: String,
        action: (KeysteadServerClient, MutableList<Request>) -> Unit,
    ) {
        val requests = mutableListOf<Request>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val response = if (exchange.requestMethod == "GET" && path.endsWith("/package-recipients")) {
                coverage
            } else {
                requests += Request(path, exchange.requestBody.readBytes().decodeToString())
                ""
            }
            val bytes = response.encodeToByteArray()
            val status = if (exchange.requestMethod == "GET") 200 else 201
            if (bytes.isEmpty()) exchange.sendResponseHeaders(status, -1)
            else exchange.sendResponseHeaders(status, bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
        try {
            action(
                KeysteadServerClient("http://127.0.0.1:${server.address.port}", "owner", "password"),
                requests,
            )
        } finally {
            server.stop(0)
        }
    }

    private data class Request(val path: String, val body: String)
}
