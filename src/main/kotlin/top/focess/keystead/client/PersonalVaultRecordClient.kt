package top.focess.keystead.client

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Instant
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncRecordEventId

data class PersonalVaultRecordEvent(
    val eventId: String,
    val fingerprint: String,
    val secretId: String,
    val revision: Long,
    val secretType: String,
    val encryptedProfile: String,
    val envelope: String,
    val deleted: Boolean,
    val contentKey: String,
)

data class PersonalVaultRecord(
    val serverSequence: Long,
    val eventId: String,
    val fingerprint: String,
    val secretId: String,
    val revision: Long,
    val secretType: String,
    val encryptedProfile: String,
    val envelope: String,
    val deleted: Boolean,
    val contentKey: String,
    val createdAt: Instant,
)

data class PersonalVaultRecordPage(
    val afterSequence: Long,
    val records: List<PersonalVaultRecord>,
    val highestSequence: Long,
    val hasMore: Boolean,
    val nextSequence: Long?,
)

data class PersonalVaultRecordDeletion(
    val secretId: String,
    val deletedEvents: Long,
)

class PersonalVaultMismatchException(
    val serverFingerprint: String,
    val localFingerprint: String,
) : KeysteadServerException(
        409,
        "The server records belong to another vault. Server: $serverFingerprint; local: $localFingerprint.",
    )

object PersonalRecordEventId {
    fun of(event: PersonalVaultRecordEvent): String =
        SyncRecordEventId.of(
            EncryptedSyncRecord(
                event.fingerprint,
                event.secretId,
                event.revision,
                event.secretType,
                event.encryptedProfile,
                event.envelope,
                event.deleted,
                event.contentKey,
            ),
        )
}

fun KeysteadServerClient.appendPersonalRecord(event: PersonalVaultRecordEvent): PersonalVaultRecord {
    val response =
        exchange(
            method = "POST",
            segments = listOf("api", "v1", "vault", "records"),
            body = event.toJson(),
        )
    requirePersonalRecordSuccess(response)
    return parsePersonalRecord(response.body)
}

fun KeysteadServerClient.listPersonalRecordPage(
    afterSequence: Long,
    limit: Int,
): PersonalVaultRecordPage {
    require(afterSequence >= 0) { "Server sequence must not be negative" }
    require(limit in 1..500) { "Personal record page limit is invalid" }
    val response =
        exchange(
            method = "GET",
            segments = listOf("api", "v1", "vault", "records"),
            query = "afterSequence=$afterSequence&limit=$limit",
        )
    requirePersonalRecordSuccess(response)
    return parsePersonalRecordPage(response.body)
}

fun KeysteadServerClient.listAllPersonalRecords(pageLimit: Int = 500): List<PersonalVaultRecord> {
    require(pageLimit in 1..500) { "Personal record page limit is invalid" }
    val records = mutableListOf<PersonalVaultRecord>()
    var cursor = 0L
    do {
        val page = listPersonalRecordPage(cursor, pageLimit)
        records += page.records
        if (page.hasMore) {
            val next = page.nextSequence
                ?: throw IllegalStateException("Server personal record page omitted its next sequence")
            if (next <= cursor) {
                throw IllegalStateException(
                    "Server personal record page did not advance from sequence $cursor",
                )
            }
            cursor = next
        }
    } while (page.hasMore)
    return records
}

fun KeysteadServerClient.deletePersonalRecordHistory(
    secretId: String,
): PersonalVaultRecordDeletion {
    require(secretId.isNotBlank()) { "Secret id must not be blank" }
    val response =
        exchange(
            method = "DELETE",
            segments = listOf("api", "v1", "vault", "records", secretId),
        )
    requirePersonalRecordSuccess(response)
    val value = personalRecordObject(response.body)
    return PersonalVaultRecordDeletion(
        secretId = value.string("secretId"),
        deletedEvents = value.long("deletedEvents"),
    )
}

