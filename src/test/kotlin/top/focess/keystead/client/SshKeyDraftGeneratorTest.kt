package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SshKeyDraftGeneratorTest {
    @Test
    fun generatedDraftPopulatesSshFormFields() {
        val draft = SshKeyDraftGenerator.generate(comment = "alice@laptop")

        assertEquals("openssh", draft.software)
        assertTrue(draft.fields.getValue("publicKey").startsWith("ssh-ed25519 "))
        assertTrue(draft.fields.getValue("publicKey").endsWith(" alice@laptop"))
        assertTrue(draft.fields.getValue("privateKey").contains("-----BEGIN OPENSSH PRIVATE KEY-----"))
        assertEquals("", draft.fields.getValue("passphrase"))
    }

    @Test
    fun generatedDraftToStringRedactsKeyMaterial() {
        val draft = SshKeyDraftGenerator.generate(comment = "alice@laptop")
        val diagnostic = draft.toString()

        listOf("publicKey", "privateKey").forEach { field ->
            assertFalse(diagnostic.contains(draft.fields.getValue(field)), "diagnostic leaked $field")
        }
        assertEquals("SshKeyDraft(<redacted>)", diagnostic)
    }
}
