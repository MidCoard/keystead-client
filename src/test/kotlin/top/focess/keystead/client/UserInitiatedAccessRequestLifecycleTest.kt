package top.focess.keystead.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class UserInitiatedAccessRequestLifecycleTest {
    @Test
    fun `account authentication clears stale recovery state without creating a request`() {
        var creations = 0
        val lifecycle = UserInitiatedAccessRequestLifecycle<TestExchange, String>()
        lifecycle.requestByUser {
                creations += 1
                StartedAccessRequest(TestExchange(), "request-$creations")
        }

        val firstExchange = lifecycle.exchange
        lifecycle.onAccountAuthenticated()

        assertEquals(1, creations)
        assertEquals(true, firstExchange?.closed)
        assertNull(lifecycle.exchange)
        assertNull(lifecycle.request)
    }

    @Test
    fun `only an explicit user action creates the exchange and request`() {
        var creations = 0
        val exchange = TestExchange()
        val lifecycle = UserInitiatedAccessRequestLifecycle<TestExchange, String>()

        lifecycle.onAccountAuthenticated()
        assertEquals(0, creations)

        val request = lifecycle.requestByUser {
            creations += 1
            StartedAccessRequest(exchange, "request-created-by-user")
        }

        assertEquals(1, creations)
        assertSame(exchange, lifecycle.exchange)
        assertEquals("request-created-by-user", request)
        assertEquals(request, lifecycle.request)
    }

    private class TestExchange : AutoCloseable {
        var closed = false

        override fun close() {
            closed = true
        }
    }
}
