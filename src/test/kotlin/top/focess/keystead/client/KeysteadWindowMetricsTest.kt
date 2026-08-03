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
    fun minimumAwtWindowSizeHonoursWindowsDisplayScaling() {
        assertEquals(1200, KeysteadWindowMetrics.minimumWidthPixels(1.25))
        assertEquals(850, KeysteadWindowMetrics.minimumHeightPixels(1.25))
        assertEquals(960, KeysteadWindowMetrics.minimumWidthPixels(1.0))
        assertEquals(680, KeysteadWindowMetrics.minimumHeightPixels(1.0))
    }

    @Test
    fun layoutModeChangesBeforeColumnsBecomeCramped() {
        assertEquals(KeysteadLayoutMode.COMPACT, KeysteadWindowMetrics.modeForWidth(960f))
        assertEquals(KeysteadLayoutMode.COMPACT, KeysteadWindowMetrics.modeForWidth(1119f))
        assertEquals(KeysteadLayoutMode.WIDE, KeysteadWindowMetrics.modeForWidth(1120f))
    }
}
