package top.focess.keystead.client

import java.awt.Image
import java.awt.Taskbar
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object KeysteadBrand {
    const val IconResource = "keystead-icon.png"

    fun loadIconImage(): BufferedImage {
        val stream =
            checkNotNull(KeysteadBrand::class.java.classLoader.getResourceAsStream(IconResource)) {
                "The Keystead app icon is missing from the classpath"
            }
        return stream.use {
            checkNotNull(ImageIO.read(it)) {
                "The Keystead app icon is not a readable image"
            }
        }
    }

    internal fun installDesktopIcon(
        image: Image,
        supported: Boolean =
            Taskbar.isTaskbarSupported() &&
                Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE),
        setter: (Image) -> Unit = { Taskbar.getTaskbar().iconImage = it },
    ): Boolean {
        if (!supported) return false
        setter(image)
        return true
    }
}
