package top.focess.keystead.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import top.focess.keystead.memory.Wipe

enum class ServerRecoveryEnrollmentState { PENDING, ACTIVE, SUPERSEDED, CONSUMED }

data class ServerRecoveryEnrollment(
    val enrollmentId: String,
    val generation: Long,
    val state: ServerRecoveryEnrollmentState,
    val wrappingAlgorithm: String,
    val wrappingPublicKey: String,
    val createdAt: Instant,
    val committedAt: Instant?,
)

data class ServerRecoverySession(val token: String, val expiresAt: Instant) {
    override fun toString() = "ServerRecoverySession(token=<redacted>, expiresAt=$expiresAt)"
}

data class ServerRecoveryVaultPackage(
    val enrollmentId: String,
    val generation: Long,
    val vaultId: String,
    val vaultKeyId: String,
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
) {
    override fun toString() = "ServerRecoveryVaultPackage(enrollmentId=$enrollmentId, generation=$generation, vaultId=$vaultId, vaultKeyId=$vaultKeyId, keyAlgorithm=$keyAlgorithm, encryptedVaultKey=<redacted>)"
}

data class ServerRecoveryMaterial(
    val enrollmentId: String,
    val generation: Long,
    val wrappingAlgorithm: String,
    val encryptedPrivateKey: String,
    val vaultPackages: List<ServerRecoveryVaultPackage>,
) {
    override fun toString() = "ServerRecoveryMaterial(enrollmentId=$enrollmentId, generation=$generation, wrappingAlgorithm=$wrappingAlgorithm, encryptedPrivateKey=<redacted>, vaultPackages=${vaultPackages.size})"
}

data class RecoveryCompletionVaultPackage(
    val vaultId: String,
    val vaultKeyId: String,
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
) {
    override fun toString() = "RecoveryCompletionVaultPackage(vaultId=$vaultId, vaultKeyId=$vaultKeyId, keyAlgorithm=$keyAlgorithm, encryptedVaultKey=<redacted>)"
}

data class ServerRecoveryCompletion(
    val accountRecovered: Boolean,
    val deviceId: String,
    val recoveredVaultIds: List<String>,
    val pendingVaultIds: List<String>,
    val replacementKitRequired: Boolean,
)

enum class ServerRecoveryDeviceRequestState { PENDING, APPROVED, CONSUMED, EXPIRED }
data class ServerRecoveryDeviceRequest(
    val requestId: String,
    val username: String,
    val deviceId: String,
    val fingerprint: String,
    val state: ServerRecoveryDeviceRequestState,
    val expiresAt: Instant,
    val canonicalRequest: String,
) {
    override fun toString() = "ServerRecoveryDeviceRequest(requestId=$requestId, username=$username, deviceId=$deviceId, fingerprint=$fingerprint, state=$state, expiresAt=$expiresAt, canonicalRequest=<redacted>)"
}

class RecoveryClient(private val client: KeysteadServerClient) {
    fun requestDeviceRecovery(username: String, identity: LocalDeviceIdentity): ServerRecoveryDeviceRequest {
        val proof = identity.proofPublicKey(); val wrapping = identity.publicKey()
        return try {
            deviceRequest(success(client.publicExchange(
                "POST", listOf("api", "v1", "auth", "recovery", "device-requests"),
                body = "{\"username\":\"${username.recoveryJson()}\",\"deviceId\":\"${identity.deviceId.recoveryJson()}\",\"proofKeyAlgorithm\":\"${identity.proofKeyAlgorithm.recoveryJson()}\",\"proofPublicKey\":\"${java.util.Base64.getEncoder().encodeToString(proof)}\",\"wrappingKeyAlgorithm\":\"${identity.keyAlgorithm.recoveryJson()}\",\"wrappingPublicKey\":\"${java.util.Base64.getEncoder().encodeToString(wrapping)}\"}",
            )))
        } finally { Wipe.wipe(proof); Wipe.wipe(wrapping) }
    }

    fun listDeviceRecoveryRequests(): List<ServerRecoveryDeviceRequest> {
        val body = success(client.exchange("GET", listOf("api", "v1", "recovery", "device-requests")))
        return try { JsonParser.parseString(body).asJsonArray.map { deviceRequest(it.toString()) } } catch (_: RuntimeException) { throw IllegalStateException("Server returned invalid recovery JSON") }
    }

