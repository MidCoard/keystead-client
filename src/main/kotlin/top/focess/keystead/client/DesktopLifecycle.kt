package top.focess.keystead.client

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
    internal var onLockVault: () -> Unit = {}

    fun lockVault() = onLockVault()
}
