package top.focess.keystead.client.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import top.focess.keystead.client.DeviceUnlockUiModel
import top.focess.keystead.client.DeviceProtectionProvider
import top.focess.keystead.client.KeysteadRevisionConflictException
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.SecretExpiryStatus
import top.focess.keystead.client.SecretGroupingMode
import top.focess.keystead.client.SecureStorageUiModel
import top.focess.keystead.client.ServerVaultRestoreModel
import top.focess.keystead.client.ServerVaultAccessRequestState
import top.focess.keystead.client.ShareExchange
import top.focess.keystead.model.SecretType
import top.focess.keystead.client.ui.KeysteadDestination
import top.focess.keystead.client.ui.KeysteadZone

/**
 * The set of user-facing strings for one interface language.
 *
 * The client keeps no English literals in its composables: every label, button, status message,
 * and dialog line is routed through this interface so a [LocalStrings] swap re-renders the whole UI
 * in another language. Model-layer helpers that build display strings (status formatters, the
 * secret grouper, expiry labels) take a `Strings` argument for the same reason; their no-argument
 * overloads delegate to [EnStrings] so existing unit tests keep asserting the canonical English.
 *
 * Add a member here and the compiler forces every [AppLocale] to provide it - there is no
 * "forgotten translation" failure mode.
 */
internal interface Strings {
    val appTitle: String

    val confirm: String
    val cancel: String
    val clear: String
    val delete: String
    val edit: String
    val copy: String
    val reveal: String
    val hide: String
    val refresh: String
    val previous: String
    val next: String
    val dismiss: String
    val decline: String
    val remove: String
    val removeServerRecordsTitle: String
    fun removeServerRecordsMessage(count: Int): String
    fun removedServerRecords(records: Int, events: Long): String

    fun destinationLabel(destination: KeysteadDestination): String
    fun destinationZoneLabel(zone: KeysteadZone): String
    val lock: String
    val vaultLocked: String
    val vaultOpen: String

    val vaultLockedHeading: String
    val masterPassword: String
    val openOrCreateVault: String
    val advancedVaultLocation: String
    val vaultFile: String
    val chooseExistingVault: String
    val chooseNewVaultLocation: String
    val chooseExistingVaultDialogTitle: String
    val chooseNewVaultDialogTitle: String
    val vaultLocationHelp: String
    val vaultFileMustNotBeBlank: String
    val unlockWithDeviceLogin: String
    val localLoginCredentialUnavailable: String
    fun deviceUnlockStatus(model: DeviceUnlockUiModel): String
    val chooseDeviceStorageFirst: String
    val identityStorageCannotChange: String
    val restoreAnotherDevice: String
    val restoreAnotherDeviceIntro: String
    val restoreStepServer: String
    val restoreStepIdentity: String
    val restoreStepAccount: String
    val restoreStepVault: String
    val connectAndVerifyDevice: String
    val checkVaultAccess: String
    val noVaultPackageInstruction: String
    val availableVault: String
    val createLocalVaultFromServer: String
    val restoreCreatesLocalFile: String
    fun serverVaultRestoreStatus(model: ServerVaultRestoreModel): String
    fun availableServerVaults(count: Int): String
    fun restoredVaultFromServer(pulled: Int): String
    fun rejectedServerRecords(count: Int): String

    val editSecret: String
    val newSecret: String
    val requiredFieldsMarked: String
    val fieldTitle: String
    val fieldUrl: String
    val fieldUsername: String
    val fieldPassword: String
    val fieldCategory: String
    val fieldProvider: String
    val fieldSoftware: String
    val fieldAccount: String
    val fieldExpiry: String
    val generate: String
    val generateApiToken: String
    val generateSshKey: String
    val generateGpgKey: String
    val generateCertificate: String
    val generateMfaSecret: String
    val atLeastOneFieldRequired: String
    val openVaultFirst: String
    val updateSelected: String
    val saveSecret: String
    val cancelClear: String

    fun secretTypeLabel(type: SecretType): String
    fun shortSecretTypeLabel(type: SecretType): String
    fun secretFieldLabel(fieldName: String): String

