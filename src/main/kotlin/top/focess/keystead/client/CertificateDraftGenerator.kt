package top.focess.keystead.client

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import top.focess.keystead.generator.CertificatePolicy
import top.focess.keystead.generator.DefaultCertificateGenerator
import top.focess.keystead.model.SecretTaxonomy

data class CertificateDraft(
    val software: String,
    val fields: Map<String, String>,
) {
    override fun toString(): String = "CertificateDraft(<redacted>)"
}

object CertificateDraftGenerator {
    private const val CERTIFICATE_VALIDITY_DAYS = 365L
    private val generator = DefaultCertificateGenerator()

    fun generate(commonName: String): CertificateDraft {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        val notAfter = now.plus(CERTIFICATE_VALIDITY_DAYS, ChronoUnit.DAYS)
        return generator
            .generate(CertificatePolicy(commonName, Date.from(now), Date.from(notAfter)))
            .use { bundle ->
                val privateKey = StringBuilder()
                bundle.privateKey().copyChars { chars -> privateKey.append(chars) }
                CertificateDraft(
                    software = SecretTaxonomy.SOFTWARE_X509,
                    fields =
                        mapOf(
                            "certificate" to bundle.certificate(),
                            "privateKey" to privateKey.toString(),
                            "passphrase" to "",
                        ),
                )
            }
    }
}
