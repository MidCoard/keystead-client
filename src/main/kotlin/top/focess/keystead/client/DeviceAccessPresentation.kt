package top.focess.keystead.client

enum class DeviceAccessMode {
    EXISTING_BIOMETRIC,
    NEW_BIOMETRIC,
    BIOMETRIC_UNAVAILABLE,
}

enum class DeviceLoginState {
    ENABLED,
    READY_TO_ENABLE,
    CREDENTIAL_LOCKED,
    VAULT_LOCKED,
    UNAVAILABLE,
}

data class DeviceLoginPresentation(
    val state: DeviceLoginState,
    val shouldEnableAutomatically: Boolean,
    val canRemove: Boolean,
) {
    companion object {
        fun derive(
            vaultOpen: Boolean,
            credentialLoaded: Boolean,
            enrollmentEligible: Boolean,
            localLoginEnrolled: Boolean,
        ): DeviceLoginPresentation {
            val state =
                when {
                    localLoginEnrolled -> DeviceLoginState.ENABLED
                    !vaultOpen -> DeviceLoginState.VAULT_LOCKED
                    !credentialLoaded -> DeviceLoginState.CREDENTIAL_LOCKED
                    !enrollmentEligible -> DeviceLoginState.UNAVAILABLE
                    else -> DeviceLoginState.READY_TO_ENABLE
                }
            return DeviceLoginPresentation(
                state = state,
                shouldEnableAutomatically = state == DeviceLoginState.READY_TO_ENABLE,
                canRemove = vaultOpen && localLoginEnrolled,
            )
        }
    }
}

enum class DeviceProtectionProvider {
    WINDOWS_HELLO,
    UNKNOWN,
    ;

    companion object {
        fun from(providerId: String?): DeviceProtectionProvider =
            when (providerId) {
                "windows-hello" -> WINDOWS_HELLO
                else -> UNKNOWN
            }
    }
}

data class DeviceAccessPresentation(
    val mode: DeviceAccessMode,
    val provider: DeviceProtectionProvider,
    val showBiometricCreate: Boolean,
) {
    companion object {
        fun derive(
            secureStorage: SecureStorageUiModel,
            credentialPersistence: LocalLoginPersistence?,
            credentialLoaded: Boolean,
        ): DeviceAccessPresentation {
            val biometricAvailable =
                secureStorage.biometricAvailability == BiometricAvailability.AVAILABLE
            val provider = DeviceProtectionProvider.from(secureStorage.providerId)
            return when (credentialPersistence) {
                LocalLoginPersistence.BIOMETRIC ->
                    DeviceAccessPresentation(
                        DeviceAccessMode.EXISTING_BIOMETRIC,
                        provider,
                        showBiometricCreate = false,
                    )
                null ->
                    if (biometricAvailable) {
                        DeviceAccessPresentation(
                            DeviceAccessMode.NEW_BIOMETRIC,
                            provider,
                            showBiometricCreate = true,
                        )
                    } else {
                        DeviceAccessPresentation(
                            DeviceAccessMode.BIOMETRIC_UNAVAILABLE,
                            provider,
                            showBiometricCreate = false,
                        )
                    }
            }
        }
    }
}
