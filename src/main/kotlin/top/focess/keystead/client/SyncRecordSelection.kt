package top.focess.keystead.client

internal data class SyncRecordChoice(
    val secretId: String,
    val canUpload: Boolean,
    val canRemoveFromServer: Boolean,
)

internal data class SyncRecordSelection(
    val selectedIds: Set<String> = emptySet(),
) {
    fun toggle(secretId: String): SyncRecordSelection =
        copy(
            selectedIds =
                if (secretId in selectedIds) {
                    selectedIds - secretId
                } else {
                    selectedIds + secretId
                },
        )

    fun selectAll(choices: List<SyncRecordChoice>): SyncRecordSelection =
        copy(selectedIds = choices.mapTo(linkedSetOf(), SyncRecordChoice::secretId))

    fun clear(): SyncRecordSelection = copy(selectedIds = emptySet())

    fun reconcile(choices: List<SyncRecordChoice>): SyncRecordSelection {
        val visible = choices.mapTo(hashSetOf(), SyncRecordChoice::secretId)
        return copy(selectedIds = selectedIds.filterTo(linkedSetOf()) { it in visible })
    }

    fun uploadableIds(choices: List<SyncRecordChoice>): Set<String> =
        eligibleIds(choices, SyncRecordChoice::canUpload)

    fun removableIds(choices: List<SyncRecordChoice>): Set<String> =
        eligibleIds(choices, SyncRecordChoice::canRemoveFromServer)

    private fun eligibleIds(
        choices: List<SyncRecordChoice>,
        predicate: (SyncRecordChoice) -> Boolean,
    ): Set<String> =
        choices
            .asSequence()
            .filter { it.secretId in selectedIds && predicate(it) }
            .mapTo(linkedSetOf(), SyncRecordChoice::secretId)
}
