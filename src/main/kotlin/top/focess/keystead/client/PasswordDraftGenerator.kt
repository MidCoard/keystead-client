package top.focess.keystead.client

import top.focess.keystead.generator.DefaultPasswordGenerator
import top.focess.keystead.generator.PasswordPolicy

internal const val STANDARD_PASSWORD_SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?/"

internal data class PasswordGeneratorOptions(
    val length: Int = 24,
    val uppercase: Boolean = true,
    val lowercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = true,
    val allowedSymbols: Set<Char> = STANDARD_PASSWORD_SYMBOLS.toSet(),
) {
    private val effectiveAllowedSymbols: Set<Char>
        get() = allowedSymbols intersect STANDARD_PASSWORD_SYMBOLS.toSet()

    val enabledGroupCount: Int
        get() = listOf(uppercase, lowercase, digits, symbols).count { it }

    val isValid: Boolean
        get() =
            length >= enabledGroupCount &&
                enabledGroupCount > 0 &&
                (!symbols || effectiveAllowedSymbols.isNotEmpty())

    internal fun policy(): PasswordPolicy {
        require(isValid) { "Password generator options are invalid" }
        val excludedSymbols = STANDARD_PASSWORD_SYMBOLS.toSet() - effectiveAllowedSymbols
        return PasswordPolicy(
            length,
            uppercase,
            lowercase,
            digits,
            symbols,
            avoidAmbiguous,
            excludedSymbols,
        )
    }
}

internal object PasswordDraftGenerator {
    private val generator = DefaultPasswordGenerator()

    fun generate(
        options: PasswordGeneratorOptions = PasswordGeneratorOptions(),
        consume: (CharArray) -> Unit,
    ) {
        generator.generate(options.policy()).use { buffer ->
            buffer.copyChars { chars -> consume(chars) }
        }
    }

    /** Convenience for non-UI generators whose downstream API still requires an immutable String. */
    fun generate(): String {
        var result = ""
        generate { chars -> result = String(chars) }
        return result
    }
}
