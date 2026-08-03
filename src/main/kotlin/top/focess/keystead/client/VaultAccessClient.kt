package top.focess.keystead.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import java.util.Base64

enum class ServerVaultAccessRequestState { PENDING, APPROVED, EXPIRED }

data class ServerVaultAccessRequest(
    val requestId: String,
    val accountId: String,
    val serverOrigin: String,
    val fingerprint: String,
    val keyAlgorithm: String,
    val exchangePublicKey: String,
    val state: ServerVaultAccessRequestState,
    val expiresAt: Instant,
    val canonicalRequest: String,
    val approvedPackage: VaultAccessKeyPackage?,
    val approvedAt: Instant?,
) {
    override fun toString() =
        "ServerVaultAccessRequest(requestId=$requestId, accountId=$accountId, serverOrigin=$serverOrigin, fingerprint=$fingerprint, keyAlgorithm=$keyAlgorithm, exchangePublicKey=<redacted>, state=$state, expiresAt=$expiresAt, canonicalRequest=<redacted>, approvedPackage=$approvedPackage, approvedAt=$approvedAt)"
}

data class VaultAccessKeyPackage(
    val fingerprint: String,
    val vaultKeyId: String,
    val keyAlgorithm: String,
    val encryptedVaultKey: String,
) {
    override fun toString() =
        "VaultAccessKeyPackage(fingerprint=$fingerprint, vaultKeyId=$vaultKeyId, keyAlgorithm=$keyAlgorithm, encryptedVaultKey=<redacted>)"
}

class VaultAccessClient(private val client: KeysteadServerClient) {
    fun create(session: EphemeralVaultAccessSession): ServerVaultAccessRequest {
        val publicKey = Base64.getEncoder().encodeToString(session.publicKey)
        return request(
            success(
                client.exchange(
                    "POST",
                    listOf("api", "v1", "vault-access-requests"),
                    body =
                        "{\"requestId\":\"${session.requestId.vaultAccessJson()}\",\"serverOrigin\":\"${session.serverOrigin.vaultAccessJson()}\",\"keyAlgorithm\":\"${session.keyAlgorithm.vaultAccessJson()}\",\"exchangePublicKey\":\"${publicKey.vaultAccessJson()}\"}",
                ),
            ),
        )
    }

    fun get(requestId: String): ServerVaultAccessRequest =
        request(
            success(
                client.exchange(
                    "GET",
                    listOf("api", "v1", "vault-access-requests", requestId),
                ),
            ),
        )

    fun listPending(): List<ServerVaultAccessRequest> {
        val body =
            success(
                client.exchange(
                    "GET",
                    listOf("api", "v1", "vault-access-requests"),
                ),
            )
        return try {
            val value = JsonParser.parseString(body)
            if (!value.isJsonArray) throw invalidResponse()
            value.asJsonArray.map { request(it.toString()) }
        } catch (error: IllegalStateException) {
            throw error
        } catch (_: RuntimeException) {
            throw invalidResponse()
        }
    }

    fun approve(requestId: String, packageValue: VaultAccessKeyPackage) {
        success(
            client.exchange(
                "POST",
                listOf("api", "v1", "vault-access-requests", requestId, "approve"),
                body =
                    "{\"vaultFingerprint\":\"${packageValue.fingerprint.vaultAccessJson()}\",\"vaultKeyId\":\"${packageValue.vaultKeyId.vaultAccessJson()}\",\"keyAlgorithm\":\"${packageValue.keyAlgorithm.vaultAccessJson()}\",\"encryptedVaultKey\":\"${packageValue.encryptedVaultKey.vaultAccessJson()}\"}",
            ),
        )
    }

    private fun request(body: String): ServerVaultAccessRequest {
        val value = objectValue(body)
        return try {
            ServerVaultAccessRequest(
                requestId = value.string("requestId"),
                accountId = value.string("accountId"),
                serverOrigin = value.string("serverOrigin"),
                fingerprint = value.string("fingerprint"),
                keyAlgorithm = value.string("keyAlgorithm"),
                exchangePublicKey = value.string("exchangePublicKey"),
                state = ServerVaultAccessRequestState.valueOf(value.string("state")),
                expiresAt = Instant.parse(value.string("expiresAt")),
                canonicalRequest = value.string("canonicalRequest"),
                approvedPackage = value.objectOrNull("approvedPackage")?.let(::keyPackage),
                approvedAt = value.stringOrNull("approvedAt")?.let(Instant::parse),
            )
        } catch (_: RuntimeException) {
            throw invalidResponse()
        }
    }

    private fun keyPackage(value: JsonObject): VaultAccessKeyPackage =
        VaultAccessKeyPackage(
            fingerprint = value.string("vaultFingerprint"),
            vaultKeyId = value.string("vaultKeyId"),
            keyAlgorithm = value.string("keyAlgorithm"),
            encryptedVaultKey = value.string("encryptedVaultKey"),
        )

    private fun success(value: ServerExchange): String {
        if (value.statusCode in 200..299) return value.body
        if (value.statusCode == 401 || value.statusCode == 403) {
            throw KeysteadAuthenticationException(value.statusCode)
        }
        throw KeysteadServerException(
            value.statusCode,
            "Keystead Server returned HTTP ${value.statusCode}",
        )
    }

    private fun objectValue(body: String): JsonObject =
        try {
            val value = JsonParser.parseString(body)
            if (!value.isJsonObject) throw invalidResponse()
            value.asJsonObject
        } catch (error: IllegalStateException) {
            throw error
        } catch (_: RuntimeException) {
            throw invalidResponse()
        }

    private fun JsonObject.string(name: String): String =
        get(name)?.takeIf { it.isJsonPrimitive }?.asString
            ?: throw invalidResponse()

    private fun JsonObject.stringOrNull(name: String): String? =
        get(name)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString

    private fun JsonObject.objectOrNull(name: String): JsonObject? =
        get(name)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonObject }?.asJsonObject

    private fun invalidResponse() =
        IllegalStateException("Server returned invalid vault access JSON")
}

private fun String.vaultAccessJson() =
    replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
