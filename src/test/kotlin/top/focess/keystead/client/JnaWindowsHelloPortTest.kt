package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertTrue

class JnaWindowsHelloPortTest {
    @Test
    fun `real availability probe is non interactive and reports a stable Windows Hello state`() {
        if (!System.getProperty("os.name").lowercase().contains("windows")) return

        val result = JnaWindowsHelloPort(windowHandle = { null }).availability()

        assertTrue(
            result.status == OsSecretStoreStatus.AVAILABLE ||
                result.status == OsSecretStoreStatus.UNAVAILABLE ||
                result.status == OsSecretStoreStatus.UNSUPPORTED,
        )
        assertTrue(result.diagnosticCode.startsWith("windows-hello-"))
    }
}
