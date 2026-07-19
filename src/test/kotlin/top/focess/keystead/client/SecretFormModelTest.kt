package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class SecretFormModelTest {
    @Test
    fun supportedTypesCoverLoginAndStructuredSecrets() {
        assertEquals(
            listOf(
                SecretType.LOGIN_PASSWORD,
                SecretType.SECURE_NOTE,
                SecretType.SSH_KEY,
                SecretType.API_TOKEN,
                SecretType.GPG_KEY,
                SecretType.MFA_SECRET,
                SecretType.CERTIFICATE,
                SecretType.GENERIC_SECRET,
            ),
            SecretFormModel.supportedTypes,
        )
    }

    @Test
    fun secureNoteStoresNoteBody() {
        val spec = SecretFormModel.specFor(SecretType.SECURE_NOTE)

        assertEquals("Secure note", spec.label)
        assertEquals(null, spec.defaultCategory)
        assertEquals(null, spec.defaultProvider)
        assertEquals(null, spec.defaultSoftware)
        assertEquals(listOf("note"), spec.fieldNames)
        assertEquals("note", spec.revealFieldName)
    }

    @Test
    fun sshSecretUsesDeveloperFieldsAndPrivateKeyReveal() {
        val spec = SecretFormModel.specFor(SecretType.SSH_KEY)

        assertEquals("SSH key", spec.label)
        assertEquals("development", spec.defaultCategory)
        assertEquals("ssh", spec.defaultProvider)
        assertEquals("openssh", spec.defaultSoftware)
        assertEquals(listOf("publicKey", "privateKey", "passphrase"), spec.fieldNames)
        assertEquals("privateKey", spec.revealFieldName)
    }

    @Test
    fun mfaSecretStoresSeedAndOtpAuthUri() {
        val spec = SecretFormModel.specFor(SecretType.MFA_SECRET)

        assertEquals("MFA secret", spec.label)
        assertEquals("communication", spec.defaultCategory)
        assertEquals("google", spec.defaultProvider)
        assertEquals("google-authenticator", spec.defaultSoftware)
        assertEquals(listOf("seed", "otpauthUri"), spec.fieldNames)
        assertEquals("seed", spec.revealFieldName)
    }

    @Test
    fun fieldValuesDropBlankOptionalInputs() {
        val values =
            SecretFormModel.fieldValues(
                SecretFormModel.specFor(SecretType.API_TOKEN),
                mapOf("token" to "  ghp_secret  ", "notes" to " "),
            )

        assertEquals(mapOf("token" to "ghp_secret"), values)
    }

    @Test
    fun loginAndStructuredValidationUseRelevantFields() {
        assertTrue(
            SecretFormModel.canSaveLogin(title = "GitHub", username = "", password = "secret"),
        )
        assertFalse(SecretFormModel.canSaveLogin(title = "GitHub", username = "alice", password = ""))
        assertTrue(
            SecretFormModel.canSaveStructured(
                title = "Token",
                values = mapOf("token" to "ghp_secret"),
            ),
        )
        assertFalse(
            SecretFormModel.canSaveStructured(
                title = "Token",
                values = mapOf("token" to " "),
            ),
        )
    }
}
