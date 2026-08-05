package top.focess.keystead.client

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard as ComposeClipboard
import kotlinx.coroutines.delay

/** UI-only reveal state; the value is never serialized or logged. */
class RevealLifecycle(private val durationSeconds: Long = 30) {
    var value: String? = null
        private set
    var selectedId: String? = null
        private set
    private var generation = 0L
    private var expiresAt: Instant? = null

    fun reveal(id: String, plaintext: String, now: Instant): Long {
        generation++
        selectedId = id
        value = plaintext
        expiresAt = now.plusSeconds(durationSeconds)
        return generation
    }

    fun expire(now: Instant, expectedGeneration: Long? = null): Boolean {
        if (expectedGeneration != null && expectedGeneration != generation) return false
        if (expiresAt == null || now.isBefore(expiresAt)) return false
        clear()
        return true
    }

    fun clear() {
        generation++
        value = null
        selectedId = null
        expiresAt = null
    }
}

interface ClipboardPort {
    var text: String?
}

class AwtClipboardPort : ClipboardPort {
    override var text: String?
        get() = withClipboardRetry { Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String }
        set(value) { withClipboardRetry { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value.orEmpty()), null) } }

    private fun <T> withClipboardRetry(action: () -> T): T? {
        repeat(5) { attempt ->
            try {
                return action()
            } catch (error: IllegalStateException) {
                // Windows reports "Cannot open system clipboard" while another process
                // holds it; retry briefly before giving up quietly.
                if (attempt == 4) return null
                Thread.sleep(60)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }
}

/**
 * Compose's desktop clipboard goes through the same Windows system clipboard and throws
 * IllegalStateException("Cannot open system clipboard") while another process holds it;
 * uncaught, that crashes the app on a plain text copy. Retry briefly, then give up quietly.
 */
class RetryingClipboard(private val delegate: ComposeClipboard) : ComposeClipboard {
    override val nativeClipboard: Any
        get() = delegate.nativeClipboard

    override suspend fun getClipEntry(): ClipEntry? = withClipboardRetry { delegate.getClipEntry() }

    override suspend fun setClipEntry(clipEntry: ClipEntry?) {
        withClipboardRetry { delegate.setClipEntry(clipEntry) }
    }

    private suspend fun <T> withClipboardRetry(action: suspend () -> T): T? {
        repeat(5) { attempt ->
            try {
                return action()
            } catch (error: IllegalStateException) {
                if (attempt == 4) return null
                delay(60)
            } catch (_: Exception) {
                return null
            }
        }
        return null
    }
}

data class ClipboardClearTicket(val digest: String, val expiresAt: Instant, val generation: Long)

class ClipboardLifecycle(private val clipboard: ClipboardPort, private val durationSeconds: Long = 30) {
    private var generation = 0L
    fun copy(value: String, now: Instant): ClipboardClearTicket {
        generation++
        clipboard.text = value
        return ClipboardClearTicket(digest(value), now.plusSeconds(durationSeconds), generation)
    }
    fun expire(now: Instant, ticket: ClipboardClearTicket): Boolean {
        if (ticket.generation != generation || now.isBefore(ticket.expiresAt)) return false
        val text = clipboard.text
        if (text == null || digest(text) != ticket.digest) return false
        clipboard.text = null
        return true
    }
    fun dispose(now: Instant, ticket: ClipboardClearTicket?) { if (ticket != null) expire(ticket.expiresAt, ticket) }
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class PlaintextFormState(val title: String, val password: String, val fields: Map<String, String>) {
    fun clear() = PlaintextFormState("", "", emptyMap())
    fun afterSave(success: Boolean) = if (success) clear() else this
}
