package top.focess.keystead.client

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaintextLifecycleTest {
    @Test fun revealIsSelectionBoundAndExpiresAtBoundary() {
        val model = RevealLifecycle(durationSeconds = 30)
        val t = Instant.parse("2026-01-01T00:00:00Z")
        model.reveal("one", "secret", t)
        assertEquals("secret", model.value)
        assertFalse(model.expire(t.plusSeconds(29)))
        assertTrue(model.expire(t.plusSeconds(30)))
        assertNull(model.value)
    }

    @Test fun staleGenerationCannotClearRereveal() {
        val model = RevealLifecycle(durationSeconds = 30)
        val t = Instant.parse("2026-01-01T00:00:00Z")
        val first = model.reveal("one", "old", t)
        model.reveal("one", "new", t.plusSeconds(1))
        assertFalse(model.expire(t.plusSeconds(30), first))
        assertEquals("new", model.value)
        model.clear()
        assertNull(model.value)
    }

    @Test fun clipboardTicketClearsOnlyMatchingClipboard() {
        val clipboard = MemoryClipboard()
        val model = ClipboardLifecycle(clipboard, durationSeconds = 10)
        val t = Instant.parse("2026-01-01T00:00:00Z")
        val ticket = model.copy("value", t)
        assertEquals("value", clipboard.text)
        assertFalse(model.expire(t.plusSeconds(9), ticket))
        clipboard.text = "user replacement"
        assertFalse(model.expire(t.plusSeconds(10), ticket))
        assertEquals("user replacement", clipboard.text)
    }

    @Test fun formLifecycleClearsOnlySuccessfulSaveAndActions() {
        val form = PlaintextFormState("title", "password", mapOf("token" to "x"))
        assertEquals(PlaintextFormState("", "", emptyMap()), form.afterSave(true))
        assertEquals(form, form.afterSave(false))
        assertEquals(PlaintextFormState("", "", emptyMap()), form.clear())
    }

    private class MemoryClipboard : ClipboardPort {
        override var text: String? = null
    }
}
