package top.focess.keystead.client

import top.focess.keystead.generator.DefaultGpgKeyGenerator
import top.focess.keystead.generator.GpgKeyPolicy
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.SecretTaxonomy

data class GpgKeyDraft(
    val software: String,
    val fields: Map<String, String>,
) {
    override fun toString(): String = "GpgKeyDraft(<redacted>)"
}

object GpgKeyDraftGenerator {
    private val generator = DefaultGpgKeyGenerator()

    fun generate(identity: String, passphrase: CharArray): GpgKeyDraft {
        try {
            val passphraseValue = String(passphrase)
            return GpgKeyPolicy(identity, passphrase).use { policy ->
                generator.generate(policy).use { keyPair ->
                    val privateKey = StringBuilder()
                    keyPair.privateKey().copyChars { chars -> privateKey.append(chars) }
                    GpgKeyDraft(
                        software = SecretTaxonomy.SOFTWARE_GPG,
                        fields =
                            mapOf(
                                "publicKey" to keyPair.publicKey(),
                                "privateKey" to privateKey.toString(),
                                "passphrase" to passphraseValue,
                            ),
                    )
                }
            }
        } finally {
            // The policy copies the passphrase and leaves the caller-owned array to us.
            Wipe.wipe(passphrase)
        }
    }
}
