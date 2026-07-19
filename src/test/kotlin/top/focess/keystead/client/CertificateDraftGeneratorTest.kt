package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretTaxonomy

class CertificateDraftGeneratorTest {
    @Test
    fun generatedDraftPopulatesCertificateFormFields() {
        val draft = CertificateDraftGenerator.generate("keystead.local")

        assertEquals(SecretTaxonomy.SOFTWARE_X509, draft.software)
        assertTrue(draft.fields.getValue("certificate").startsWith("-----BEGIN CERTIFICATE-----"))
        assertTrue(draft.fields.getValue("privateKey").startsWith("-----BEGIN PRIVATE KEY-----"))
        assertEquals("", draft.fields.getValue("passphrase"))
    }

    @Test
    fun generatedDraftToStringRedactsCertificateAndPrivateKey() {
        val draft = CertificateDraftGenerator.generate("keystead.local")
        val diagnostic = draft.toString()

        listOf("certificate", "privateKey").forEach { field ->
            assertFalse(diagnostic.contains(draft.fields.getValue(field)), "diagnostic leaked $field")
        }
        assertEquals("CertificateDraft(<redacted>)", diagnostic)
    }
}
