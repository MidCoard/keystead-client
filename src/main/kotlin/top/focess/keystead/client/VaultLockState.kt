package top.focess.keystead.client

/** Explicit lock intent outlives the operation holding the vault's mutable handle. */
internal data class VaultLockState(val generation: Long = 0, val pending: Boolean = false) {
    fun request() = copy(generation = generation + 1, pending = true)
    fun shouldClose(vaultOperationActive: Boolean) = pending && !vaultOperationActive
    fun exposesVault(vaultOpen: Boolean) = vaultOpen && !pending
    fun completed() = copy(pending = false)
    fun accepts(operationGeneration: Long) = !pending && generation == operationGeneration
}
