package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretTaxonomy

class GpgKeyDraftGeneratorTest {
    @Test
    fun generatedDraftPopulatesGpgFormFieldsAndWipesInputPassphrase() {
        val passphrase = "changeit".toCharArray()
        val draft = GpgKeyDraftGenerator.generate("Alice <alice@example.com>", passphrase)

        assertEquals(SecretTaxonomy.SOFTWARE_GPG, draft.software)
        assertTrue(draft.fields.getValue("publicKey").startsWith("-----BEGIN PGP PUBLIC KEY BLOCK-----"))
        assertTrue(draft.fields.getValue("privateKey").startsWith("-----BEGIN PGP PRIVATE KEY BLOCK-----"))
        assertEquals("changeit", draft.fields.getValue("passphrase"))
        assertContentEquals(CharArray(passphrase.size), passphrase)
    }

    @Test
    fun generatedDraftToStringRedactsKeyMaterialAndPassphrase() {
        val draft = GpgKeyDraftGenerator.generate("Alice <alice@example.com>", "changeit".toCharArray())
        val diagnostic = draft.toString()

        listOf("publicKey", "privateKey", "passphrase").forEach { field ->
            assertFalse(diagnostic.contains(draft.fields.getValue(field)), "diagnostic leaked $field")
        }
        assertEquals("GpgKeyDraft(<redacted>)", diagnostic)
    }
}
