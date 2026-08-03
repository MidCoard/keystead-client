package top.focess.keystead.client

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

internal fun writeFully(
    channel: WritableByteChannel,
    encoded: ByteArray,
) {
    val buffer = ByteBuffer.wrap(encoded)
    while (buffer.hasRemaining()) {
        if (channel.write(buffer) <= 0) {
            throw IOException("Channel made no progress while writing secure data")
        }
    }
}
