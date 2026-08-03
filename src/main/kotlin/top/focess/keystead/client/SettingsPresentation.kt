package top.focess.keystead.client

internal data class SettingsPresentation(
    val canDeleteVaultFile: Boolean,
) {
    companion object {
        fun derive(
            vaultOpen: Boolean,
            vaultFileExists: Boolean,
        ): SettingsPresentation =
            SettingsPresentation(
                canDeleteVaultFile = vaultOpen && vaultFileExists,
            )
    }
}
