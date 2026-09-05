package top.focess.keystead.client.i18n

import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import top.focess.keystead.client.BiometricAvailability
import top.focess.keystead.client.DeviceProtectionProvider
import top.focess.keystead.client.DeviceUnlockUiModel
import top.focess.keystead.client.KeysteadRevisionConflictException
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.SecretExpiryStatus
import top.focess.keystead.client.SecretGroupingMode
import top.focess.keystead.client.SecureStorageMode
import top.focess.keystead.client.SecureStorageUiModel
import top.focess.keystead.client.ServerVaultAccessRequestState
import top.focess.keystead.client.ServerVaultRestoreModel
import top.focess.keystead.client.ShareExchange
import top.focess.keystead.client.generated.resources.Res
import top.focess.keystead.client.generated.resources.allStringResources
import top.focess.keystead.client.ui.KeysteadDestination
import top.focess.keystead.client.ui.KeysteadZone
import top.focess.keystead.model.SecretType

internal val EnStrings: Strings by lazy { loadResourceStrings(AppLocale.ENGLISH) }
internal val ZhStrings: Strings by lazy { loadResourceStrings(AppLocale.CHINESE) }

private val resourceLoadLock = Any()
private val formatArgument = Regex("""%(\d+)\$[sd]""")

private fun loadResourceStrings(locale: AppLocale): Strings {
    val values = synchronized(resourceLoadLock) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag(locale.languageTag))
            runBlocking {
                Res.allStringResources.mapValues { (_, resource) -> getString(resource) }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }
    return ResourceStrings(locale, values)
}

