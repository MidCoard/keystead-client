package top.focess.keystead.client

import top.focess.keystead.generator.DefaultPasswordGenerator
import top.focess.keystead.generator.PasswordPolicy

object PasswordDraftGenerator {
    private const val DefaultLength = 24
    private val generator = DefaultPasswordGenerator()
    private val policy =
        PasswordPolicy(
            DefaultLength,
            true,
            true,
            true,
            true,
            true,
            emptySet(),
        )

    fun generate(): String {
        generator.generate(policy).use { buffer ->
            var generated = CharArray(0)
            buffer.copyChars { chars -> generated = chars.copyOf() }
            return try {
                String(generated)
            } finally {
                generated.fill('\u0000')
            }
        }
    }
}
