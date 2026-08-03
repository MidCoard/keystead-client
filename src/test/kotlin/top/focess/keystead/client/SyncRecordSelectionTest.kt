package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncRecordSelectionTest {
    private val choices =
        listOf(
            SyncRecordChoice("local-only", canUpload = true, canRemoveFromServer = false),
            SyncRecordChoice("server-only", canUpload = false, canRemoveFromServer = true),
            SyncRecordChoice("both", canUpload = true, canRemoveFromServer = true),
        )

    @Test
    fun selectedActionsContainOnlyRecordsEligibleForThatAction() {
        val selection = SyncRecordSelection().selectAll(choices)

        assertEquals(setOf("local-only", "both"), selection.uploadableIds(choices))
        assertEquals(setOf("server-only", "both"), selection.removableIds(choices))
    }

    @Test
    fun togglingAndReconcilingNeverRetainsRowsThatAreNoLongerVisible() {
        val selected =
            SyncRecordSelection()
                .toggle("local-only")
                .toggle("both")
                .reconcile(choices.filterNot { it.secretId == "both" })

        assertEquals(setOf("local-only"), selected.selectedIds)
        assertTrue(selected.toggle("local-only").selectedIds.isEmpty())
        assertTrue(selected.clear().selectedIds.isEmpty())
    }
}
