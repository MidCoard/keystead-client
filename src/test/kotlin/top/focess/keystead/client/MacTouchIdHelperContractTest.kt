package top.focess.keystead.client

import java.nio.file.Path
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacTouchIdHelperContractTest {
    @Test
    fun packagedHelperIsResolvedFromTheMergedApplicationResourcesDirectory() {
        val resources = Files.createTempDirectory("keystead-app-resources")
        val helper = Files.createFile(resources.resolve("keystead-mac-secure-store"))
        val previous = System.getProperty("compose.application.resources.dir")
        try {
            System.setProperty("compose.application.resources.dir", resources.toString())
            assertEquals(helper, MacTouchIdHelperLocator.resolve(resources.resolve("fallback")))
        } finally {
            if (previous == null) System.clearProperty("compose.application.resources.dir")
            else System.setProperty("compose.application.resources.dir", previous)
        }
    }

    @Test
    fun availabilityCommandUsesTheDocumentedExitProtocol() {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return
        val helper = Path.of(checkNotNull(System.getProperty("keystead.mac.touch-id.helper")))
        val process = ProcessBuilder(helper.toString(), "availability").start()

        assertTrue(process.waitFor(15, TimeUnit.SECONDS))
        assertTrue(process.exitValue() in setOf(0, 10, 11, 12, 13))
        assertEquals(0, process.inputStream.readAllBytes().size)
    }

    @Test
    fun unsignedHelperCanStoreAndDeleteAKeychainSecret() {
        if (!System.getProperty("os.name").lowercase().contains("mac")) return
        val helper = Path.of(checkNotNull(System.getProperty("keystead.mac.touch-id.helper")))
        val account = "keystead-test-${UUID.randomUUID()}"
        val secret = ByteArray(32) { it.toByte() }
        try {
            val save = ProcessBuilder(helper.toString(), "save", account).start()
            save.outputStream.use { it.write(secret) }
            assertTrue(save.waitFor(15, TimeUnit.SECONDS))
            assertEquals(
                0,
                save.exitValue(),
                save.errorStream.readAllBytes().toString(Charsets.US_ASCII),
            )
        } finally {
            val delete = ProcessBuilder(helper.toString(), "delete", account).start()
            delete.outputStream.close()
            assertTrue(delete.waitFor(15, TimeUnit.SECONDS))
            assertTrue(delete.exitValue() in setOf(0, 44))
        }
    }
}
