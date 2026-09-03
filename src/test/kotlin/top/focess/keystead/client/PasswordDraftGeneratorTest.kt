package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `configured generator uses only individually selected symbols`() {
        var rendered = ""
        lateinit var temporaryChars: CharArray

        PasswordDraftGenerator.generate(
            PasswordGeneratorOptions(
                length = 32,
                uppercase = false,
                lowercase = false,
                digits = false,
                symbols = true,
                allowedSymbols = setOf('!', '?'),
            ),
        ) { chars ->
            temporaryChars = chars
            rendered = String(chars)
        }

        assertEquals(32, rendered.length)
        assertTrue(rendered.all { it == '!' || it == '?' })
        assertTrue(temporaryChars.all { it == '\u0000' })
    }

    @Test
    fun `generator options reject an empty character policy`() {
        val options =
            PasswordGeneratorOptions(
                uppercase = false,
                lowercase = false,
                digits = false,
                symbols = false,
            )

        assertFalse(options.isValid)
    }
}
