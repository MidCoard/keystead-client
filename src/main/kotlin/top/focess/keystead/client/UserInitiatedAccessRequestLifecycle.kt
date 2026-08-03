package top.focess.keystead.client

/** The exchange resource and request created by one explicit recovery action. */
internal data class StartedAccessRequest<E : AutoCloseable, R>(
    val exchange: E,
    val request: R,
)

/**
 * Owns recovery-request state separately from account authentication.
 *
 * Calling [onAccountAuthenticated] can only clear stale state. The request factory is reachable
 * exclusively through [requestByUser], so login, registration, and session restoration cannot
 * create a recovery request as a side effect.
 */
internal class UserInitiatedAccessRequestLifecycle<E : AutoCloseable, R> : AutoCloseable {
    var exchange: E? = null
        private set
    var request: R? = null
        private set

    fun onAccountAuthenticated() {
        clear()
    }

    fun requestByUser(create: () -> StartedAccessRequest<E, R>): R {
        clear()
        val started = create()
        exchange = started.exchange
        request = started.request
        return started.request
    }

    fun updateRequest(value: R) {
        check(exchange != null) { "A recovery request cannot exist without its exchange session" }
        request = value
    }

    fun clear() {
        exchange?.close()
        exchange = null
        request = null
    }

    override fun close() {
        clear()
    }
}
