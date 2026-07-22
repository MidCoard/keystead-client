package top.focess.keystead.client

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SecretExpiryTest {
    private val today = LocalDate.of(2026, 7, 22)

    @Test
    fun nullExpiryHasNoState() {
        assertNull(SecretExpiry.state(null, today))
    }

    @Test
    fun blankExpiryHasNoState() {
        assertNull(SecretExpiry.state("   ", today))
    }

    @Test
    fun unparseableExpiryHasNoState() {
        assertNull(SecretExpiry.state("not-a-date", today))
    }

    @Test
    fun expiryWithinWindowIsDueSoon() {
        val state = SecretExpiry.state(today.plusDays(5).toString(), today)
        assertEquals(SecretExpiryStatus.DUE_SOON, state?.status)
        assertEquals(5L, state?.daysRemaining)
    }

    @Test
    fun pastExpiryIsExpired() {
        val state = SecretExpiry.state(today.minusDays(1).toString(), today)
        assertEquals(SecretExpiryStatus.EXPIRED, state?.status)
        assertEquals(-1L, state?.daysRemaining)
    }

    @Test
    fun farFutureExpiryIsActive() {
        val state = SecretExpiry.state(today.plusDays(30).toString(), today)
        assertEquals(SecretExpiryStatus.ACTIVE, state?.status)
        assertEquals(30L, state?.daysRemaining)
    }

    @Test
    fun expiryTodayIsDueSoonWithZeroDays() {
        val state = SecretExpiry.state(today.toString(), today)
        assertEquals(SecretExpiryStatus.DUE_SOON, state?.status)
        assertEquals(0L, state?.daysRemaining)
    }

    @Test
    fun expiryAtWindowEdgeIsDueSoon() {
        val state = SecretExpiry.state(today.plusDays(14).toString(), today)
        assertEquals(SecretExpiryStatus.DUE_SOON, state?.status)
        assertEquals(14L, state?.daysRemaining)
    }
}
