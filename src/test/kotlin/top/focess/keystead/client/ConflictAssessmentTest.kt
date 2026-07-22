package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConflictAssessmentTest {

    @Test
    fun serverDeletedConflictIsNotAutoRecoverable() {
        val error =
            KeysteadRevisionConflictException(
                vaultId = "vault-1",
                secretId = "secret-1",
                serverDeleted = true,
                serverUpdatedAt = "2026-07-22T00:00:00Z",
            )
        val assessment = ConflictAssessment.from(error)
        assertFalse(assessment.canAutoRecover)
        assertNotNull(assessment.warning)
        assertTrue(assessment.title.contains("deleted", ignoreCase = true))
        assertTrue(assessment.warning!!.contains("discards", ignoreCase = true))
    }

    @Test
    fun newerRevisionConflictIsAutoRecoverable() {
        val error =
            KeysteadRevisionConflictException(
                vaultId = "vault-1",
                secretId = "secret-1",
                serverRevision = 9,
                clientRevision = 7,
            )
        val assessment = ConflictAssessment.from(error)
        assertTrue(assessment.canAutoRecover)
        assertNull(assessment.warning)
        assertTrue(assessment.title.contains("newer", ignoreCase = true))
    }

    @Test
    fun nullServerDeletedIsTreatedAsAutoRecoverable() {
        val error = KeysteadRevisionConflictException()
        val assessment = ConflictAssessment.from(error)
        assertTrue(assessment.canAutoRecover)
        assertNull(assessment.warning)
    }

    @Test
    fun messageIsNeverBlank() {
        val withDetail =
            ConflictAssessment.from(
                KeysteadRevisionConflictException(serverRevision = 9, clientRevision = 7)
            )
        val withoutDetail = ConflictAssessment.from(KeysteadRevisionConflictException())
        assertTrue(withDetail.message.isNotBlank())
        assertTrue(withoutDetail.message.isNotBlank())
    }
}
