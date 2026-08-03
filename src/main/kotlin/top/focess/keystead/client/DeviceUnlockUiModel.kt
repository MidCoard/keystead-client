package top.focess.keystead.client

enum class DeviceUnlockState {
    NOT_CONFIGURED,
    DEVICE_LOGIN_NOT_ENABLED,
    LOADED,
    PASSPHRASE_REQUIRED,
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
            model.state != DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED

    fun defaultMethod(model: DeviceUnlockUiModel): VaultUnlockMethod =
        if (model.canLoad || model.canUnlock) {
            VaultUnlockMethod.DEVICE_LOGIN
        } else {
            VaultUnlockMethod.MASTER_PASSWORD
        }

    fun canSubmitDeviceLogin(model: DeviceUnlockUiModel, devicePassphrase: String): Boolean =
        model.canUnlock ||
            (model.canLoad && (!model.passphraseRequired || devicePassphrase.isNotBlank()))
}

data class DeviceUnlockUiModel(
    val state: DeviceUnlockState,
    val canLoad: Boolean = false,
    val passphraseRequired: Boolean = false,
    val canUnlock: Boolean = false,
) {
    companion object {
        fun derive(
            descriptor: LocalUnlockCredentialDescriptor?,
            credentialLoaded: Boolean,
            loadedPersistence: LocalLoginPersistence?,
            selectedMode: SecureStorageMode?,
            biometricAvailability: BiometricAvailability,
            deviceLoginAvailable: Boolean,
        ): DeviceUnlockUiModel {
            if (descriptor == null) return DeviceUnlockUiModel(DeviceUnlockState.NOT_CONFIGURED)
            if (!deviceLoginAvailable) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED,
                )
            }
            if (credentialLoaded && loadedPersistence != null) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.LOADED,
                    canUnlock = true,
                )
            }
            if (descriptor.persistence == LocalLoginPersistence.PASSPHRASE_FILE) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.PASSPHRASE_REQUIRED,
                    canLoad = selectedMode == SecureStorageMode.PASSPHRASE_FILE,
                    passphraseRequired = true,
                )
            }
            if (selectedMode != SecureStorageMode.BIOMETRIC) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.BIOMETRIC_NOT_SELECTED,
                )
            }
            if (biometricAvailability != BiometricAvailability.AVAILABLE) {
                return DeviceUnlockUiModel(
                    DeviceUnlockState.BIOMETRIC_UNAVAILABLE,
                )
            }
            return DeviceUnlockUiModel(
                DeviceUnlockState.BIOMETRIC_READY,
                canUnlock = true,
            )
        }
    }
}
