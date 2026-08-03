package top.focess.keystead.client

import java.nio.file.Path
import top.focess.keystead.memory.Wipe

data class ServerVaultProvisioningResult(
    val session: LocalVaultSession,
    val pulledRecords: Int,
    val rejectedRecords: Int = 0,
)

class ServerVaultInitialPullException(
    val localVaultFile: Path,
    val fingerprint: String,
    cause: Throwable,
) : IllegalStateException(
        "The local vault is protected by its new master passphrase, but its encrypted records " +
            "could not be downloaded. Unlock this file and retry Sync.",
        cause,
    )

class ServerVaultProvisioningService {
    fun restore(
        file: Path,
        request: ServerVaultAccessRequest,
        exchangeSession: EphemeralVaultAccessSession,
        newMasterPassphrase: CharArray,
        client: KeysteadServerClient,
        stateStore: SyncStateStore,
    ): ServerVaultProvisioningResult {
        val masterPassphrase = newMasterPassphrase.copyOf()
        Wipe.wipe(newMasterPassphrase)
        try {
            require(masterPassphrase.isNotEmpty()) {
                "New vault master passphrase must not be empty"
            }
            val opened =
                LocalVaultSession.openProvisionedFromServer(
                    file = file,
                    request = request,
                    exchangeSession = exchangeSession,
                )
            try {
                opened.installMasterPassphrase(masterPassphrase)
            } catch (error: Throwable) {
                opened.close()
                throw error
            }
            return try {
                val pulled = opened.pullPendingPersonalRecordsFrom(client, stateStore)
                ServerVaultProvisioningResult(
                    session = opened,
                    pulledRecords = pulled.imported,
                    rejectedRecords = pulled.rejected.size,
                )
            } catch (error: Throwable) {
                opened.close()
                throw ServerVaultInitialPullException(
                    file,
                    request.approvedPackage?.fingerprint.orEmpty(),
                    error,
                )
            }
        } finally {
            Wipe.wipe(masterPassphrase)
        }
    }

}
