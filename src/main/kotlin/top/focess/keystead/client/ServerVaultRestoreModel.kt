package top.focess.keystead.client

enum class ServerVaultRestoreStage {
    SIGN_IN_REQUIRED,
    SERVER_OFFLINE,
    ACCESS_REQUEST_REQUIRED,
    WAITING_FOR_APPROVAL,
    REQUEST_EXPIRED,
    WAITING_FOR_PACKAGE,
    READY_TO_RESTORE,
    TARGET_IN_USE,
    MASTER_PASSPHRASE_REQUIRED,
}

data class ServerVaultRestoreModel(
    val stage: ServerVaultRestoreStage,
    val canCreateRequest: Boolean = false,
    val canRefreshRequest: Boolean = false,
    val canRestore: Boolean = false,
) {
    companion object {
        fun derive(
            authenticated: Boolean,
            serverAvailability: ServerAvailability,
            requestState: ServerVaultAccessRequestState?,
            approvedPackageAvailable: Boolean,
            targetPathAvailable: Boolean,
            masterPassphraseReady: Boolean,
        ): ServerVaultRestoreModel {
            if (!authenticated) {
                return ServerVaultRestoreModel(ServerVaultRestoreStage.SIGN_IN_REQUIRED)
            }
            if (!serverAvailability.isOnline) {
                return ServerVaultRestoreModel(ServerVaultRestoreStage.SERVER_OFFLINE)
            }
            if (requestState == ServerVaultAccessRequestState.APPROVED && approvedPackageAvailable) {
                if (!targetPathAvailable) {
                    return ServerVaultRestoreModel(ServerVaultRestoreStage.TARGET_IN_USE)
                }
                if (!masterPassphraseReady) {
                    return ServerVaultRestoreModel(ServerVaultRestoreStage.MASTER_PASSPHRASE_REQUIRED)
                }
                return ServerVaultRestoreModel(
                    stage = ServerVaultRestoreStage.READY_TO_RESTORE,
                    canRestore = true,
                )
            }
            return when (requestState) {
                null ->
                    ServerVaultRestoreModel(
                        stage = ServerVaultRestoreStage.ACCESS_REQUEST_REQUIRED,
                        canCreateRequest = true,
                    )
                ServerVaultAccessRequestState.PENDING ->
                    ServerVaultRestoreModel(
                        stage = ServerVaultRestoreStage.WAITING_FOR_APPROVAL,
                        canRefreshRequest = true,
                    )
                ServerVaultAccessRequestState.EXPIRED ->
                    ServerVaultRestoreModel(
                        stage = ServerVaultRestoreStage.REQUEST_EXPIRED,
                        canCreateRequest = true,
                    )
                ServerVaultAccessRequestState.APPROVED ->
                    ServerVaultRestoreModel(
                        stage = ServerVaultRestoreStage.WAITING_FOR_PACKAGE,
                        canRefreshRequest = true,
                    )
            }
        }
    }
}
