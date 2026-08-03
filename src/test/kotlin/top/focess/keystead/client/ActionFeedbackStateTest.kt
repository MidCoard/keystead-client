package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActionFeedbackStateTest {
    @Test
    fun `ordinary status updates publish persistent success feedback`() {
        val feedbackState = ActionFeedbackState("Vault locked")
        var status by feedbackState

        assertNull(feedbackState.current)

        status = "Secret saved"

        assertEquals("Secret saved", status)
        assertEquals("Secret saved", feedbackState.current?.message)
        assertEquals(ActionFeedbackTone.SUCCESS, feedbackState.current?.tone)
    }

    @Test
    fun `errors replace earlier feedback and are visibly classified`() {
        val feedbackState = ActionFeedbackState("Vault locked")
        var status by feedbackState
        status = "Secret saved"

        feedbackState.error("Server credentials were rejected")

        assertEquals("Server credentials were rejected", status)
        assertEquals(ActionFeedbackTone.ERROR, feedbackState.current?.tone)
    }

    @Test
    fun `information can be published without presenting it as success`() {
        val feedbackState = ActionFeedbackState("Vault locked")

        feedbackState.info("Vault locked")

        assertEquals("Vault locked", feedbackState.status)
        assertEquals(ActionFeedbackTone.INFO, feedbackState.current?.tone)
    }

    @Test
    fun `dismiss only clears the feedback the user actually dismissed`() {
        val feedbackState = ActionFeedbackState("Vault locked")
        var status by feedbackState
        status = "First result"
        val firstId = feedbackState.current!!.id
        status = "Newer result"

        feedbackState.dismiss(firstId)
        assertEquals("Newer result", feedbackState.current?.message)

        feedbackState.dismiss(feedbackState.current!!.id)
        assertNull(feedbackState.current)
        assertEquals("Newer result", feedbackState.status)
    }
}
