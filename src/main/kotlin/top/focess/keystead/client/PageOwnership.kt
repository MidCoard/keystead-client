package top.focess.keystead.client

import top.focess.keystead.client.ui.KeysteadDestination

enum class PageCapability {
    SECRET_MANAGEMENT,
    SECRET_EDITOR,
    PORTABLE_BACKUP_CREATE,
    PORTABLE_BACKUP_RESTORE,
    LOCAL_VAULT_LOGIN,
    SERVER_ACCOUNT,
    SERVER_VAULT_SYNC,
    SERVER_VAULT_RESTORE,
    DEVICE_VAULT_ACCESS_APPROVAL,
    SECRET_SHARING,
    APP_SETTINGS,
}

object PageOwnership {
    fun capabilities(destination: KeysteadDestination): Set<PageCapability> =
        when (destination) {
            KeysteadDestination.SECRETS -> setOf(PageCapability.SECRET_MANAGEMENT)
            KeysteadDestination.ADD -> setOf(PageCapability.SECRET_EDITOR)
            KeysteadDestination.BACKUP -> setOf(PageCapability.PORTABLE_BACKUP_CREATE)
            KeysteadDestination.DEVICE_ACCESS ->
                setOf(PageCapability.LOCAL_VAULT_LOGIN)
            KeysteadDestination.ACCOUNT ->
                setOf(PageCapability.SERVER_ACCOUNT)
            KeysteadDestination.SYNC -> setOf(PageCapability.SERVER_VAULT_SYNC)
            KeysteadDestination.SHARE -> setOf(PageCapability.SECRET_SHARING)
            KeysteadDestination.RECOVERY ->
                setOf(
                    PageCapability.PORTABLE_BACKUP_RESTORE,
                    PageCapability.SERVER_VAULT_RESTORE,
                    PageCapability.DEVICE_VAULT_ACCESS_APPROVAL,
                )
            KeysteadDestination.SETTINGS -> setOf(PageCapability.APP_SETTINGS)
        }
}