    fun approveDeviceRecovery(
        requestId: String,
        approverDeviceId: String,
        signature: String,
        packages: List<RecoveryCompletionVaultPackage>,
    ) {
        val packageJson = packages.joinToString(",") { value -> "{\"vaultId\":\"${value.vaultId.recoveryJson()}\",\"vaultKeyId\":\"${value.vaultKeyId.recoveryJson()}\",\"keyAlgorithm\":\"${value.keyAlgorithm.recoveryJson()}\",\"encryptedVaultKey\":\"${value.encryptedVaultKey.recoveryJson()}\"}" }
        success(client.exchange("POST", listOf("api", "v1", "recovery", "device-requests", requestId, "approve"), body = "{\"deviceId\":\"${approverDeviceId.recoveryJson()}\",\"signature\":\"${signature.recoveryJson()}\",\"vaultPackages\":[$packageJson]}"))
    }

    fun claimDeviceRecovery(requestId: String, signature: String): ServerRecoverySession {
        val value = objectValue(success(client.publicExchange("POST", listOf("api", "v1", "auth", "recovery", "device-requests", requestId, "claim"), body = "{\"signature\":\"${signature.recoveryJson()}\"}")))
        return ServerRecoverySession(value.string("token"), Instant.parse(value.string("expiresAt")))
    }
    fun createEnrollment(
        enrollmentId: String,
        generation: Long,
        accountCredential: String,
        wrappingAlgorithm: String,
        wrappingPublicKey: String,
        encryptedPrivateKey: String,
    ): ServerRecoveryEnrollment = enrollment(success(client.exchange(
        "POST", listOf("api", "v1", "recovery", "enrollments"),
        body = JsonObject().apply {
            addProperty("enrollmentId", enrollmentId); addProperty("generation", generation)
            addProperty("accountCredential", accountCredential); addProperty("wrappingAlgorithm", wrappingAlgorithm)
            addProperty("wrappingPublicKey", wrappingPublicKey); addProperty("encryptedPrivateKey", encryptedPrivateKey)
        }.toString(),
    )))

    fun commitEnrollment(enrollmentId: String, generation: Long): ServerRecoveryEnrollment =
        enrollment(success(client.exchange("POST", listOf("api", "v1", "recovery", "enrollments", enrollmentId, "commit"), body = "{\"generation\":$generation}")))

    fun putVaultPackage(username: String, packageValue: ServerRecoveryVaultPackage) {
        success(client.exchange(
            "PUT", listOf("api", "v1", "recovery", "users", username, "enrollments", packageValue.enrollmentId, "vaults", packageValue.vaultId),
            body = "{\"generation\":${packageValue.generation},\"vaultKeyId\":\"${packageValue.vaultKeyId.recoveryJson()}\",\"keyAlgorithm\":\"${packageValue.keyAlgorithm.recoveryJson()}\",\"encryptedVaultKey\":\"${packageValue.encryptedVaultKey.recoveryJson()}\"}",
        ))
    }

    fun createChallenge(username: String, enrollmentId: String, generation: Long): Pair<String, Instant> {
        val value = objectValue(success(client.publicExchange("POST", listOf("api", "v1", "auth", "recovery", "challenges"), body = "{\"username\":\"${username.recoveryJson()}\",\"enrollmentId\":\"${enrollmentId.recoveryJson()}\",\"generation\":$generation}")))
        return value.string("challengeId") to Instant.parse(value.string("expiresAt"))
    }

    fun verifyKit(challengeId: String, accountCredential: String): ServerRecoverySession {
        val value = objectValue(success(client.publicExchange("POST", listOf("api", "v1", "auth", "recovery", "kit"), body = "{\"challengeId\":\"${challengeId.recoveryJson()}\",\"accountCredential\":\"${accountCredential.recoveryJson()}\"}")))
        return ServerRecoverySession(value.string("token"), Instant.parse(value.string("expiresAt")))
    }