    val secretsTitle: String
    fun secretsShown(shown: Int, total: Int): String
    val filters: String
    val search: String
    val all: String
    val clearFilters: String
    val noSavedSecrets: String
    val savedSecretsAppearHere: String
    fun expiryReminders(expired: Int, dueSoon: Int): String
    val expiryReviewRotate: String
    val selectedSecret: String
    val currentCode: String
    val authCodeShown: String
    val authCodeHidden: String
    val hideCode: String
    val showCode: String
    val copyCode: String
    val noSecretSelected: String
    val selectASecret: String
    fun secretRowLabel(title: String, typeLabel: String): String

    fun groupingLabel(mode: SecretGroupingMode): String
    fun groupingBucketLabel(mode: SecretGroupingMode): String

    fun expiryLabel(status: SecretExpiryStatus, daysRemaining: Long): String

    val settingsTitle: String
    val settingsIntro: String
    val configLocationLabel: String
    val configLocationHelp: String
    val configLocationGlobal: String
    val configLocationVaultLocal: String
    fun configLocationPath(path: String): String
    val groupSession: String
    val groupAbout: String
    val groupLanguage: String
    val groupVaultFile: String
    val languageHelp: String
    val deleteVaultFile: String
    val deleteVaultFileHelp: String
    val memoryOnly: String
    val memoryStorageDescription: String
    val deviceAccessIntro: String
    val createProtectedIdentity: String
    val verifyLocalLogin: String
    val deviceLogin: String
    val deviceLoginEnabledLabel: String
    val deviceLoginNotEnabledLabel: String
    val deviceLoginEnabledHelp: String
    val deviceLoginIdentityLocked: String
    val deviceLoginReady: String
    val deviceLoginVaultLocked: String
    val deviceLoginUnavailable: String
    val deviceLoginNotConfigured: String
    val localLoginReadyStatus: String
    val deviceLoginAlreadyEnabled: String
    val removeDeviceLogin: String
    val removeDeviceLoginTitle: String
    val removeDeviceLoginMessage: String
    val deviceLoginEnabled: String
    val deviceLoginRemoved: String
    fun deviceProtectionLabel(provider: DeviceProtectionProvider): String
    fun deviceProtectionAvailableLabel(provider: DeviceProtectionProvider): String
    fun deviceProtectionUnavailableLabel(provider: DeviceProtectionProvider): String
    val notSet: String
    val notSignedIn: String
    val noneValue: String
    fun sessionServer(url: String): String
    fun sessionUser(user: String): String
    fun sessionVaultFile(file: String): String
    fun vaultFileDeleted(file: String): String
    fun vaultFileDeleteFailed(reason: String): String
    val aboutText: String
    fun storageStatus(model: SecureStorageUiModel): String

    val recoveryHubIntro: String
    val recoverFromBackup: String
    val recoverFromServer: String
    val serverRecoveryIntro: String
    val restoreThisDeviceTask: String
    val approveAnotherDeviceTask: String
    val approveAnotherDeviceIntro: String
    val trustedDeviceRequestHelp: String
    val createApprovalRequest: String
    val waitingForApproval: String
    val checkApprovalStatus: String
    val trustedDeviceApprovalHelp: String
    val findPendingRequest: String
    val approveVaultAccess: String
    val vaultAccessApprovalSignInHelp: String
    val vaultAccessApprovalUnlockHelp: String
    fun vaultAccessRequestState(state: ServerVaultAccessRequestState): String

    val shareTitle: String
    val shareNotSignedInHelp: String
    val groupMintShare: String
    val groupRedeemShare: String
    val groupYourShares: String
    val payloadLabel: String
    val tempPassphraseLabel: String
    val passphrasePolicyHint: String
    val expires: String
    val burnAfterReading: String
    val mintShare: String
    val shareReady: String
    val shareOutOfBandOnce: String
    val shareOutOfBand: String
    val shareCodeField: String
    val tempPassphraseShort: String
    val someSharesBurnNote: String
    val redeemShare: String
    val shareOpened: String
    val payloadLabelShort: String
    val noOutstandingShares: String
    val burnsAfterReading: String
    fun shareCode(code: String): String
    fun shareExpires(at: String): String
    fun shareOpenedTitle(title: String): String
    fun shareOpenedType(type: String): String
    fun shareOpenedNote(note: String): String
    fun shareOpenedCreated(at: String): String
    fun shareCreatedExpires(created: String, expires: String): String
    fun pageOf(current: Int, total: Int): String
    fun shareTtlLabel(ttl: ShareExchange.ShareTtl): String

