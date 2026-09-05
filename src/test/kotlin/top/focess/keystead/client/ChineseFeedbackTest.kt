package top.focess.keystead.client

import java.net.ConnectException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import top.focess.keystead.client.i18n.EnStrings
import top.focess.keystead.client.i18n.ZhStrings

class ChineseFeedbackTest {
    @Test
    fun comparisonFieldLabelsIncludeDedicatedAndLoginPayloads() {
        mapOf("title" to "标题", "username" to "用户名", "password" to "密码", "body" to "正文", "notes" to "备注").forEach { (field, label) ->
            assertEquals(label, ZhStrings.secretFieldLabel(field))
        }
        assertEquals("my custom key", ZhStrings.secretFieldLabel("my custom key"))
    }

    @Test
    fun commonErrorsAreLocalizedWithoutReturningRawServerText() {
        listOf(
            KeysteadShareNotFoundException(), KeysteadShareExpiredException(),
            KeysteadShareRateLimitedException(30), KeysteadServerException(503, "upstream unavailable"),
            ConnectException("Connection refused"), IllegalStateException("Vault access request has expired"),
            IllegalStateException("Unknown English error"),
        ).forEach { error ->
            val message = ZhStrings.errorMessage(error)
            assertFalse(message == error.message)
            assertContains(message, Regex("[\\u4e00-\\u9fff]"))
        }
        assertEquals("English detail", EnStrings.errorMessage(IllegalStateException("English detail")))
        assertEquals(ZhStrings.deviceLoginNotConfigured, ZhStrings.errorMessage(IllegalStateException(ZhStrings.deviceLoginNotConfigured)))
    }

    @Test
    fun conflictWithoutRevisionMetadataStillHasChineseRecoveryInstructions() {
        val message = ZhStrings.conflictMessage(KeysteadRevisionConflictException())
        assertContains(message, "拉取")
        assertFalse(message.contains("pull"))
    }

    @Test
    fun languageChangeDiscardsOldFeedbackAndCannotDismissNewFeedback() {
        val state = ActionFeedbackState("Vault locked")
        state.error("Old English failure")
        val oldId = state.current!!.id
        state.reset(ZhStrings.vaultLocked)
        assertEquals(ZhStrings.vaultLocked, state.status)
        assertNull(state.current)
        state.info(ZhStrings.savedSecret)
        state.dismiss(oldId)
        assertEquals(ZhStrings.savedSecret, state.current!!.message)
    }
}