private class ResourceStrings(
    private val locale: AppLocale,
    private val values: Map<String, String>,
) : Strings {
    override val appTitle: String get() = text("app_title")
    override val confirm: String get() = text("confirm")
    override val cancel: String get() = text("cancel")
    override val clear: String get() = text("clear")
    override val delete: String get() = text("delete")
    override val edit: String get() = text("edit")
    override val copy: String get() = text("copy")
    override val reveal: String get() = text("reveal")
    override val hide: String get() = text("hide")
    override val refresh: String get() = text("refresh")
    override val previous: String get() = text("previous")
    override val next: String get() = text("next")
    override val dismiss: String get() = text("dismiss")
    override val decline: String get() = text("decline")
    override val remove: String get() = text("remove")
    override val removeServerRecordsTitle: String get() = text("remove_server_records_title")
    override fun removeServerRecordsMessage(count: Int): String = text("remove_server_records_message", count)
    override fun removedServerRecords(records: Int, events: Long): String = text("removed_server_records", records, events)
    override fun destinationLabel(destination: KeysteadDestination): String = enumText("destination_label", destination)
    override val lock: String get() = text("lock")
    override val openApplication: String get() = text("open_application")
    override val quit: String get() = text("quit")
    override val vaultLocked: String get() = text("vault_locked")
    override val vaultOpen: String get() = text("vault_open")
    override val vaultAutoLocked: String get() = text("vault_auto_locked")
    override val groupAutoLock: String get() = text("group_auto_lock")
    override val autoLockHelp: String get() = text("auto_lock_help")
    override fun autoLockMinutes(minutes: Int): String = text("auto_lock_minutes", minutes)
    override val vaultLockedHeading: String get() = text("vault_locked_heading")
    override val masterPassword: String get() = text("master_password")
    override val openOrCreateVault: String get() = text("open_or_create_vault")
    override val advancedVaultLocation: String get() = text("advanced_vault_location")
    override val vaultFile: String get() = text("vault_file")
    override val chooseExistingVault: String get() = text("choose_existing_vault")
    override val chooseNewVaultLocation: String get() = text("choose_new_vault_location")
    override val chooseExistingVaultDialogTitle: String get() = text("choose_existing_vault_dialog_title")
    override val chooseNewVaultDialogTitle: String get() = text("choose_new_vault_dialog_title")
    override val vaultLocationHelp: String get() = text("vault_location_help")
    override val vaultFileMustNotBeBlank: String get() = text("vault_file_must_not_be_blank")
    override val unlockWithDeviceLogin: String get() = text("unlock_with_device_login")
    override val touchIdAuthenticationReason: String get() = text("touch_id_authentication_reason")
    override val localLoginCredentialUnavailable: String get() = text("local_login_credential_unavailable")
    override val chooseDeviceStorageFirst: String get() = text("choose_device_storage_first")
    override val identityStorageCannotChange: String get() = text("identity_storage_cannot_change")
    override val restoreAnotherDevice: String get() = text("restore_another_device")
    override val restoreAnotherDeviceIntro: String get() = text("restore_another_device_intro")
    override val restoreStepServer: String get() = text("restore_step_server")
    override val restoreStepIdentity: String get() = text("restore_step_identity")
    override val restoreStepAccount: String get() = text("restore_step_account")
    override val restoreStepVault: String get() = text("restore_step_vault")
    override val connectAndVerifyDevice: String get() = text("connect_and_verify_device")
    override val checkVaultAccess: String get() = text("check_vault_access")
    override val noVaultPackageInstruction: String get() = text("no_vault_package_instruction")
    override val availableVault: String get() = text("available_vault")
    override val createLocalVaultFromServer: String get() = text("create_local_vault_from_server")
    override val restoreCreatesLocalFile: String get() = text("restore_creates_local_file")
    override fun availableServerVaults(count: Int): String = text("available_server_vaults", count)
    override fun restoredVaultFromServer(pulled: Int): String = text("restored_vault_from_server", pulled)
    override fun rejectedServerRecords(count: Int): String = text("rejected_server_records", count)
    override val editSecret: String get() = text("edit_secret")
    override val newSecret: String get() = text("new_secret")
    override val requiredFieldsMarked: String get() = text("required_fields_marked")
    override val fieldTitle: String get() = text("field_title")
    override val fieldUrl: String get() = text("field_url")
    override val fieldUsername: String get() = text("field_username")
    override val fieldPassword: String get() = text("field_password")
    override val checkBreachedPassword: String get() = text("check_breached_password")
    override val checkingPassword: String get() = text("checking_password")
    override val passwordStrengthWeak: String get() = text("password_strength_weak")
    override val passwordStrengthFair: String get() = text("password_strength_fair")
    override val passwordStrengthStrong: String get() = text("password_strength_strong")
    override val passwordNotFoundInBreaches: String get() = text("password_not_found_in_breaches")
    override fun passwordFoundInBreaches(count: Int): String = text("password_found_in_breaches", count)
    override val passwordBreachCheckUnavailable: String get() = text("password_breach_check_unavailable")
    override val passwordBreachPrivacy: String get() = text("password_breach_privacy")
    override fun passwordBreachAuditFound(count: Int): String = text("password_breach_audit_found", count)
    override fun passwordLeakBadge(count: Int): String = text("password_leak_badge", count)
    override val fieldCategory: String get() = text("field_category")
    override val fieldProvider: String get() = text("field_provider")
    override val fieldSoftware: String get() = text("field_software")
    override val fieldAccount: String get() = text("field_account")
    override val fieldExpiry: String get() = text("field_expiry")
    override val generate: String get() = text("generate")
    override val passwordGeneratorTitle: String get() = text("password_generator_title")
    override fun passwordGeneratorLength(length: Int): String = text("password_generator_length", length)
    override val passwordGeneratorUppercase: String get() = text("password_generator_uppercase")
    override val passwordGeneratorLowercase: String get() = text("password_generator_lowercase")
    override val passwordGeneratorDigits: String get() = text("password_generator_digits")
    override val passwordGeneratorSymbols: String get() = text("password_generator_symbols")
    override val passwordGeneratorSelectSymbols: String get() = text("password_generator_select_symbols")
    override val passwordGeneratorAvoidAmbiguous: String get() = text("password_generator_avoid_ambiguous")
    override val passwordGeneratorInvalidSelection: String get() = text("password_generator_invalid_selection")
    override val passwordGeneratorReplaceNotice: String get() = text("password_generator_replace_notice")
    override val generateApiToken: String get() = text("generate_api_token")
    override val generateSshKey: String get() = text("generate_ssh_key")
    override val generateGpgKey: String get() = text("generate_gpg_key")
    override val generateCertificate: String get() = text("generate_certificate")
    override val generateMfaSecret: String get() = text("generate_mfa_secret")
    override val atLeastOneFieldRequired: String get() = text("at_least_one_field_required")
    override val openVaultFirst: String get() = text("open_vault_first")
    override val updateSelected: String get() = text("update_selected")
    override val saveSecret: String get() = text("save_secret")
    override val cancelClear: String get() = text("cancel_clear")
    override fun secretTypeLabel(type: SecretType): String = enumText("secret_type_label", type)
    override fun shortSecretTypeLabel(type: SecretType): String = enumText("short_secret_type_label", type)
    override val secretsTitle: String get() = text("secrets_title")
    override fun secretsShown(shown: Int, total: Int): String = text("secrets_shown", shown, total)
    override val filters: String get() = text("filters")
    override val search: String get() = text("search")
    override val all: String get() = text("all")
    override val clearFilters: String get() = text("clear_filters")
    override val noSavedSecrets: String get() = text("no_saved_secrets")
    override val savedSecretsAppearHere: String get() = text("saved_secrets_appear_here")
    override val expiryReviewRotate: String get() = text("expiry_review_rotate")
    override val selectedSecret: String get() = text("selected_secret")
    override val secretDetails: String get() = text("secret_details")
    override val fieldLabels: String get() = text("field_labels")
    override val fieldTags: String get() = text("field_tags")
    override val fieldCreatedAt: String get() = text("field_created_at")
    override val fieldUpdatedAt: String get() = text("field_updated_at")
    override val fieldRevision: String get() = text("field_revision")
    override val currentCode: String get() = text("current_code")
    override val authCodeShown: String get() = text("auth_code_shown")
    override val authCodeHidden: String get() = text("auth_code_hidden")
    override val hideCode: String get() = text("hide_code")
    override val showCode: String get() = text("show_code")
    override val copyCode: String get() = text("copy_code")
    override val noSecretSelected: String get() = text("no_secret_selected")
    override val selectASecret: String get() = text("select_asecret")
    override fun secretRowLabel(title: String, typeLabel: String): String = text("secret_row_label", title, typeLabel)
    override fun groupingLabel(mode: SecretGroupingMode): String = enumText("grouping_label", mode)
    override fun groupingBucketLabel(mode: SecretGroupingMode): String = enumText("grouping_bucket_label", mode)
    override val settingsTitle: String get() = text("settings_title")
    override val settingsIntro: String get() = text("settings_intro")
    override val configLocationLabel: String get() = text("config_location_label")
    override val configLocationHelp: String get() = text("config_location_help")
    override val configLocationGlobal: String get() = text("config_location_global")
    override val configLocationVaultLocal: String get() = text("config_location_vault_local")
    override fun configLocationPath(path: String): String = text("config_location_path", path)
    override val groupSession: String get() = text("group_session")
    override val groupAbout: String get() = text("group_about")
    override val groupLanguage: String get() = text("group_language")
    override val groupVaultFile: String get() = text("group_vault_file")
    override val languageHelp: String get() = text("language_help")
    override val deleteVaultFile: String get() = text("delete_vault_file")
    override val deleteVaultFileHelp: String get() = text("delete_vault_file_help")
    override val memoryOnly: String get() = text("memory_only")
    override val memoryStorageDescription: String get() = text("memory_storage_description")
    override fun deviceAccessIntro(provider: DeviceProtectionProvider): String = enumText("device_access_intro", provider)
    override fun createProtectedIdentity(provider: DeviceProtectionProvider): String = enumText("create_protected_identity", provider)
    override fun verifyLocalLogin(provider: DeviceProtectionProvider): String = enumText("verify_local_login", provider)
    override val deviceLogin: String get() = text("device_login")
    override val deviceLoginEnabledLabel: String get() = text("device_login_enabled_label")
    override val deviceLoginNotEnabledLabel: String get() = text("device_login_not_enabled_label")
    override val deviceLoginEnabledHelp: String get() = text("device_login_enabled_help")
    override val deviceLoginIdentityLocked: String get() = text("device_login_identity_locked")
    override val deviceLoginReady: String get() = text("device_login_ready")
    override val deviceLoginVaultLocked: String get() = text("device_login_vault_locked")
    override val deviceLoginUnavailable: String get() = text("device_login_unavailable")
    override val deviceLoginNotConfigured: String get() = text("device_login_not_configured")
    override val localLoginReadyStatus: String get() = text("local_login_ready_status")
    override val deviceLoginAlreadyEnabled: String get() = text("device_login_already_enabled")
    override val removeDeviceLogin: String get() = text("remove_device_login")
    override val removeDeviceLoginTitle: String get() = text("remove_device_login_title")
    override val removeDeviceLoginMessage: String get() = text("remove_device_login_message")
    override val deviceLoginEnabled: String get() = text("device_login_enabled")
    override val deviceLoginRemoved: String get() = text("device_login_removed")
    override fun deviceProtectionLabel(provider: DeviceProtectionProvider): String = enumText("device_protection_label", provider)
    override fun deviceProtectionAvailableLabel(provider: DeviceProtectionProvider): String = enumText("device_protection_available_label", provider)
    override fun deviceProtectionUnavailableLabel(provider: DeviceProtectionProvider): String = enumText("device_protection_unavailable_label", provider)
    override val notSet: String get() = text("not_set")
    override val notSignedIn: String get() = text("not_signed_in")
    override val noneValue: String get() = text("none_value")
    override fun vaultFileDeleted(file: String): String = text("vault_file_deleted", file)
    override fun vaultFileDeleteFailed(reason: String): String = text("vault_file_delete_failed", reason)
    override val aboutText: String get() = text("about_text")
    override val recoveryHubIntro: String get() = text("recovery_hub_intro")
    override val recoverFromBackup: String get() = text("recover_from_backup")
    override val recoverFromServer: String get() = text("recover_from_server")
    override val serverRecoveryIntro: String get() = text("server_recovery_intro")
    override val restoreThisDeviceTask: String get() = text("restore_this_device_task")
    override val approveAnotherDeviceTask: String get() = text("approve_another_device_task")
    override val approveAnotherDeviceIntro: String get() = text("approve_another_device_intro")
    override val trustedDeviceRequestHelp: String get() = text("trusted_device_request_help")
    override val createApprovalRequest: String get() = text("create_approval_request")
    override val waitingForApproval: String get() = text("waiting_for_approval")
    override val checkApprovalStatus: String get() = text("check_approval_status")
    override val trustedDeviceApprovalHelp: String get() = text("trusted_device_approval_help")
    override val findPendingRequest: String get() = text("find_pending_request")
    override val approveVaultAccess: String get() = text("approve_vault_access")
    override val vaultAccessApprovalSignInHelp: String get() = text("vault_access_approval_sign_in_help")
    override val vaultAccessApprovalUnlockHelp: String get() = text("vault_access_approval_unlock_help")
    override fun vaultAccessRequestState(state: ServerVaultAccessRequestState): String = enumText("vault_access_request_state", state)
    override val shareTitle: String get() = text("share_title")
    override val shareNotSignedInHelp: String get() = text("share_not_signed_in_help")
    override val groupMintShare: String get() = text("group_mint_share")
    override val groupRedeemShare: String get() = text("group_redeem_share")
    override val groupYourShares: String get() = text("group_your_shares")
    override val payloadLabel: String get() = text("payload_label")
    override val tempPassphraseLabel: String get() = text("temp_passphrase_label")
    override val passphrasePolicyHint: String get() = text("passphrase_policy_hint")
    override val expires: String get() = text("expires")
    override val burnAfterReading: String get() = text("burn_after_reading")
    override val mintShare: String get() = text("mint_share")
    override val shareReady: String get() = text("share_ready")
    override val shareOutOfBandOnce: String get() = text("share_out_of_band_once")
    override val shareOutOfBand: String get() = text("share_out_of_band")
    override val shareCodeField: String get() = text("share_code_field")
    override val tempPassphraseShort: String get() = text("temp_passphrase_short")
    override val someSharesBurnNote: String get() = text("some_shares_burn_note")
    override val redeemShare: String get() = text("redeem_share")
    override val shareOpened: String get() = text("share_opened")
    override val payloadLabelShort: String get() = text("payload_label_short")
    override val noOutstandingShares: String get() = text("no_outstanding_shares")
    override val burnsAfterReading: String get() = text("burns_after_reading")
    override fun shareCode(code: String): String = text("share_code", code)
    override fun shareExpires(at: String): String = text("share_expires", at)
    override fun shareOpenedTitle(title: String): String = text("share_opened_title", title)
    override fun shareOpenedType(type: String): String = text("share_opened_type", type)
    override fun shareOpenedNote(note: String): String = text("share_opened_note", note)
    override fun shareOpenedCreated(at: String): String = text("share_opened_created", at)
    override fun shareCreatedExpires(created: String, expires: String): String = text("share_created_expires", created, expires)
    override fun pageOf(current: Int, total: Int): String = text("page_of", current, total)
    override fun shareTtlLabel(ttl: ShareExchange.ShareTtl): String = enumText("share_ttl_label", ttl)
    override val serverSync: String get() = text("server_sync")
    override val serverChecking: String get() = text("server_checking")
    override val serverOnline: String get() = text("server_online")
    override val serverUnavailable: String get() = text("server_unavailable")
    override val serverUnavailableHelp: String get() = text("server_unavailable_help")
    override val connectedOffline: String get() = text("connected_offline")
    override val connectedOfflineHelp: String get() = text("connected_offline_help")
    override val checkAgain: String get() = text("check_again")
    override val serverRequired: String get() = text("server_required")
    override val loginRequired: String get() = text("login_required")
    override val syncNotSignedInHelp: String get() = text("sync_not_signed_in_help")
    override val worksOffline: String get() = text("works_offline")
    override val groupServerSignIn: String get() = text("group_server_sign_in")
    override val groupVaultsAndSync: String get() = text("group_vaults_and_sync")
    override val groupBackup: String get() = text("group_backup")
    override val serverUrl: String get() = text("server_url")
    override val user: String get() = text("user")
    override val serverPassword: String get() = text("server_password")
    override val confirmServerPassword: String get() = text("confirm_server_password")
    override val serverPasswordRequirement: String get() = text("server_password_requirement")
    override val signIn: String get() = text("sign_in")
    override val signedIn: String get() = text("signed_in")
    override val createAccount: String get() = text("create_account")
    override val accountSignInIntro: String get() = text("account_sign_in_intro")
    override val accountCreateIntro: String get() = text("account_create_intro")
    override val signInFailed: String get() = text("sign_in_failed")
    override val createAccountFailed: String get() = text("create_account_failed")
    override val serverCredentialsRejected: String get() = text("server_credentials_rejected")
    override val serverUserAlreadyExists: String get() = text("server_user_already_exists")
    override fun signedInAs(username: String): String = text("signed_in_as", username)
    override val refreshSession: String get() = text("refresh_session")
    override val signOut: String get() = text("sign_out")
    override val signOutEverywhere: String get() = text("sign_out_everywhere")
    override val createUser: String get() = text("create_user")
    override val push: String get() = text("push")
    override val pull: String get() = text("pull")
    override val pullAndRetry: String get() = text("pull_and_retry")
    override val pullConfirmTitle: String get() = text("pull_confirm_title")
    override val pullConfirmMessage: String get() = text("pull_confirm_message")
    override val compareSyncTitle: String get() = text("compare_sync_title")
    override val compareSyncEmpty: String get() = text("compare_sync_empty")
    override val compareAcceptSelected: String get() = text("compare_accept_selected")
    override val compareAcceptAll: String get() = text("compare_accept_all")
    override val pullLatest: String get() = text("pull_latest")
    override val recordInventory: String get() = text("record_inventory")
    override val refreshRecordInventory: String get() = text("refresh_record_inventory")
    override val recordSelectionHelp: String get() = text("record_selection_help")
    override val selectAllRecords: String get() = text("select_all_records")
    override val clearRecordSelection: String get() = text("clear_record_selection")
    override fun selectedRecordSummary(selected: Int, uploadable: Int, removable: Int): String = text("selected_record_summary", selected, uploadable, removable)
    override fun selectedUploadSummary(selected: Int, uploadable: Int): String = text("selected_upload_summary", selected, uploadable)
    override fun uploadSelectedRecords(count: Int): String = text("upload_selected_records", count)
    override fun removeSelectedServerCopies(count: Int): String = text("remove_selected_server_copies", count)
    override fun uploadedSelectedRecords(count: Int): String = text("uploaded_selected_records", count)
    override val recordInventoryEmpty: String get() = text("record_inventory_empty")
    override val unlockVaultToCompare: String get() = text("unlock_vault_to_compare")
    override val currentRecordComparison: String get() = text("current_record_comparison")
    override val serverRecordHistory: String get() = text("server_record_history")
    override val historyRemoveHelp: String get() = text("history_remove_help")
    override val recordIdentifierHash: String get() = text("record_identifier_hash")
    override val clientSidePane: String get() = text("client_side_pane")
    override val serverSidePane: String get() = text("server_side_pane")
    override val revisionLabel: String get() = text("revision_label")
    override val recordStateLabel: String get() = text("record_state_label")
    override val localBadge: String get() = text("local_badge")
    override val serverBadge: String get() = text("server_badge")
    override val otherVaultBadge: String get() = text("other_vault_badge")
    override val uploadRecord: String get() = text("upload_record")
    override val removeServerCopy: String get() = text("remove_server_copy")
    override val localContentHash: String get() = text("local_content_hash")
    override val serverComputedContentHash: String get() = text("server_computed_content_hash")
    override val serverAdvertisedContentHash: String get() = text("server_advertised_content_hash")
    override val localProfileCiphertextHash: String get() = text("local_profile_ciphertext_hash")
    override val serverProfileCiphertextHash: String get() = text("server_profile_ciphertext_hash")
    override val localEnvelopeCiphertextHash: String get() = text("local_envelope_ciphertext_hash")
    override val serverEnvelopeCiphertextHash: String get() = text("server_envelope_ciphertext_hash")
    override val hashVerified: String get() = text("hash_verified")
    override val hashInvalid: String get() = text("hash_invalid")
    override fun invalidRemoteHistory(events: Int): String = text("invalid_remote_history", events)
    override fun legacyRemoteHistory(events: Int): String = text("legacy_remote_history", events)
    override fun remoteRecordSummary(events: Int, current: Int): String = text("remote_record_summary", events, current)
    override fun recordComparisonStatus(status: RecordComparisonStatus): String = enumText("record_comparison_status", status)
    override fun recordRevisions(local: Long?, server: Long?): String = text("record_revisions", local, server)
    override fun recordContentHashes(local: String?, server: String?): String = text("record_content_hashes", local, server)
    override fun serverSequence(sequence: Long?): String = text("server_sequence", sequence)
    override fun personalVaultMismatch(serverFingerprint: String, localFingerprint: String): String = text("personal_vault_mismatch", serverFingerprint, localFingerprint)
    override val exportBackup: String get() = text("export_backup")
    override val restoreBackup: String get() = text("restore_backup")
    override val fullBackupIntro: String get() = text("full_backup_intro")
    override val createPortableBackup: String get() = text("create_portable_backup")
    override val restorePortableBackup: String get() = text("restore_portable_backup")
    override val createPortableBackupHelp: String get() = text("create_portable_backup_help")
    override val restorePortableBackupHelp: String get() = text("restore_portable_backup_help")
    override val openVaultToCreateBackup: String get() = text("open_vault_to_create_backup")
    override val backupSourceFile: String get() = text("backup_source_file")
    override val chooseBackupSource: String get() = text("choose_backup_source")
    override val restoreTargetVault: String get() = text("restore_target_vault")
    override val chooseRestoreTarget: String get() = text("choose_restore_target")
    override val backupSourceInvalid: String get() = text("backup_source_invalid")
    override val restoreTargetMustBeNew: String get() = text("restore_target_must_be_new")
    override val backupPassword: String get() = text("backup_password")
    override val confirmBackupPassword: String get() = text("confirm_backup_password")
    override val newVaultMasterPassphrase: String get() = text("new_vault_master_passphrase")
    override val confirmNewVaultMasterPassphrase: String get() = text("confirm_new_vault_master_passphrase")
    override val backupPasswordsDoNotMatch: String get() = text("backup_passwords_do_not_match")
    override val masterPassphrasesDoNotMatch: String get() = text("master_passphrases_do_not_match")
    override val reviewBackupRestore: String get() = text("review_backup_restore")
    override val confirmBackupRestoreTitle: String get() = text("confirm_backup_restore_title")
    override fun confirmBackupRestoreMessage(source: String, target: String): String = text("confirm_backup_restore_message", source, target)
    override val conflictDeletedTitle: String get() = text("conflict_deleted_title")
    override val conflictNewerTitle: String get() = text("conflict_newer_title")
    override val conflictDeletedWarning: String get() = text("conflict_deleted_warning")
    override val deleteSecretTitle: String get() = text("delete_secret_title")
    override fun deleteSecretMessage(title: String): String = text("delete_secret_message", title)
    override val deleteVaultFileTitle: String get() = text("delete_vault_file_title")
    override fun deleteVaultFileMessage(file: String): String = text("delete_vault_file_message", file)
    override val signedInRestored: String get() = text("signed_in_restored")
    override val serverSessionExpired: String get() = text("server_session_expired")
    override fun couldNotRestoreServerSession(message: String): String = text("could_not_restore_server_session", message)
    override val serverAuthFailed: String get() = text("server_auth_failed")
    override fun couldNotReachServer(errorType: String): String = text("could_not_reach_server", errorType)
    override val deletedSecret: String get() = text("deleted_secret")
    override val deviceRevoked: String get() = text("device_revoked")
    override val signedInToServer: String get() = text("signed_in_to_server")
    override fun signedInWithoutRestoreRequest(reason: String): String = text("signed_in_without_restore_request", reason)
    override val signedInWithVerifiedDevice: String get() = text("signed_in_with_verified_device")
    override fun pulledAndRepushed(pulled: Int, pushed: Int): String = text("pulled_and_repushed", pulled, pushed)
    override fun exportedBackupTo(name: String): String = text("exported_backup_to", name)
    override fun restoredBackupTo(name: String): String = text("restored_backup_to", name)
    override val generatedPassword: String get() = text("generated_password")
    override val generatedApiToken: String get() = text("generated_api_token")
    override val generatedSshKey: String get() = text("generated_ssh_key")
    override val generatedGpgKey: String get() = text("generated_gpg_key")
    override val generatedCertificate: String get() = text("generated_certificate")
    override val generatedMfaSecret: String get() = text("generated_mfa_secret")
    override val updatedSecret: String get() = text("updated_secret")
    override val savedSecret: String get() = text("saved_secret")
    override val serverSessionRefreshed: String get() = text("server_session_refreshed")
    override val signedOutOfServer: String get() = text("signed_out_of_server")
    override val signedOutEverywhere: String get() = text("signed_out_everywhere")
    override val serverUserCreatedAndSignedIn: String get() = text("server_user_created_and_signed_in")
    override val serverVaultReady: String get() = text("server_vault_ready")
    override val noServerVaults: String get() = text("no_server_vaults")
    override fun serverVaultsList(fingerprints: String): String = text("server_vaults_list", fingerprints)
    override fun publishedKeyPackages(count: Int): String = text("published_key_packages", count)
    override fun pushedRecords(pushed: Int, cursor: String): String = text("pushed_records", pushed, cursor)
    override fun pulledRecords(pulled: Int, cursor: String): String = text("pulled_records", pulled, cursor)
    override val provisionedVaultOpen: String get() = text("provisioned_vault_open")
    override val conflictDismissed: String get() = text("conflict_dismissed")
    override val memberRemovedRotateNote: String get() = text("member_removed_rotate_note")
    override fun publishedMissingPackages(count: Int): String = text("published_missing_packages", count)
    override fun vaultKeyRotation(stateName: String): String = text("vault_key_rotation", stateName)
    override val vaultAccessRequestCreated: String get() = text("vault_access_request_created")
    override val vaultAccessRequestUpdated: String get() = text("vault_access_request_updated")
    override val pendingVaultAccessRequestLoaded: String get() = text("pending_vault_access_request_loaded")
    override val vaultAccessApproved: String get() = text("vault_access_approved")
    override val filtersCleared: String get() = text("filters_cleared")
    override val secretRevealed: String get() = text("secret_revealed")
    override val copiedToClipboard: String get() = text("copied_to_clipboard")
    override val copiedCodeToClipboard: String get() = text("copied_code_to_clipboard")
    override val loadedSecretForEdit: String get() = text("loaded_secret_for_edit")
    override val copiedShareCodeToClipboard: String get() = text("copied_share_code_to_clipboard")
    override fun shareMinted(code: String): String = text("share_minted", code)
    override val shareRedeemed: String get() = text("share_redeemed")
    override fun loadedShares(count: Int): String = text("loaded_shares", count)
    override fun deletedShare(code: String): String = text("deleted_share", code)
    override val exportBackupDialogTitle: String get() = text("export_backup_dialog_title")
    override val restoreBackupDialogTitle: String get() = text("restore_backup_dialog_title")
    override val restoreTargetDialogTitle: String get() = text("restore_target_dialog_title")
    override val serverLoginRequiredFirst: String get() = text("server_login_required_first")
    override val vaultMembershipNotFound: String get() = text("vault_membership_not_found")
    override val noPendingVaultAccessRequest: String get() = text("no_pending_vault_access_request")

    override fun destinationZoneLabel(zone: KeysteadZone): String =
        if (zone == KeysteadZone.INTERNAL) "" else enumText("destination_zone_label", zone)

    override fun deviceUnlockStatus(model: DeviceUnlockUiModel): String =
        text(
            "device_unlock_status__${model.state.name.lowercase(Locale.ROOT)}",
            biometricName(model.provider),
        )

    override fun serverVaultRestoreStatus(model: ServerVaultRestoreModel): String =
        text("server_vault_restore_status__${model.stage.name.lowercase(Locale.ROOT)}")

    override fun storageStatus(model: SecureStorageUiModel): String {
        val provider = biometricName(DeviceProtectionProvider.from(model.providerId))
        val selected = when (model.selectedMode) {
            SecureStorageMode.BIOMETRIC ->
                text(
                    if (model.biometricActive) {
                        "storage_selected__biometric_active"
                    } else {
                        "storage_selected__biometric_inactive"
                    },
                    provider,
                )
            SecureStorageMode.MEMORY_ONLY -> text("storage_selected__memory_only")
            null -> text("storage_selected__none")
        }
        val availability = when (model.biometricAvailability) {
            BiometricAvailability.NOT_CHECKED -> text("storage_availability__not_checked")
            BiometricAvailability.CHECKING -> text("storage_availability__checking")
            BiometricAvailability.AVAILABLE -> text("storage_availability__available", provider)
            BiometricAvailability.UNAVAILABLE ->
                text("storage_availability__unavailable", model.diagnosticCode ?: "provider-unavailable")
        }
        return text("storage_status_pattern", selected, availability)
    }

    override fun expiryReminders(expired: Int, dueSoon: Int): String =
        when {
            expired > 0 && dueSoon > 0 -> text("expiry_reminders", expired, dueSoon)
            expired > 0 -> text("expiry_reminders__expired_only", expired)
            dueSoon > 0 -> text("expiry_reminders__due_soon_only", dueSoon)
            else -> text("expiry_reminders__none")
        }

    override fun expiryLabel(status: SecretExpiryStatus, daysRemaining: Long): String =
        when (status) {
            SecretExpiryStatus.EXPIRED -> {
                val days = -daysRemaining
                if (days == 1L) text("expiry_label__expired_one") else text("expiry_label__expired_many", days)
            }
            SecretExpiryStatus.DUE_SOON ->
                if (daysRemaining == 0L) text("expiry_label__due_today") else text("expiry_label__due_many", daysRemaining)
            SecretExpiryStatus.ACTIVE ->
                if (daysRemaining == 1L) text("expiry_label__active_one") else text("expiry_label__active_many", daysRemaining)
        }

    override fun secretFieldLabel(fieldName: String): String {
        when (fieldName) {
            "title" -> return text("field_title")
            "username" -> return text("field_username")
            "password" -> return text("field_password")
            "url" -> return text("field_url")
            "body" -> return text("secret_field_label__body")
            "notes" -> return text("secret_field_label__note")
            "deleted" -> return recordStateLabel
        }
        val suffix = when (fieldName) {
            "note" -> "note"
            "publicKey" -> "public_key"
            "privateKey" -> "private_key"
            "passphrase" -> "passphrase"
            "token" -> "token"
            "seed" -> "seed"
            "otpauthUri" -> "otpauth_uri"
            "certificate" -> "certificate"
            "value" -> "value"
            else -> return fieldName
        }
        return text("secret_field_label__$suffix")
    }

    override fun customAttributeLabel(name: String): String = name

    override fun sessionServer(url: String): String =
        text("session_server", url.ifBlank { text("not_set") })

    override fun sessionUser(user: String): String =
        text("session_user", user.ifBlank { text("not_signed_in") })

    override fun sessionVaultFile(file: String): String =
        text("session_vault_file", file.ifBlank { text("none_value") })

    override fun recordStateValue(deleted: Boolean?): String = recordState(deleted)

    override fun recordDeletionStates(local: Boolean?, server: Boolean?): String =
        text("record_deletion_states", recordState(local), recordState(server))

    override fun serverRecordMetadata(typeLabel: String, revision: Long, deleted: Boolean): String =
        text(
            if (deleted) "server_record_metadata__deleted" else "server_record_metadata__active",
            typeLabel,
            revision,
        )

    override fun errorMessage(error: Throwable): String {
        if (error is KeysteadRevisionConflictException) return conflictMessage(error)
        if (locale == AppLocale.ENGLISH) return error.message ?: text("error_generic")
        error.message?.takeIf { it in values.values }?.let { return it }
        val key = localizedErrorKey(error)
        return if (key == null) text("error_generic") else text(key)
    }

    override fun conflictMessage(error: KeysteadRevisionConflictException): String {
        val latest = error.serverRevision ?: error.latestRevision
        val rejected = error.clientRevision ?: error.rejectedRevision
        val prefix = conflictPrefix(error)
        if (latest != null && rejected != null) {
            return text("conflict_revision_message", prefix, latest, rejected)
        }
        if (locale == AppLocale.CHINESE) return text("conflict_pull_again", text("conflict_default_message"))
        val message = error.message ?: text("conflict_default_message")
        val alreadyActionable =
            message.contains("pull before pushing", ignoreCase = true) ||
                (locale == AppLocale.CHINESE && message.contains("pull", ignoreCase = true))
        return if (alreadyActionable) message else text("conflict_pull_again", message)
    }

    private fun conflictPrefix(error: KeysteadRevisionConflictException): String {
        val fingerprint = error.fingerprint ?: return ""
        val secretId = error.secretId ?: return ""
        val deleted = error.serverDeleted == true
        val updatedAt = error.serverUpdatedAt
        val key = when {
            deleted && updatedAt != null -> "conflict_prefix_deleted_at"
            deleted -> "conflict_prefix_deleted"
            updatedAt != null -> "conflict_prefix_newer_at"
            else -> "conflict_prefix_newer"
        }
        return if (updatedAt == null) {
            text(key, secretId, fingerprint)
        } else {
            text(key, secretId, fingerprint, updatedAt)
        }
    }

    private fun recordState(deleted: Boolean?): String =
        text(
            when (deleted) {
                null -> "record_state_value__not_present"
                true -> "record_state_value__deleted"
                false -> "record_state_value__active"
            },
        )

    private fun biometricName(provider: DeviceProtectionProvider): String =
        text("biometric_name__${provider.name.lowercase(Locale.ROOT)}")

    private fun enumText(baseKey: String, value: Enum<*>): String =
        text("${baseKey}__${value.name.lowercase(Locale.ROOT)}")

    private fun text(key: String, vararg args: Any?): String {
        val template = values[key] ?: error("Missing ${locale.languageTag} string resource: $key")
        if (args.isEmpty()) return template
        return formatArgument.replace(template) { match ->
            val index = match.groupValues[1].toInt() - 1
            check(index in args.indices) { "Missing format argument ${index + 1} for $key" }
            args[index]?.toString() ?: "—"
        }
    }
}
