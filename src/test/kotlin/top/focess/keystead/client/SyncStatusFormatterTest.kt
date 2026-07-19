package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStatusFormatterTest {
    @Test
    fun revisionConflictIncludesServerAndLocalRevisionWhenAvailable() {
        val status =
            SyncStatusFormatter.messageFor(
                KeysteadRevisionConflictException(
                    message = "Record revision must increase",
                    latestRevision = 8,
                    rejectedRevision = 7,
                ),
            )

        assertEquals(
            "Server has revision 8 and rejected local revision 7. Pull before pushing again.",
            status,
        )
    }

    @Test
    fun revisionConflictPrefersDatabaseContractFieldsAndNamesSecret() {
        val status =
            SyncStatusFormatter.messageFor(
                KeysteadRevisionConflictException(
                    message = "Record revision must increase",
                    latestRevision = 8,
                    rejectedRevision = 7,
                    vaultId = "vault-1",
                    secretId = "secret-1",
                    serverRevision = 9,
                    clientRevision = 6,
                    serverDeleted = true,
                    serverUpdatedAt = "2026-07-03T15:00:00Z",
                ),
            )

        assertEquals(
            "Secret secret-1 in vault vault-1 was deleted on the server at 2026-07-03T15:00:00Z. Server has revision 9 and rejected local revision 6. Pull before pushing again.",
            status,
        )
    }

    @Test
    fun revisionConflictFallsBackWhenServerOmitsRevisionDetails() {
        val status =
            SyncStatusFormatter.messageFor(
                KeysteadRevisionConflictException("Server has a newer revision."),
            )

        assertEquals("Server has a newer revision. Pull before pushing again.", status)
    }

    @Test
    fun revisionConflictDoesNotDuplicateExistingPullGuidance() {
        val status = SyncStatusFormatter.messageFor(KeysteadRevisionConflictException())

        assertEquals("Server has a newer revision; pull before pushing again.", status)
    }
}
