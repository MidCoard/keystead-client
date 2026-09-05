package top.focess.keystead.client

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import top.focess.keystead.access.VaultAccessRequest
import top.focess.keystead.access.VaultAccessRequestCodec
import top.focess.keystead.model.SecretType
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

/** A Windows-style JVM exchanges real encrypted records with the current desktop JVM. */
class CrossPlatformSyncConvergenceTest {
    @Test
    fun restoredWindowsClientAndMacClientConvergeAcrossRepeatedPullsAndUploads() {
        val directory = Files.createTempDirectory("keystead-cross-platform-sync")
        val events = CopyOnWriteArrayList<JsonObject>()
        val approvedPackage = AtomicReference<JsonObject>()
        try {
            withServer(events, approvedPackage).use { server ->
                val client = KeysteadServerClient(server.baseUrl, "alice", "fixture-password")
                LocalVaultSession.openOrCreate(directory.resolve("mac.kvault"), "mac-master".toCharArray()).use { mac ->
                    val loginId = mac.addLogin("邮箱", "用户@example.test", "initial-secret", "https://example.test")
                    val noteId = mac.addStructuredSecret(SecretType.SECURE_NOTE, "笔记", mapOf("note" to "line one\n第二行"))
                    mac.addStructuredSecret(SecretType.SSH_KEY, "Deploy key", linkedMapOf(
                        "publicKey" to "ssh-ed25519 fixture", "privateKey" to "line one\r\nline two",
                        "passphrase" to "key-passphrase",
                    ))
                    mac.addStructuredSecret(SecretType.MFA_SECRET, "MFA", mapOf("seed" to "JBSWY3DPEHPK3PXP"))
                    mac.addStructuredSecret(SecretType.API_TOKEN, "API", mapOf("token" to "token-123"))
                    val windowsFile = directory.resolve("windows.kvault")
                    EphemeralVaultAccessSession.create(server.baseUrl).use { exchange ->
                        val pending = pendingRequest(exchange)
                        VaultAccessWorkflow(client).approve(pending, mac)
                        val packageJson = approvedPackage.get()
                        val approved = pending.copy(
                            state = ServerVaultAccessRequestState.APPROVED,
                            approvedPackage = VaultAccessKeyPackage(
                                fingerprint = packageJson["vaultFingerprint"].asString,
                                vaultKeyId = packageJson["vaultKeyId"].asString,
                                keyAlgorithm = packageJson["keyAlgorithm"].asString,
                                encryptedVaultKey = packageJson["encryptedVaultKey"].asString,
                            ),
                            approvedAt = Instant.now(),
                        )
                        ServerVaultProvisioningService().restore(
                            windowsFile, approved, exchange, "windows-master".toCharArray(), client,
                            SyncStateStore(directory.resolve("windows-sync")),
                        ).session.use { restored ->
                            assertEquals(mac.fingerprintValue(), restored.fingerprintValue())
                            assertEquals(5, restored.listSecrets().size)
                        }
                    }
                    val initial = runWindows(directory, windowsFile, server.baseUrl, "export")
                    assertStableRecords(mac.currentPersonalRecords(), initial, "after same-DEK restore")
                    repeat(3) { round ->
                        mac.updateLogin(loginId, "邮箱", "用户@example.test", "mac-round-$round", "https://example.test")
                        mac.pushAllPersonalRecordsTo(client)
                        val windowsPulled = runWindows(directory, windowsFile, server.baseUrl, "pull")
                        assertStableRecords(mac.currentPersonalRecords(), windowsPulled, "Windows pull round $round")
                        assertServerMatched(mac, client, "Mac upload round $round")

                        val windowsUploaded = runWindows(directory, windowsFile, server.baseUrl, "edit-upload", noteId, "windows-round-$round")
                        val newest = client.listAllPersonalRecords().groupBy { it.secretId }
                            .values.map { records -> records.maxWith(compareBy<PersonalVaultRecord> { it.revision }.thenBy { it.serverSequence }) }
                        val report = mac.importSelectedSyncRecords(newest.map(::encrypted))
                        assertTrue(report.rejected().isEmpty())
                        assertStableRecords(windowsUploaded, mac.currentPersonalRecords(), "Mac pull round $round")
                        assertEquals("windows-round-$round", mac.revealField(noteId, "note"))
                        assertServerMatched(mac, client, "Mac pull round $round")
                    }
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun historicalWindowsRecordsMatchAfterRepeatedPullsWithoutPromotion() {
        val legacyCore = requireNotNull(cachedLegacyCore()) {
            "Provide keystead.legacyCoreJar or cache the published Core 0.5.3 artifact before running compatibility tests"
        }
        val directory = Files.createTempDirectory("keystead-legacy-windows-sync")
        val events = CopyOnWriteArrayList<JsonObject>()
        val approvedPackage = AtomicReference<JsonObject>()
        try {
            withServer(events, approvedPackage).use { server ->
                val windowsFile = directory.resolve("legacy-windows.kvault")
                val macFile = directory.resolve("mac.kvault")
                LocalVaultSession.openOrCreate(windowsFile, "windows-master".toCharArray()).close()
                Files.copy(windowsFile, macFile)
                LocalVaultSession.openOrCreate(windowsFile, "windows-master".toCharArray()).use { source ->
                    source.addLogin("邮箱", "alice@example.test", "password", "https://example.test")
                    source.addStructuredSecret(SecretType.SECURE_NOTE, "笔记", mapOf("note" to "秘密\r\ntext"))
                }
                val legacy = runWindows(
                    directory, windowsFile, server.baseUrl, "export-upload", legacyCore = legacyCore,
                )
                val client = KeysteadServerClient(server.baseUrl, "alice", "fixture-password")
                val storedLegacy = client.listAllPersonalRecords()
                assertEquals(2, storedLegacy.size)
                LocalVaultSession.openOrCreate(macFile, "windows-master".toCharArray()).use { mac ->
                    val imported = mac.importSelectedSyncRecords(legacy)
                    assertEquals(2, imported.imported())
                    assertTrue(imported.rejected().isEmpty())
                    val canonical = mac.currentPersonalRecords()
                    assertNotEquals(
                        legacy.associate { it.secretId() to it.contentKey() },
                        canonical.associate { it.secretId() to it.contentKey() },
                        "The fixture must exercise actual legacy CRLF wire hashes",
                    )
                    assertServerMatched(mac, client, "first legacy pull")
                    repeat(3) { round ->
                        val repeated = mac.importSelectedSyncRecords(storedLegacy.map(::encrypted))
                        assertEquals(0, repeated.imported(), "Repeated legacy pull $round must not resolve a false conflict")
                        assertTrue(repeated.rejected().isEmpty())
                        assertStableRecords(canonical, mac.currentPersonalRecords(), "legacy pull $round")
                        assertServerMatched(mac, client, "legacy pull $round")
                    }
                    assertEquals(2, events.size, "Reading legacy records must not generate additional server events")
                    val tampered = storedLegacy.mapIndexed { index, record ->
                        if (index == 0) record.copy(envelope = "corrupt") else record
                    }
                    val inventory = PersonalVaultRecordInventory.compare(
                        mac.currentPersonalRecords(), tampered, canonicalContentKey = mac::canonicalSyncContentKey,
                    )
                    assertEquals(1, inventory.invalidRemoteRecords)
                    assertEquals(
                        RecordComparisonStatus.HASH_MISMATCH,
                        inventory.comparisons.orEmpty().first { it.secretId == tampered.first().secretId }.status,
                    )
                    val rejected = mac.importSelectedSyncRecords(listOf(encrypted(tampered.first())))
                    assertEquals(1, rejected.rejected().size)
                    assertStableRecords(canonical, mac.currentPersonalRecords(), "rejected tampered legacy record")
                }
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    /** CI supplies an isolated published artifact; the cache fallback also supports local probes. */
    private fun cachedLegacyCore(): Path? {
        System.getProperty("keystead.legacyCoreJar")?.let { value ->
            return Path.of(value).also { check(Files.isRegularFile(it)) { "Legacy Core fixture JAR does not exist" } }
        }
        val gradleHome = System.getenv("GRADLE_USER_HOME")?.let(Path::of)
            ?: Path.of(System.getProperty("user.home"), ".gradle")
        val directory = gradleHome.resolve("caches/modules-2/files-2.1/top.focess/keystead-core/0.5.3")
        if (!Files.isDirectory(directory)) return null
        return Files.walk(directory, 2).use { paths ->
            paths.filter { it.fileName.toString() == "keystead-core-0.5.3.jar" }.findFirst().orElse(null)
        }
    }

    private fun assertStableRecords(expected: List<EncryptedSyncRecord>, actual: List<EncryptedSyncRecord>, stage: String) {
        fun identities(records: List<EncryptedSyncRecord>) = records.associate {
            it.secretId() to Triple(it.revision(), it.contentKey(), SyncRecordEventId.of(it))
        }
        assertEquals(identities(expected), identities(actual), "Unchanged records must converge $stage")
    }

    private fun assertServerMatched(session: LocalVaultSession, client: KeysteadServerClient, stage: String) {
        val inventory = PersonalVaultRecordInventory.compare(
            session.currentPersonalRecords(), client.listAllPersonalRecords(), canonicalContentKey = session::canonicalSyncContentKey,
        )
        assertTrue(inventory.comparisons.orEmpty().all { it.status == RecordComparisonStatus.MATCHED }, stage)
    }

    private fun runWindows(
        directory: Path,
        file: Path,
        server: String,
        action: String,
        vararg extra: String,
        legacyCore: Path? = null,
    ): List<EncryptedSyncRecord> {
        val output = directory.resolve("windows-export.json")
        val log = directory.resolve("windows-worker.log")
        val classpathEntries = generateSequence(javaClass.classLoader) { it.parent }
            .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
            .filter { it.protocol == "file" }.map { Path.of(it.toURI()).toString() }.toList()
        val classpath = (listOfNotNull(legacyCore?.toString()) +
            System.getProperty("java.class.path").split(java.io.File.pathSeparator) + classpathEntries)
            .distinct().joinToString(java.io.File.pathSeparator)
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val process = ProcessBuilder(
            listOf(java.toString(), "--enable-native-access=ALL-UNNAMED", "--sun-misc-unsafe-memory-access=allow",
                "-Dline.separator=\r\n", "-Dfile.encoding=windows-1252", "-cp", classpath,
                CrossPlatformSyncWorker::class.java.name, file.toString(), server, action, output.toString()) + extra,
        ).redirectErrorStream(true).redirectOutput(log.toFile()).start()
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Windows-style worker timed out")
            assertEquals(0, process.exitValue(), Files.readString(log))
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        return Gson().fromJson(Files.readString(output), Array<EncryptedSyncRecord>::class.java).toList()
    }

    private fun encrypted(record: PersonalVaultRecord) = EncryptedSyncRecord(
        record.fingerprint, record.secretId, record.revision, record.secretType,
        record.encryptedProfile, record.envelope, record.deleted, record.contentKey,
    )

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
                        val existing = events.indexOfFirst { it["eventId"] == event["eventId"] }
                        if (existing >= 0) {
                            recordResponse(existing + 1L, events[existing])
                        } else {
                            events += event
                            recordResponse(events.size.toLong(), event)
                        }
                    }
                    exchange.requestMethod == "POST" && path.endsWith("/approve") -> {
                        approvedPackage.set(
                            JsonParser.parseString(exchange.requestBody.bufferedReader().readText()).asJsonObject,
                        )
                        ""
                    }
                    exchange.requestMethod == "GET" && path == "/api/v1/vault/records" -> {
                        val after = exchange.requestURI.query.split("&").first { it.startsWith("afterSequence=") }.substringAfter("=").toLong()
                        val records = events.mapIndexedNotNull { index, event ->
                            if (index + 1L > after) recordResponse(index + 1L, event) else null
                        }
                        "{\"afterSequence\":$after,\"records\":[${records.joinToString(",")}],\"highestSequence\":${events.size},\"hasMore\":false,\"nextSequence\":null}"
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
        "{\"serverSequence\":$sequence,\"eventId\":${event["eventId"]},\"fingerprint\":${event["fingerprint"]},\"secretId\":${event["secretId"]},\"revision\":${event["revision"]},\"secretType\":${event["secretType"]},\"encryptedProfile\":${event["encryptedProfile"]},\"envelope\":${event["envelope"]},\"deleted\":${event["deleted"]},\"contentKey\":${event["contentKey"]},\"createdAt\":\"2030-01-01T00:00:00.123456Z\"}"

    private class TestServer(private val server: HttpServer) : AutoCloseable {
        val baseUrl = "http://127.0.0.1:${server.address.port}"

        override fun close() {
            server.stop(0)
        }
    }
}

/** Runs in another JVM so line.separator and default charset are initialized as on Windows. */
object CrossPlatformSyncWorker {
    @JvmStatic
    fun main(args: Array<String>) {
        check(System.lineSeparator() == "\r\n")
        val file = Path.of(args[0])
        val client = KeysteadServerClient(args[1], "alice", "fixture-password")
        LocalVaultSession.openOrCreate(file, "windows-master".toCharArray()).use { session ->
            when (args[2]) {
                "export" -> Unit
                "export-upload" -> {
                    check(top.focess.keystead.service.DefaultVaultService::class.java
                        .protectionDomain.codeSource.location.path.endsWith("keystead-core-0.5.3.jar"))
                    session.pushAllPersonalRecordsTo(client)
                }
                "pull" -> {
                    val latest = client.listAllPersonalRecords().groupBy { it.secretId }.values.map { records ->
                        records.maxWith(compareBy<PersonalVaultRecord> { it.revision }.thenBy { it.serverSequence })
                    }
                    val report = session.importSelectedSyncRecords(latest.map { record ->
                        EncryptedSyncRecord(record.fingerprint, record.secretId, record.revision, record.secretType,
                            record.encryptedProfile, record.envelope, record.deleted, record.contentKey)
                    })
                    check(report.rejected().isEmpty())
                }
                "edit-upload" -> {
                    session.updateStructuredSecret(args[4], "笔记", mapOf("note" to args[5]))
                    session.pushSelectedPersonalRecordsTo(client, setOf(args[4]))
                }
                else -> error("Unknown worker action")
            }
            Files.writeString(Path.of(args[3]), Gson().toJson(session.currentPersonalRecords()))
        }
    }
}
