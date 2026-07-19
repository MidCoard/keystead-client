package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PasswordDraftGeneratorTest {
    @Test
    fun generatedPasswordUsesStrongDefaultPolicy() {
        val password = PasswordDraftGenerator.generate()

        assertEquals(24, password.length)
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }
}
