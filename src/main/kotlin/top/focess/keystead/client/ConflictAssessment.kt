package top.focess.keystead.client

import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.Strings

/**
 * Structured assessment of a server revision conflict, produced from a
 * [KeysteadRevisionConflictException].
 *
 * The common case (the server holds a newer non-deleted revision) is auto-recoverable by pulling
 * the latest records and retrying the push. When the server has *deleted* the conflicting secret,
 * pulling would discard the local change, so auto-recovery is withheld and a [warning] explains
 * the manual choice (pull to accept the deletion, or re-save to keep the local copy).
 */
internal data class ConflictAssessment(
    val title: String,
    val message: String,
    val canAutoRecover: Boolean,
    val warning: String?,
) {
    companion object {
        fun from(error: KeysteadRevisionConflictException): ConflictAssessment = from(error, EnStrings)

        fun from(error: KeysteadRevisionConflictException, strings: Strings): ConflictAssessment {
            val base = SyncStatusFormatter.messageFor(error, strings)
            return if (error.serverDeleted == true) {
                ConflictAssessment(
                    title = strings.conflictDeletedTitle,
                    message = base,
                    canAutoRecover = false,
                    warning = strings.conflictDeletedWarning,
                )
            } else {
                ConflictAssessment(
                    title = strings.conflictNewerTitle,
                    message = base,
                    canAutoRecover = true,
                    warning = null,
                )
            }
        }
    }
}
