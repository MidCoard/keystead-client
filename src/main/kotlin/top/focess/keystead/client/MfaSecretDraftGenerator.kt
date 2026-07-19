package top.focess.keystead.client

import top.focess.keystead.generator.DefaultMfaSecretGenerator
import top.focess.keystead.generator.MfaSecretPolicy
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.model.SecretTaxonomy

data class MfaSecretDraft(
    val software: String,
    val fields: Map<String, String>,
) {
    override fun toString(): String = "MfaSecretDraft(<redacted>)"
}

object MfaSecretDraftGenerator {
    private val generator = DefaultMfaSecretGenerator()

    fun generate(
        issuer: String,
        accountName: String,
    ): MfaSecretDraft =
        generator.generate(MfaSecretPolicy.totp(issuer, accountName)).use { secret ->
            MfaSecretDraft(
                software = SecretTaxonomy.SOFTWARE_GOOGLE_AUTHENTICATOR,
                fields =
                    mapOf(
                        "seed" to copy(secret.seed()),
                        "otpauthUri" to copy(secret.otpauthUri()),
                    ),
            )
        }

    private fun copy(buffer: SecretBuffer): String {
        var value = ""
        buffer.copyChars { chars -> value = String(chars) }
        return value
    }
}
