package top.focess.keystead.client.i18n

import top.focess.keystead.client.DeviceUnlockState
import top.focess.keystead.client.DeviceUnlockUiModel
import top.focess.keystead.client.DeviceProtectionProvider
import top.focess.keystead.client.KeysteadRevisionConflictException
import top.focess.keystead.client.RecordComparisonStatus
import top.focess.keystead.client.BiometricAvailability
import top.focess.keystead.client.SecretExpiryStatus
import top.focess.keystead.client.SecretGroupingMode
import top.focess.keystead.client.SecureStorageUiModel
import top.focess.keystead.client.SecureStorageMode
import top.focess.keystead.client.ServerVaultRestoreModel
import top.focess.keystead.client.ServerVaultRestoreStage
import top.focess.keystead.client.ServerVaultAccessRequestState
import top.focess.keystead.client.ShareExchange
import top.focess.keystead.client.ui.KeysteadDestination
import top.focess.keystead.client.ui.KeysteadZone
import top.focess.keystead.model.SecretType

internal object EnStrings : Strings {
    override val appTitle = "Keystead"

    override val confirm = "Confirm"
    override val cancel = "Cancel"
    override val clear = "Clear"
    override val delete = "Delete"
    override val edit = "Edit"
    override val copy = "Copy"
    override val reveal = "Reveal"
    override val hide = "Hide"
    override val refresh = "Refresh"
    override val previous = "Previous"
    override val next = "Next"
    override val dismiss = "Dismiss"
    override val decline = "Decline"
    override val remove = "Remove"
    override val removeServerRecordsTitle = "Remove server copies?"
    override fun removeServerRecordsMessage(count: Int) =
        "Remove all server history for $count selected record(s)? The local vault is unchanged. " +
            "These records can be uploaded again by another client that still has them. " +
            "The server keeps only a redacted audit event."
    override fun removedServerRecords(records: Int, events: Long) =
        "Removed $events server event(s) for $records selected record(s). Local records were unchanged."

    override fun destinationLabel(destination: KeysteadDestination) = when (destination) {
        KeysteadDestination.SECRETS -> "Secrets"
        KeysteadDestination.ADD -> "Add"
        KeysteadDestination.BACKUP -> "Backup"
        KeysteadDestination.DEVICE_ACCESS -> "Local login"
        KeysteadDestination.ACCOUNT -> "Account"
        KeysteadDestination.SYNC -> "Sync"
        KeysteadDestination.SHARE -> "Share"
        KeysteadDestination.RECOVERY -> "Recovery"
        KeysteadDestination.SETTINGS -> "Settings"
    }

    override fun destinationZoneLabel(zone: KeysteadZone) = when (zone) {
        KeysteadZone.LOCAL_VAULT -> "Local vault"
        KeysteadZone.CONNECTED -> "Connected"
        KeysteadZone.SYSTEM -> "System"
        KeysteadZone.INTERNAL -> ""
    }

    override val lock = "Lock"
    override val vaultLocked = "Vault locked"
    override val vaultOpen = "Vault open"

