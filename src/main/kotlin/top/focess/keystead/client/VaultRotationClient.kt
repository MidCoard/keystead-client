package top.focess.keystead.client

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant

class VaultRotationClient(private val client: KeysteadServerClient) {
    fun listMemberships(): List<ServerVaultMembership> =
        array(client.exchange("GET", listOf("api", "v1", "vaults")), membershipFields).map { value ->
            ServerVaultMembership(
                value.string("vaultId"), value.string("ownerId"), value.string("encryptedMetadata"), value.string("role"),
                ServerVaultMemberState.valueOf(value.string("membershipState")), value.nullableString("currentVaultKeyId"),
                ServerVaultKeyLifecycleState.valueOf(value.string("keyLifecycleState")), value.long("lifecycleVersion"),
            )
        }

    fun accept(vaultId: String) { success(client.exchange("POST", listOf("api", "v1", "vaults", vaultId, "members", "accept"))) }
    fun decline(vaultId: String) { success(client.exchange("POST", listOf("api", "v1", "vaults", vaultId, "members", "decline"))) }

    fun listMembers(vaultId: String): List<ServerVaultMember> =
        array(client.exchange("GET", listOf("api", "v1", "vaults", vaultId, "members")), memberFields).map { value ->
            ServerVaultMember(value.string("vaultId"), value.string("userId"), value.string("role"), ServerVaultMemberState.valueOf(value.string("state")), Instant.parse(value.string("createdAt")), Instant.parse(value.string("updatedAt")))
        }

    fun invite(vaultId: String, userId: String, role: String) = memberWrite("PUT", vaultId, userId, null, role)
    fun changeRole(vaultId: String, userId: String, role: String) = memberWrite("PUT", vaultId, userId, "role", role)
    fun remove(vaultId: String, userId: String) { success(client.exchange("DELETE", listOf("api", "v1", "vaults", vaultId, "members", userId))) }

    fun packageRecipients(vaultId: String): List<ServerVaultRecipientDevice> {
        val coverage = obj(
            success(client.exchange("GET", listOf("api", "v1", "vaults", vaultId, "package-recipients"))),
            coverageFields,
        )
        coverage.nullableString("currentVaultKeyId")
        runCatching { ServerVaultKeyLifecycleState.valueOf(coverage.string("keyLifecycleState")) }
            .getOrElse { invalid() }
        coverage.long("lifecycleVersion")
        val devices = coverage.get("devices")?.takeIf(JsonElement::isJsonArray)?.asJsonArray ?: invalid()
        return devices.map { element ->
            val value = strict(element, recipientFields)
            ServerVaultRecipientDevice(value.string("userId"), value.string("role"), ServerVaultMemberState.valueOf(value.string("memberState")), value.string("deviceId"), value.string("keyAlgorithm"), value.string("publicKey"), value.boolean("covered"))
        }
    }

    fun begin(vaultId: String, expectedKeyId: String, targetKeyId: String, lifecycleVersion: Long, selectedPendingUsers: Set<String> = emptySet()): ServerVaultRotation {
        val users = selectedPendingUsers.sorted().joinToString(",") { "\"${it.clientJson()}\"" }
        return rotation(success(client.exchange("POST", listOf("api", "v1", "vaults", vaultId, "rotations"), body = "{\"expectedCurrentVaultKeyId\":\"${expectedKeyId.clientJson()}\",\"targetVaultKeyId\":\"${targetKeyId.clientJson()}\",\"expectedLifecycleVersion\":$lifecycleVersion,\"selectedPendingUsers\":[$users]}")))
    }

    fun status(vaultId: String, generationId: String): ServerVaultRotation = rotation(success(client.exchange("GET", listOf("api", "v1", "vaults", vaultId, "rotations", generationId))))

    fun upload(vaultId: String, generationId: String, target: ServerVaultRotationTarget, vaultKeyId: String, encryptedVaultKey: String): ServerVaultRotation {
        val body = JsonObject().apply {
            addProperty("vaultKeyId", vaultKeyId); addProperty("targetType", target.targetType.name)
            nullable("recipientId", target.recipientId); nullable("deviceId", target.deviceId); nullable("principalId", target.principalId); nullable("enrollmentId", target.enrollmentId)
            if (target.recoveryGeneration == null) add("recoveryGeneration", com.google.gson.JsonNull.INSTANCE) else addProperty("recoveryGeneration", target.recoveryGeneration)
            addProperty("keyAlgorithm", target.keyAlgorithm); addProperty("encryptedVaultKey", encryptedVaultKey)
        }
        return rotation(success(client.exchange("PUT", listOf("api", "v1", "vaults", vaultId, "rotations", generationId, "targets", target.targetId, "package"), body = body.toString())))
    }

    fun selfPackage(vaultId: String, generationId: String, deviceId: String): ServerRotationPackage {
        val value = obj(success(client.exchange("GET", listOf("api", "v1", "vaults", vaultId, "rotations", generationId, "self-package"), query = "deviceId=${clientQuery(deviceId)}")), packageFields)
        return ServerRotationPackage(value.string("targetId"), value.string("vaultKeyId"), value.string("keyAlgorithm"), value.string("encryptedVaultKey"))
    }

