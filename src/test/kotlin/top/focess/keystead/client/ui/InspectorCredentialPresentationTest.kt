package top.focess.keystead.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.client.SecretInspectorField
import top.focess.keystead.client.SecretListItem

class InspectorCredentialPresentationTest {
    @Test
    fun loginCredentialsRenderAsAConsistentTwoRowBlock() {
        val rows = InspectorCredentialPresentation.rows("alice@example.com", "")

        assertEquals(
            listOf(InspectorCredentialKind.USERNAME, InspectorCredentialKind.PASSWORD),
            rows.map { it.kind },
        )
        assertEquals("alice@example.com", rows[0].value)
        assertTrue(rows[0].copyEnabled)
        assertEquals("••••••", rows[1].value)
        assertFalse(rows[1].copyEnabled)
    }

    @Test
    fun revealedPasswordStaysInThePasswordRowAndBecomesCopyable() {
        val password = InspectorCredentialPresentation.rows("alice", "correct horse")[1]

        assertEquals("correct horse", password.value)
        assertTrue(password.revealed)
        assertTrue(password.copyEnabled)
    }

    @Test
    fun selectedLoginDetailsIncludeUrlAndEveryStoredMetadataGroup() {
        val secret =
            SecretListItem(
                id = "secret-id",
                title = "GitHub",
                type = "LOGIN_PASSWORD",
                category = "development",
                provider = "github",
                software = "github.com",
                account = "alice",
                expiry = "2030-12-31",
                url = "https://github.com/alice",
                labels = setOf("work"),
                tags = setOf("important"),
                attributes = mapOf("expiry" to "2030-12-31", "environment" to "production"),
                createdAt = "2026-08-20T10:00:00Z",
                updatedAt = "2026-08-21T11:00:00Z",
                revision = 3,
            )

        val rows = InspectorDetailPresentation.rows(secret)

        assertEquals(
            listOf(
                InspectorDetailKind.URL,
                InspectorDetailKind.ACCOUNT,
                InspectorDetailKind.PROVIDER,
                InspectorDetailKind.SOFTWARE,
                InspectorDetailKind.CATEGORY,
                InspectorDetailKind.EXPIRY,
                InspectorDetailKind.LABELS,
                InspectorDetailKind.TAGS,
                InspectorDetailKind.ATTRIBUTE,
                InspectorDetailKind.CREATED_AT,
                InspectorDetailKind.UPDATED_AT,
                InspectorDetailKind.REVISION,
            ),
            rows.map { it.kind },
        )
        assertEquals("https://github.com/alice", rows[0].value)
        assertEquals("environment", rows[8].name)
        assertEquals("production", rows[8].value)
        assertEquals("3", rows.last().value)
    }

    @Test
    fun structuredFieldsExposePublicValuesButMaskEachSecretIndependently() {
        val fields =
            listOf(
                SecretInspectorField("publicKey", secret = false, value = "PUBLIC"),
                SecretInspectorField("privateKey", secret = true),
                SecretInspectorField("passphrase", secret = true),
            )

        val hidden = InspectorFieldPresentation.rows(fields, null, "")
        val revealed = InspectorFieldPresentation.rows(fields, "privateKey", "PRIVATE")

        assertEquals("PUBLIC", hidden[0].value)
        assertTrue(hidden[0].copyEnabled)
        assertEquals("••••••", hidden[1].value)
        assertFalse(hidden[1].copyEnabled)
        assertEquals("••••••", hidden[2].value)
        assertEquals("PRIVATE", revealed[1].value)
        assertTrue(revealed[1].copyEnabled)
        assertEquals("••••••", revealed[2].value)
    }
}
