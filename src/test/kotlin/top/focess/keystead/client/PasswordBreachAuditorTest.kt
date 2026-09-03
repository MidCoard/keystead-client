package top.focess.keystead.client

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class PasswordBreachAuditorTest {
    @Test
    fun `audit checks saved login passwords and wipes every plaintext copy`() {
        val directory = createTempDirectory("keystead-breach-audit")
        try {
            LocalVaultSession.openOrCreate(
                directory.resolve("vault.kvault"),
                "master-password".toCharArray(),
            ).use { session ->
                val breachedId =
                    session.addLogin("Breached", "alice", "known-password", null)
                session.addLogin("Safe", "bob", "unique-password", null)
                session.addStructuredSecret(
                    SecretType.API_TOKEN,
                    "Not a login",
                    mapOf("token" to "known-password"),
                )
                val observed = mutableListOf<CharArray>()
                val lookup =
                    PasswordBreachLookup { password ->
                        observed += password
                        val count = if (password.concatToString() == "known-password") 42 else 0
                        object : PasswordBreachQuery {
                            override fun breachCount(): Int = count

                            override fun close() = Unit
                        }
                    }

                val result = PasswordBreachAuditor(lookup).audit(session, session.listSecrets())

                assertEquals(mapOf(breachedId to 42), result.findings)
                assertEquals(
                    session.listSecrets()
                        .filter { it.type == SecretType.LOGIN_PASSWORD.name }
                        .mapTo(linkedSetOf()) { it.id },
                    result.checkedSecretIds,
                )
                assertEquals(2, result.checked)
                assertEquals(0, result.failed)
                assertTrue(observed.all { password -> password.all { it == '\u0000' } })
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
