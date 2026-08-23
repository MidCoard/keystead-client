package top.focess.keystead.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class CenteredContentPresentationTest {
    @Test
    fun wideContentUsesTheStableMaximumWidth() {
        assertEquals(560f, CenteredContentPresentation.widthDp(900f, 560f))
    }

    @Test
    fun narrowContentUsesAllAvailableWidth() {
        assertEquals(420f, CenteredContentPresentation.widthDp(420f, 560f))
    }
}
