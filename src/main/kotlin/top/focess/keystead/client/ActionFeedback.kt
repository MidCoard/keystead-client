package top.focess.keystead.client

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal enum class ActionFeedbackTone {
    SUCCESS,
    INFO,
    ERROR,
}

internal data class ActionFeedback(
    val id: Long,
    val message: String,
    val tone: ActionFeedbackTone,
)

/**
 * Bridges the client's existing status assignments to visible, persistent action feedback.
 *
 * Ordinary successful actions can continue assigning to the delegated status property. Callers
 * use [info] or [error] when the result is not a success. Feedback is replaced by the next result
 * and otherwise remains visible until the user dismisses that exact result.
 */
internal class ActionFeedbackState(initialStatus: String) : ReadWriteProperty<Any?, String> {
    var status by mutableStateOf(initialStatus)
        private set

    var current by mutableStateOf<ActionFeedback?>(null)
        private set

    private var nextId = 1L

    override fun getValue(thisRef: Any?, property: KProperty<*>): String = status

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        publish(value, ActionFeedbackTone.SUCCESS)
    }

    fun info(message: String) {
        publish(message, ActionFeedbackTone.INFO)
    }

    fun error(message: String) {
        publish(message, ActionFeedbackTone.ERROR)
    }

    fun dismiss(feedbackId: Long) {
        if (current?.id == feedbackId) current = null
    }

    private fun publish(message: String, tone: ActionFeedbackTone) {
        status = message
        current = ActionFeedback(id = nextId++, message = message, tone = tone)
    }
}
