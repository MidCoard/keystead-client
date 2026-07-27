package top.focess.keystead.client

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import top.focess.keystead.crypto.DefaultCryptoService
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal fun interface ServerAuthorization {
    fun headerValue(): String
}

private class BasicServerAuthorization(
    username: String,
    password: String,
) : ServerAuthorization {
    private val value =
        "Basic " +
            Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray(StandardCharsets.UTF_8),
            )

    override fun headerValue(): String = value

    override fun toString(): String = "BasicServerAuthorization(<redacted>)"
}

data class ServerEncryptedRecord(
    val secretId: String? = null,
    val revision: Long,
    val secretType: String,
    val encryptedProfile: String,
    val envelope: String,
    val deleted: Boolean,
)

data class ServerEncryptedRecordPage(
    val fingerprint: String,
    val sinceRevision: Long,
    val records: List<ServerEncryptedRecord>,
    val highestRevision: Long,
    val hasMore: Boolean,
    val nextSinceRevision: Long?,
)

data class ServerDevice(
    val deviceId: String,
    val keyAlgorithm: String,
    val publicKey: String,
    val wrappingKeyAlgorithm: String? = null,
    val wrappingPublicKey: String? = null,
    val createdAt: Instant? = null,
    val verifiedAt: Instant? = null,
    val lastSeenAt: Instant? = null,
    val revokedAt: Instant? = null,
) {
    init {
        require(deviceId.isNotBlank()) { "Device id must not be blank" }
        require(keyAlgorithm in supportedDeviceProofAlgorithms) {
            "Device proof key algorithm is unsupported"
        }
        require(publicKey.isNotBlank()) { "Device proof public key must not be blank" }
        require((wrappingKeyAlgorithm == null) == (wrappingPublicKey == null)) {
            "Device wrapping key fields must be present together"
        }
        if (wrappingKeyAlgorithm != null && wrappingPublicKey != null) {
            require(wrappingKeyAlgorithm in supportedDeviceWrappingAlgorithms) {
                "Device wrapping key algorithm is unsupported"
            }
            require(wrappingPublicKey.isNotBlank()) {
                "Device wrapping public key must not be blank"
            }
        }
        if (createdAt != null) {
            require(verifiedAt == null || !verifiedAt.isBefore(createdAt)) {
                "Device verified time must not precede creation"
            }
            require(lastSeenAt == null || !lastSeenAt.isBefore(createdAt)) {
                "Device last-seen time must not precede creation"
            }
            require(revokedAt == null || !revokedAt.isBefore(createdAt)) {
                "Device revoked time must not precede creation"
            }
        }
    }

    val canReceiveVaultKeyPackage: Boolean
        get() =
            createdAt != null &&
                verifiedAt != null &&
                revokedAt == null &&
                !wrappingKeyAlgorithm.isNullOrBlank() &&
                !wrappingPublicKey.isNullOrBlank()
}

data class ServerDeviceChallenge(
    val deviceId: String,
    val challengeId: String,
    val nonce: String,
    val expiresAt: Instant,
) {
    init {
        require(deviceId.isNotBlank()) { "Device challenge id must not be blank" }
        require(challengeId.isNotBlank()) { "Challenge id must not be blank" }
        require(nonce.isNotBlank()) { "Challenge nonce must not be blank" }
    }
}

private val supportedDeviceProofAlgorithms =
    setOf(
        "RSA_OAEP_SHA256",
        "RSA_PSS_SHA256",
        "ECDSA_P256_SHA256",
        "ECDSA_P384_SHA384",
        "ECDSA_P521_SHA512",
        "ED25519",
    )

private val supportedDeviceWrappingAlgorithms =
    setOf("RSA_OAEP_SHA256", DefaultCryptoService.DEVICE_KEY_ALGORITHM)

data class ServerVaultKeyPackage(
    val fingerprint: String,
    val deviceId: String,
    val vaultKeyId: String = "legacy",
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
)

