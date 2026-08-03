package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.client.i18n.AppLocale

class ServerFeatureModelTest {
    @Test
    fun connectedNoticeUsesScopedNonErrorPresentation() {
        val online = ServerFeatureModel.connectedNotice(ServerAvailability.ONLINE)
        val checking = ServerFeatureModel.connectedNotice(ServerAvailability.CHECKING)
        val offline = ServerFeatureModel.connectedNotice(ServerAvailability.OFFLINE)

        assertEquals(ConnectedNoticeTone.POSITIVE, online.tone)
        assertFalse(online.retryVisible)

        assertEquals(ConnectedNoticeTone.NEUTRAL, checking.tone)
        assertTrue(checking.retryVisible)
        assertFalse(checking.retryEnabled)

        assertEquals(ConnectedNoticeTone.CAUTION, offline.tone)
        assertTrue(offline.retryVisible)
        assertTrue(offline.retryEnabled)
    }

    @Test
    fun offlineNoticeGivesOnlyActionableServerGuidance() {
        assertEquals(
            "Server unavailable. Check the server address or start the server, then try again.",
            AppLocale.ENGLISH.strings.connectedOfflineHelp,
        )
        assertEquals(
            "服务器不可用。请检查服务器地址或启动服务器，然后重试。",
            AppLocale.CHINESE.strings.connectedOfflineHelp,
        )
    }

    @Test
    fun authenticatedCallsRequireBothSessionAndOnlineServer() {
        assertTrue(
            ServerFeatureModel.canUseAuthenticatedServer(
                ServerAvailability.ONLINE,
                authenticated = true,
            ),
        )
        assertFalse(
            ServerFeatureModel.canUseAuthenticatedServer(
                ServerAvailability.CHECKING,
                authenticated = true,
            ),
        )
        assertFalse(
            ServerFeatureModel.canUseAuthenticatedServer(
                ServerAvailability.OFFLINE,
                authenticated = true,
            ),
        )
        assertFalse(
            ServerFeatureModel.canUseAuthenticatedServer(
                ServerAvailability.ONLINE,
                authenticated = false,
            ),
        )
    }

    @Test
    fun shareMintAndPublicRedeemRequireOnlineServer() {
        assertTrue(
            ServerFeatureModel.canMintShare(
                availability = ServerAvailability.ONLINE,
                authenticated = true,
                title = "Database",
                payload = "secret",
                passphrase = "Long-passphrase1!",
            ),
        )
        assertFalse(
            ServerFeatureModel.canMintShare(
                availability = ServerAvailability.OFFLINE,
                authenticated = true,
                title = "Database",
                payload = "secret",
                passphrase = "Long-passphrase1!",
            ),
        )
        assertTrue(
            ServerFeatureModel.canRedeemShare(
                availability = ServerAvailability.ONLINE,
                code = "share-code",
                passphrase = "Long-passphrase1!",
            ),
        )
        assertFalse(
            ServerFeatureModel.canRedeemShare(
                availability = ServerAvailability.CHECKING,
                code = "share-code",
                passphrase = "Long-passphrase1!",
            ),
        )
    }

}
