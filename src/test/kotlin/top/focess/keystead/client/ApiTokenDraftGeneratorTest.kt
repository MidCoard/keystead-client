package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiTokenDraftGeneratorTest {
    @Test
    fun generatedDraftPopulatesApiTokenField() {
        val draft = ApiTokenDraftGenerator.generate(prefix = "ghp")
        val token = draft.fields.getValue("token")

        assertEquals("github.com", draft.software)
        assertTrue(token.startsWith("ghp_"))
        assertEquals(47, token.length)
        assertTrue(token.matches(Regex("ghp_[A-Za-z0-9_-]+")))
        assertFalse(token.contains("="))
    }

    @Test
    fun generatedDraftToStringRedactsToken() {
        val draft = ApiTokenDraftGenerator.generate(prefix = "ghp")
        val diagnostic = draft.toString()

        assertFalse(diagnostic.contains(draft.fields.getValue("token")), "diagnostic leaked token")
        assertEquals("ApiTokenDraft(<redacted>)", diagnostic)
    }
}
