package top.focess.keystead.client

import java.nio.charset.StandardCharsets
import top.focess.keystead.memory.Wipe

enum class AccountAuthMode {
    SIGN_IN,
    CREATE_ACCOUNT,
}

data class AccountAuthUiState(
    val mode: AccountAuthMode = AccountAuthPresentation.defaultMode,
    val failure: String? = null,
) {
    fun withFailure(message: String): AccountAuthUiState = copy(failure = message)

    fun onInputChanged(): AccountAuthUiState = copy(failure = null)

    fun select(nextMode: AccountAuthMode): AccountAuthUiState =
        copy(mode = nextMode, failure = null)
}

object AccountAuthPresentation {
    private const val MINIMUM_PASSWORD_CHARACTERS = 12
    private const val MAXIMUM_BCRYPT_PASSWORD_BYTES = 72

    val modes: List<AccountAuthMode> = AccountAuthMode.entries
    val defaultMode: AccountAuthMode = AccountAuthMode.SIGN_IN

    fun canSubmit(
        mode: AccountAuthMode,
        serverUrl: String,
        username: String,
        password: String,
        passwordConfirmation: String,
        serverAvailable: Boolean,
        authenticated: Boolean,
    ): Boolean {
        if (
            authenticated ||
                !serverAvailable ||
                serverUrl.isBlank() ||
                username.isBlank()
        ) {
            return false
        }
        return when (mode) {
            AccountAuthMode.SIGN_IN -> password.isNotBlank()
            AccountAuthMode.CREATE_ACCOUNT ->
                registrationPasswordMeetsRequirements(password) &&
                    password == passwordConfirmation
        }
    }

    fun registrationPasswordMeetsRequirements(password: String): Boolean {
        if (password.length < MINIMUM_PASSWORD_CHARACTERS) return false
        val utf8 = password.toByteArray(StandardCharsets.UTF_8)
        return try {
            utf8.size <= MAXIMUM_BCRYPT_PASSWORD_BYTES
        } finally {
            Wipe.wipe(utf8)
        }
    }

    fun showSessionManagement(authenticated: Boolean): Boolean = authenticated
}
