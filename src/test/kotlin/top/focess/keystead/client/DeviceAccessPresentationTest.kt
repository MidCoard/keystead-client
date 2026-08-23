package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.ZhStrings

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
    }

    @Test
    fun macTouchIdProviderIsPresentedAsTouchId() {
        val presentation =
            DeviceAccessPresentation.derive(
                SecureStorageUiModel(
                    SecureStorageMode.BIOMETRIC,
                    BiometricAvailability.AVAILABLE,
                    providerId = "mac-touch-id",
                    biometricActive = true,
                ),
                LocalLoginPersistence.BIOMETRIC,
                credentialLoaded = true,
            )

        assertEquals(DeviceProtectionProvider.MAC_TOUCH_ID, presentation.provider)
        assertEquals("Protected by Touch ID", EnStrings.deviceProtectionLabel(presentation.provider))
        assertEquals("受 Touch ID 保护", ZhStrings.deviceProtectionLabel(presentation.provider))
        assertEquals(
            "Open this local vault with Touch ID. Local login never connects to Keystead Server.",
            EnStrings.deviceAccessIntro(presentation.provider),
        )
        assertEquals("Set up Touch ID", EnStrings.createProtectedIdentity(presentation.provider))
        assertEquals("Verify with Touch ID", EnStrings.verifyLocalLogin(presentation.provider))
    }

    @Test
    fun newLoginPrefersBiometricsAndShowsUnavailableWhenAbsent() {
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
        assertFalse(fallback.showBiometricCreate)
    }

}