    override val vaultLockedHeading = "Your vault is locked"
    override val masterPassword = "Master password"
    override val openOrCreateVault = "Open or create vault"
    override val advancedVaultLocation = "Advanced (vault location)"
    override val vaultFile = "Vault file"
    override val chooseExistingVault = "Open existing…"
    override val chooseNewVaultLocation = "Choose new location…"
    override val chooseExistingVaultDialogTitle = "Open an existing Keystead vault"
    override val chooseNewVaultDialogTitle = "Choose a new Keystead vault location"
    override val vaultLocationHelp =
        "After a vault opens successfully, this path becomes the default for the next launch. " +
            "Changing it does not move or delete the previous file."
    override val vaultFileMustNotBeBlank = "Vault file must not be blank"
    override val unlockWithDeviceLogin = "Unlock with local login"
    override val localLoginCredentialUnavailable =
        "The local-login credential is unavailable. Reload or create it on the Local login page."
    override fun deviceUnlockStatus(model: DeviceUnlockUiModel) = when (model.state) {
        DeviceUnlockState.NOT_CONFIGURED -> "Local login is not configured."
        DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED ->
            "Device login is not enabled for this vault. Open it with the master password."
        DeviceUnlockState.LOADED -> "The local login credential is loaded and ready."
        DeviceUnlockState.BIOMETRIC_NOT_SELECTED -> "Local login uses Windows Hello. Select it on the Local login page."
        DeviceUnlockState.BIOMETRIC_UNAVAILABLE -> "Windows Hello is unavailable or not configured. Keystead will not bypass it."
        DeviceUnlockState.BIOMETRIC_READY -> "Windows Hello is ready. Windows will verify you before the vault opens."
    }
    override val chooseDeviceStorageFirst = "Choose local-login protection first"
    override val identityStorageCannotChange =
        "Local login is already configured. Remove it before choosing different protection."
    override val restoreAnotherDevice = "Restore from Keystead Server"
    override val restoreAnotherDeviceIntro =
        "The server stores encrypted records, not the DEK. Request access explicitly, then have another device that already opens this vault verify the request fingerprint and authorize it."
    override val restoreStepServer = "Server"
    override val restoreStepIdentity = "Request"
    override val restoreStepAccount = "Account"
    override val restoreStepVault = "Vault"
    override val connectAndVerifyDevice = "Connect and verify this device"
    override val checkVaultAccess = "Check for vault access"
    override val noVaultPackageInstruction =
        "No vault package is available for this device yet. Create an access request and approve it from Recovery on a trusted device."
    override val availableVault = "Available vault"
    override val createLocalVaultFromServer = "Create local vault and download records"
    override val restoreCreatesLocalFile =
        "Choose a new local master passphrase. Local login is optional and can be enabled later."
    override fun serverVaultRestoreStatus(model: ServerVaultRestoreModel): String =
        when (model.stage) {
            ServerVaultRestoreStage.SIGN_IN_REQUIRED ->
                "Sign in on the Account page before restoring from the server."
            ServerVaultRestoreStage.SERVER_OFFLINE ->
                "Server restore is unavailable until the server can be reached."
            ServerVaultRestoreStage.ACCESS_REQUEST_REQUIRED ->
                "Create a one-time request, then approve its fingerprint on another signed-in device with this vault open."
            ServerVaultRestoreStage.WAITING_FOR_APPROVAL ->
                "Waiting for a trusted device to approve this request."
            ServerVaultRestoreStage.REQUEST_EXPIRED ->
                "This request expired. Create a new approval request."
            ServerVaultRestoreStage.WAITING_FOR_PACKAGE ->
                "Approval is being finalized. Refresh this request."
            ServerVaultRestoreStage.READY_TO_RESTORE ->
                "Encrypted vault access is ready."
            ServerVaultRestoreStage.TARGET_IN_USE ->
                "This vault path already exists. Choose a new file."
            ServerVaultRestoreStage.MASTER_PASSPHRASE_REQUIRED ->
                "Set and confirm a new master passphrase for this reconstructed local vault."
        }
    override fun availableServerVaults(count: Int) = "$count vault(s) can be restored."
    override fun restoredVaultFromServer(pulled: Int) =
        "Restored the local vault and downloaded $pulled encrypted record(s)"
    override fun rejectedServerRecords(count: Int) =
        "Ignored $count server record(s) that could not be authenticated with this vault key."

    override val editSecret = "Edit secret"
    override val newSecret = "New secret"
    override val requiredFieldsMarked = "Required fields are marked with *."
    override val fieldTitle = "Title"
    override val fieldUrl = "URL"
    override val fieldUsername = "Username"
    override val fieldPassword = "Password"
    override val fieldCategory = "Category"
    override val fieldProvider = "Provider"
    override val fieldSoftware = "Software"
    override val fieldAccount = "Account"
    override val fieldExpiry = "Expiry (optional, YYYY-MM-DD)"
    override val generate = "Generate"
    override val generateApiToken = "Generate API token"
    override val generateSshKey = "Generate SSH key"
    override val generateGpgKey = "Generate GPG key"
    override val generateCertificate = "Generate certificate"
    override val generateMfaSecret = "Generate MFA secret"
    override val atLeastOneFieldRequired = "At least one field is required to save."
    override val openVaultFirst = "Open vault first"
    override val updateSelected = "Update selected"
    override val saveSecret = "Save secret"
    override val cancelClear = "Cancel / Clear"

