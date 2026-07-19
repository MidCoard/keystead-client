package top.focess.keystead.client

data class DeviceRevocationResult(
    val deviceId: String,
) {
    init {
        require(deviceId.isNotBlank()) { "deviceId must not be blank" }
    }

    override fun toString(): String = "DeviceRevocationResult(deviceId=<redacted>)"
}

class DeviceRevocationService {
    fun revoke(
        authSession: ServerAuthSession,
        identity: LocalDeviceIdentity,
        knownDevice: ServerDevice,
    ): DeviceRevocationResult {
        return try {
            check(knownDevice.deviceId == identity.deviceId) {
                "Server device does not match the local identity"
            }
            authSession.client().revokeDevice(identity.deviceId)
            DeviceRevocationResult(identity.deviceId)
        } finally {
            authSession.close()
            identity.close()
        }
    }
}
