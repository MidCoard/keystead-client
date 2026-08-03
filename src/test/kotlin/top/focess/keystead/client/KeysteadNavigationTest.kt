package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.client.ui.KeysteadDestination
import top.focess.keystead.client.ui.KeysteadRailPresentation
import top.focess.keystead.client.ui.KeysteadZone
import top.focess.keystead.client.ui.RecoveryHubPresentation
import top.focess.keystead.client.ui.RecoveryMethod

class KeysteadNavigationTest {
    @Test
    fun visibleDestinationsUseTheApprovedZoneOrder() {
        assertEquals(
            listOf(
                KeysteadDestination.SECRETS,
                KeysteadDestination.BACKUP,
                KeysteadDestination.RECOVERY,
                KeysteadDestination.DEVICE_ACCESS,
                KeysteadDestination.ACCOUNT,
                KeysteadDestination.SYNC,
                KeysteadDestination.SHARE,
                KeysteadDestination.SETTINGS,
            ),
            KeysteadDestination.visibleEntries,
        )
        assertEquals(
            listOf(
                KeysteadZone.LOCAL_VAULT,
                KeysteadZone.CONNECTED,
                KeysteadZone.SYSTEM,
            ),
            KeysteadDestination.visibleEntries.map(KeysteadDestination::zone).distinct(),
        )
    }

    @Test
    fun addSecretIsAnInternalRoute() {
        assertEquals(KeysteadZone.INTERNAL, KeysteadDestination.ADD.zone)
        assertFalse(KeysteadDestination.ADD.visibleInSidebar)
        assertFalse(KeysteadDestination.visibleEntries.contains(KeysteadDestination.ADD))
    }

    @Test
    fun railWidthAndZoneLabelsStayStableAcrossConnectionChanges() {
        assertEquals(192, KeysteadRailPresentation.widthDp)
        assertFalse(KeysteadRailPresentation.connectionLabelChangesLayout)
        assertTrue(KeysteadRailPresentation.hasStrongZoneDividers)
        assertTrue(KeysteadRailPresentation.destinationsScrollVertically)
        assertTrue(KeysteadRailPresentation.lockActionStaysVisible)
        assertTrue(KeysteadRailPresentation.usesHorizontalRows)
        assertTrue(KeysteadRailPresentation.usesExplicitHighContrastText)
        assertEquals(16, KeysteadRailPresentation.labelFontSizeSp)
    }

    @Test
    fun lockedNavigationRequiresAnOpenVaultForSync() {
        assertTrue(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.SECRETS,
            ),
        )
        assertFalse(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.BACKUP,
            ),
        )
        assertFalse(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.SYNC,
            ),
        )
        assertFalse(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.SHARE,
            ),
        )
        assertTrue(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.RECOVERY,
            ),
        )
        assertFalse(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.DEVICE_ACCESS,
            ),
        )
        assertTrue(
            KeysteadRailPresentation.destinationEnabled(
                vaultOpen = false,
                destination = KeysteadDestination.ACCOUNT,
            ),
        )
    }

    @Test
    fun lockedContentRouterReplacesSyncWithTheUnlockScreen() {
        assertTrue(
            KeysteadRailPresentation.usesUnlockContent(
                vaultOpen = false,
                destination = KeysteadDestination.SYNC,
            ),
        )
        assertTrue(
            KeysteadRailPresentation.usesUnlockContent(
                vaultOpen = false,
                destination = KeysteadDestination.SHARE,
            ),
        )
        assertTrue(
            KeysteadRailPresentation.usesUnlockContent(
                vaultOpen = false,
                destination = KeysteadDestination.BACKUP,
            ),
        )
    }

    @Test
    fun recoveryHubOffersEveryRecoverySourceFromOneRoute() {
        assertEquals(
            listOf(
                RecoveryMethod.PORTABLE_BACKUP,
                RecoveryMethod.SERVER,
            ),
            RecoveryHubPresentation.methods,
        )
        assertEquals(RecoveryMethod.PORTABLE_BACKUP, RecoveryHubPresentation.defaultMethod)
    }
}
