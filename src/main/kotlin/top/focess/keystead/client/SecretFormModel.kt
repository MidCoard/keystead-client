package top.focess.keystead.client

import top.focess.keystead.model.SecretType
import top.focess.keystead.model.SecretTaxonomy

data class SecretFieldSpec(
    val name: String,
    val label: String,
    val secret: Boolean = true,
)

data class SecretFormSpec(
    val type: SecretType,
    val label: String,
    val defaultCategory: String?,
    val defaultProvider: String?,
    val defaultSoftware: String?,
    val revealFieldName: String,
    val fields: List<SecretFieldSpec>,
) {
    val fieldNames: List<String>
        get() = fields.map { it.name }
}

object SecretFormModel {
    val supportedTypes: List<SecretType> =
        listOf(
            SecretType.LOGIN_PASSWORD,
            SecretType.SECURE_NOTE,
            SecretType.SSH_KEY,
            SecretType.API_TOKEN,
            SecretType.GPG_KEY,
            SecretType.MFA_SECRET,
            SecretType.CERTIFICATE,
            SecretType.GENERIC_SECRET,
        )

    private val structuredSpecs =
        mapOf(
            SecretType.SECURE_NOTE to
                SecretFormSpec(
                    type = SecretType.SECURE_NOTE,
                    label = "Secure note",
                    defaultCategory = null,
                    defaultProvider = null,
                    defaultSoftware = null,
                    revealFieldName = "note",
                    fields = listOf(SecretFieldSpec("note", "Note")),
                ),
            SecretType.SSH_KEY to
                SecretFormSpec(
                    type = SecretType.SSH_KEY,
                    label = "SSH key",
                    defaultCategory = SecretTaxonomy.CATEGORY_DEVELOPMENT,
                    defaultProvider = SecretTaxonomy.PROVIDER_SSH,
                    defaultSoftware = SecretTaxonomy.SOFTWARE_OPENSSH,
                    revealFieldName = "privateKey",
                    fields =
                        listOf(
                            SecretFieldSpec("publicKey", "Public key", secret = false),
                            SecretFieldSpec("privateKey", "Private key"),
                            SecretFieldSpec("passphrase", "Passphrase"),
                        ),
                ),
            SecretType.API_TOKEN to
                SecretFormSpec(
                    type = SecretType.API_TOKEN,
                    label = "API token",
                    defaultCategory = SecretTaxonomy.CATEGORY_DEVELOPMENT,
                    defaultProvider = SecretTaxonomy.PROVIDER_API,
                    defaultSoftware = null,
                    revealFieldName = "token",
                    fields = listOf(SecretFieldSpec("token", "Token")),
                ),
            SecretType.GPG_KEY to
                SecretFormSpec(
                    type = SecretType.GPG_KEY,
                    label = "GPG key",
                    defaultCategory = SecretTaxonomy.CATEGORY_DEVELOPMENT,
                    defaultProvider = SecretTaxonomy.PROVIDER_GPG,
                    defaultSoftware = SecretTaxonomy.SOFTWARE_GPG,
                    revealFieldName = "privateKey",
                    fields =
                        listOf(
                            SecretFieldSpec("publicKey", "Public key", secret = false),
                            SecretFieldSpec("privateKey", "Private key"),
                            SecretFieldSpec("passphrase", "Passphrase"),
                        ),
                ),
            SecretType.MFA_SECRET to
                SecretFormSpec(
                    type = SecretType.MFA_SECRET,
                    label = "MFA secret",
                    defaultCategory = SecretTaxonomy.CATEGORY_COMMUNICATION,
                    defaultProvider = SecretTaxonomy.PROVIDER_GOOGLE,
                    defaultSoftware = SecretTaxonomy.SOFTWARE_GOOGLE_AUTHENTICATOR,
                    revealFieldName = "seed",
                    fields =
                        listOf(
                            SecretFieldSpec("seed", "Seed"),
                            SecretFieldSpec("otpauthUri", "otpauth URI"),
                        ),
                ),
            SecretType.CERTIFICATE to
                SecretFormSpec(
                    type = SecretType.CERTIFICATE,
                    label = "Certificate",
                    defaultCategory = SecretTaxonomy.CATEGORY_DEVELOPMENT,
                    defaultProvider = SecretTaxonomy.PROVIDER_X509,
                    defaultSoftware = SecretTaxonomy.SOFTWARE_X509,
                    revealFieldName = "privateKey",
                    fields =
                        listOf(
                            SecretFieldSpec("certificate", "Certificate", secret = false),
                            SecretFieldSpec("privateKey", "Private key"),
                            SecretFieldSpec("passphrase", "Passphrase"),
                        ),
                ),
            SecretType.GENERIC_SECRET to
                SecretFormSpec(
                    type = SecretType.GENERIC_SECRET,
                    label = "Generic",
                    defaultCategory = null,
                    defaultProvider = null,
                    defaultSoftware = null,
                    revealFieldName = "value",
                    fields = listOf(SecretFieldSpec("value", "Value")),
                ),
        )

    fun specFor(type: SecretType): SecretFormSpec =
        structuredSpecs[type]
            ?: error("Secret type ${type.name} does not use structured fields")

    fun specForOrNull(type: SecretType): SecretFormSpec? = structuredSpecs[type]

    fun fieldValues(spec: SecretFormSpec, rawValues: Map<String, String>): Map<String, String> =
        spec.fieldNames
            .mapNotNull { name ->
                val value = rawValues[name]?.trim().orEmpty()
                if (value.isBlank()) null else name to value
            }
            .toMap()

    fun canSaveLogin(title: String, username: String, password: String): Boolean =
        title.isNotBlank() && password.isNotBlank()

    fun canSaveStructured(title: String, values: Map<String, String>): Boolean =
        title.isNotBlank() && values.values.any { it.isNotBlank() }
}
