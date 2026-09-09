package top.focess.keystead.client

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import java.awt.EventQueue
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import top.focess.keystead.client.i18n.Strings

/** Native Windows menus need their own font; Compose's text fallback does not apply. */
@Composable
internal fun WindowsTray(
    image: Image,
    strings: Strings,
    onOpen: () -> Unit,
    onLock: () -> Unit,
    onQuit: () -> Unit,
) {
    val openAction = rememberUpdatedState(onOpen)
    val lockAction = rememberUpdatedState(onLock)
    val quitAction = rememberUpdatedState(onQuit)
    val tray = remember(image) {
        WindowsTrayMenu(image, { openAction.value() }, { lockAction.value() }, { quitAction.value() })
    }
    DisposableEffect(tray) {
        onTrayEventThread {
            tray.update(strings)
            SystemTray.getSystemTray().add(tray.icon)
        }
        onDispose { onTrayEventThread { SystemTray.getSystemTray().remove(tray.icon) } }
    }
    SideEffect { onTrayEventThread { tray.update(strings) } }
}

private class WindowsTrayMenu(image: Image, onOpen: () -> Unit, onLock: () -> Unit, onQuit: () -> Unit) {
    private val open = MenuItem().apply { addActionListener { onOpen() } }
    private val lock = MenuItem().apply { addActionListener { onLock() } }
    private val quit = MenuItem().apply { addActionListener { onQuit() } }
    private val menu = PopupMenu().apply {
        add(open)
        add(lock)
        addSeparator()
        add(quit)
    }
    val icon = TrayIcon(image, "Keystead", menu).apply {
        isImageAutoSize = true
        addActionListener { onOpen() }
    }
    private var labels: List<String>? = null

    fun update(strings: Strings) {
        val updatedLabels = listOf(strings.openApplication, strings.lock, strings.quit)
        if (labels == updatedLabels) return
        labels = updatedLabels
        val font = windowsTrayFont(updatedLabels.joinToString(""))
        menu.font = font
        listOf(open, lock, quit).zip(updatedLabels).forEach { (item, label) ->
            item.font = font
            item.label = label
        }
        icon.toolTip = strings.appTitle
    }
}

internal fun windowsTrayFont(text: String): Font {
    val systemFont = Toolkit.getDefaultToolkit().getDesktopProperty("win.menu.font") as? Font
        ?: Font(Font.DIALOG, Font.PLAIN, 12)
    val families = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    // Prefer a physical CJK font: native AWT menus cannot rely on Compose/Skia fallback.
    val candidates = listOf("Microsoft YaHei UI", "Microsoft YaHei", "SimSun")
        .filter { it in families }
        .map { Font(it, systemFont.style, systemFont.size).deriveFont(systemFont.size2D) }
    return (candidates + listOf(systemFont) + families.sorted().map { Font(it, systemFont.style, systemFont.size) })
        .firstOrNull { it.canDisplayUpTo(text) == -1 }
        ?: Font(Font.DIALOG, systemFont.style, systemFont.size)
}

private fun onTrayEventThread(action: () -> Unit) {
    if (EventQueue.isDispatchThread()) action() else EventQueue.invokeAndWait(action)
}
