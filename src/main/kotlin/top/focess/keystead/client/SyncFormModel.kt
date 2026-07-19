package top.focess.keystead.client

object SyncFormModel {
    fun canLogin(serverUrl: String, username: String, password: String): Boolean =
        serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    fun canLoginWithDevice(
        serverUrl: String,
        username: String,
        password: String,
        identityLoaded: Boolean,
    ): Boolean = identityLoaded && canLogin(serverUrl, username, password)

    fun canUseServer(authenticated: Boolean): Boolean = authenticated

    fun canRegisterUser(serverUrl: String, username: String, password: String): Boolean =
        serverUrl.isNotBlank() && username.isNotBlank() && password.length >= 12

    fun canCreateServerVault(
        vaultOpen: Boolean,
        authenticated: Boolean,
    ): Boolean = vaultOpen && authenticated

    fun canLoadIdentity(deviceId: String, passphrase: String): Boolean =
        deviceId.isNotBlank() && passphrase.isNotBlank()

    fun canEnrollDevice(authenticated: Boolean, identityLoaded: Boolean): Boolean =
        authenticated && identityLoaded

    fun canRevokeDevice(
        authenticated: Boolean,
        identityLoaded: Boolean,
        registered: Boolean,
    ): Boolean = authenticated && identityLoaded && registered

    fun canPublishKeyPackages(vaultOpen: Boolean, authenticated: Boolean): Boolean =
        vaultOpen && authenticated

    fun sinceRevisionOrNull(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return 0
        }
        val parsed = trimmed.toLongOrNull() ?: return null
        return parsed.takeIf { it >= 0 }
    }
}
