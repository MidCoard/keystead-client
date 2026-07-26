package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import top.focess.keystead.service.SyncImportConflict
import top.focess.keystead.service.SyncImportReport

class BackupReportFormatterTest {
    @Test
    fun cleanImportOnly() {
        assertEquals(
            "Restore complete: imported 3",
            BackupReportFormatter.summarize(report(imported = 3)),
        )
    }

    @Test
    fun reportsSkippedWithoutConflicts() {
        assertEquals(
            "Restore complete: imported 1, skipped 1",
            BackupReportFormatter.summarize(report(imported = 1, skipped = 1)),
        )
    }

    @Test
    fun reportsConflictsAndSkipped() {
        val conflicts =
            listOf(
                conflict(local = 2L, remote = 1L),
                conflict(local = 3L, remote = 1L),
            )
        assertEquals(
            "Restore complete with 2 conflicts: imported 0, skipped 2",
            BackupReportFormatter.summarize(report(imported = 0, skipped = 2, conflicts = conflicts)),
        )
    }

    @Test
    fun reportsSingleConflictSingular() {
        assertEquals(
            "Restore complete with 1 conflict: imported 0, skipped 1",
            BackupReportFormatter.summarize(
                report(imported = 0, skipped = 1, conflicts = listOf(conflict(local = 2L, remote = 1L))),
            ),
        )
    }

    private fun report(
        imported: Int = 0,
        skipped: Int = 0,
        conflicts: List<SyncImportConflict> = emptyList(),
    ): SyncImportReport = SyncImportReport(imported, skipped, conflicts)

    private fun conflict(local: Long, remote: Long): SyncImportConflict =
        SyncImportConflict("fingerprint", "secret-id", local, remote, false, false)
}
