package top.focess.keystead.client.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class UsernameSuggestionMenuPresentationTest {
    @Test
    fun popupWidthMatchesTheMeasuredUsernameField() {
        assertEquals(310f, UsernameSuggestionMenuPresentation.widthDp(620, 2f))
        assertEquals(480f, UsernameSuggestionMenuPresentation.widthDp(600, 1.25f))
    }
}
