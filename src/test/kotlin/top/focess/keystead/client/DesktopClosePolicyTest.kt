package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopClosePolicyTest {
    @Test
    fun closeLocksAndHidesWhenATrayIsAvailable() {
        assertEquals(DesktopCloseAction.LOCK_AND_HIDE, DesktopClosePolicy.action(traySupported = true))
    }

    @Test
    fun closeExitsWhenThePlatformHasNoTray() {
        assertEquals(DesktopCloseAction.EXIT, DesktopClosePolicy.action(traySupported = false))
    }

    @Test
    fun disableTrayArgumentOverridesPlatformSupport() {
        assertFalse(DesktopTrayPolicy.isEnabled(true, listOf("--disable-tray")))
    }

    @Test
    fun trayStaysEnabledWithoutTheDisableArgument() {
        assertTrue(DesktopTrayPolicy.isEnabled(true, emptyList()))
    }

    @Test
    fun trayCannotBeEnabledOnAnUnsupportedPlatform() {
        assertFalse(DesktopTrayPolicy.isEnabled(false, emptyList()))
    }
}
