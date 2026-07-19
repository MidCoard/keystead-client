package top.focess.keystead.client

import top.focess.keystead.generator.ApiTokenPolicy
import top.focess.keystead.generator.DefaultApiTokenGenerator
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.model.SecretTaxonomy

data class ApiTokenDraft(
    val software: String?,
    val fields: Map<String, String>,
) {
    override fun toString(): String = "ApiTokenDraft(<redacted>)"
}

object ApiTokenDraftGenerator {
    private val generator = DefaultApiTokenGenerator()

    fun generate(prefix: String): ApiTokenDraft =
        generator.generate(ApiTokenPolicy(prefix, 32)).use { token ->
            ApiTokenDraft(
                software = softwareFor(prefix),
                fields = mapOf("token" to copy(token)),
            )
        }

    private fun softwareFor(prefix: String): String? =
        if (prefix.trim() == "ghp") SecretTaxonomy.SOFTWARE_GITHUB else null

    private fun copy(buffer: SecretBuffer): String {
        var value = ""
        buffer.copyChars { chars -> value = String(chars) }
        return value
    }
}
