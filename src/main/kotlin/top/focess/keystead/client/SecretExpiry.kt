package top.focess.keystead.client

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/**
 * Status of a secret's optional expiry date, relative to "today". Expiry is stored as a free-form
 * `expiry` secret attribute (ISO `yyyy-MM-dd`); this object turns it into an actionable reminder
 * state without touching plaintext secret fields.
 */
internal enum class SecretExpiryStatus { ACTIVE, DUE_SOON, EXPIRED }

internal data class SecretExpiryState(val status: SecretExpiryStatus, val daysRemaining: Long) {
    /** Human-readable reminder label for the row and summary banner. */
    fun label(): String =
        when (status) {
            SecretExpiryStatus.EXPIRED -> {
                val days = -daysRemaining
                if (days == 1L) "expired 1 day ago" else "expired $days days ago"
            }
            SecretExpiryStatus.DUE_SOON ->
                if (daysRemaining == 0L) "expires today" else "expires in $daysRemaining days"
            SecretExpiryStatus.ACTIVE ->
                if (daysRemaining == 1L) "expires in 1 day" else "expires in $daysRemaining days"
        }
}

/** Classifies a secret's expiry attribute into a reminder state. */
internal object SecretExpiry {
    /** Number of days (inclusive) before expiry that counts as "due soon". */
    const val SOON_WINDOW_DAYS = 14L

    /**
     * Returns the expiry state for [expiry], or `null` when there is no parseable expiry. [today]
     * is overridable for deterministic testing.
     */
    fun state(expiry: String?, today: LocalDate = LocalDate.now()): SecretExpiryState? {
        if (expiry.isNullOrBlank()) return null
        val parsed =
            try {
                LocalDate.parse(expiry.trim())
            } catch (e: DateTimeParseException) {
                return null
            }
        val days = ChronoUnit.DAYS.between(today, parsed)
        val status =
            when {
                days < 0 -> SecretExpiryStatus.EXPIRED
                days <= SOON_WINDOW_DAYS -> SecretExpiryStatus.DUE_SOON
                else -> SecretExpiryStatus.ACTIVE
            }
        return SecretExpiryState(status, days)
    }
}
