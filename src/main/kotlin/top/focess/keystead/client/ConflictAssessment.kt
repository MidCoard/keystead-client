package top.focess.keystead.client

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
        fun from(error: KeysteadRevisionConflictException): ConflictAssessment {
            val base = SyncStatusFormatter.messageFor(error)
            return if (error.serverDeleted == true) {
                ConflictAssessment(
                    title = "Conflict: deleted on server",
                    message = base,
                    canAutoRecover = false,
                    warning =
                        "This secret was deleted on the server. Pulling discards your local " +
                            "change. Pull to accept the deletion, or cancel and re-save to keep " +
                            "your local copy.",
                )
            } else {
                ConflictAssessment(
                    title = "Conflict: newer data on server",
                    message = base,
                    canAutoRecover = true,
                    warning = null,
                )
            }
        }
    }
}
