package top.focess.keystead.client

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

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
        get() = runCatching { Toolkit.getDefaultToolkit().systemClipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String }.getOrNull()
        set(value) { runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(value.orEmpty()), null) } }
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
        if (clipboard.text == null || digest(clipboard.text!!) != ticket.digest) return false
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
