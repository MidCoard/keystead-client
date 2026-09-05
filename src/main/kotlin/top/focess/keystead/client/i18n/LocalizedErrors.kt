package top.focess.keystead.client.i18n

import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpTimeoutException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import top.focess.keystead.client.*
import top.focess.keystead.crypto.CryptoException
import top.focess.keystead.memory.NativeMemoryUnavailableException
import top.focess.keystead.service.ValidationException
import top.focess.keystead.store.StoreException

/** UI descriptions only: never alter protocol values, stored secrets, or exception messages. */
internal fun localizedErrorKey(error: Throwable): String? = when (error) {
    is KeysteadAuthenticationException -> "server_credentials_rejected"
    is KeysteadAccountConflictException -> "server_user_already_exists"
    is KeysteadShareNotFoundException -> "error_share_missing"
    is KeysteadShareExpiredException -> "error_share_expired"
    is KeysteadShareRateLimitedException -> "error_rate_limited"
    is KeysteadServerException -> when (error.statusCode) {
        401 -> "server_session_expired"
        403 -> "error_access_denied"
        404 -> "error_server_missing"
        409 -> "conflict_default_message"
        429 -> "error_rate_limited"
        in 500..599 -> "error_server_unavailable"
        else -> "error_invalid_request"
    }
    is OsSecretStoreException -> when (error.failure) {
        OsSecretStoreFailure.ACCESS_DENIED -> "error_access_denied"
        OsSecretStoreFailure.LOCKED -> "error_storage_locked"
        else -> "local_login_credential_unavailable"
    }
    is NativeMemoryUnavailableException -> "error_protected_memory"
    is HttpTimeoutException, is ConnectException, is UnknownHostException -> "error_network"
    is FileAlreadyExistsException -> "restore_target_must_be_new"
    is AccessDeniedException -> "error_file_access"
    is NoSuchFileException -> "error_file_missing"
    is InvalidPathException -> "error_path_invalid"
    is CryptoException -> "error_decryption"
    else -> knownValidationKey(error.message.orEmpty()) ?: when (error) {
        is StoreException -> "error_vault_storage"
        is ValidationException, is IllegalArgumentException -> "error_invalid_input"
        is IOException -> "error_io"
        else -> error.cause?.takeIf { it !== error }?.let { cause ->
            // Inspect one level only: Throwable cause graphs need not be acyclic.
            knownValidationKey(cause.message.orEmpty())
        }
    }
}

private fun knownValidationKey(message: String): String? = when {
    message == "Vault access request has expired" -> "server_vault_restore_status__request_expired"
    message == "Vault access request has not been approved" -> "error_approval_required"
    message == "Server restore target must be a new vault file" -> "restore_target_must_be_new"
    message == "Server restore target must use the .kvault extension" -> "error_vault_extension"
    message.contains("passphrase must not be empty", ignoreCase = true) -> "error_passphrase_required"
    message.contains("already locked", ignoreCase = true) ||
        message.contains("already open", ignoreCase = true) -> "error_vault_locked"
    message.contains("symbolic link", ignoreCase = true) -> "error_vault_symlink"
    message == "Share title must not be blank" -> "error_share_title"
    message == "Share payload must not be empty" -> "error_share_payload"
    message == "Share code must not be blank" -> "error_share_code"
    else -> null
}
