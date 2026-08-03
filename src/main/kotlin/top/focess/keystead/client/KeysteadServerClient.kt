package top.focess.keystead.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

internal fun interface ServerAuthorization {
    fun headerValue(): String
}

private class BasicServerAuthorization(username: String, password: String) : ServerAuthorization {
    private val value =
        "Basic " +
            Base64.getEncoder().encodeToString(
                "$username:$password".toByteArray(StandardCharsets.UTF_8),
            )

    override fun headerValue(): String = value

    override fun toString(): String = "BasicServerAuthorization(<redacted>)"
}

data class ServerMintedShare(val code: String, val expiresAt: Instant)

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

class KeysteadShareNotFoundException :
    KeysteadServerException(404, "No share found for that code.")

class KeysteadShareExpiredException :
    KeysteadServerException(410, "This share has expired or has already been redeemed.")

class KeysteadShareRateLimitedException(val retryAfterSeconds: Long?) :
    KeysteadServerException(
        429,
        retryAfterSeconds?.let {
            "Too many share attempts. Wait $it second(s) and try again."
        } ?: "Too many share attempts. Wait a minute and try again.",
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
        fun forPublicRedeem(
            baseUrl: String,
            http: HttpClient = HttpClient.newHttpClient(),
        ): KeysteadServerClient = KeysteadServerClient(baseUrl, PublicRedeemAuthorization, http)
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
        val authorizationValue = authorization.headerValue().takeIf(String::isNotEmpty)
        return exchangeWithHeader(method, segments, query, body, authorizationValue)
    }

    internal fun publicExchange(
        method: String,
        segments: List<String>,
        query: String? = null,
        body: String? = null,
    ): ServerExchange = exchangeWithHeader(method, segments, query, body, null)

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
        val publisher =
            body?.let(HttpRequest.BodyPublishers::ofString)
                ?: HttpRequest.BodyPublishers.noBody()
        val response =
            http.send(
                builder.method(method, publisher).build(),
                HttpResponse.BodyHandlers.ofString(),
            )
        val retryAfter = response.headers().firstValueAsLong("Retry-After")
        return ServerExchange(
            response.statusCode(),
            response.body(),
            if (retryAfter.isPresent) retryAfter.asLong else null,
        )
    }

    fun mintShare(
        payload: String,
        expiresAt: Instant?,
        burnAfterReading: Boolean?,
    ): ServerMintedShare {
        val body =
            JsonObject().apply {
                addProperty("payload", payload)
                expiresAt?.let { addProperty("expiresAt", it.toString()) }
                burnAfterReading?.let { addProperty("burnAfterReading", it) }
            }.toString()
        val response = exchange("POST", listOf("api", "v1", "shares"), body = body)
        requireShareSuccess(response.statusCode, response.retryAfterSeconds)
        val value = jsonObject(response.body)
        return ServerMintedShare(
            code = value.requiredString("code"),
            expiresAt = Instant.parse(value.requiredString("expiresAt")),
        )
    }

    fun redeemShare(code: String): String {
        val response = publicExchange("GET", listOf("api", "v1", "shares", code))
        requireShareSuccess(response.statusCode, response.retryAfterSeconds)
        return jsonObject(response.body).requiredString("payload")
    }

    fun listShares(): List<ServerShareSummary> {
        val response = exchange("GET", listOf("api", "v1", "shares"))
        requireShareSuccess(response.statusCode, response.retryAfterSeconds)
        val value = JsonParser.parseString(response.body)
        if (!value.isJsonArray) throw IllegalStateException("Server returned invalid share JSON")
        return value.asJsonArray.map { element ->
            val item = jsonObject(element.toString())
            ServerShareSummary(
                code = item.requiredString("code"),
                createdAt = Instant.parse(item.requiredString("createdAt")),
                expiresAt = Instant.parse(item.requiredString("expiresAt")),
                burnAfterReading = item.requiredBoolean("burnAfterReading"),
            )
        }
    }

    fun deleteShare(code: String) {
        val response = exchange("DELETE", listOf("api", "v1", "shares", code))
        requireShareSuccess(response.statusCode, response.retryAfterSeconds)
    }

    private fun requireShareSuccess(statusCode: Int, retryAfterSeconds: Long?) {
        if (statusCode in 200..299) return
        when (statusCode) {
            404 -> throw KeysteadShareNotFoundException()
            410 -> throw KeysteadShareExpiredException()
            429 -> throw KeysteadShareRateLimitedException(retryAfterSeconds)
            401, 403 -> throw KeysteadAuthenticationException(statusCode)
            else -> throw KeysteadServerException(statusCode, "Keystead Server returned HTTP $statusCode")
        }
    }

    private fun endpoint(vararg segments: String, query: String? = null): URI {
        val path = segments.joinToString(separator = "/", prefix = "/") { segment ->
            URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20")
        }
        return URI.create(root + path + if (query == null) "" else "?$query")
    }

    private fun jsonObject(body: String): JsonObject =
        try {
            JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalStateException("Server returned invalid share JSON")
        } catch (error: IllegalStateException) {
            throw error
        } catch (_: RuntimeException) {
            throw IllegalStateException("Server returned invalid share JSON")
        }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IllegalStateException("Server share is missing $name")

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
            ?: throw IllegalStateException("Server share is missing $name")
}

internal data class ServerExchange(
    val statusCode: Int,
    val body: String,
    val retryAfterSeconds: Long? = null,
)
