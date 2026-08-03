package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import top.focess.keystead.client.ui.KeysteadDestination

class PageOwnershipTest {
    @Test
    fun everyVisibleDestinationOwnsOnlyItsApprovedCapabilities() {
        assertEquals(
            setOf(PageCapability.SECRET_MANAGEMENT),
            PageOwnership.capabilities(KeysteadDestination.SECRETS),
        )
        assertEquals(
            setOf(PageCapability.PORTABLE_BACKUP_CREATE),
            PageOwnership.capabilities(KeysteadDestination.BACKUP),
        )
        assertEquals(
            setOf(PageCapability.LOCAL_VAULT_LOGIN),
            PageOwnership.capabilities(KeysteadDestination.DEVICE_ACCESS),
        )
        assertEquals(
            setOf(PageCapability.SERVER_ACCOUNT),
            PageOwnership.capabilities(KeysteadDestination.ACCOUNT),
        )
        assertEquals(
            setOf(PageCapability.SERVER_VAULT_SYNC),
            PageOwnership.capabilities(KeysteadDestination.SYNC),
        )
        assertEquals(
            setOf(PageCapability.SECRET_SHARING),
            PageOwnership.capabilities(KeysteadDestination.SHARE),
        )
        assertEquals(
            setOf(
                PageCapability.PORTABLE_BACKUP_RESTORE,
                PageCapability.SERVER_VAULT_RESTORE,
                PageCapability.DEVICE_VAULT_ACCESS_APPROVAL,
            ),
            PageOwnership.capabilities(KeysteadDestination.RECOVERY),
        )
        assertEquals(
            setOf(PageCapability.APP_SETTINGS),
            PageOwnership.capabilities(KeysteadDestination.SETTINGS),
        )
    }

    @Test
    fun secretEditorIsOwnedByTheHiddenAddRoute() {
        assertEquals(
            setOf(PageCapability.SECRET_EDITOR),
            PageOwnership.capabilities(KeysteadDestination.ADD),
        )
    }
}
