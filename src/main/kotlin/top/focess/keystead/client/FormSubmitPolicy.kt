package top.focess.keystead.client

object FormSubmitPolicy {
    fun submitIfEnabled(enabled: Boolean, onSubmit: () -> Unit): Boolean {
        if (!enabled) return false
        onSubmit()
        return true
    }

    fun handleCtrlEnter(
        enabled: Boolean,
        ctrlPressed: Boolean,
        submitKey: Boolean,
        keyUp: Boolean,
        onSubmit: () -> Unit,
    ): Boolean {
        if (!enabled || !ctrlPressed || !submitKey) return false
        if (keyUp) onSubmit()
        return true
    }
}
