package top.focess.keystead.client

enum class ConnectedNoticeTone {
    POSITIVE,
    NEUTRAL,
    CAUTION,
}

data class ConnectedNoticeModel(
    val tone: ConnectedNoticeTone,
    val retryVisible: Boolean,
    val retryEnabled: Boolean,
)

object ServerFeatureModel {
    fun connectedNotice(availability: ServerAvailability): ConnectedNoticeModel =
        when (availability) {
            ServerAvailability.ONLINE ->
                ConnectedNoticeModel(ConnectedNoticeTone.POSITIVE, retryVisible = false, retryEnabled = false)
            ServerAvailability.CHECKING ->
                ConnectedNoticeModel(ConnectedNoticeTone.NEUTRAL, retryVisible = true, retryEnabled = false)
            ServerAvailability.OFFLINE ->
                ConnectedNoticeModel(ConnectedNoticeTone.CAUTION, retryVisible = true, retryEnabled = true)
        }

    fun canUseAuthenticatedServer(
        availability: ServerAvailability,
        authenticated: Boolean,
    ): Boolean = availability.isOnline && authenticated

    fun canMintShare(
        availability: ServerAvailability,
        authenticated: Boolean,
        title: String,
        payload: String,
        passphrase: String,
    ): Boolean =
        canUseAuthenticatedServer(availability, authenticated) &&
            title.isNotBlank() &&
            payload.isNotEmpty() &&
            ShareExchange.meetsPassphrasePolicy(passphrase)

    fun canRedeemShare(
        availability: ServerAvailability,
        code: String,
        passphrase: String,
    ): Boolean =
        availability.isOnline &&
            code.isNotBlank() &&
            ShareExchange.meetsPassphrasePolicy(passphrase)
}
