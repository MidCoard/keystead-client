package top.focess.keystead.client

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class ClientDataDirectoryTest {
    @Test
    fun explicitClientHomeSupportsIsolatedDesktopSessions() {
        assertEquals(
            Path.of("D:/keystead-test/client-a").toAbsolutePath().normalize(),
            ClientDataDirectory.resolve(" D:/keystead-test/client-a ", "D:/ignored"),
        )
    }

    @Test
    fun defaultClientHomeRemainsUnderTheUserHome() {
        assertEquals(
            Path.of("D:/person", ".keystead-client").toAbsolutePath().normalize(),
            ClientDataDirectory.resolve(null, "D:/person"),
        )
    }
}
