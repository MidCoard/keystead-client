package top.focess.keystead.client

import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncStateStoreTest {
    @Test
    fun remembersPushAndPullRevisionsPerVault() {
        val directory = createTempDirectory("keystead-sync-state-test")
        val store = SyncStateStore(directory)
        val firstVault = UUID.fromString("71000000-0000-0000-0000-000000000001").toString()
        val secondVault = UUID.fromString("71000000-0000-0000-0000-000000000002").toString()

        assertEquals(0, store.lastPushedRevision(firstVault))
        assertEquals(0, store.lastPulledRevision(firstVault))

        store.recordPushed(firstVault, 7)
        store.recordPulled(firstVault, 9)
        store.recordPushed(secondVault, 3)

        val reloaded = SyncStateStore(directory)
        assertEquals(7, reloaded.lastPushedRevision(firstVault))
        assertEquals(9, reloaded.lastPulledRevision(firstVault))
        assertEquals(3, reloaded.lastPushedRevision(secondVault))
        assertEquals(0, reloaded.lastPulledRevision(secondVault))
    }
}
