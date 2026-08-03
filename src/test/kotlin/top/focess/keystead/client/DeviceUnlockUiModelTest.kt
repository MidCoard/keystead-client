package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.client.i18n.AppLocale

class DeviceUnlockUiModelTest {
    private val passphraseDescriptor =
        LocalUnlockCredentialDescriptor(
            LocalLoginPersistence.PASSPHRASE_FILE,
            keyFingerprint = "local-credential",
        )
    private val biometricDescriptor =
        passphraseDescriptor.copy(persistence = LocalLoginPersistence.BIOMETRIC)

    @Test
    fun noDescriptorIsNotConfigured() {
        val model = DeviceUnlockUiModel.derive(null, false, null, null, BiometricAvailability.AVAILABLE, true)
        assertEquals(DeviceUnlockState.NOT_CONFIGURED, model.state)
        assertFalse(model.canUnlock)
        assertFalse(VaultUnlockMethodPolicy.shouldOfferDeviceLogin(model))
    }

    @Test
    fun loadedLocalCredentialCanUnlock() {
        val model =
            DeviceUnlockUiModel.derive(
                biometricDescriptor,
                true,
                LocalLoginPersistence.BIOMETRIC,
                SecureStorageMode.BIOMETRIC,
                BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = true,
            )
        assertEquals(DeviceUnlockState.LOADED, model.state)
        assertTrue(model.canUnlock)
    }

    @Test
    fun localPassphraseCredentialRequestsPassphrase() {
        val model =
            DeviceUnlockUiModel.derive(
                passphraseDescriptor,
                false,
                null,
                SecureStorageMode.PASSPHRASE_FILE,
                BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = true,
            )
        assertEquals(DeviceUnlockState.PASSPHRASE_REQUIRED, model.state)
        assertTrue(model.canLoad)
        assertTrue(model.passphraseRequired)
    }

    @Test
    fun localBiometricCredentialSeparatesSelectionAvailabilityAndReadiness() {
        val notSelected = DeviceUnlockUiModel.derive(biometricDescriptor, false, null, SecureStorageMode.PASSPHRASE_FILE, BiometricAvailability.AVAILABLE, true)
        val unavailable = DeviceUnlockUiModel.derive(biometricDescriptor, false, null, SecureStorageMode.BIOMETRIC, BiometricAvailability.UNAVAILABLE, true)
        val ready = DeviceUnlockUiModel.derive(biometricDescriptor, false, null, SecureStorageMode.BIOMETRIC, BiometricAvailability.AVAILABLE, true)

        assertEquals(DeviceUnlockState.BIOMETRIC_NOT_SELECTED, notSelected.state)
        assertEquals(DeviceUnlockState.BIOMETRIC_UNAVAILABLE, unavailable.state)
        assertEquals(DeviceUnlockState.BIOMETRIC_READY, ready.state)
        assertTrue(ready.canUnlock)
        assertTrue(VaultUnlockMethodPolicy.shouldOfferDeviceLogin(ready))
    }

    @Test
    fun configuredLocalCredentialDoesNotOfferUnlockWhenTheVaultHasNoLocalLoginSlot() {
        val model =
            DeviceUnlockUiModel.derive(
                biometricDescriptor,
                credentialLoaded = false,
                loadedPersistence = null,
                selectedMode = SecureStorageMode.BIOMETRIC,
                biometricAvailability = BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = false,
            )

        assertEquals(DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED, model.state)
        assertFalse(model.canLoad)
        assertFalse(model.canUnlock)
        assertFalse(VaultUnlockMethodPolicy.shouldOfferDeviceLogin(model))
        assertTrue(AppLocale.ENGLISH.strings.deviceUnlockStatus(model).contains("master password"))
    }

    @Test
    fun unlockCopyUsesTheSingleDeviceLoginConcept() {
        val english = AppLocale.ENGLISH.strings

        assertEquals("Unlock with local login", english.unlockWithDeviceLogin)
        assertEquals(
            "The local-login credential is unavailable. Reload or create it on the Local login page.",
            english.localLoginCredentialUnavailable,
        )
    }

    @Test
    fun deviceLoginIsTheDefaultWhenTheSelectedVaultSupportsIt() {
        val ready =
            DeviceUnlockUiModel.derive(
                biometricDescriptor,
                credentialLoaded = false,
                loadedPersistence = null,
                selectedMode = SecureStorageMode.BIOMETRIC,
                biometricAvailability = BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = true,
            )
        val unavailable =
            DeviceUnlockUiModel.derive(
                biometricDescriptor,
                credentialLoaded = false,
                loadedPersistence = null,
                selectedMode = SecureStorageMode.BIOMETRIC,
                biometricAvailability = BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = false,
            )

        assertEquals(VaultUnlockMethod.DEVICE_LOGIN, VaultUnlockMethodPolicy.defaultMethod(ready))
        assertEquals(VaultUnlockMethod.MASTER_PASSWORD, VaultUnlockMethodPolicy.defaultMethod(unavailable))
    }

    @Test
    fun localLoginPassphraseCanSubmitLoadAndUnlockAsOneAction() {
        val model =
            DeviceUnlockUiModel.derive(
                passphraseDescriptor,
                credentialLoaded = false,
                loadedPersistence = null,
                selectedMode = SecureStorageMode.PASSPHRASE_FILE,
                biometricAvailability = BiometricAvailability.AVAILABLE,
                deviceLoginAvailable = true,
            )

        assertFalse(VaultUnlockMethodPolicy.canSubmitDeviceLogin(model, ""))
        assertTrue(VaultUnlockMethodPolicy.canSubmitDeviceLogin(model, "identity-passphrase"))
    }

}
