package top.focess.keystead.client

import java.time.Clock
import java.util.Base64

class DeviceEnrollmentService(
    private val clock: Clock = Clock.systemUTC(),
) {
    fun enroll(
        client: KeysteadServerClient,
        identity: LocalDeviceIdentity,
    ): ServerDevice {
        val expected = registration(identity)
        val existing = findDevice(client.listDevices(), identity.deviceId)
        if (existing == null) {
            client.registerDevice(expected)
        } else {
            requireUsableMatchingIdentity(existing, expected)
            if (existing.verifiedAt != null) {
                check(existing.canReceiveVaultKeyPackage) {
                    "Verified device has no eligible wrapping key"
                }
                return existing
            }
        }

        val challenge = client.createDeviceChallenge(identity.deviceId)
        check(challenge.deviceId == identity.deviceId) {
            "Server challenge belongs to a different device"
        }
        check(challenge.expiresAt.isAfter(clock.instant())) {
            "Server device challenge has expired"
        }
        val signature =
            identity.signDeviceChallenge(challenge.challengeId, challenge.nonce)
        client.proveDevice(identity.deviceId, challenge.challengeId, signature)

        val confirmed =
            findDevice(client.listDevices(), identity.deviceId)
                ?: throw IllegalStateException("Verified device is missing from server response")
        requireUsableMatchingIdentity(confirmed, expected)
        check(confirmed.verifiedAt != null) { "Server did not verify the device" }
        check(confirmed.canReceiveVaultKeyPackage) {
            "Verified device has no eligible wrapping key"
        }
        return confirmed
    }

    private fun registration(identity: LocalDeviceIdentity): ServerDevice {
        val proofPublicKey = identity.proofPublicKey()
        val wrappingPublicKey = identity.publicKey()
        return try {
            ServerDevice(
                deviceId = identity.deviceId,
                keyAlgorithm = identity.proofKeyAlgorithm,
                publicKey = Base64.getEncoder().encodeToString(proofPublicKey),
                wrappingKeyAlgorithm = identity.keyAlgorithm,
                wrappingPublicKey = Base64.getEncoder().encodeToString(wrappingPublicKey),
            )
        } finally {
            proofPublicKey.fill(0)
            wrappingPublicKey.fill(0)
        }
    }

    private fun findDevice(devices: List<ServerDevice>, deviceId: String): ServerDevice? {
        val matches = devices.filter { it.deviceId == deviceId }
        check(matches.size <= 1) { "Server returned duplicate device identities" }
        return matches.singleOrNull()
    }

    private fun requireUsableMatchingIdentity(actual: ServerDevice, expected: ServerDevice) {
        check(actual.revokedAt == null) { "Server device is revoked" }
        check(
            actual.keyAlgorithm == expected.keyAlgorithm &&
                actual.publicKey == expected.publicKey &&
                actual.wrappingKeyAlgorithm == expected.wrappingKeyAlgorithm &&
                actual.wrappingPublicKey == expected.wrappingPublicKey,
        ) {
            "Server device key material does not match the local identity"
        }
    }
}
