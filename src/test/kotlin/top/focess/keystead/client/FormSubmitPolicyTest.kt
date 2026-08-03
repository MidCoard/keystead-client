package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormSubmitPolicyTest {
    @Test
    fun enabledPrimaryActionSubmitsExactlyOnce() {
        var submissions = 0

        val handled = FormSubmitPolicy.submitIfEnabled(enabled = true) { submissions += 1 }

        assertTrue(handled)
        assertEquals(1, submissions)
    }

    @Test
    fun disabledPrimaryActionDoesNotSubmit() {
        var submissions = 0

        val handled = FormSubmitPolicy.submitIfEnabled(enabled = false) { submissions += 1 }

        assertFalse(handled)
        assertEquals(0, submissions)
    }

    @Test
    fun enabledCtrlEnterConsumesBothEventsAndSubmitsOnlyOnKeyUp() {
        var submissions = 0

        val keyDownHandled =
            FormSubmitPolicy.handleCtrlEnter(
                enabled = true,
                ctrlPressed = true,
                submitKey = true,
                keyUp = false,
            ) { submissions += 1 }
        val keyUpHandled =
            FormSubmitPolicy.handleCtrlEnter(
                enabled = true,
                ctrlPressed = true,
                submitKey = true,
                keyUp = true,
            ) { submissions += 1 }

        assertTrue(keyDownHandled)
        assertTrue(keyUpHandled)
        assertEquals(1, submissions)
    }

    @Test
    fun disabledCtrlEnterDoesNotConsumeOrSubmit() {
        var submissions = 0

        val handled =
            FormSubmitPolicy.handleCtrlEnter(
                enabled = false,
                ctrlPressed = true,
                submitKey = true,
                keyUp = true,
            ) { submissions += 1 }

        assertFalse(handled)
        assertEquals(0, submissions)
    }
}
