package top.focess.keystead.client

import java.awt.Image
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeysteadBrandTest {
    @Test
    fun installsBrandImageWhenDesktopIconIsSupported() {
        val image = KeysteadBrand.loadIconImage()
        var installedImage: Image? = null

        val installed =
            KeysteadBrand.installDesktopIcon(
                image = image,
                supported = true,
                setter = { installedImage = it },
            )

        assertTrue(installed)
        assertSame(image, installedImage)
    }

    @Test
    fun skipsDesktopIconWhenPlatformDoesNotSupportIt() {
        val image = KeysteadBrand.loadIconImage()
        var setterCalled = false

        val installed =
            KeysteadBrand.installDesktopIcon(
                image = image,
                supported = false,
                setter = { setterCalled = true },
            )

        assertFalse(installed)
        assertFalse(setterCalled)
    }
}
