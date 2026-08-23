package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.ZhStrings

class MacTouchIdSecretStoreTest {
    @Test
    fun storesAndLoadsSecretsThroughTheTouchIdPort() {
        val port = FakeMacTouchIdPort()
        val store = MacTouchIdSecretStore(port) { ZhStrings.touchIdAuthenticationReason }
        val secret = ByteArray(32) { it.toByte() }

        store.save("desktop", secret)

        assertEquals("mac-touch-id", store.providerId)
        assertContentEquals(secret, store.load("desktop"))
        assertEquals("使用 Touch ID 解锁 Keystead。", port.lastAuthenticationReason)
        store.delete("desktop")
        assertEquals(null, store.load("desktop"))
    }

    @Test
    fun touchIdSystemNoticeHasConciseEnglishAndChineseVersions() {
        assertEquals("Unlock Keystead with Touch ID.", EnStrings.touchIdAuthenticationReason)
        assertEquals("使用 Touch ID 解锁 Keystead。", ZhStrings.touchIdAuthenticationReason)
    }

    private class FakeMacTouchIdPort : MacTouchIdPort {
        private val values = mutableMapOf<String, ByteArray>()
        var lastAuthenticationReason: String? = null
            private set

        override fun availability() =
            OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "touch-id-available")

        override fun save(account: String, secret: ByteArray) {
            values[account] = secret.copyOf()
        }

        override fun load(account: String, authenticationReason: String): ByteArray? {
            lastAuthenticationReason = authenticationReason
            return values[account]?.copyOf()
        }

        override fun delete(account: String) {
            values.remove(account)
        }
    }
}
