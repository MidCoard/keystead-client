package top.focess.keystead.client

import top.focess.keystead.service.BackupImportReport

/** Turns a [BackupImportReport] into a one-line status string for the UI. */
internal object BackupReportFormatter {

    fun summarize(report: BackupImportReport): String {
        val parts = buildList {
            add("imported ${report.imported}")
            if (report.tombstones > 0) {
                add("restored ${report.tombstones} deletion${plural(report.tombstones)}")
            }
            if (report.skipped > 0) {
                add("skipped ${report.skipped}")
            }
            if (report.unsupported > 0) {
                add("${report.unsupported} unsupported")
            }
        }
        val summary = parts.joinToString(", ")
        return if (report.conflicts.isEmpty()) {
            "Restore complete: $summary"
        } else {
            "Restore complete with ${report.conflicts.size} conflict${plural(report.conflicts.size)}: $summary"
        }
    }

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}
