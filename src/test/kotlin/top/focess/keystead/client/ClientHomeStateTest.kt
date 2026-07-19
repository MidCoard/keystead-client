package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientHomeStateTest {
    @Test
    fun signedInStateRequiresActiveUser() {
        assertFalse(ClientHomePreview.disconnected().isSignedIn)
        assertTrue(ClientHomePreview.signedIn().isSignedIn)
    }
}
