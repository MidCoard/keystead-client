package top.focess.keystead.client

import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.Strings

/**
 * Turns a [KeysteadRevisionConflictException] into a one-line status string for the UI.
 *
 * The localized phrasing lives on [Strings.conflictMessage] so the status bar follows the active
 * locale; this object keeps a stable, testable entry point. The no-argument overload delegates to
 * [EnStrings] so unit tests keep asserting the canonical English.
 */
object SyncStatusFormatter {
    fun messageFor(error: KeysteadRevisionConflictException): String = messageFor(error, EnStrings)

    internal fun messageFor(error: KeysteadRevisionConflictException, strings: Strings): String =
        strings.conflictMessage(error)
}