private fun PersonalVaultRecordEvent.toJson(): String =
    "{\"eventId\":\"${eventId.personalRecordJson()}\",\"fingerprint\":\"${fingerprint.personalRecordJson()}\",\"secretId\":\"${secretId.personalRecordJson()}\",\"revision\":$revision,\"secretType\":\"${secretType.personalRecordJson()}\",\"encryptedProfile\":\"${encryptedProfile.personalRecordJson()}\",\"envelope\":\"${envelope.personalRecordJson()}\",\"deleted\":$deleted,\"contentKey\":\"${contentKey.personalRecordJson()}\"}"

private fun requirePersonalRecordSuccess(response: ServerExchange) {
    if (response.statusCode in 200..299) return
    if (response.statusCode == 401 || response.statusCode == 403) {
        throw KeysteadAuthenticationException(response.statusCode)
    }
    if (response.statusCode == 409) {
        val mismatch =
            runCatching { personalRecordObject(response.body) }
                .getOrNull()
                ?.takeIf { it.get("code")?.asString == "PERSONAL_VAULT_MISMATCH" }
        if (mismatch != null) {
            throw PersonalVaultMismatchException(
                serverFingerprint = mismatch.string("serverFingerprint"),
                localFingerprint = mismatch.string("submittedFingerprint"),
            )
        }
    }
    throw KeysteadServerException(
        response.statusCode,
        "Keystead Server returned HTTP ${response.statusCode}",
    )
}

private fun parsePersonalRecordPage(body: String): PersonalVaultRecordPage {
    val value = personalRecordObject(body)
    return try {
        val recordsValue = value.get("records")
        if (recordsValue == null || !recordsValue.isJsonArray) throw invalidPersonalRecordResponse()
        PersonalVaultRecordPage(
            afterSequence = value.long("afterSequence"),
            records = recordsValue.asJsonArray.map { parsePersonalRecord(it.toString()) },
            highestSequence = value.long("highestSequence"),
            hasMore = value.boolean("hasMore"),
            nextSequence = value.longOrNull("nextSequence"),
        )
    } catch (error: IllegalStateException) {
        throw error
    } catch (_: RuntimeException) {
        throw invalidPersonalRecordResponse()
    }
}

private fun parsePersonalRecord(body: String): PersonalVaultRecord {
    val value = personalRecordObject(body)
    return try {
        PersonalVaultRecord(
            serverSequence = value.long("serverSequence"),
            eventId = value.string("eventId"),
            fingerprint = value.string("fingerprint"),
            secretId = value.string("secretId"),
            revision = value.long("revision"),
            secretType = value.string("secretType"),
            encryptedProfile = value.string("encryptedProfile"),
            envelope = value.string("envelope"),
            deleted = value.boolean("deleted"),
            // Legacy pre-KVE2 events carry an empty content key; keep it as-is.
            contentKey = value.string("contentKey"),
            createdAt = Instant.parse(value.string("createdAt")),
        )
    } catch (error: IllegalStateException) {
        throw error
    } catch (_: RuntimeException) {
        throw invalidPersonalRecordResponse()
    }
}

private fun personalRecordObject(body: String): JsonObject =
    try {
        JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw invalidPersonalRecordResponse()
    } catch (error: IllegalStateException) {
        throw error
    } catch (_: RuntimeException) {
        throw invalidPersonalRecordResponse()
    }

private fun JsonObject.string(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?: throw invalidPersonalRecordResponse()

private fun JsonObject.long(name: String): Long =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
        ?: throw invalidPersonalRecordResponse()

private fun JsonObject.longOrNull(name: String): Long? =
    get(name)?.takeUnless { it.isJsonNull }
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asLong

private fun JsonObject.boolean(name: String): Boolean =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean
        ?: throw invalidPersonalRecordResponse()

private fun invalidPersonalRecordResponse() =
    IllegalStateException("Server returned invalid personal vault record JSON")

private fun String.personalRecordJson(): String =
    buildString(length) {
        for (character in this@personalRecordJson) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000c' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(character)
            }
        }
    }
