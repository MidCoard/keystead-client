package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class RetryingClipboardTest {

    @Test
    fun retriesWhenTheSystemClipboardIsBusy() = runBlocking {
        var attempts = 0
        val clipboard =
            RetryingClipboard(
                FakeClipboard {
                    attempts++
                    if (attempts < 3) {
                        throw IllegalStateException("Cannot open system clipboard")
                    }
                },
            )

        clipboard.setClipEntry(null)

        assertEquals(3, attempts)
    }

    @Test
    fun givesUpQuietlyWhenTheClipboardStaysBusy() = runBlocking {
        val clipboard =
            RetryingClipboard(
                FakeClipboard { throw IllegalStateException("Cannot open system clipboard") },
            )

        // Must not throw; a failed copy is better than a crashed app.
        assertNull(clipboard.getClipEntry())
        clipboard.setClipEntry(null)
    }

    private class FakeClipboard(private val action: () -> Unit) :
        androidx.compose.ui.platform.Clipboard {
        override val nativeClipboard: java.awt.datatransfer.Clipboard =
            java.awt.datatransfer.Clipboard("test")

        override suspend fun getClipEntry(): androidx.compose.ui.platform.ClipEntry? {
            action()
            return null
        }

        override suspend fun setClipEntry(clipEntry: androidx.compose.ui.platform.ClipEntry?) {
            action()
        }
    }
}
