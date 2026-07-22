package top.focess.keystead.client

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SecureStorageCodecTest {
    private val key = SecureStorageKey("keystead", "device-1", "refresh-token")

    @Test
    fun roundTripsEntries() {
        val encoded = SecureStorageCodec.encode(mapOf(key to byteArrayOf(1, 2, 3)))
        val decoded = SecureStorageCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertContentEquals(byteArrayOf(1, 2, 3), decoded[key])
    }

    @Test
    fun decodeRejectsEntryCountAboveMaximum() {
        assertCorrupt(packet { it.writeInt(4097) })
    }

    @Test
    fun decodeRejectsNegativeEntryCount() {
        assertCorrupt(packet { it.writeInt(-1) })
    }

    @Test
    fun decodeRejectsTrailingBytesAfterEntries() {
        assertCorrupt(packet {
            it.writeInt(0)
            it.writeByte(0)
        })
    }

    @Test
    fun decodeRejectsValueLengthAboveMaximum() {
        assertCorrupt(packet {
            it.writeInt(1)
            writeText(it, "a")
            writeText(it, "b")
            writeText(it, "c")
            it.writeInt(1024 * 1024 + 1)
        })
    }

    @Test
    fun decodeRejectsDuplicateKeys() {
        assertCorrupt(packet {
            it.writeInt(2)
            writeEntry(it, "a", "b", "c", byteArrayOf(0))
            writeEntry(it, "a", "b", "c", byteArrayOf(0))
        })
    }

    @Test
    fun decodeRejectsKeySegmentLengthAboveMaximum() {
        assertCorrupt(packet {
            it.writeInt(1)
            it.writeInt(513)
        })
    }

    @Test
    fun decodeRejectsZeroKeySegmentLength() {
        assertCorrupt(packet {
            it.writeInt(1)
            it.writeInt(0)
        })
    }

    @Test
    fun decodeRejectsMalformedUtf8KeySegment() {
        assertCorrupt(packet {
            it.writeInt(1)
            it.writeInt(1)
            it.writeByte(0xFF)
        })
    }

    @Test
    fun encodeRejectsTooManyEntries() {
        val values = (1..4097).associate {
            SecureStorageKey("ns", "acct", "n$it") to byteArrayOf(0)
        }
        assertFailsWith<IllegalArgumentException> { SecureStorageCodec.encode(values) }
    }

    @Test
    fun encodeRejectsOversizedValue() {
        assertFailsWith<IllegalArgumentException> {
            SecureStorageCodec.encode(mapOf(key to ByteArray(1024 * 1024 + 1)))
        }
    }

    private fun assertCorrupt(bytes: ByteArray) {
        val failure = assertFailsWith<OsSecretStoreException> { SecureStorageCodec.decode(bytes) }
        assertEquals(OsSecretStoreFailure.CORRUPT, failure.failure)
    }

    private fun packet(write: (DataOutputStream) -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use(write)
        return out.toByteArray()
    }

    private fun writeEntry(
        data: DataOutputStream,
        namespace: String,
        account: String,
        name: String,
        value: ByteArray,
    ) {
        writeText(data, namespace)
        writeText(data, account)
        writeText(data, name)
        data.writeInt(value.size)
        data.write(value)
    }

    private fun writeText(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        data.writeInt(bytes.size)
        data.write(bytes)
    }
}
