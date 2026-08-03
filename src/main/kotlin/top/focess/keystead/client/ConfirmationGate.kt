package top.focess.keystead.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.focess.keystead.client.i18n.Strings

/**
 * A pending destructive action awaiting user confirmation.
 *
 * Each variant carries the dialog copy so the confirmation UI and the message formatting stay in
 * one testable place; the payload (e.g. the secret id) is what the confirmed action executes on.
 * The copy is produced on demand from the active [Strings] so the dialog follows the interface
 * locale rather than capturing English at request time.
 */
internal sealed interface DestructiveConfirmation {
    fun title(strings: Strings): String
    fun message(strings: Strings): String

    data class DeleteSecret(val secretId: String, val secretTitle: String) : DestructiveConfirmation {
        override fun title(strings: Strings): String = strings.deleteSecretTitle
        override fun message(strings: Strings): String = strings.deleteSecretMessage(secretTitle)
    }

    data class DeleteVaultFile(val vaultFile: String) : DestructiveConfirmation {
        override fun title(strings: Strings): String = strings.deleteVaultFileTitle
        override fun message(strings: Strings): String = strings.deleteVaultFileMessage(vaultFile)
    }

    data object RemoveDeviceLogin : DestructiveConfirmation {
        override fun title(strings: Strings): String = strings.removeDeviceLoginTitle
        override fun message(strings: Strings): String = strings.removeDeviceLoginMessage
    }

    data class RemoveServerRecords(val secretIds: Set<String>) : DestructiveConfirmation {
        init {
            require(secretIds.isNotEmpty()) { "At least one server record must be selected" }
        }

        override fun title(strings: Strings): String = strings.removeServerRecordsTitle
        override fun message(strings: Strings): String =
            strings.removeServerRecordsMessage(secretIds.size)
    }

}

/**
 * Observable gate holding a single pending action that requires confirmation.
 *
 * Mirrors the [RevealLifecycle] / [ClipboardLifecycle] pattern: the gate holds the testable
 * state machine while Compose observes [pending] for recomposition. [confirm] returns and clears
 * the pending target so the caller can execute the action; [cancel] discards it.
 */
internal class ConfirmationGate<T> {
    private var pendingValue by mutableStateOf<T?>(null)
    val pending: T? get() = pendingValue
    val isPending: Boolean get() = pendingValue != null

    fun request(target: T) {
        pendingValue = target
    }

    fun confirm(): T? {
        val target = pendingValue
        pendingValue = null
        return target
    }

    fun cancel() {
        pendingValue = null
    }
}
