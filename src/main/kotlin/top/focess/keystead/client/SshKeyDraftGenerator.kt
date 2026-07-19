package top.focess.keystead.client

import top.focess.keystead.generator.DefaultSshKeyGenerator
import top.focess.keystead.generator.SshKeyPolicy
import top.focess.keystead.model.SecretTaxonomy

data class SshKeyDraft(
    val software: String,
    val fields: Map<String, String>,
) {
    override fun toString(): String = "SshKeyDraft(<redacted>)"
}

object SshKeyDraftGenerator {
    private val generator = DefaultSshKeyGenerator()

    fun generate(comment: String?): SshKeyDraft =
        generator.generate(SshKeyPolicy.ed25519(comment)).use { keyPair ->
            val privateKey = StringBuilder()
            keyPair.privateKey().copyChars { chars -> privateKey.append(chars) }
            SshKeyDraft(
                software = SecretTaxonomy.SOFTWARE_OPENSSH,
                fields =
                    mapOf(
                        "publicKey" to keyPair.publicKey(),
                        "privateKey" to privateKey.toString(),
                        "passphrase" to "",
                    ),
            )
        }
}
