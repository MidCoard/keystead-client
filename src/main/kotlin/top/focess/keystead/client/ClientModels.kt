package top.focess.keystead.client

enum class ClientConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

data class VaultSummary(
    val vaultId: String,
    val displayName: String,
    val recordCount: Int,
    val lastSyncedRevision: Long,
)

data class ClientHomeState(
    val connectionState: ClientConnectionState,
    val serverUrl: String,
    val activeUser: String?,
    val vaults: List<VaultSummary>,
) {
    val isSignedIn: Boolean
        get() = activeUser != null
}

object ClientHomePreview {
    fun disconnected(): ClientHomeState =
        ClientHomeState(
            connectionState = ClientConnectionState.DISCONNECTED,
            serverUrl = "http://localhost:8080",
            activeUser = null,
            vaults = emptyList(),
        )

    fun signedIn(): ClientHomeState =
        ClientHomeState(
            connectionState = ClientConnectionState.CONNECTED,
            serverUrl = "http://localhost:8080",
            activeUser = "local",
            vaults =
                listOf(
                    VaultSummary(
                        vaultId = "personal",
                        displayName = "Personal",
                        recordCount = 12,
                        lastSyncedRevision = 42,
                    ),
                    VaultSummary(
                        vaultId = "developer",
                        displayName = "Developer",
                        recordCount = 8,
                        lastSyncedRevision = 17,
                    ),
                ),
        )
}
