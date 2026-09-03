package top.focess.keystead.client

/**
 * Prevents two blocking operations that touch the same mutable resource from running together.
 * The UI owns the lifecycle; this class only provides the atomic admission decision.
 */
internal class UiActionGate<K> {
    private val active = mutableSetOf<K>()

    @Synchronized
    fun tryStart(key: K): Boolean = active.add(key)

    @Synchronized
    fun tryStart(key: K, conflicts: Set<K>): Boolean {
        if (active.any(conflicts::contains)) return false
        active += key
        return true
    }

    @Synchronized
    fun finish(key: K) {
        active.remove(key)
    }
}

internal enum class UiActionGroup {
    VAULT,
    ACCOUNT,
    SYNC,
    SHARE,
    RECOVERY,
    DEVICE_LOGIN,
    BACKUP,
}
