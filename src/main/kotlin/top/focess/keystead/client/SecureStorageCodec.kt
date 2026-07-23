package top.focess.keystead.client

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

internal object SecureStorageCodec {
    private const val MAX_ENTRIES = 4096
    private const val MAX_TEXT_BYTES = 512
    internal const val MAX_VALUE_BYTES = 1024 * 1024

    fun encode(values: Map<SecureStorageKey, ByteArray>): ByteArray {
        require(values.size <= MAX_ENTRIES) { "Secure storage has too many entries" }
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeInt(values.size)
            values.entries.sortedBy { "${it.key.namespace}\u0000${it.key.account}\u0000${it.key.name}" }.forEach { (key, value) ->
                writeText(data, key.namespace)
                writeText(data, key.account)
                writeText(data, key.name)
                require(value.size <= MAX_VALUE_BYTES) { "Secure storage value is too large" }
                data.writeInt(value.size)
                data.write(value)
            }
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): LinkedHashMap<SecureStorageKey, ByteArray> {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val count = data.readInt()
                if (count !in 0..MAX_ENTRIES) corrupt()
                val result = linkedMapOf<SecureStorageKey, ByteArray>()
                repeat(count) {
                    val key = SecureStorageKey(readText(data), readText(data), readText(data))
                    val length = data.readInt()
                    if (length !in 0..MAX_VALUE_BYTES) corrupt()
                    val value = ByteArray(length)
                    data.readFully(value)
                    if (result.put(key, value) != null) corrupt()
                }
                if (data.read() != -1) corrupt()
                return result
            }
        } catch (error: OsSecretStoreException) {
            throw error
        } catch (error: RuntimeException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-map-invalid", error)
        } catch (error: java.io.IOException) {
            throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-map-invalid", error)
        }
    }

    private fun writeText(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size in 1..MAX_TEXT_BYTES) { "Secure storage key is too large" }
        data.writeInt(bytes.size)
        data.write(bytes)
    }

    private fun readText(data: DataInputStream): String {
        val length = data.readInt()
        if (length !in 1..MAX_TEXT_BYTES) corrupt()
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return StandardCharsets.UTF_8.newDecoder().run {
            onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        }
    }

    private fun corrupt(): Nothing =
        throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, "native-map-invalid")
}