    val serverSync: String
    val serverChecking: String
    val serverOnline: String
    val serverUnavailable: String
    val serverUnavailableHelp: String
    val connectedOffline: String
    val connectedOfflineHelp: String
    val checkAgain: String
    val serverRequired: String
    val loginRequired: String
    val syncNotSignedInHelp: String
    val worksOffline: String
    val groupServerSignIn: String
    val groupVaultsAndSync: String
    val groupBackup: String
    val serverUrl: String
    val user: String
    val serverPassword: String
    val confirmServerPassword: String
    val serverPasswordRequirement: String
    val signIn: String
    val signedIn: String
    val createAccount: String
    val accountSignInIntro: String
    val accountCreateIntro: String
    val signInFailed: String
    val createAccountFailed: String
    val serverCredentialsRejected: String
    val serverUserAlreadyExists: String
    fun signedInAs(username: String): String
    val refreshSession: String
    val signOut: String
    val signOutEverywhere: String
    val createUser: String
    val push: String
    val pull: String
    val pullAndRetry: String
    val pullConfirmTitle: String
    val pullConfirmMessage: String
    val compareSyncTitle: String
    val compareSyncEmpty: String
    val compareAcceptSelected: String
    val compareAcceptAll: String
    val pullLatest: String
    val recordInventory: String
    val refreshRecordInventory: String
    val recordSelectionHelp: String
    val selectAllRecords: String
    val clearRecordSelection: String
    fun selectedRecordSummary(selected: Int, uploadable: Int, removable: Int): String
    fun selectedUploadSummary(selected: Int, uploadable: Int): String
    fun uploadSelectedRecords(count: Int): String
    fun removeSelectedServerCopies(count: Int): String
    fun uploadedSelectedRecords(count: Int): String
    val recordInventoryEmpty: String
    val unlockVaultToCompare: String
    val currentRecordComparison: String
    val serverRecordHistory: String
    val historyRemoveHelp: String
    val recordIdentifierHash: String
    val clientSidePane: String
    val serverSidePane: String
    val revisionLabel: String
    val recordStateLabel: String
    fun recordStateValue(deleted: Boolean?): String
    val localBadge: String
    val serverBadge: String
    val otherVaultBadge: String
    val uploadRecord: String
    val removeServerCopy: String
    val localContentHash: String
    val serverComputedContentHash: String
    val serverAdvertisedContentHash: String
    val localProfileCiphertextHash: String
    val serverProfileCiphertextHash: String
    val localEnvelopeCiphertextHash: String
    val serverEnvelopeCiphertextHash: String
    val hashVerified: String
    val hashInvalid: String
    fun remoteRecordSummary(events: Int, current: Int): String
    fun recordComparisonStatus(status: RecordComparisonStatus): String
    fun recordRevisions(local: Long?, server: Long?): String
    fun recordContentHashes(local: String?, server: String?): String
    fun recordDeletionStates(local: Boolean?, server: Boolean?): String
    fun serverSequence(sequence: Long?): String
    fun serverRecordMetadata(typeLabel: String, revision: Long, deleted: Boolean): String
    fun personalVaultMismatch(serverFingerprint: String, localFingerprint: String): String
    val exportBackup: String
    val restoreBackup: String
    val fullBackupIntro: String
    val createPortableBackup: String
    val restorePortableBackup: String
    val createPortableBackupHelp: String
    val restorePortableBackupHelp: String
    val openVaultToCreateBackup: String
    val backupSourceFile: String
    val chooseBackupSource: String
    val restoreTargetVault: String
    val chooseRestoreTarget: String
    val backupSourceInvalid: String
    val restoreTargetMustBeNew: String
    val backupPassword: String
    val confirmBackupPassword: String
    val newVaultMasterPassphrase: String
    val confirmNewVaultMasterPassphrase: String
    val backupPasswordsDoNotMatch: String
    val masterPassphrasesDoNotMatch: String
    val reviewBackupRestore: String
    val confirmBackupRestoreTitle: String
    fun confirmBackupRestoreMessage(source: String, target: String): String