    fun material(token: String): ServerRecoveryMaterial {
        val value = objectValue(success(client.recoveryExchange("GET", listOf("api", "v1", "auth", "recovery", "material"), token)))
        val packages = value.getAsJsonArray("vaultPackages").map { element ->
            val item = element.asJsonObject
            ServerRecoveryVaultPackage(item.string("enrollmentId"), item.long("generation"), item.string("vaultId"), item.string("vaultKeyId"), item.string("keyAlgorithm"), item.string("encryptedVaultKey"))
        }
        return ServerRecoveryMaterial(value.string("enrollmentId"), value.long("generation"), value.string("wrappingAlgorithm"), value.string("encryptedPrivateKey"), packages)
    }

    fun complete(
        token: String,
        newPassword: String,
        identity: LocalDeviceIdentity,
        vaultPackages: List<RecoveryCompletionVaultPackage>,
    ): ServerRecoveryCompletion {
        val proof = identity.proofPublicKey()
        val wrapping = identity.publicKey()
        try {
            val packages = vaultPackages.joinToString(",") { value ->
                "{\"vaultId\":\"${value.vaultId.recoveryJson()}\",\"vaultKeyId\":\"${value.vaultKeyId.recoveryJson()}\",\"keyAlgorithm\":\"${value.keyAlgorithm.recoveryJson()}\",\"encryptedVaultKey\":\"${value.encryptedVaultKey.recoveryJson()}\"}"
            }
            val body = "{\"newPassword\":\"${newPassword.recoveryJson()}\",\"deviceId\":\"${identity.deviceId.recoveryJson()}\",\"proofKeyAlgorithm\":\"${identity.proofKeyAlgorithm.recoveryJson()}\",\"proofPublicKey\":\"${java.util.Base64.getEncoder().encodeToString(proof)}\",\"wrappingKeyAlgorithm\":\"${identity.keyAlgorithm.recoveryJson()}\",\"wrappingPublicKey\":\"${java.util.Base64.getEncoder().encodeToString(wrapping)}\",\"vaultPackages\":[$packages]}"
            val value = objectValue(success(client.recoveryExchange("POST", listOf("api", "v1", "auth", "recovery", "complete"), token, body)))
            return ServerRecoveryCompletion(
                value.get("accountRecovered").asBoolean,
                value.string("deviceId"),
                value.getAsJsonArray("recoveredVaultIds").map { it.asString },
                value.getAsJsonArray("pendingVaultIds").map { it.asString },
                value.get("replacementKitRequired").asBoolean,
            )
        } finally { Wipe.wipe(proof); Wipe.wipe(wrapping) }
    }

    private fun enrollment(body: String): ServerRecoveryEnrollment {
        val value = objectValue(body)
        return ServerRecoveryEnrollment(value.string("enrollmentId"), value.long("generation"), ServerRecoveryEnrollmentState.valueOf(value.string("state")), value.string("wrappingAlgorithm"), value.string("wrappingPublicKey"), Instant.parse(value.string("createdAt")), value.get("committedAt")?.takeUnless { it.isJsonNull }?.asString?.let(Instant::parse))
    }

    private fun deviceRequest(body: String): ServerRecoveryDeviceRequest {
        val value = objectValue(body)
        return ServerRecoveryDeviceRequest(
            value.string("requestId"), value.string("username"), value.string("deviceId"),
            value.string("fingerprint"), ServerRecoveryDeviceRequestState.valueOf(value.string("state")),
            Instant.parse(value.string("expiresAt")), value.string("canonicalRequest"),
        )
    }

    private fun success(value: ServerExchange): String {
        if (value.statusCode in 200..299) return value.body
        if (value.statusCode == 401 || value.statusCode == 403) throw KeysteadAuthenticationException(value.statusCode)
        throw KeysteadServerException(value.statusCode, "Keystead Server returned HTTP ${value.statusCode}")
    }
    private fun objectValue(body: String): JsonObject = try { JsonParser.parseString(body).asJsonObject } catch (_: RuntimeException) { throw IllegalStateException("Server returned invalid recovery JSON") }
    private fun JsonObject.string(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: error("Server recovery response is missing $name")
    private fun JsonObject.long(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive }?.asLong ?: error("Server recovery response is missing $name")
}

private fun String.recoveryJson() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
