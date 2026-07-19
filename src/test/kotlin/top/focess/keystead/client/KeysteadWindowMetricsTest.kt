package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KeysteadWindowMetricsTest {
    @Test
    fun desktopWindowHasUsefulMinimumSize() {
        assertTrue(KeysteadWindowMetrics.MinimumWidthDp >= 960)
        assertTrue(KeysteadWindowMetrics.MinimumHeightDp >= 680)
    }

    @Test
    fun layoutModeChangesBeforeColumnsBecomeCramped() {
        assertEquals(KeysteadLayoutMode.COMPACT, KeysteadWindowMetrics.modeForWidth(960f))
        assertEquals(KeysteadLayoutMode.COMPACT, KeysteadWindowMetrics.modeForWidth(1119f))
        assertEquals(KeysteadLayoutMode.WIDE, KeysteadWindowMetrics.modeForWidth(1120f))
    }
}
