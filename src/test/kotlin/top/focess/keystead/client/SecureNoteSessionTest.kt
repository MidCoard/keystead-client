package top.focess.keystead.client

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import top.focess.keystead.model.SecretType

class SecureNoteSessionTest {
    @Test
    fun notesRoundTripThroughEditorRevealPromotionSyncAndReopen() {
        val directory = Files.createTempDirectory("keystead-note-session")
        val file = directory.resolve("vault.kvault")
        try {
            LocalVaultSession.openOrCreate(file, "master-password".toCharArray()).use { session ->
                val id = session.addStructuredSecret(SecretType.SECURE_NOTE, "Note", mapOf("note" to "private body"), expiry = "2030-01-01")
                session.addLogin("Login", "alice", "password", null)
                assertEquals(2, session.listSecrets().size)
                assertEquals("private body", session.revealField(id, "note"))
                val before = session.editSnapshot(id)
                assertEquals(mapOf("note" to "private body"), before.fields)
                session.updateStructuredSecret(id, "Edited", mapOf("note" to "changed body"), expiry = null)
                assertNull(session.editSnapshot(id).expiry)
                val edited = session.editSnapshot(id)
                session.promoteLocalRecord(id)
                assertEquals(edited, session.editSnapshot(id))
                val record = session.currentPersonalRecords().first { it.secretId() == id }
                assertTrue(session.authenticateSyncRecord(record))
                assertEquals("changed body", session.previewSyncRecordFields(record)!!["body"])
            }
            LocalVaultSession.openOrCreate(file, "master-password".toCharArray()).use { session ->
                assertEquals("changed body", session.revealField(session.listSecrets().first { it.type == "SECURE_NOTE" }.id, "note"))
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
