package top.focess.keystead.client

object SyncStatusFormatter {
    fun messageFor(error: KeysteadRevisionConflictException): String {
        val latest = error.serverRevision ?: error.latestRevision
        val rejected = error.clientRevision ?: error.rejectedRevision
        val prefix = targetPrefix(error)
        if (latest != null && rejected != null) {
            return "${prefix}Server has revision $latest and rejected local revision $rejected. Pull before pushing again."
        }
        val message = error.message ?: "Server has a newer revision."
        if (message.contains("pull before pushing", ignoreCase = true)) {
            return message
        }
        return "$message Pull before pushing again."
    }

    private fun targetPrefix(error: KeysteadRevisionConflictException): String {
        val vaultId = error.vaultId
        val secretId = error.secretId
        if (vaultId == null || secretId == null) {
            return ""
        }
        val state =
            if (error.serverDeleted == true) {
                " was deleted on the server"
            } else {
                " has a newer server copy"
            }
        val updatedAt = error.serverUpdatedAt?.let { " at $it" } ?: ""
        return "Secret $secretId in vault $vaultId$state$updatedAt. "
    }
}
