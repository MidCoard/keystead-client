package top.focess.keystead.client

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Instant

class KeysteadServerAuthClient(
    baseUrl: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val root = baseUrl.trimEnd('/')

    fun registerUser(username: String, password: CharArray) {
        val passwordCopy = password.copyOf()
        try {
            val body = credentialsBody(username, passwordCopy)
            val request =
                HttpRequest.newBuilder(URI.create("$root/api/v1/users"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            if (response.statusCode() == 409) {
                throw KeysteadAccountConflictException()
            }
            requireAuthSuccess(response.statusCode())
        } finally {
            passwordCopy.fill('\u0000')
            password.fill('\u0000')
        }
    }

    fun login(
        username: String,
        password: CharArray,
        deviceId: String? = null,
        tokenSink: ((refreshToken: String, refreshTokenExpiresAt: Instant) -> Unit)? = null,
        onRevoked: (() -> Unit)? = null,
    ): ServerAuthSession {
        val passwordCopy = password.copyOf()
        try {
            val body = credentialsBody(username, passwordCopy, deviceId)
            val tokens = sendForTokens("login", body)
            val session = ServerAuthSession(root, http, clock, tokens, tokenSink, onRevoked)
            session.persist(tokens)
            return session
        } finally {
            passwordCopy.fill('\u0000')
            password.fill('\u0000')
        }
    }

    private fun sendForTokens(path: String, body: String): ServerAuthTokens {
        val request =
            HttpRequest.newBuilder(URI.create("$root/api/v1/auth/$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireAuthSuccess(response.statusCode())
        return response.body().toServerAuthTokens()
    }

    /**
     * Rebuilds an authenticated session from a persisted refresh token by immediately
     * refreshing it. The server rotates the refresh token on every refresh, so the returned
     * session holds fresh tokens and the persisted token is updated via [tokenSink].
     *
     * Throws [KeysteadAuthenticationException] if the refresh token is expired or rejected
     * (the store is cleared via [onRevoked] first); propagates network errors without
     * clearing the store so the next launch can retry.
     */
    fun restore(
        refreshToken: String,
        refreshTokenExpiresAt: Instant,
        tokenSink: ((refreshToken: String, refreshTokenExpiresAt: Instant) -> Unit)? = null,
        onRevoked: (() -> Unit)? = null,
    ): ServerAuthSession {
        val seed =
            ServerAuthTokens(
                accessToken = "",
                refreshToken = refreshToken,
                accessTokenExpiresAt = Instant.EPOCH,
                refreshTokenExpiresAt = refreshTokenExpiresAt,
            )
        val session = ServerAuthSession(root, http, clock, seed, tokenSink, onRevoked)
        session.refresh()
        return session
    }
}

private fun credentialsBody(
    username: String,
    password: CharArray,
    deviceId: String? = null,
): String =
    buildString {
        append("{\"username\":\"")
        append(username.json())
        append("\",\"password\":\"")
        append(String(password).json())
        append('"')
        if (deviceId != null) {
            append(",\"deviceId\":\"")
            append(deviceId.json())
            append('"')
        }
        append('}')
    }

class ServerAuthSession internal constructor(
    private val root: String,
    private val http: HttpClient,
    private val clock: Clock,
    tokens: ServerAuthTokens,
    private val tokenSink: ((refreshToken: String, refreshTokenExpiresAt: Instant) -> Unit)? = null,
    private val onRevoked: (() -> Unit)? = null,
) : ServerAuthorization,
    AutoCloseable {
    private var tokens: ServerAuthTokens? = tokens

    val accessTokenExpiresAt: Instant
        @Synchronized get() = currentTokens().accessTokenExpiresAt

    val refreshTokenExpiresAt: Instant
        @Synchronized get() = currentTokens().refreshTokenExpiresAt

    @Synchronized
    fun client(): KeysteadServerClient {
        currentTokens()
        return KeysteadServerClient(root, this, http)
    }

    @Synchronized
    fun refresh() {
        val current = currentTokens()
        if (!clock.instant().isBefore(current.refreshTokenExpiresAt)) {
            onRevoked?.invoke()
            close()
            throw KeysteadAuthenticationException(401)
        }
        val updated =
            try {
                sendForTokens("refresh", refreshTokenBody(current.refreshToken))
            } catch (error: KeysteadAuthenticationException) {
                onRevoked?.invoke()
                close()
                throw error
            }
        tokens = updated
        persist(updated)
    }

    @Synchronized
    fun revoke() {
        val current = currentTokens()
        try {
            sendExpectingSuccess("revoke", refreshTokenBody(current.refreshToken))
        } finally {
            onRevoked?.invoke()
            close()
        }
    }

    @Synchronized
    fun logoutAll() {
        try {
            val request =
                HttpRequest.newBuilder(URI.create("$root/api/v1/auth/logout-all"))
                    .header("Authorization", headerValue())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            requireAuthSuccess(response.statusCode())
        } finally {
            onRevoked?.invoke()
            close()
        }
    }

    @Synchronized
    override fun headerValue(): String {
        var current = currentTokens()
        if (!clock.instant().isBefore(current.accessTokenExpiresAt)) {
            refresh()
            current = currentTokens()
        }
        return "Bearer ${current.accessToken}"
    }

    @Synchronized
    override fun close() {
        tokens = null
    }

    /**
     * Best-effort persistence of the supplied tokens' refresh token. A storage failure is
     * swallowed so it can never invalidate an otherwise-valid in-memory session; the
     * rotated refresh token simply will not survive the next launch.
     */
    internal fun persist(tokens: ServerAuthTokens) {
        val sink = tokenSink ?: return
        try {
            sink.invoke(tokens.refreshToken, tokens.refreshTokenExpiresAt)
        } catch (_: RuntimeException) {
        }
    }

    override fun toString(): String = "ServerAuthSession(<redacted>)"

    private fun currentTokens(): ServerAuthTokens =
        tokens ?: throw IllegalStateException("Server authentication session is closed")

    private fun sendForTokens(path: String, body: String): ServerAuthTokens {
        val request = authRequest(path, body)
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        requireAuthSuccess(response.statusCode())
        return response.body().toServerAuthTokens()
    }

    private fun sendExpectingSuccess(path: String, body: String) {
        val response = http.send(authRequest(path, body), HttpResponse.BodyHandlers.discarding())
        requireAuthSuccess(response.statusCode())
    }

    private fun authRequest(path: String, body: String): HttpRequest =
        HttpRequest.newBuilder(URI.create("$root/api/v1/auth/$path"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
}

internal class ServerAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
) {
    override fun toString(): String = "ServerAuthTokens(<redacted>)"
}

private fun String.toServerAuthTokens(): ServerAuthTokens {
    val accessToken = requiredJsonString("accessToken")
    val refreshToken = requiredJsonString("refreshToken")
    val accessTokenExpiresAt = requiredInstant("accessTokenExpiresAt")
    val refreshTokenExpiresAt = requiredInstant("refreshTokenExpiresAt")
    if (accessToken.isBlank() || refreshToken.isBlank()) {
        throw malformedAuthResponse()
    }
    return ServerAuthTokens(
        accessToken,
        refreshToken,
        accessTokenExpiresAt,
        refreshTokenExpiresAt,
    )
}

private fun String.requiredJsonString(key: String): String =
    Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
        .find(this)
        ?.groupValues
        ?.get(1)
        ?.jsonUnescape()
        ?: throw malformedAuthResponse()

private fun String.requiredInstant(key: String): Instant =
    try {
        Instant.parse(requiredJsonString(key))
    } catch (_: RuntimeException) {
        throw malformedAuthResponse()
    }

private fun refreshTokenBody(refreshToken: String): String =
    "{\"refreshToken\":\"${refreshToken.json()}\"}"

private fun requireAuthSuccess(statusCode: Int) {
    if (statusCode in 200..299) {
        return
    }
    if (statusCode == 401 || statusCode == 403) {
        throw KeysteadAuthenticationException(statusCode)
    }
    throw KeysteadServerException(statusCode, "Keystead Server returned HTTP $statusCode")
}

private fun malformedAuthResponse(): KeysteadServerException =
    KeysteadServerException(502, "Keystead Server returned a malformed authentication response")

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
                throw malformedAuthResponse()
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
                        throw malformedAuthResponse()
                    }
                    var value = 0
                    repeat(4) {
                        val digit = this@jsonUnescape[index++].digitToIntOrNull(16)
                            ?: throw malformedAuthResponse()
                        value = value * 16 + digit
                    }
                    append(value.toChar())
                }
                else -> throw malformedAuthResponse()
            }
        }
    }