    override fun secretTypeLabel(type: SecretType) = when (type) {
        SecretType.LOGIN_PASSWORD -> "Login"
        SecretType.SECURE_NOTE -> "Secure note"
        SecretType.SSH_KEY -> "SSH key"
        SecretType.API_TOKEN -> "API token"
        SecretType.GPG_KEY -> "GPG key"
        SecretType.MFA_SECRET -> "MFA secret"
        SecretType.CERTIFICATE -> "Certificate"
        SecretType.GENERIC_SECRET -> "Generic"
    }

    override fun shortSecretTypeLabel(type: SecretType) = when (type) {
        SecretType.LOGIN_PASSWORD -> "Login"
        SecretType.SSH_KEY -> "SSH"
        SecretType.API_TOKEN -> "API"
        SecretType.GPG_KEY -> "GPG"
        SecretType.MFA_SECRET -> "MFA"
        SecretType.CERTIFICATE -> "Cert"
        SecretType.GENERIC_SECRET -> "Generic"
        SecretType.SECURE_NOTE -> "Note"
    }

    override fun secretFieldLabel(fieldName: String) = when (fieldName) {
        "note" -> "Note"
        "publicKey" -> "Public key"
        "privateKey" -> "Private key"
        "passphrase" -> "Passphrase"
        "token" -> "Token"
        "seed" -> "Seed"
        "otpauthUri" -> "otpauth URI"
        "certificate" -> "Certificate"
        "value" -> "Value"
        else -> fieldName
    }

    override val secretsTitle = "Secrets"
    override fun secretsShown(shown: Int, total: Int) = "$shown of $total shown"
    override val filters = "Filters"
    override val search = "Search"
    override val all = "All"
    override val clearFilters = "Clear filters"
    override val noSavedSecrets = "No saved secrets"
    override val savedSecretsAppearHere = "Saved secrets will appear here."
    override fun expiryReminders(expired: Int, dueSoon: Int): String {
        val parts = buildList {
            if (expired > 0) add("$expired expired")
            if (dueSoon > 0) add("$dueSoon expiring soon")
        }
        return "Expiry reminders: ${parts.joinToString(", ")}"
    }
    override val expiryReviewRotate = "Review and rotate these secrets."
    override val selectedSecret = "Selected secret"
    override val currentCode = "Current code"
    override val authCodeShown = "Authentication code shown"
    override val authCodeHidden = "Authentication code hidden"
    override val hideCode = "Hide code"
    override val showCode = "Show code"
    override val copyCode = "Copy code"
    override val noSecretSelected = "No secret selected"
    override val selectASecret = "Select a secret from the list."
    override fun secretRowLabel(title: String, typeLabel: String) = "Secret: $title, $typeLabel"

    override fun groupingLabel(mode: SecretGroupingMode) = when (mode) {
        SecretGroupingMode.NONE -> "None"
        SecretGroupingMode.TYPE -> "Type"
        SecretGroupingMode.CATEGORY -> "Category"
        SecretGroupingMode.PROVIDER -> "Provider"
    }

    override fun groupingBucketLabel(mode: SecretGroupingMode) = when (mode) {
        SecretGroupingMode.CATEGORY -> "No category"
        SecretGroupingMode.PROVIDER -> "No provider"
        else -> "Other"
    }

    override fun expiryLabel(status: SecretExpiryStatus, daysRemaining: Long) = when (status) {
        SecretExpiryStatus.EXPIRED -> {
            val days = -daysRemaining
            if (days == 1L) "expired 1 day ago" else "expired $days days ago"
        }
        SecretExpiryStatus.DUE_SOON ->
            if (daysRemaining == 0L) "expires today" else "expires in $daysRemaining days"
        SecretExpiryStatus.ACTIVE ->
            if (daysRemaining == 1L) "expires in 1 day" else "expires in $daysRemaining days"
    }

