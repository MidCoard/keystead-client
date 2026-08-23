package top.focess.keystead.client

enum class DeviceUnlockState {
    NOT_CONFIGURED,
    DEVICE_LOGIN_NOT_ENABLED,
    LOADED,
    BIOMETRIC_NOT_SELECTED,
    BIOMETRIC_UNAVAILABLE,
    BIOMETRIC_READY,
}

enum class VaultUnlockMethod {
    DEVICE_LOGIN,
    MASTER_PASSWORD,
}

object VaultUnlockMethodPolicy {
    fun shouldOfferDeviceLogin(model: DeviceUnlockUiModel): Boolean =
        model.state != DeviceUnlockState.NOT_CONFIGURED &&
            model.state != DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED &&
            model.state != DeviceUnlockState.BIOMETRIC_UNAVAILABLE

    fun defaultMethod(model: DeviceUnlockUiModel): VaultUnlockMethod =
        if (model.canLoad || model.canUnlock) {
            VaultUnlockMethod.DEVICE_LOGIN
        } else {
            VaultUnlockMethod.MASTER_PASSWORD
        }

    fun canSubmitDeviceLogin(model: DeviceUnlockUiModel): Boolean =
        model.canLoad || model.canUnlock
}

data class DeviceUnlockUiModel(
    val state: DeviceUnlockState,
    val canLoad: Boolean = false,
    val canUnlock: Boolean = false,
    val provider: DeviceProtectionProvider = DeviceProtectionProvider.UNKNOWN,
) {
    companion object {
        fun derive(
            descriptor: LocalUnlockCredentialDescriptor?,
            credentialLoaded: Boolean,
            loadedPersistence: LocalLoginPersistence?,
            selectedMode: SecureStorageMode?,
            biometricAvailability: BiometricAvailability,
            deviceLoginAvailable: Boolean,
            providerId: String? = null,
        ): DeviceUnlockUiModel {
            val provider = DeviceProtectionProvider.from(providerId)
            if (descriptor == null) return DeviceUnlockUiModel(DeviceUnlockState.NOT_CONFIGURED, provider = provider)
            if (!deviceLoginAvailable) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED,
                    provider = provider,
                )
            }
            if (credentialLoaded && loadedPersistence != null) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.LOADED,
                    canUnlock = true,
                    provider = provider,
                )
            }
            if (selectedMode != SecureStorageMode.BIOMETRIC) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.BIOMETRIC_NOT_SELECTED,
                    provider = provider,
                )
            }
            if (biometricAvailability != BiometricAvailability.AVAILABLE) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.BIOMETRIC_UNAVAILABLE,
                    provider = provider,
                )
            }
            return DeviceUnlockUiModel(
                DeviceUnlockState.BIOMETRIC_READY,
                canUnlock = true,
                provider = provider,
            )
        }
    }
}
