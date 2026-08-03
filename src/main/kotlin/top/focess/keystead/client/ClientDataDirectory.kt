package top.focess.keystead.client

import java.nio.file.Path

internal object ClientDataDirectory {
    private const val OVERRIDE_ENVIRONMENT = "KEYSTEAD_CLIENT_HOME"

    fun resolve(
        configuredOverride: String? = System.getenv(OVERRIDE_ENVIRONMENT),
        userHome: String = System.getProperty("user.home"),
    ): Path =
        configuredOverride
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let(Path::of)
            ?.toAbsolutePath()
            ?.normalize()
            ?: Path.of(userHome, ".keystead-client").toAbsolutePath().normalize()
}