data class ServerAutomationPrincipal(
    val principalId: String,
    val publicKeyAlgorithm: String,
    val publicKey: String,
)

data class ServerAutomationVaultKeyPackage(
    val vaultKeyId: String,
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
)

data class ServerVault(
    val fingerprint: String,
    val encryptedMetadata: String,
)

data class ServerMintedShare(
    val code: String,
    val expiresAt: Instant,
)

data class ServerShareSummary(
    val code: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val burnAfterReading: Boolean,
)

open class KeysteadServerException(
    val statusCode: Int,
    message: String,
) : IllegalStateException(message)

class KeysteadRevisionConflictException(
    message: String = defaultRevisionConflictMessage,
    val latestRevision: Long? = null,
    val rejectedRevision: Long? = null,
    val fingerprint: String? = null,
    val secretId: String? = null,
    val serverRevision: Long? = null,
    val clientRevision: Long? = null,
    val serverDeleted: Boolean? = null,
    val serverUpdatedAt: String? = null,
) : KeysteadServerException(409, message)

class KeysteadAuthenticationException(statusCode: Int) :
    KeysteadServerException(statusCode, "Server rejected the username or password.")

class KeysteadAccountConflictException :
    KeysteadServerException(409, "Server user already exists.")

/**
 * Raised when no share exists for the supplied code (HTTP 404). For a burn-after-reading share
 * this also covers the case where a concurrent redeem already consumed it.
 */
class KeysteadShareNotFoundException :
    KeysteadServerException(404, "No share found for that code.")

/**
 * Raised when the share has expired or has already been redeemed (HTTP 410).
 */
class KeysteadShareExpiredException :
    KeysteadServerException(410, "This share has expired or has already been redeemed.")

/**
 * Raised when the server's share rate limit is hit (HTTP 429). [retryAfterSeconds] carries the
 * server-advertised back-off when the `Retry-After` header is present.
 */
class KeysteadShareRateLimitedException(val retryAfterSeconds: Long?) :
    KeysteadServerException(
        429,
        if (retryAfterSeconds != null) {
            "Too many share attempts. Wait $retryAfterSeconds second(s) and try again."
        } else {
            "Too many share attempts. Wait a minute and try again."
        },
    )

private const val defaultRevisionConflictMessage =
    "Server has a newer revision; pull before pushing again."

