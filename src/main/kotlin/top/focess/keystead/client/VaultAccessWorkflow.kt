package top.focess.keystead.client

import java.util.Base64
import top.focess.keystead.access.VaultAccessKeyContextCodec
import top.focess.keystead.access.VaultAccessRequestCodec
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.memory.Wipe

class VaultAccessWorkflow(private val client: KeysteadServerClient) {
    private val access = VaultAccessClient(client)

    fun request(session: EphemeralVaultAccessSession): ServerVaultAccessRequest =
        access.create(session)

    fun refresh(requestId: String): ServerVaultAccessRequest = access.get(requestId)

    fun pending(): List<ServerVaultAccessRequest> = access.listPending()

    fun approve(request: ServerVaultAccessRequest, vault: LocalVaultSession) {
        require(request.state == ServerVaultAccessRequestState.PENDING) {
            "Only a pending vault access request can be approved"
        }
        // The DEK package is useful only if the account stream already contains a reconstructible
        // snapshot. Event ids make this safe to repeat when an approval is retried.
        vault.pushAllPersonalRecordsTo(client)
        val canonical = Base64.getUrlDecoder().decode(request.canonicalRequest)
        try {
            val decoded = VaultAccessRequestCodec.decode(canonical)
            require(decoded.requestId() == request.requestId)
            require(decoded.accountId() == request.accountId)
            require(decoded.serverOrigin() == request.serverOrigin)
            require(decoded.keyAlgorithm() == request.keyAlgorithm)
            require(VaultAccessRequestCodec.fingerprint(decoded) == request.fingerprint)
            require(decoded.keyAlgorithm() == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
                "Requesting session exchange algorithm is unsupported"
            }

            val publicKey = decoded.exchangePublicKey()
            try {
                require(
                    Base64.getEncoder().encodeToString(publicKey) == request.exchangePublicKey,
                ) { "Vault access request public key does not match its canonical request" }
                val context =
                    VaultAccessKeyContextCodec.encode(
                        canonical,
                        vault.fingerprintValue(),
                        vault.vaultKeyIdValue(),
                    )
                val wrapped =
                    try {
                        vault.wrapCurrentVaultKey(publicKey, context)
                    } finally {
                        Wipe.wipe(context)
                    }
                val encrypted = wrapped.encryptedVaultKey()
                try {
                    access.approve(
                        request.requestId,
                        VaultAccessKeyPackage(
                            fingerprint = vault.fingerprintValue(),
                            vaultKeyId = wrapped.vaultKeyId().value(),
                            keyAlgorithm = wrapped.keyAlgorithm(),
                            encryptedVaultKey = Base64.getEncoder().encodeToString(encrypted),
                        ),
                    )
                } finally {
                    Wipe.wipe(encrypted)
                }
            } finally {
                Wipe.wipe(publicKey)
            }
        } finally {
            Wipe.wipe(canonical)
        }
    }
}
