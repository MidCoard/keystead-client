package top.focess.keystead.client

import java.time.Duration
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoLockSettingsTest {
    @Test
    fun `missing or unsupported timeout defaults to one minute`() {
        val store = ClientSettingsStore(createTempDirectory().resolve("settings.json"))

        assertEquals(AutoLockTimeout.ONE_MINUTE, AutoLockSettings(store).load())

        store.save(ClientSettings(autoLockMinutes = 99))
        assertEquals(AutoLockTimeout.ONE_MINUTE, AutoLockSettings(store).load())
    }

    @Test
    fun `selected timeout survives reload`() {
        val store = ClientSettingsStore(createTempDirectory().resolve("settings.json"))

        AutoLockSettings(store).save(AutoLockTimeout.FIFTEEN_MINUTES)

        assertEquals(AutoLockTimeout.FIFTEEN_MINUTES, AutoLockSettings(store).load())
    }

    @Test
    fun `activity resets the monotonic idle deadline`() {
        var now = 0L
        var nowMillis = 0L
        val tracker = UserIdleTracker({ now }, { nowMillis })

        now = Duration.ofSeconds(59).toNanos()
        nowMillis = Duration.ofSeconds(59).toMillis()
        assertFalse(tracker.isIdleFor(Duration.ofMinutes(1)))
        now = Duration.ofMinutes(1).toNanos()
        nowMillis = Duration.ofMinutes(1).toMillis()
        assertTrue(tracker.isIdleFor(Duration.ofMinutes(1)))

        tracker.recordActivity()
        now += Duration.ofSeconds(59).toNanos()
        nowMillis += Duration.ofSeconds(59).toMillis()
        assertFalse(tracker.isIdleFor(Duration.ofMinutes(1)))
        now += Duration.ofSeconds(1).toNanos()
        nowMillis += Duration.ofSeconds(1).toMillis()
        assertTrue(tracker.isIdleFor(Duration.ofMinutes(1)))
    }

    @Test
    fun `wall clock locks after system sleep even when monotonic clock pauses`() {
        var nanos = 0L
        var millis = 0L
        val tracker = UserIdleTracker({ nanos }, { millis })

        millis = Duration.ofMinutes(2).toMillis()

        assertTrue(tracker.isIdleFor(Duration.ofMinutes(1)))
    }

    @Test
    fun `expired timeout waits for an active vault operation`() {
        assertFalse(
            AutoLockPolicy.shouldLock(
                vaultOpen = true,
                idleTimeoutReached = true,
                vaultOperationActive = true,
            ),
        )
        assertTrue(
            AutoLockPolicy.shouldLock(
                vaultOpen = true,
                idleTimeoutReached = true,
                vaultOperationActive = false,
            ),
        )
    }
}
