package top.focess.keystead.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import top.focess.keystead.client.i18n.AppLocale

enum class DesktopCloseAction { LOCK_AND_HIDE, EXIT }

object DesktopTrayPolicy {
    private const val DISABLE_TRAY_ARGUMENT = "--disable-tray"

    fun isEnabled(platformSupported: Boolean, arguments: List<String>): Boolean =
        platformSupported && DISABLE_TRAY_ARGUMENT !in arguments
}

object DesktopClosePolicy {
    fun action(traySupported: Boolean): DesktopCloseAction =
        if (traySupported) DesktopCloseAction.LOCK_AND_HIDE else DesktopCloseAction.EXIT
}

class DesktopAppController {
    internal var locale by mutableStateOf(AppLocale.ENGLISH)
    internal var onLockVault: () -> Unit = {}

    fun lockVault() = onLockVault()
}