    override val settingsTitle = "Settings"
    override val settingsIntro =
        "Device storage and session details. Vaults are local encrypted files; the server only ever sees encrypted records and fingerprints."
    override val groupSession = "Session"
    override val groupAbout = "About"
    override val groupLanguage = "Language"
    override val groupVaultFile = "Vault file"
    override val languageHelp = "Choose the interface language. Takes effect immediately."
    override val deleteVaultFile = "Delete vault file..."
    override val deleteVaultFileHelp =
        "Permanently removes the currently open encrypted vault file from this computer."
    override val memoryOnly = "Session only (RAM)"
    override val memoryStorageDescription = "Private key lives in RAM only and is discarded when you explicitly lock the identity or quit the app."
    override val deviceAccessIntro =
        "Open this local vault with Windows Hello. Local login never connects to Keystead Server."
    override val createProtectedIdentity = "Set up Windows Hello"
    override val verifyLocalLogin = "Verify with Windows Hello"
    override val deviceLogin = "Local login"
    override val deviceLoginEnabledLabel = "Enabled"
    override val deviceLoginNotEnabledLabel = "Not enabled"
    override val deviceLoginEnabledHelp =
        "This vault can now open on this computer without your master password."
    override val deviceLoginIdentityLocked =
        "Load the local credential to enable local login for this open vault."
    override val deviceLoginReady =
        "Not enabled. Open this vault with its master password to enable device login again."
    override val deviceLoginVaultLocked =
        "Prepare the local credential now. It will be attached after you open or reconstruct a vault."
    override val deviceLoginUnavailable = "Local login requires persistent protected storage."
    override val deviceLoginNotConfigured = "Local login has not been configured."
    override val localLoginReadyStatus = "Local login credential is ready"
    override val deviceLoginAlreadyEnabled = "Local login is already enabled for this vault."
    override val removeDeviceLogin = "Remove local login..."
    override val removeDeviceLoginTitle = "Remove local login?"
    override val removeDeviceLoginMessage =
        "You must use the vault master password the next time you open this vault. This removes all local-login slots."
    override val deviceLoginEnabled = "Local login enabled for this vault"
    override val deviceLoginRemoved = "Local login removed; use the master password next time"
    override fun deviceProtectionLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "Protected by Windows Hello"
            DeviceProtectionProvider.UNKNOWN -> "Biometric protection unavailable"
        }
    override fun deviceProtectionAvailableLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "Windows Hello is available"
            DeviceProtectionProvider.UNKNOWN -> "Biometric protection is available"
        }
    override fun deviceProtectionUnavailableLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "Windows Hello is unavailable or not configured"
            DeviceProtectionProvider.UNKNOWN -> "Biometric protection is unavailable on this platform"
        }
    override val notSet = "(not set)"
    override val notSignedIn = "(not signed in)"
    override val noneValue = "(none)"
    override fun sessionServer(url: String) = "Server: ${url.ifBlank { notSet }}"
    override fun sessionUser(user: String) = "User: ${user.ifBlank { notSignedIn }}"
    override fun sessionVaultFile(file: String) =
        "Current vault file: ${file.ifBlank { noneValue }}"
    override fun vaultFileDeleted(file: String) = "Deleted vault file $file"
    override fun vaultFileDeleteFailed(reason: String) = "Could not delete vault file: $reason"
    override val aboutText =
        "Keystead - local-first, zero-knowledge secret vault. Secrets are encrypted on this device before anything leaves it."
    override fun storageStatus(model: SecureStorageUiModel): String {
        val selected =
            when (model.selectedMode) {
                SecureStorageMode.BIOMETRIC ->
                    if (model.biometricActive) "Selected: Windows Hello"
                    else "Selected: Windows Hello (inactive)"
                SecureStorageMode.MEMORY_ONLY -> "Selected: memory only"
                null -> "Selected: no storage mode"
            }
        val availability =
            when (model.biometricAvailability) {
                BiometricAvailability.NOT_CHECKED -> "Windows Hello not checked"
                BiometricAvailability.CHECKING -> "Checking Windows Hello"
                BiometricAvailability.AVAILABLE ->
                    "Biometric provider available: ${model.providerId ?: "Windows Hello"}"
                BiometricAvailability.UNAVAILABLE ->
                    "Biometric provider unavailable: ${model.diagnosticCode ?: "provider-unavailable"}"
            }
        return "$selected. $availability."
    }

    override val recoveryHubIntro =
        "Restore a new local vault from a portable backup or from Keystead Server."
    override val recoverFromBackup = "Portable backup"
    override val recoverFromServer = "Keystead Server"
    override val serverRecoveryIntro =
        "Server recovery uses a one-time exchange key created for this sign-in. Local biometric login is never sent to the server."
    override val restoreThisDeviceTask = "Rebuild on this device"
    override val approveAnotherDeviceTask = "Authorize recovery request"
    override val approveAnotherDeviceIntro =
        "Open the vault that owns the server records, verify the request fingerprint, then encrypt its DEK to the request's one-time public key."
    override val trustedDeviceRequestHelp =
        "On another device that already has this vault open, choose Recovery → Keystead Server → Authorize recovery request."
    override val createApprovalRequest = "Request recovery access"
    override val waitingForApproval = "Waiting for another device to approve this request."
    override val checkApprovalStatus = "Refresh request status"
    override val trustedDeviceApprovalHelp =
        "Compare every character of the fingerprint with the requesting device. Confirmation sends only the DEK encrypted to that one-time request key."
    override val findPendingRequest = "Refresh requests awaiting authorization"
    override val approveVaultAccess = "Confirm and send the vault key"
    override val vaultAccessApprovalSignInHelp = "Sign in on the Account page to view recovery requests."
    override val vaultAccessApprovalUnlockHelp = "Open the local vault that owns the server records before authorizing."
    override fun vaultAccessRequestState(state: ServerVaultAccessRequestState) =
        when (state) {
            ServerVaultAccessRequestState.PENDING -> "Awaiting authorization"
            ServerVaultAccessRequestState.APPROVED -> "Authorized"
            ServerVaultAccessRequestState.EXPIRED -> "Expired"
        }
    override val shareTitle = "Share"
    override val shareNotSignedInHelp =
        "Sign in to Keystead Server on the Account page to mint shares. Redeeming a share you received does not require signing in."
    override val groupMintShare = "Mint a share"
    override val groupRedeemShare = "Redeem a share"
    override val groupYourShares = "Your shares on the server"
    override val payloadLabel = "Payload (the secret to share)"
    override val tempPassphraseLabel = "Temp passphrase (min 12 chars, 3 classes)"
    override val passphrasePolicyHint = "Use 12+ characters across at least 3 of lower/upper/digit/symbol."
    override val expires = "Expires"
    override val burnAfterReading = "Burn after reading (one-time redeem)"
    override val mintShare = "Mint share"
    override val shareReady = "Share ready"
    override val shareOutOfBandOnce =
        "Share this code and the passphrase out of band. The recipient can redeem it once."
    override val shareOutOfBand = "Share this code and the passphrase out of band."
    override val shareCodeField = "Share code"
    override val tempPassphraseShort = "Temp passphrase"
    override val someSharesBurnNote =
        "Some shares burn after reading - enter the passphrase carefully, as it can only be redeemed once."
    override val redeemShare = "Redeem share"
    override val shareOpened = "Share opened"
    override val payloadLabelShort = "Payload:"
    override val noOutstandingShares = "No outstanding shares. Mint one above, or refresh to check the server."
    override val burnsAfterReading = "Burns after reading"
    override fun shareCode(code: String) = "Code: $code"
    override fun shareExpires(at: String) = "Expires: $at"
    override fun shareOpenedTitle(title: String) = "Title: $title"
    override fun shareOpenedType(type: String) = "Type: $type"
    override fun shareOpenedNote(note: String) = "Note: $note"
    override fun shareOpenedCreated(at: String) = "Created: $at"
    override fun shareCreatedExpires(created: String, expires: String) = "Created $created - expires $expires"
    override fun pageOf(current: Int, total: Int) = "Page $current of $total"
    override fun shareTtlLabel(ttl: ShareExchange.ShareTtl) = ttl.label

    override val serverSync = "Server sync"
    override val serverChecking = "Checking server"
    override val serverOnline = "Server online"
    override val serverUnavailable = "Server unavailable"
    override val serverUnavailableHelp =
        "Online features are disabled. Start the server or check its address, then try again."
    override val connectedOffline = "Offline"
    override val connectedOfflineHelp =
        "Server unavailable. Check the server address or start the server, then try again."
    override val checkAgain = "Check again"
    override val serverRequired = "Server required"
    override val loginRequired = "Login required"
    override val syncNotSignedInHelp =
        "Sign in on the Account page to pull, upload, and compare encrypted records."
    override val worksOffline = "Works offline"
    override val groupServerSignIn = "Server sign-in"
    override val groupVaultsAndSync = "Vaults and sync"
    override val groupBackup = "Backup"
    override val serverUrl = "Server URL"
    override val user = "User"
    override val serverPassword = "Account password"
    override val confirmServerPassword = "Confirm account password"
    override val serverPasswordRequirement = "Use 12–72 characters and at most 72 UTF-8 bytes."
    override val signIn = "Sign in"
    override val signedIn = "Signed in"
    override val createAccount = "Create account"
    override val accountSignInIntro = "Sign in to use this Keystead Server."
    override val accountCreateIntro =
        "Create an account on this server, then sign in automatically."
    override val signInFailed = "Could not sign in"
    override val createAccountFailed = "Could not create account"
    override val serverCredentialsRejected = "The username or password is incorrect."
    override val serverUserAlreadyExists = "An account with this username already exists."
    override fun signedInAs(username: String) = "Signed in as $username"
    override val refreshSession = "Refresh session"
    override val signOut = "Sign out"
    override val signOutEverywhere = "Sign out everywhere"
    override val createUser = "Create user"
    override val push = "Push"
    override val pull = "Pull"
    override val pullAndRetry = "Pull and retry"
    override val pullConfirmTitle = "Pull server records?"
    override val pullConfirmMessage =
        "Imports new server records into this vault. Records encrypted under another vault's key are rejected."
    override val compareSyncTitle = "Compare and sync"
    override val compareSyncEmpty = "No server records to compare."
    override val compareAcceptSelected = "Accept selected"
    override val compareAcceptAll = "Accept all"
    override val pullLatest = "Pull latest"
    override val recordInventory = "Server record inventory"
    override val refreshRecordInventory = "Refresh record inventory"
    override val recordSelectionHelp =
        "Select records explicitly. Upload uses local encrypted records; removal deletes only their server history."
    override val selectAllRecords = "Select all"
    override val clearRecordSelection = "Clear selection"
    override fun selectedRecordSummary(selected: Int, uploadable: Int, removable: Int) =
        "$selected selected · $uploadable can upload · $removable have server copies"
    override fun selectedUploadSummary(selected: Int, uploadable: Int) =
        "$selected selected · $uploadable can upload"
    override fun uploadSelectedRecords(count: Int) = "Upload selected ($count)"
    override fun removeSelectedServerCopies(count: Int) = "Remove server copies ($count)"
    override fun uploadedSelectedRecords(count: Int) =
        "Uploaded $count selected encrypted record(s). Unselected records were not uploaded."
    override val recordInventoryEmpty = "The server has no encrypted record events for this account."
    override val unlockVaultToCompare =
        "Open the local vault before loading or comparing server records."
    override val currentRecordComparison = "Current record comparison"
    override val serverRecordHistory = "Server event history"
    override val historyRemoveHelp =
        "Tick any event to select its whole record. Removal deletes all server events for the selected record(s); local records stay unchanged."
    override val recordIdentifierHash = "Record identifier hash"
    override val clientSidePane = "This device (local)"
    override val serverSidePane = "Server"
    override val revisionLabel = "Revision"
    override val recordStateLabel = "State"
    override fun recordStateValue(deleted: Boolean?) =
        when (deleted) {
            null -> "not present"
            true -> "deleted"
            false -> "active"
        }
    override val localBadge = "Local"
    override val serverBadge = "Server"
    override val otherVaultBadge = "Other vault"
    override val uploadRecord = "Upload"
    override val removeServerCopy = "Remove server copy"
    override val localContentHash = "Local event hash"
    override val serverComputedContentHash = "Server event hash (computed)"
    override val serverAdvertisedContentHash = "Server event hash (advertised)"
    override val localProfileCiphertextHash = "Local profile ciphertext hash"
    override val serverProfileCiphertextHash = "Server profile ciphertext hash"
    override val localEnvelopeCiphertextHash = "Local envelope ciphertext hash"
    override val serverEnvelopeCiphertextHash = "Server envelope ciphertext hash"
    override val hashVerified = "Hash verified"
    override val hashInvalid = "Hash mismatch"
    override fun remoteRecordSummary(events: Int, current: Int) =
        "$events encrypted server events; $current records have a current server state."
    override fun recordComparisonStatus(status: RecordComparisonStatus) =
        when (status) {
            RecordComparisonStatus.MATCHED -> "Matched"
            RecordComparisonStatus.LOCAL_ONLY -> "Local only"
            RecordComparisonStatus.SERVER_ONLY -> "Server only"
            RecordComparisonStatus.LOCAL_NEWER -> "Local is newer"
            RecordComparisonStatus.SERVER_NEWER -> "Server is newer"
            RecordComparisonStatus.HASH_MISMATCH -> "Hash mismatch"
        }
    override fun recordRevisions(local: Long?, server: Long?) =
        "Local revision ${local ?: "—"} · server revision ${server ?: "—"}"
    override fun recordContentHashes(local: String?, server: String?) =
        "Local event hash: ${local ?: "—"}\nServer event hash (computed): ${server ?: "—"}"
    override fun recordDeletionStates(local: Boolean?, server: Boolean?) =
        "Local state: ${recordState(local)} · server state: ${recordState(server)}"
    override fun serverSequence(sequence: Long?) =
        "Server sequence: ${sequence ?: "—"}"
    override fun serverRecordMetadata(typeLabel: String, revision: Long, deleted: Boolean) =
        "$typeLabel · server revision $revision${if (deleted) " · deleted" else ""}"

    private fun recordState(deleted: Boolean?): String =
        when (deleted) {
            null -> "not present"
            true -> "deleted"
            false -> "active"
        }
    override fun personalVaultMismatch(serverFingerprint: String, localFingerprint: String) =
        "The server records belong to another vault. Server fingerprint: $serverFingerprint; current local fingerprint: $localFingerprint. Push and approval are blocked because that would pair one vault's DEK with another vault's records. Open the correct local vault, or restore the server vault into a new file on the requesting device."
    override val exportBackup = "Export backup"
    override val restoreBackup = "Restore backup"
    override val fullBackupIntro = "A portable backup is a complete encrypted copy of one vault."
    override val createPortableBackup = "Create portable backup"
    override val restorePortableBackup = "Restore portable backup"
    override val createPortableBackupHelp =
        "Protect the .ksbackup with an independent password. It can restore this vault without this computer, device login, the server, or the original vault passphrase."
    override val restorePortableBackupHelp =
        "Choose a .ksbackup and a new .kvault location. Restore never overwrites an existing file and does not contact the server."
    override val openVaultToCreateBackup = "Open the vault you want to back up first."
    override val backupSourceFile = "Backup file (.ksbackup)"
    override val chooseBackupSource = "Choose backup"
    override val restoreTargetVault = "New vault location (.kvault)"
    override val chooseRestoreTarget = "Choose location"
    override val backupSourceInvalid = "Choose an existing .ksbackup file."
    override val restoreTargetMustBeNew = "Choose a new .kvault file. Existing files are never overwritten."
    override val backupPassword = "Backup password"
    override val confirmBackupPassword = "Confirm backup password"
    override val newVaultMasterPassphrase = "New vault master passphrase"
    override val confirmNewVaultMasterPassphrase = "Confirm new master passphrase"
    override val backupPasswordsDoNotMatch = "Backup passwords do not match"
    override val masterPassphrasesDoNotMatch = "New master passphrases do not match"
    override val reviewBackupRestore = "Review restore"
    override val confirmBackupRestoreTitle = "Create restored vault?"
    override fun confirmBackupRestoreMessage(source: String, target: String) =
        "Backup:\n$source\n\nNew vault:\n$target\n\nThis creates a new local vault. It will not replace or modify an existing vault."

    override val conflictDeletedTitle = "Conflict: deleted on server"
    override val conflictNewerTitle = "Conflict: newer data on server"
    override val conflictDeletedWarning =
        "This secret was deleted on the server. Pulling discards your local " +
            "change. Pull to accept the deletion, or cancel and re-save to keep " +
            "your local copy."
    override fun conflictMessage(error: KeysteadRevisionConflictException): String {
        val latest = error.serverRevision ?: error.latestRevision
        val rejected = error.clientRevision ?: error.rejectedRevision
        val prefix = conflictPrefix(error)
        if (latest != null && rejected != null) {
            return "${prefix}Server has revision $latest and rejected local revision $rejected. Pull before pushing again."
        }
        val message = error.message ?: "Server has a newer revision."
        if (message.contains("pull before pushing", ignoreCase = true)) {
            return message
        }
        return "$message Pull before pushing again."
    }
    private fun conflictPrefix(error: KeysteadRevisionConflictException): String {
        val fingerprint = error.fingerprint
        val secretId = error.secretId
        if (fingerprint == null || secretId == null) {
            return ""
        }
        val state =
            if (error.serverDeleted == true) {
                " was deleted on the server"
            } else {
                " has a newer server copy"
            }
        val updatedAt = error.serverUpdatedAt?.let { " at $it" } ?: ""
        return "Secret $secretId in vault $fingerprint$state$updatedAt. "
    }

    override val deleteSecretTitle = "Delete secret"
    override fun deleteSecretMessage(title: String) = "Delete \"$title\"? This cannot be undone."
    override val deleteVaultFileTitle = "Delete vault file?"
    override fun deleteVaultFileMessage(file: String) =
        "Permanently delete \"$file\" from this computer? This cannot be undone."

    override val signedInRestored = "Keystead Server sign-in restored"
    override val serverSessionExpired = "Server session expired; sign in again"
    override fun couldNotRestoreServerSession(message: String) = "Could not restore server session: $message"
    override val serverAuthFailed = "Server authentication failed"
    override fun couldNotReachServer(errorType: String) = "Could not reach the Keystead server ($errorType)"
    override val deletedSecret = "Deleted secret"
    override val deviceRevoked = "Device revoked"
    override val signedInToServer = "Signed in to Keystead Server"
    override fun signedInWithoutRestoreRequest(reason: String) =
        "Signed in, but the restore request could not be created: $reason"
    override val signedInWithVerifiedDevice = "Signed in with verified device"
    override fun pulledAndRepushed(pulled: Int, pushed: Int) = "Pulled $pulled and re-pushed $pushed records"
    override fun exportedBackupTo(name: String) = "Exported backup to $name"
    override fun restoredBackupTo(name: String) = "Restored backup as $name"
    override val generatedPassword = "Generated password"
    override val generatedApiToken = "Generated API token"
    override val generatedSshKey = "Generated SSH key"
    override val generatedGpgKey = "Generated GPG key"
    override val generatedCertificate = "Generated certificate"
    override val generatedMfaSecret = "Generated MFA secret"
    override val updatedSecret = "Updated secret"
    override val savedSecret = "Saved secret"
    override val serverSessionRefreshed = "Server session refreshed"
    override val signedOutOfServer = "Signed out of Keystead Server"
    override val signedOutEverywhere = "Signed out on every device"
    override val serverUserCreatedAndSignedIn = "Server user created and signed in"
    override val serverVaultReady = "Server vault ready"
    override val noServerVaults = "No server vaults"
    override fun serverVaultsList(fingerprints: String) = "Server vaults: $fingerprints"
    override fun publishedKeyPackages(count: Int) = "Published $count vault key packages"
    override fun pushedRecords(pushed: Int, cursor: String) = "Pushed $pushed records; cursor $cursor"
    override fun pulledRecords(pulled: Int, cursor: String) = "Pulled $pulled records; cursor $cursor"
    override val provisionedVaultOpen = "Provisioned vault open"
    override val conflictDismissed = "Conflict dismissed"
    override val memberRemovedRotateNote = "Member removed; rotate the vault key before resuming writes"
    override fun publishedMissingPackages(count: Int) = "Published $count missing member device packages"
    override fun vaultKeyRotation(stateName: String) = "Vault key rotation $stateName"
    override val vaultAccessRequestCreated = "Vault access request created"
    override val vaultAccessRequestUpdated = "Vault access request updated"
    override val pendingVaultAccessRequestLoaded = "Pending vault access request loaded for review"
    override val vaultAccessApproved = "Vault access approved for the requesting device"
    override val filtersCleared = "Filters cleared"
    override val secretRevealed = "Secret revealed"
    override val copiedToClipboard = "Copied to clipboard"
    override val copiedCodeToClipboard = "Copied code to clipboard"
    override val loadedSecretForEdit = "Loaded secret for edit"
    override val copiedShareCodeToClipboard = "Copied share code to clipboard (clears in 30s)"
    override fun shareMinted(code: String) = "Share minted: $code"
    override val shareRedeemed = "Share redeemed"
    override fun loadedShares(count: Int) = "Loaded $count share(s)"
    override fun deletedShare(code: String) = "Deleted share $code"
    override val exportBackupDialogTitle = "Export Keystead backup"
    override val restoreBackupDialogTitle = "Restore Keystead backup"
    override val restoreTargetDialogTitle = "Choose new vault location"

    override val serverLoginRequiredFirst = "Log in to Keystead Server first"
    override val vaultMembershipNotFound = "Vault membership was not found"
    override val noPendingVaultAccessRequest = "No pending vault access request"
}
