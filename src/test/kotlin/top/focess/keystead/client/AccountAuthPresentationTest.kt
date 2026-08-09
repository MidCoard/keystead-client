package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountAuthPresentationTest {
    @Test
    fun `account entry exposes sign in and create account as peer modes`() {
        assertEquals(
            listOf(AccountAuthMode.SIGN_IN, AccountAuthMode.CREATE_ACCOUNT),
            AccountAuthPresentation.modes,
        )
        assertEquals(AccountAuthMode.SIGN_IN, AccountAuthPresentation.defaultMode)
    }

    @Test
    fun `sign in requires an online server and nonblank credentials`() {
        assertTrue(
            AccountAuthPresentation.canSubmit(
                mode = AccountAuthMode.SIGN_IN,
                serverUrl = "http://localhost:22144",
                username = "alice",
                password = "secret",
                passwordConfirmation = "",
                serverAvailable = true,
                authenticated = false,
            ),
        )
        assertFalse(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.SIGN_IN,
                "http://localhost:22144",
                "alice",
                "secret",
                "",
                serverAvailable = false,
                authenticated = false,
            ),
        )
    }

    @Test
    fun `create account requires a twelve character password and matching confirmation`() {
        assertTrue(
            AccountAuthPresentation.canSubmit(
                mode = AccountAuthMode.CREATE_ACCOUNT,
                serverUrl = "http://localhost:22144",
                username = "alice",
                password = "long-password",
                passwordConfirmation = "long-password",
                serverAvailable = true,
                authenticated = false,
            ),
        )
        assertFalse(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.CREATE_ACCOUNT,
                "http://localhost:22144",
                "alice",
                "short",
                "short",
                serverAvailable = true,
                authenticated = false,
            ),
        )
        assertFalse(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.CREATE_ACCOUNT,
                "http://localhost:22144",
                "alice",
                "long-password",
                "different-password",
                serverAvailable = true,
                authenticated = false,
            ),
        )
        assertFalse(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.CREATE_ACCOUNT,
                "http://localhost:22144",
                "alice",
                "long-password",
                "long-password",
                serverAvailable = true,
                authenticated = true,
            ),
        )
    }

    @Test
    fun `create account rejects passwords beyond the bcrypt utf8 byte limit`() {
        val exactLimit = "a".repeat(72)
        val beyondByteLimit = "密".repeat(25)

        assertTrue(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.CREATE_ACCOUNT,
                "http://localhost:22144",
                "alice",
                exactLimit,
                exactLimit,
                serverAvailable = true,
                authenticated = false,
            ),
        )
        assertFalse(
            AccountAuthPresentation.canSubmit(
                AccountAuthMode.CREATE_ACCOUNT,
                "http://localhost:22144",
                "alice",
                beyondByteLimit,
                beyondByteLimit,
                serverAvailable = true,
                authenticated = false,
            ),
        )
    }

    @Test
    fun `editing the form or changing modes clears the inline failure`() {
        val failed = AccountAuthUiState().withFailure("Server rejected the credentials")

        assertEquals("Server rejected the credentials", failed.failure)
        assertNull(failed.onInputChanged().failure)
        assertEquals(AccountAuthMode.CREATE_ACCOUNT, failed.select(AccountAuthMode.CREATE_ACCOUNT).mode)
        assertNull(failed.select(AccountAuthMode.CREATE_ACCOUNT).failure)
    }

    @Test
    fun `session management stays out of the unauthenticated form`() {
        assertFalse(AccountAuthPresentation.showSessionManagement(authenticated = false))
        assertTrue(AccountAuthPresentation.showSessionManagement(authenticated = true))
    }
}
