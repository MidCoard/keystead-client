package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertContains
import top.focess.keystead.client.i18n.EnStrings

class RecordComparisonStringsTest {
    @Test
    fun contentHashComparisonKeepsBothCompleteHashes() {
        val localHash = "local-abcdefghijklmnopqrstuvwxyz-0123456789"
        val serverHash = "server-ABCDEFGHIJKLMNOPQRSTUVWXYZ-9876543210"

        val rendered = EnStrings.recordContentHashes(localHash, serverHash)

        assertContains(rendered, localHash)
        assertContains(rendered, serverHash)
    }
}
