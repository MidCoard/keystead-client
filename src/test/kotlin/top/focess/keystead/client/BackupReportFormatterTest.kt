package top.focess.keystead.client

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import top.focess.keystead.model.SecretId
import top.focess.keystead.service.BackupConflict
import top.focess.keystead.service.BackupImportReport

class BackupReportFormatterTest {
    @Test
    fun cleanImportOnly() {
        assertEquals(
            "Restore complete: imported 3",
            BackupReportFormatter.summarize(report(imported = 3)),
        )
    }

    @Test
    fun includesRestoredDeletionsSingular() {
        assertEquals(
            "Restore complete: imported 1, restored 1 deletion",
            BackupReportFormatter.summarize(report(imported = 1, tombstones = 1)),
        )
    }

    @Test
    fun includesRestoredDeletionsPlural() {
        assertEquals(
            "Restore complete: imported 1, restored 2 deletions",
            BackupReportFormatter.summarize(report(imported = 1, tombstones = 2)),
        )
    }

    @Test
    fun reportsConflictsAndSkipped() {
        val conflicts =
            listOf(
                conflict(existing = 2L, incoming = 1L),
                conflict(existing = 3L, incoming = 1L),
            )
        assertEquals(
            "Restore complete with 2 conflicts: imported 0, skipped 2",
            BackupReportFormatter.summarize(report(imported = 0, skipped = 2, conflicts = conflicts)),
        )
    }

    @Test
    fun includesUnsupportedCount() {
        assertEquals(
            "Restore complete: imported 0, 1 unsupported",
            BackupReportFormatter.summarize(report(imported = 0, unsupported = 1)),
        )
    }

    private fun report(
        imported: Int = 0,
        tombstones: Int = 0,
        unsupported: Int = 0,
        skipped: Int = 0,
        conflicts: List<BackupConflict> = emptyList(),
    ): BackupImportReport = BackupImportReport(imported, skipped, unsupported, tombstones, conflicts)

    private fun conflict(existing: Long, incoming: Long): BackupConflict =
        BackupConflict(SecretId(UUID.randomUUID()), existing, incoming)
}
