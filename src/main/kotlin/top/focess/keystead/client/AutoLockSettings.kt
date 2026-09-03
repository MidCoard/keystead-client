package top.focess.keystead.client

import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

internal enum class AutoLockTimeout(val minutes: Int) {
    ONE_MINUTE(1),
    FIVE_MINUTES(5),
    FIFTEEN_MINUTES(15),
    THIRTY_MINUTES(30),
    ;

    val duration: Duration
        get() = Duration.ofMinutes(minutes.toLong())

    companion object {
        val default: AutoLockTimeout = ONE_MINUTE

        fun fromMinutes(minutes: Int?): AutoLockTimeout =
            entries.firstOrNull { it.minutes == minutes } ?: default
    }
}

internal class AutoLockSettings(private val store: ClientSettingsStore) {
    fun load(): AutoLockTimeout = AutoLockTimeout.fromMinutes(store.load().autoLockMinutes)

    fun save(timeout: AutoLockTimeout) {
        val settings = store.load()
        settings.autoLockMinutes = timeout.minutes
        store.save(settings)
    }
}

/** Thread-safe monotonic activity clock used by the desktop input listener and lock timer. */
internal class UserIdleTracker(
    private val nanoTime: () -> Long = System::nanoTime,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    private val lastActivityNanos = AtomicLong(nanoTime())
    private val lastActivityMillis = AtomicLong(currentTimeMillis())

    fun recordActivity() {
        lastActivityNanos.set(nanoTime())
        lastActivityMillis.set(currentTimeMillis())
    }

    fun isIdleFor(timeout: Duration): Boolean {
        val monotonicElapsed = nanoTime() - lastActivityNanos.get()
        val wallClockElapsed = currentTimeMillis() - lastActivityMillis.get()
        return monotonicElapsed >= timeout.toNanos() || wallClockElapsed >= timeout.toMillis()
    }
}

internal object AutoLockPolicy {
    fun shouldLock(
        vaultOpen: Boolean,
        idleTimeoutReached: Boolean,
        vaultOperationActive: Boolean,
    ): Boolean = vaultOpen && idleTimeoutReached && !vaultOperationActive
}

/** Records input events delivered to this desktop process, including dialogs and text fields. */
internal class DesktopUserActivityListener(
    onActivity: () -> Unit,
) : AutoCloseable {
    private val toolkit = Toolkit.getDefaultToolkit()
    private val listener =
        AWTEventListener { event ->
            val isActivity =
                when (event) {
                    is KeyEvent -> event.id == KeyEvent.KEY_PRESSED
                    is MouseEvent ->
                        event.id == MouseEvent.MOUSE_PRESSED ||
                            event.id == MouseEvent.MOUSE_MOVED ||
                            event.id == MouseEvent.MOUSE_DRAGGED ||
                            event.id == MouseEvent.MOUSE_WHEEL
                    else -> false
                }
            if (isActivity) onActivity()
        }

    init {
        toolkit.addAWTEventListener(
            listener,
            AWTEvent.KEY_EVENT_MASK or
                AWTEvent.MOUSE_EVENT_MASK or
                AWTEvent.MOUSE_MOTION_EVENT_MASK or
                AWTEvent.MOUSE_WHEEL_EVENT_MASK,
        )
    }

    override fun close() {
        toolkit.removeAWTEventListener(listener)
    }
}
