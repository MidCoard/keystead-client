package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncFormModelTest {
    @Test
    fun serverLoginRequiresUrlAndCredentials() {
        assertTrue(
            SyncFormModel.canLogin(
                serverUrl = "http://localhost:8080",
                username = "alice",
                password = "secret",
            ),
        )
        assertFalse(SyncFormModel.canLogin("", "alice", "secret"))
        assertFalse(SyncFormModel.canLogin("http://localhost:8080", "", "secret"))
        assertFalse(SyncFormModel.canLogin("http://localhost:8080", "alice", ""))
        assertTrue(
            SyncFormModel.canLoginWithDevice(
                "http://localhost:8080",
                "alice",
                "secret",
                identityLoaded = true,
            ),
        )
        assertFalse(
            SyncFormModel.canLoginWithDevice(
                "http://localhost:8080",
                "alice",
                "secret",
                identityLoaded = false,
            ),
        )
    }

    @Test
    fun protectedServerActionsRequireAuthenticatedSession() {
        assertTrue(SyncFormModel.canUseServer(authenticated = true))
        assertFalse(SyncFormModel.canUseServer(authenticated = false))
    }

    @Test
    fun serverRegistrationRequiresUrlUsernameAndServerPasswordLength() {
        assertTrue(
            SyncFormModel.canRegisterUser(
                serverUrl = "http://localhost:8080",
                username = "alice",
                password = "long-password",
            ),
        )
        assertFalse(SyncFormModel.canRegisterUser("", "alice", "long-password"))
        assertFalse(SyncFormModel.canRegisterUser("http://localhost:8080", "", "long-password"))
        assertFalse(SyncFormModel.canRegisterUser("http://localhost:8080", "alice", "short"))
    }

    @Test
    fun serverVaultCreationRequiresOpenVaultAndAuthenticatedSession() {
        assertTrue(
            SyncFormModel.canCreateServerVault(
                vaultOpen = true,
                authenticated = true,
            ),
        )
        assertFalse(SyncFormModel.canCreateServerVault(vaultOpen = false, authenticated = true))
        assertFalse(SyncFormModel.canCreateServerVault(vaultOpen = true, authenticated = false))
    }

    @Test
    fun deviceIdentityRequiresDeviceIdAndPassphrase() {
        assertTrue(SyncFormModel.canLoadIdentity("laptop-1", "device-passphrase"))
        assertFalse(SyncFormModel.canLoadIdentity("", "device-passphrase"))
        assertFalse(SyncFormModel.canLoadIdentity("laptop-1", ""))
    }

    @Test
    fun deviceEnrollmentRequiresAuthenticationAndLoadedIdentityButNotOpenVault() {
        assertTrue(SyncFormModel.canEnrollDevice(authenticated = true, identityLoaded = true))
        assertFalse(SyncFormModel.canEnrollDevice(authenticated = false, identityLoaded = true))
        assertFalse(SyncFormModel.canEnrollDevice(authenticated = true, identityLoaded = false))
    }

    @Test
    fun deviceRevocationRequiresRegisteredIdentityAndPackagePublicationRequiresVault() {
        assertTrue(
            SyncFormModel.canRevokeDevice(
                authenticated = true,
                identityLoaded = true,
                registered = true,
            ),
        )
        assertFalse(SyncFormModel.canRevokeDevice(true, true, false))
        assertFalse(SyncFormModel.canRevokeDevice(true, false, true))
        assertFalse(SyncFormModel.canRevokeDevice(false, true, true))
        assertTrue(SyncFormModel.canPublishKeyPackages(vaultOpen = true, authenticated = true))
        assertFalse(SyncFormModel.canPublishKeyPackages(vaultOpen = false, authenticated = true))
        assertFalse(SyncFormModel.canPublishKeyPackages(vaultOpen = true, authenticated = false))
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
