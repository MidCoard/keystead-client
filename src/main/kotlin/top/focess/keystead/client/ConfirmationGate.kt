package top.focess.keystead.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A pending destructive action awaiting user confirmation.
 *
 * Each variant carries the dialog copy so the confirmation UI and the message formatting stay in
 * one testable place; the payload (e.g. the secret id) is what the confirmed action executes on.
 */
internal sealed interface DestructiveConfirmation {
    val title: String
    val message: String

    data class DeleteSecret(val secretId: String, val secretTitle: String) : DestructiveConfirmation {
        override val title: String = "Delete secret"
        override val message: String = "Delete \"$secretTitle\"? This cannot be undone."
    }

    data object RevokeDevice : DestructiveConfirmation {
        override val title: String = "Revoke device"
        override val message: String =
            "Revoke this device's server access? You will be signed out and must re-enroll."
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
