package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceAccessPresentationTest {
    @Test
    fun localLoginBecomesReadyOnlyWhenVaultAndCredentialAreReady() {
        val ready =
            DeviceLoginPresentation.derive(
                vaultOpen = true,
                credentialLoaded = true,
                enrollmentEligible = true,
                localLoginEnrolled = false,
            )
        val credentialLocked =
            DeviceLoginPresentation.derive(
                vaultOpen = true,
                credentialLoaded = false,
                enrollmentEligible = true,
                localLoginEnrolled = false,
            )

        assertEquals(DeviceLoginState.READY_TO_ENABLE, ready.state)
        assertTrue(ready.shouldEnableAutomatically)
        assertEquals(DeviceLoginState.CREDENTIAL_LOCKED, credentialLocked.state)
        assertFalse(credentialLocked.shouldEnableAutomatically)
    }

    @Test
    fun configuredLocalLoginOffersRemovalButNeverDuplicateEnrollment() {
        val presentation =
            DeviceLoginPresentation.derive(
                vaultOpen = true,
                credentialLoaded = true,
                enrollmentEligible = true,
                localLoginEnrolled = true,
            )

        assertEquals(DeviceLoginState.ENABLED, presentation.state)
        assertFalse(presentation.shouldEnableAutomatically)
        assertTrue(presentation.canRemove)
    }

    @Test
    fun existingBiometricLoginShowsPassiveWindowsHelloStatus() {
        val presentation =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    SecureStorageMode.BIOMETRIC,
                    BiometricAvailability.AVAILABLE,
                    providerId = "windows-hello",
                    biometricActive = true,
                ),
                LocalLoginPersistence.BIOMETRIC,
                credentialLoaded = true,
            )

        assertEquals(DeviceAccessMode.EXISTING_BIOMETRIC, presentation.mode)
        assertEquals(DeviceProtectionProvider.WINDOWS_HELLO, presentation.provider)
        assertFalse(presentation.showBiometricCreate)
        assertFalse(presentation.showPassphraseInput)
    }

    @Test
    fun newLoginPrefersBiometricsAndFallsBackToPassphrase() {
        val biometric =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    selectedMode = null,
                    biometricAvailability = BiometricAvailability.AVAILABLE,
                    providerId = "windows-hello",
                ),
                null,
                credentialLoaded = false,
            )
        val fallback =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    selectedMode = null,
                    biometricAvailability = BiometricAvailability.UNAVAILABLE,
                    providerId = "windows-hello",
                ),
                null,
                credentialLoaded = false,
            )

        assertEquals(DeviceAccessMode.NEW_BIOMETRIC, biometric.mode)
        assertTrue(biometric.showBiometricCreate)
        assertEquals(DeviceAccessMode.BIOMETRIC_UNAVAILABLE, fallback.mode)
        assertTrue(fallback.showPassphraseInput)
        assertTrue(fallback.showPassphraseCreate)
    }

    @Test
    fun passphraseLoginOffersLoadOnlyWhileLocked() {
        val locked =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    SecureStorageMode.PASSPHRASE_FILE,
                    BiometricAvailability.AVAILABLE,
                ),
                LocalLoginPersistence.PASSPHRASE_FILE,
                credentialLoaded = false,
            )
        val loaded =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    SecureStorageMode.PASSPHRASE_FILE,
                    BiometricAvailability.AVAILABLE,
                ),
                LocalLoginPersistence.PASSPHRASE_FILE,
                credentialLoaded = true,
            )

        assertTrue(locked.showPassphraseInput)
        assertTrue(locked.showPassphraseLoad)
        assertFalse(loaded.showPassphraseInput)
        assertFalse(loaded.showPassphraseLoad)
    }
}
