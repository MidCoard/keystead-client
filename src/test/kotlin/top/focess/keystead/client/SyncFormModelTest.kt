package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncFormModelTest {
    @Test
    fun serverLoginRequiresOnlineServerUrlAndCredentials() {
        assertTrue(
            SyncFormModel.canLogin(
                serverUrl = "http://localhost:22144",
                username = "alice",
                password = "secret",
                serverAvailable = true,
            ),
        )
        assertFalse(SyncFormModel.canLogin("", "alice", "secret", serverAvailable = true))
        assertFalse(SyncFormModel.canLogin("http://localhost:22144", "", "secret", serverAvailable = true))
        assertFalse(SyncFormModel.canLogin("http://localhost:22144", "alice", "", serverAvailable = true))
        assertFalse(SyncFormModel.canLogin("http://localhost:22144", "alice", "secret", serverAvailable = false))
    }

    @Test
    fun protectedServerActionsRequireOnlineServerAndAuthenticatedSession() {
        assertTrue(SyncFormModel.canUseServer(authenticated = true, serverAvailable = true))
        assertFalse(SyncFormModel.canUseServer(authenticated = false, serverAvailable = true))
        assertFalse(SyncFormModel.canUseServer(authenticated = true, serverAvailable = false))
    }

    @Test
    fun connectionSettingsStayEditableWhenTheCurrentServerIsOffline() {
        assertTrue(
            SyncFormModel.canEditConnection(
                authenticated = true,
                serverAvailable = false,
            ),
        )
        assertTrue(
            SyncFormModel.canEditConnection(
                authenticated = false,
                serverAvailable = true,
            ),
        )
        assertFalse(
            SyncFormModel.canEditConnection(
                authenticated = true,
                serverAvailable = true,
            ),
        )
    }

    @Test
    fun serverRegistrationRequiresUrlUsernameAndServerPasswordLength() {
        assertTrue(
            SyncFormModel.canRegisterUser(
                serverUrl = "http://localhost:22144",
                username = "alice",
                password = "long-password",
                serverAvailable = true,
            ),
        )
        assertFalse(SyncFormModel.canRegisterUser("", "alice", "long-password", serverAvailable = true))
        assertFalse(SyncFormModel.canRegisterUser("http://localhost:22144", "", "long-password", serverAvailable = true))
        assertFalse(SyncFormModel.canRegisterUser("http://localhost:22144", "alice", "short", serverAvailable = true))
        assertFalse(SyncFormModel.canRegisterUser("http://localhost:22144", "alice", "long-password", serverAvailable = false))
    }

    @Test
    fun sinceRevisionBlankDefaultsToZeroAndRejectsInvalidValues() {
        assertEquals(0, SyncFormModel.sinceRevisionOrNull(""))
        assertEquals(0, SyncFormModel.sinceRevisionOrNull(" 0 "))
        assertEquals(42, SyncFormModel.sinceRevisionOrNull("42"))
        assertNull(SyncFormModel.sinceRevisionOrNull("-1"))
        assertNull(SyncFormModel.sinceRevisionOrNull("abc"))
    }
}
