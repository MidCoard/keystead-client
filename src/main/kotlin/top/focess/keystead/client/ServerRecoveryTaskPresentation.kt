package top.focess.keystead.client

enum class ServerRecoveryTask {
    RESTORE_THIS_DEVICE,
    APPROVE_ANOTHER_DEVICE,
}

data class ServerRecoveryTaskPresentation(
    val requiresOpenVault: Boolean,
    val createsNewLocalVault: Boolean,
) {
    companion object {
        val tasks: List<ServerRecoveryTask> =
            listOf(
                ServerRecoveryTask.RESTORE_THIS_DEVICE,
                ServerRecoveryTask.APPROVE_ANOTHER_DEVICE,
            )

        fun forTask(task: ServerRecoveryTask): ServerRecoveryTaskPresentation =
            when (task) {
                ServerRecoveryTask.RESTORE_THIS_DEVICE ->
                    ServerRecoveryTaskPresentation(
                        requiresOpenVault = false,
                        createsNewLocalVault = true,
                    )
                ServerRecoveryTask.APPROVE_ANOTHER_DEVICE ->
                    ServerRecoveryTaskPresentation(
                        requiresOpenVault = true,
                        createsNewLocalVault = false,
                    )
            }
    }
}