    val conflictDeletedTitle: String
    val conflictNewerTitle: String
    val conflictDeletedWarning: String
    fun conflictMessage(error: KeysteadRevisionConflictException): String

    val deleteSecretTitle: String
    fun deleteSecretMessage(title: String): String
    val deleteVaultFileTitle: String
    fun deleteVaultFileMessage(file: String): String

    val signedInRestored: String
    val serverSessionExpired: String
    fun couldNotRestoreServerSession(message: String): String
    val serverAuthFailed: String
    fun couldNotReachServer(errorType: String): String
    val deletedSecret: String
    val deviceRevoked: String
    val signedInToServer: String
    fun signedInWithoutRestoreRequest(reason: String): String
    val signedInWithVerifiedDevice: String
    fun pulledAndRepushed(pulled: Int, pushed: Int): String
    fun exportedBackupTo(name: String): String
    fun restoredBackupTo(name: String): String
    val generatedPassword: String
    val generatedApiToken: String
    val generatedSshKey: String
    val generatedGpgKey: String
    val generatedCertificate: String
    val generatedMfaSecret: String
    val updatedSecret: String
    val savedSecret: String
    val serverSessionRefreshed: String
    val signedOutOfServer: String
    val signedOutEverywhere: String
    val serverUserCreatedAndSignedIn: String
    val serverVaultReady: String
    val noServerVaults: String
    fun serverVaultsList(fingerprints: String): String
    fun publishedKeyPackages(count: Int): String
    fun pushedRecords(pushed: Int, cursor: String): String
    fun pulledRecords(pulled: Int, cursor: String): String
    val provisionedVaultOpen: String
    val conflictDismissed: String
    val memberRemovedRotateNote: String
    fun publishedMissingPackages(count: Int): String
    fun vaultKeyRotation(stateName: String): String
    val vaultAccessRequestCreated: String
    val vaultAccessRequestUpdated: String
    val pendingVaultAccessRequestLoaded: String
    val vaultAccessApproved: String
    val filtersCleared: String
    val secretRevealed: String
    val copiedToClipboard: String
    val copiedCodeToClipboard: String
    val loadedSecretForEdit: String
    val copiedShareCodeToClipboard: String
    fun shareMinted(code: String): String
    val shareRedeemed: String
    fun loadedShares(count: Int): String
    fun deletedShare(code: String): String
    val exportBackupDialogTitle: String
    val restoreBackupDialogTitle: String
    val restoreTargetDialogTitle: String

    val serverLoginRequiredFirst: String
    val vaultMembershipNotFound: String
    val noPendingVaultAccessRequest: String
}

/**
 * Interface languages the client ships. [nativeName] is the language name spelled in that language,
 * so the picker stays readable regardless of the active locale.
 */
internal enum class AppLocale(val languageTag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    CHINESE("zh", "中文");

    val strings: Strings
        get() = when (this) {
            ENGLISH -> EnStrings
            CHINESE -> ZhStrings
        }

    companion object {
        /** Picks a locale for [tag] (an ISO language tag such as `Locale.toLanguageTag`), defaulting to English. */
        fun forLanguageTag(tag: String): AppLocale =
            when (tag.lowercase().substringBefore('-')) {
                "zh" -> CHINESE
                else -> ENGLISH
            }
    }
}

/** Composition-local holding the active [Strings]; provided once at the app root from the chosen [AppLocale]. */
internal val LocalStrings = compositionLocalOf<Strings> { EnStrings }

/** Reads the active [Strings]. */
@Composable
internal fun localStrings(): Strings = LocalStrings.current
