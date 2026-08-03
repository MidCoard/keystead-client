package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BrandAssetTest {
    @Test
    fun appIconHasTransparentCornersAndAnOpaqueCenter() {
        val image = assertNotNull(KeysteadBrand.loadIconImage())
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.colorModel.hasAlpha())
        assertEquals(0, image.getRGB(0, 0) ushr 24)
        assertTrue(image.getRGB(256, 256) ushr 24 >= 240)
    }
}
