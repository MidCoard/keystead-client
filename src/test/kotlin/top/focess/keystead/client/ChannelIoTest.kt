package top.focess.keystead.client

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ChannelIoTest {
    @Test
    fun writeFullyRetriesShortWritesUntilEveryByteIsWritten() {
        val channel = ShortWriteChannel(maximumBytesPerWrite = 2)

        writeFully(channel, byteArrayOf(1, 2, 3, 4, 5))

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), channel.bytes())
    }

    @Test
    fun writeFullyRejectsAChannelThatMakesNoProgress() {
        assertFailsWith<IOException> {
            writeFully(ShortWriteChannel(maximumBytesPerWrite = 0), byteArrayOf(1))
        }
    }

    private class ShortWriteChannel(
        private val maximumBytesPerWrite: Int,
    ) : WritableByteChannel {
        private val output = ByteArrayOutputStream()
        private var open = true

        override fun write(source: ByteBuffer): Int {
            val count = minOf(maximumBytesPerWrite, source.remaining())
            repeat(count) { output.write(source.get().toInt()) }
            return count
        }

        override fun isOpen(): Boolean = open

        override fun close() {
            open = false
        }

        fun bytes(): ByteArray = output.toByteArray()
    }
}
