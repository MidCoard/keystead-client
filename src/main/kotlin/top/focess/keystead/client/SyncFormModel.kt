package top.focess.keystead.client

object SyncFormModel {
    fun canLogin(
        serverUrl: String,
        username: String,
        password: String,
        serverAvailable: Boolean,
    ): Boolean =
        serverAvailable &&
            serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank()

    fun canUseServer(
        authenticated: Boolean,
        serverAvailable: Boolean,
    ): Boolean = authenticated && serverAvailable

    fun canEditConnection(
        authenticated: Boolean,
        serverAvailable: Boolean,
    ): Boolean = !authenticated || !serverAvailable

    fun canRegisterUser(
        serverUrl: String,
        username: String,
        password: String,
        serverAvailable: Boolean,
    ): Boolean =
        serverAvailable &&
            serverUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.length >= 12

    fun sinceRevisionOrNull(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return 0
        }
        val parsed = trimmed.toLongOrNull() ?: return null
        return parsed.takeIf { it >= 0 }
    }
}
