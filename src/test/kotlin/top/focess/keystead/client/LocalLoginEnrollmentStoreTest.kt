package top.focess.keystead.client

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalLoginEnrollmentStoreTest {
    @Test
    fun enrollmentMatchesOnlyTheRecordedVaultSlotAndCredential() {
        val file = createTempDirectory("keystead-local-login-enrollment").resolve("enrollments.properties")
        val store = LocalLoginEnrollmentStore(file)

        store.remember(
            vaultFingerprint = "vault-a",
            slotKeyId = "slot-a",
            credentialFingerprint = "credential-a",
        )

        assertTrue(
            store.isEnrolled(
                vaultFingerprint = "vault-a",
                slotKeyIds = setOf("slot-a"),
                credentialFingerprint = "credential-a",
            ),
        )
        assertFalse(store.isEnrolled("vault-a", setOf("slot-other"), "credential-a"))
        assertFalse(store.isEnrolled("vault-a", setOf("slot-a"), "credential-other"))
        assertFalse(store.isEnrolled("vault-other", setOf("slot-a"), "credential-a"))

        store.clear("vault-a")
        assertFalse(store.isEnrolled("vault-a", setOf("slot-a"), "credential-a"))
    }
}
