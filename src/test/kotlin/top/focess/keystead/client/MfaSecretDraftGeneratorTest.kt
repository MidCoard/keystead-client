package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MfaSecretDraftGeneratorTest {
    @Test
    fun generatedDraftPopulatesMfaFormFields() {
        val draft =
            MfaSecretDraftGenerator.generate(
                issuer = "Keystead",
                accountName = "alice@example.com",
            )

        val seed = draft.fields.getValue("seed")
        val uri = draft.fields.getValue("otpauthUri")

        assertEquals("google-authenticator", draft.software)
        assertEquals(32, seed.length)
        assertTrue(seed.matches(Regex("[A-Z2-7]+")))
        assertTrue(uri.startsWith("otpauth://totp/Keystead%3Aalice%40example.com?"))
        assertTrue(uri.contains("secret=$seed"))
    }

    @Test
    fun generatedDraftToStringRedactsSeedAndProvisioningUri() {
        val draft =
            MfaSecretDraftGenerator.generate(
                issuer = "Keystead",
                accountName = "alice@example.com",
            )
        val diagnostic = draft.toString()

        listOf("seed", "otpauthUri").forEach { field ->
            assertFalse(diagnostic.contains(draft.fields.getValue(field)), "diagnostic leaked $field")
        }
        assertEquals("MfaSecretDraft(<redacted>)", diagnostic)
    }
}