class KeysteadServerClient private constructor(
    baseUrl: String,
    private val http: HttpClient,
    private val authorization: ServerAuthorization,
) {
    private val root = baseUrl.trimEnd('/')

    constructor(
        baseUrl: String,
        username: String,
        password: String,
        http: HttpClient = HttpClient.newHttpClient(),
    ) : this(baseUrl, http, BasicServerAuthorization(username, password))

    internal constructor(
        baseUrl: String,
        authorization: ServerAuthorization,
        http: HttpClient = HttpClient.newHttpClient(),
    ) : this(baseUrl, http, authorization)

    companion object {
        /**
         * Builds a client that can only redeem shares via the server's public redeem endpoint.
         *
         * No server account is required, so a recipient who has not signed in can still open a
         * share they received out of band. Authenticated methods (mint/list/delete) called on
         * this client are rejected by the server because no credentials are attached; the
         * [redeemShare] call itself never sends an `Authorization` header.
         */
        fun forPublicRedeem(baseUrl: String, http: HttpClient = HttpClient.newHttpClient()): KeysteadServerClient =
            KeysteadServerClient(baseUrl, PublicRedeemAuthorization, http)
    }

    private object PublicRedeemAuthorization : ServerAuthorization {
        override fun headerValue(): String = ""

        override fun toString(): String = "PublicRedeemAuthorization"
    }

    internal fun exchange(
        method: String,
        segments: List<String>,
        query: String? = null,
        body: String? = null,
    ): ServerExchange {
        return exchangeWithHeader(method, segments, query, body, authorization.headerValue())
    }

    internal fun publicExchange(
        method: String,
        segments: List<String>,
        query: String? = null,
        body: String? = null,
    ): ServerExchange = exchangeWithHeader(method, segments, query, body, null)

    internal fun recoveryExchange(
        method: String,
        segments: List<String>,
        token: String,
        body: String? = null,
    ): ServerExchange = exchangeWithHeader(method, segments, null, body, "Recovery $token")

    private fun exchangeWithHeader(
        method: String,
        segments: List<String>,
        query: String?,
        body: String?,
        authorizationHeader: String?,
    ): ServerExchange {
        val builder = HttpRequest.newBuilder(endpoint(*segments.toTypedArray(), query = query))
        authorizationHeader?.let { builder.header("Authorization", it) }
        if (body != null) builder.header("Content-Type", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        val request = builder.method(method, publisher).build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        return ServerExchange(response.statusCode(), response.body())
    }

    fun registerUser(username: String, password: String) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "users"))
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"username":"${username.json()}","password":"${password.json()}"}""",
                    ),
                )
                .build()

        sendExpectingSuccess(request) { KeysteadAccountConflictException() }
    }

    fun putVault(fingerprint: String, encryptedMetadata: String) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint))
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .PUT(
                    HttpRequest.BodyPublishers.ofString(
                        """{"encryptedMetadata":"${encryptedMetadata.json()}"}""",
                    ),
                )
                .build()

        sendExpectingSuccess(request)
    }

    fun listVaults(): List<ServerVault> {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "vaults"))
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body())
        return parseVaults(response.body())
    }

    fun putRecord(fingerprint: String, secretId: String, record: ServerEncryptedRecord) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "records", secretId))
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(record.toJson()))
                .build()

        sendExpectingSuccess(request)
    }

    fun deleteRecord(fingerprint: String, secretId: String, revision: Long) {
        val request =
            HttpRequest.newBuilder(
                    endpoint(
                        "api",
                        "v1",
                        "vaults",
                        fingerprint,
                        "records",
                        secretId,
                        query = "revision=$revision",
                    ),
                )
                .header("Authorization", authorization.headerValue())
                .DELETE()
                .build()

        sendExpectingSuccess(request)
    }

    fun listRecords(fingerprint: String, sinceRevision: Long): List<ServerEncryptedRecord> {
        val request =
            HttpRequest.newBuilder(
                    endpoint(
                        "api",
                        "v1",
                        "vaults",
                        fingerprint,
                        "records",
                        query = "sinceRevision=$sinceRevision",
                    ),
                )
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body())
        return parseRecords(response.body())
    }

    fun listRecordPage(
        fingerprint: String,
        sinceRevision: Long,
        limit: Int,
    ): ServerEncryptedRecordPage {
        val request =
            HttpRequest.newBuilder(
                    endpoint(
                        "api",
                        "v1",
                        "vaults",
                        fingerprint,
                        "records",
                        "page",
                        query = "sinceRevision=$sinceRevision&limit=$limit",
                    ),
                )
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body())
        return parseRecordPage(response.body())
    }

    fun registerDevice(device: ServerDevice) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "devices"))
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(device.toJson()))
                .build()

        sendExpectingSuccess(request) { lifecycleConflict() }
    }

    fun listDevices(): List<ServerDevice> {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "devices"))
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body()) { lifecycleConflict() }
        return parseDevices(response.body())
    }

    fun createDeviceChallenge(deviceId: String): ServerDeviceChallenge {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "devices", deviceId, "challenges"))
                .header("Authorization", authorization.headerValue())
                .POST(HttpRequest.BodyPublishers.noBody())
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body())
        return parseDeviceChallenge(response.body())
    }

    fun proveDevice(deviceId: String, challengeId: String, signature: String) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "devices", deviceId, "proof"))
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        """{"challengeId":"${challengeId.json()}","signature":"${signature.json()}"}""",
                    ),
                )
                .build()
        sendExpectingSuccess(request) { lifecycleConflict() }
    }

    fun revokeDevice(deviceId: String) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "devices", deviceId))
                .header("Authorization", authorization.headerValue())
                .DELETE()
                .build()
        sendExpectingSuccess(request) { lifecycleConflict() }
    }

    fun putVaultKeyPackage(fingerprint: String, deviceId: String, keyPackage: ServerVaultKeyPackage) {
        val request =
            HttpRequest.newBuilder(
                    endpoint("api", "v1", "vaults", fingerprint, "key-packages", deviceId),
                )
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(keyPackage.toJson()))
                .build()

        sendExpectingSuccess(request)
    }

    fun putRecipientVaultKeyPackage(
        fingerprint: String,
        recipientId: String,
        deviceId: String,
        keyPackage: ServerVaultKeyPackage,
    ) {
        val request = HttpRequest.newBuilder(
            endpoint("api", "v1", "vaults", fingerprint, "key-packages", "recipients", recipientId, "devices", deviceId),
        ).header("Authorization", authorization.headerValue())
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(keyPackage.toJson()))
            .build()
        sendExpectingSuccess(request)
    }

    fun listVaultKeyPackages(fingerprint: String): List<ServerVaultKeyPackage> {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "key-packages"))
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body())
        return parseKeyPackages(response.body())
    }

    fun rotateVaultKey(fingerprint: String, vaultKeyId: String) {
        val request = HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "key-rotation"))
            .header("Authorization", authorization.headerValue()).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"vaultKeyId":"${vaultKeyId.json()}"}"""))
            .build()
        sendExpectingSuccess(request)
    }

    fun putAutomationPrincipal(fingerprint: String, principal: ServerAutomationPrincipal) {
        val request = HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "automation-principals", principal.principalId))
            .header("Authorization", authorization.headerValue()).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"publicKeyAlgorithm":"${principal.publicKeyAlgorithm.json()}","publicKey":"${principal.publicKey.json()}"}"""))
            .build()
        sendExpectingSuccess(request)
    }

    fun putAutomationVaultKeyPackage(fingerprint: String, principalId: String, keyPackage: ServerAutomationVaultKeyPackage) {
        val request = HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "automation-principals", principalId, "key-package"))
            .header("Authorization", authorization.headerValue()).header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString("""{"vaultKeyId":"${keyPackage.vaultKeyId.json()}","keyAlgorithm":"${keyPackage.keyAlgorithm.json()}","encryptedVaultKey":"${keyPackage.encryptedVaultKey.json()}"}"""))
            .build()
        sendExpectingSuccess(request)
    }

    fun revokeAutomationPrincipal(fingerprint: String, principalId: String) {
        val request = HttpRequest.newBuilder(endpoint("api", "v1", "vaults", fingerprint, "automation-principals", principalId))
            .header("Authorization", authorization.headerValue()).DELETE().build()
        sendExpectingSuccess(request)
    }

    fun mintShare(
        payload: String,
        expiresAt: Instant?,
        burnAfterReading: Boolean?,
    ): ServerMintedShare {
        val body =
            buildString {
                append("{\"payload\":\"")
                append(payload.json())
                append('"')
                if (expiresAt != null) {
                    append(",\"expiresAt\":\"")
                    append(expiresAt.toString().json())
                    append('"')
                }
                if (burnAfterReading != null) {
                    append(",\"burnAfterReading\":")
                    append(burnAfterReading)
                }
                append('}')
            }
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "shares"))
                .header("Authorization", authorization.headerValue())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireShareSuccess(response.statusCode(), retryAfterSeconds(response))
        return ServerMintedShare(
            code = jsonString(response.body(), "code"),
            expiresAt = jsonInstant(response.body(), "expiresAt"),
        )
    }

    fun redeemShare(code: String): String {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "shares", code))
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireShareSuccess(response.statusCode(), retryAfterSeconds(response))
        return jsonString(response.body(), "payload")
    }

    fun listShares(): List<ServerShareSummary> {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "shares"))
                .header("Authorization", authorization.headerValue())
                .GET()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireShareSuccess(response.statusCode(), retryAfterSeconds(response))
        return parseShareSummaries(response.body())
    }

    fun deleteShare(code: String) {
        val request =
            HttpRequest.newBuilder(endpoint("api", "v1", "shares", code))
                .header("Authorization", authorization.headerValue())
                .DELETE()
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireShareSuccess(response.statusCode(), retryAfterSeconds(response))
    }

    private fun sendExpectingSuccess(
        request: HttpRequest,
        conflict: (String) -> KeysteadServerException = { revisionConflict(it) },
    ) {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireSuccess(response.statusCode(), response.body(), conflict)
    }

    private fun requireSuccess(
        statusCode: Int,
        body: String,
        conflict: (String) -> KeysteadServerException = { revisionConflict(it) },
    ) {
        if (statusCode in 200..299) {
            return
        }
        if (statusCode == 409) {
            throw conflict(body)
        }
        if (statusCode == 401 || statusCode == 403) {
            throw KeysteadAuthenticationException(statusCode)
        }
        throw KeysteadServerException(statusCode, "Keystead Server returned HTTP $statusCode")
    }

    /**
     * Status mapping for the share endpoints, where 404/410/429 carry share-specific meaning
     * (missing, expired/burned, rate-limited). [retryAfterSeconds] is the parsed `Retry-After`
     * header, if any, surfaced to the user on 429.
     */
    private fun requireShareSuccess(statusCode: Int, retryAfterSeconds: Long?) {
        if (statusCode in 200..299) {
            return
        }
        when (statusCode) {
            404 -> throw KeysteadShareNotFoundException()
            410 -> throw KeysteadShareExpiredException()
            429 -> throw KeysteadShareRateLimitedException(retryAfterSeconds)
            401, 403 -> throw KeysteadAuthenticationException(statusCode)
            else -> throw KeysteadServerException(statusCode, "Keystead Server returned HTTP $statusCode")
        }
    }

    private fun retryAfterSeconds(response: HttpResponse<*>): Long? {
        val value = response.headers().firstValueAsLong("Retry-After")
        return if (value.isPresent) value.asLong else null
    }

    private fun revisionConflict(body: String): KeysteadRevisionConflictException =
        KeysteadRevisionConflictException(
            message = jsonStringOrNull(body, "message") ?: defaultRevisionConflictMessage,
            latestRevision = jsonLongOrNull(body, "latestRevision"),
            rejectedRevision = jsonLongOrNull(body, "rejectedRevision"),
            fingerprint = jsonStringOrNull(body, "fingerprint"),
            secretId = jsonStringOrNull(body, "secretId"),
            serverRevision = jsonLongOrNull(body, "serverRevision"),
            clientRevision = jsonLongOrNull(body, "clientRevision"),
            serverDeleted = jsonBooleanOrNull(body, "serverDeleted"),
            serverUpdatedAt = jsonStringOrNull(body, "serverUpdatedAt"),
        )

    private fun lifecycleConflict() = KeysteadServerException(409, "Device lifecycle request was rejected by the server")

    private fun endpoint(vararg segments: String, query: String? = null): URI {
        val path = segments.joinToString(separator = "/", prefix = "/") { it.pathSegment() }
        return URI.create(root + path + if (query == null) "" else "?$query")
    }

    private fun ServerEncryptedRecord.toJson(): String =
        """{"revision":$revision,"secretType":"${secretType.json()}","encryptedProfile":"${encryptedProfile.json()}","envelope":"${envelope.json()}","deleted":$deleted}"""

    private fun ServerDevice.toJson(): String =
        buildString {
            append("{\"deviceId\":\"")
            append(deviceId.json())
            append("\",\"keyAlgorithm\":\"")
            append(keyAlgorithm.json())
            append("\",\"publicKey\":\"")
            append(publicKey.json())
            append('"')
            if (wrappingKeyAlgorithm != null && wrappingPublicKey != null) {
                append(",\"wrappingKeyAlgorithm\":\"")
                append(wrappingKeyAlgorithm.json())
                append("\",\"wrappingPublicKey\":\"")
                append(wrappingPublicKey.json())
                append('"')
            }
            append('}')
        }

    private fun ServerVaultKeyPackage.toJson(): String =
        """{"vaultKeyId":"${vaultKeyId.json()}","keyAlgorithm":"${keyAlgorithm.json()}","encryptedVaultKey":"${encryptedVaultKey.json()}"}"""

    private fun parseRecords(body: String): List<ServerEncryptedRecord> {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return emptyList()
        }
        return jsonObjects(trimmed)
            .asSequence()
            .map { json ->
                val deleted = jsonBoolean(json, "deleted")
                ServerEncryptedRecord(
                    secretId = jsonString(json, "secretId"),
                    revision = jsonLong(json, "revision"),
                    secretType = jsonString(json, "secretType"),
                    encryptedProfile =
                        jsonStringOrNull(json, "encryptedProfile")
                            ?: if (deleted) {
                                jsonStringOrNull(json, "metadata").orEmpty()
                            } else {
                                jsonString(json, "metadata")
                            },
                    envelope =
                        if (deleted) {
                            jsonStringOrNull(json, "envelope").orEmpty()
                        } else {
                            jsonString(json, "envelope")
                        },
                    deleted = deleted,
                )
            }
            .toList()
    }

    private fun parseRecordPage(body: String): ServerEncryptedRecordPage {
        val recordsBody =
            Regex(""""records"\s*:\s*(\[[\s\S]*])\s*,\s*"highestRevision"""")
                .find(body)
                ?.groupValues
                ?.get(1)
                ?: throw IllegalStateException("Server record page is missing records")
        return ServerEncryptedRecordPage(
            fingerprint = jsonString(body, "fingerprint"),
            sinceRevision = jsonLong(body, "sinceRevision"),
            records = parseRecords(recordsBody),
            highestRevision = jsonLong(body, "highestRevision"),
            hasMore = jsonBoolean(body, "hasMore"),
            nextSinceRevision = jsonLongOrNull(body, "nextSinceRevision"),
        )
    }

    private fun parseVaults(body: String): List<ServerVault> {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return emptyList()
        }
        return jsonObjects(trimmed)
            .asSequence()
            .map { json ->
                ServerVault(
                    fingerprint = jsonString(json, "fingerprint"),
                    encryptedMetadata = jsonString(json, "encryptedMetadata"),
                )
            }
            .toList()
    }

    private fun parseDevices(body: String): List<ServerDevice> {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return emptyList()
        }
        val parsed = JsonParser.parseString(trimmed)
        if (!parsed.isJsonArray) throw IllegalStateException("Server returned invalid lifecycle JSON")
        return parsed.asJsonArray.map { element ->
                val obj = strictObject(element.toString(), setOf("deviceId","keyAlgorithm","publicKey","wrappingKeyAlgorithm","wrappingPublicKey","createdAt","verifiedAt","lastSeenAt","revokedAt"))
                ServerDevice(
                    deviceId = obj.reqString("deviceId"), keyAlgorithm = obj.reqString("keyAlgorithm"), publicKey = obj.reqString("publicKey"),
                    wrappingKeyAlgorithm = obj.optString("wrappingKeyAlgorithm"), wrappingPublicKey = obj.optString("wrappingPublicKey"),
                    createdAt = obj.optInstant("createdAt"), verifiedAt = obj.optInstant("verifiedAt"), lastSeenAt = obj.optInstant("lastSeenAt"), revokedAt = obj.optInstant("revokedAt"),
                )
            }
    }

    private fun parseDeviceChallenge(body: String): ServerDeviceChallenge {
        val o = strictObject(body, setOf("deviceId","challengeId","nonce","expiresAt"))
        return ServerDeviceChallenge(o.reqString("deviceId"), o.reqString("challengeId"), o.reqString("nonce"), Instant.parse(o.reqString("expiresAt")))
    }

    private fun strictObject(body: String, allowed: Set<String>): JsonObject {
        val e = try { JsonParser.parseString(body) } catch (_: RuntimeException) { throw IllegalStateException("Server returned invalid lifecycle JSON") }
        if (!e.isJsonObject) throw IllegalStateException("Server returned invalid lifecycle JSON")
        val o=e.asJsonObject; if (o.keySet().any { it !in allowed }) throw IllegalStateException("Server returned unknown lifecycle field"); return o
    }
    private fun strictArray(body: String, allowed: Set<String>): List<String> { val e=try{JsonParser.parseString(body)}catch(_:RuntimeException){throw IllegalStateException("Server returned invalid lifecycle JSON")}; if(!e.isJsonArray) throw IllegalStateException("Server returned invalid lifecycle JSON"); return e.asJsonArray.map { strictObject(it.toString(),allowed).toString() } }
    private fun JsonObject.reqString(k:String):String { val p=get(k); if(p==null||!p.isJsonPrimitive||!p.asJsonPrimitive.isString) throw IllegalStateException("Server record is missing $k"); if(p.asString.isBlank()) throw IllegalArgumentException("Challenge $k must not be blank"); return p.asString }
    private fun JsonObject.optString(k:String):String? { val p=get(k) ?: return null; if(p.isJsonNull) return null; if(!p.isJsonPrimitive || !p.asJsonPrimitive.isString || p.asString.isBlank()) throw IllegalStateException("Server lifecycle field invalid"); return p.asString }
    private fun JsonObject.optInstant(k:String):Instant? = optString(k)?.let { try { Instant.parse(it) } catch (_:RuntimeException) { throw IllegalStateException("Server lifecycle field invalid") } }

    private fun parseKeyPackages(body: String): List<ServerVaultKeyPackage> {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return emptyList()
        }
        return jsonObjects(trimmed)
            .asSequence()
            .map { json ->
                ServerVaultKeyPackage(
                    fingerprint = jsonString(json, "fingerprint"),
                    deviceId = jsonString(json, "deviceId"),
                    vaultKeyId = jsonStringOrNull(json, "vaultKeyId") ?: "legacy",
                    keyAlgorithm = jsonString(json, "keyAlgorithm"),
                    encryptedVaultKey = jsonString(json, "encryptedVaultKey"),
                )
            }
            .toList()
    }

    private fun parseShareSummaries(body: String): List<ServerShareSummary> {
        val trimmed = body.trim()
        if (trimmed == "[]") {
            return emptyList()
        }
        return jsonObjects(trimmed)
            .asSequence()
            .map { json ->
                ServerShareSummary(
                    code = jsonString(json, "code"),
                    createdAt = jsonInstant(json, "createdAt"),
                    expiresAt = jsonInstant(json, "expiresAt"),
                    burnAfterReading = jsonBoolean(json, "burnAfterReading"),
                )
            }
            .toList()
    }

    private fun jsonObjects(jsonArray: String): List<String> {
        if (!jsonArray.startsWith('[') || !jsonArray.endsWith(']')) {
            throw IllegalStateException("Server returned an invalid JSON array")
        }
        val objects = mutableListOf<String>()
        var objectStart = -1
        var objectDepth = 0
        var inString = false
        var escaped = false
        jsonArray.forEachIndexed { index, character ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    character == '\\' -> escaped = true
                    character == '"' -> {
                        inString = false
                    }
                }
            } else {
                when (character) {
                    '"' -> {
                        if (objectDepth == 0) {
                            throw IllegalStateException("Server returned an invalid JSON array")
                        }
                        inString = true
                    }
                    '{' -> {
                        if (objectDepth == 0) objectStart = index
                        objectDepth++
                    }
                    '}' -> {
                        objectDepth--
                        if (objectDepth < 0 || objectStart < 0) {
                            throw IllegalStateException("Server returned an invalid JSON array")
                        }
                        if (objectDepth == 0) {
                            objects += jsonArray.substring(objectStart, index + 1)
                            objectStart = -1
                        }
                    }
                    '[', ']', ',', ' ', '\t', '\r', '\n' -> Unit
                    else -> {
                        if (objectDepth == 0) {
                            throw IllegalStateException("Server returned an invalid JSON array")
                        }
                    }
                }
            }
        }
        if (inString || escaped || objectDepth != 0 || objectStart >= 0) {
            throw IllegalStateException("Server returned an invalid JSON array")
        }
        return objects
    }

    private fun jsonString(json: String, key: String): String =
        jsonStringOrNull(json, key)
            ?: throw IllegalStateException("Server record is missing $key")

    private fun jsonStringOrNull(json: String, key: String): String? =
        Regex(""""${Regex.escape(key)}"\s*:\s*"((?:\\.|[^"])*)"""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.jsonUnescape()

    private fun jsonLong(json: String, key: String): Long =
        jsonLongOrNull(json, key)
            ?: throw IllegalStateException("Server record is missing $key")

    private fun jsonLongOrNull(json: String, key: String): Long? =
        Regex(""""${Regex.escape(key)}"\s*:\s*(\d+)""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toLong()

    private fun jsonBoolean(json: String, key: String): Boolean =
        jsonBooleanOrNull(json, key)
            ?: throw IllegalStateException("Server record is missing $key")

    private fun jsonBooleanOrNull(json: String, key: String): Boolean? =
        Regex(""""${Regex.escape(key)}"\s*:\s*(true|false)""")
            .find(json)
            ?.groupValues
            ?.get(1)
            ?.toBooleanStrict()

    private fun jsonInstant(json: String, key: String): Instant =
        jsonInstantOrNull(json, key)
            ?: throw IllegalStateException("Server record is missing $key")

    private fun jsonInstantOrNull(json: String, key: String): Instant? =
        jsonStringOrNull(json, key)?.let { value ->
            try {
                Instant.parse(value)
            } catch (_: RuntimeException) {
                throw IllegalStateException("Server record has invalid $key")
            }
        }

    private fun String.pathSegment(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8).replace("+", "%20")

    private fun String.json(): String =
        buildString(length) {
            for (character in this@json) {
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000c' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (character.code < 0x20) {
                            append("\\u")
                            append(character.code.toString(16).padStart(4, '0'))
                        } else {
                            append(character)
                        }
                    }
                }
            }
        }

    private fun String.jsonUnescape(): String =
        buildString(length) {
            var index = 0
            while (index < this@jsonUnescape.length) {
                val character = this@jsonUnescape[index++]
                if (character != '\\') {
                    append(character)
                    continue
                }
                if (index >= this@jsonUnescape.length) {
                    throw IllegalStateException("Server returned an invalid JSON string")
                }
                when (val escaped = this@jsonUnescape[index++]) {
                    '"', '\\', '/' -> append(escaped)
                    'b' -> append('\b')
                    'f' -> append('\u000c')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'u' -> {
                        if (index + 4 > this@jsonUnescape.length) {
                            throw IllegalStateException("Server returned an invalid JSON string")
                        }
                        var value = 0
                        repeat(4) {
                            val digit = this@jsonUnescape[index++].digitToIntOrNull(16)
                                ?: throw IllegalStateException("Server returned an invalid JSON string")
                            value = value * 16 + digit
                        }
                        append(value.toChar())
                    }
                    else -> throw IllegalStateException("Server returned an invalid JSON string")
                }
            }
        }
}

internal data class ServerExchange(val statusCode: Int, val body: String)