    fun cancel(vaultId: String, generationId: String) { success(client.exchange("DELETE", listOf("api", "v1", "vaults", vaultId, "rotations", generationId))) }
    fun commit(vaultId: String, generationId: String): ServerVaultRotation = rotation(success(client.exchange("POST", listOf("api", "v1", "vaults", vaultId, "rotations", generationId, "commit"))))

    private fun memberWrite(method: String, vaultId: String, userId: String, suffix: String?, role: String) {
        val path = mutableListOf("api", "v1", "vaults", vaultId, "members", userId); suffix?.let(path::add)
        success(client.exchange(method, path, body = "{\"role\":\"${role.clientJson()}\"}"))
    }

    private fun rotation(body: String): ServerVaultRotation {
        val value = obj(body, rotationFields)
        val targets = value.getAsJsonArray("targets") ?: error("Server rotation is missing targets")
        return ServerVaultRotation(
            value.string("generationId"), value.string("vaultId"), value.string("sourceVaultKeyId"), value.string("targetVaultKeyId"),
            ServerVaultRotationState.valueOf(value.string("state")), value.long("lifecycleVersion"),
            targets.map { element ->
                val target = strict(element, targetFields)
                ServerVaultRotationTarget(target.string("targetId"), ServerVaultRotationTargetType.valueOf(target.string("targetType")), target.nullableString("recipientId"), target.nullableString("deviceId"), target.nullableString("principalId"), target.nullableString("enrollmentId"), target.nullableLong("recoveryGeneration"), target.string("keyAlgorithm"), target.string("publicKey"), target.boolean("required"), target.boolean("covered"))
            },
        )
    }

    private fun success(exchange: ServerExchange): String {
        if (exchange.statusCode in 200..299) return exchange.body
        if (exchange.statusCode == 409) {
            val lifecycle = runCatching { obj(exchange.body, setOf("lifecycleState")).nullableString("lifecycleState")?.let(ServerVaultKeyLifecycleState::valueOf) }.getOrNull()
            throw KeysteadVaultLifecycleConflictException(lifecycle)
        }
        if (exchange.statusCode == 401 || exchange.statusCode == 403) throw KeysteadAuthenticationException(exchange.statusCode)
        throw KeysteadServerException(exchange.statusCode, "Keystead Server returned HTTP ${exchange.statusCode}")
    }

    private fun array(exchange: ServerExchange, fields: Set<String>): List<JsonObject> = array(success(exchange), fields)
    private fun array(body: String, fields: Set<String>): List<JsonObject> { val parsed = parse(body); if (!parsed.isJsonArray) invalid(); return parsed.asJsonArray.map { strict(it, fields) } }
    private fun obj(body: String, fields: Set<String>) = strict(parse(body), fields)
    private fun parse(body: String): JsonElement = try { JsonParser.parseString(body) } catch (_: RuntimeException) { invalid() }
    private fun strict(element: JsonElement, fields: Set<String>): JsonObject { if (!element.isJsonObject) invalid(); val value = element.asJsonObject; if (value.keySet() != fields) invalid(); return value }
    private fun JsonObject.string(name: String): String = get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: invalid()
    private fun JsonObject.nullableString(name: String): String? = get(name)?.takeUnless(JsonElement::isJsonNull)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
    private fun JsonObject.long(name: String): Long = get(name)?.takeIf(JsonElement::isJsonPrimitive)?.runCatching { asLong }?.getOrNull() ?: invalid()
    private fun JsonObject.nullableLong(name: String): Long? = get(name)?.takeUnless(JsonElement::isJsonNull)?.runCatching { asLong }?.getOrNull()
    private fun JsonObject.boolean(name: String): Boolean = get(name)?.takeIf(JsonElement::isJsonPrimitive)?.runCatching { asBoolean }?.getOrNull() ?: invalid()
    private fun JsonObject.nullable(name: String, value: String?) { if (value == null) add(name, com.google.gson.JsonNull.INSTANCE) else addProperty(name, value) }
    private fun invalid(): Nothing = throw IllegalStateException("Server returned invalid collaboration JSON")

    private companion object {
        val membershipFields = setOf("vaultId", "ownerId", "encryptedMetadata", "role", "membershipState", "currentVaultKeyId", "keyLifecycleState", "lifecycleVersion")
        val memberFields = setOf("vaultId", "userId", "role", "state", "createdAt", "updatedAt")
        val recipientFields = setOf("userId", "role", "memberState", "deviceId", "keyAlgorithm", "publicKey", "covered")
        val coverageFields = setOf("currentVaultKeyId", "keyLifecycleState", "lifecycleVersion", "devices")
        val rotationFields = setOf("generationId", "vaultId", "sourceVaultKeyId", "targetVaultKeyId", "state", "lifecycleVersion", "targets")
        val targetFields = setOf("targetId", "targetType", "recipientId", "deviceId", "principalId", "enrollmentId", "recoveryGeneration", "keyAlgorithm", "publicKey", "required", "covered")
        val packageFields = setOf("targetId", "vaultKeyId", "keyAlgorithm", "encryptedVaultKey")
    }
}

private fun String.clientJson(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")
private fun clientQuery(value: String) = java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")
