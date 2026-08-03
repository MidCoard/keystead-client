package top.focess.keystead.client

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.focess.keystead.memory.Wipe
import top.focess.keystead.client.i18n.AppLocale
import top.focess.keystead.client.i18n.LanguageSettings
import top.focess.keystead.client.i18n.LocalStrings
import top.focess.keystead.client.ui.AddSecretPanel
import top.focess.keystead.client.ui.AccountPanel
import top.focess.keystead.client.ui.BackupPanel
import top.focess.keystead.client.ui.LocalLoginPanel
import top.focess.keystead.client.ui.InspectorPanel
import top.focess.keystead.client.ui.PortableBackupRestorePanel
import top.focess.keystead.client.ui.RecoveryHub
import top.focess.keystead.client.ui.RecoveryHubPresentation
import top.focess.keystead.client.ui.RecoveryMethod
import top.focess.keystead.client.ui.SecretListPanel
import top.focess.keystead.client.ui.ServerRestorePanel
import top.focess.keystead.client.ui.ServerRecoveryHub
import top.focess.keystead.client.ui.VaultAccessApprovalPanel
import top.focess.keystead.client.ui.SharePanel
import top.focess.keystead.client.ui.SettingsPanel
import top.focess.keystead.client.ui.SyncPanel
import top.focess.keystead.model.SecretType
import top.focess.keystead.share.ShareContents

private val defaultClientDirectory: Path = ClientDataDirectory.resolve()
private val defaultVaultDirectory: String =
    defaultClientDirectory.resolve("vault.kvault").toString()
private const val desktopStorageInstance = "keystead-desktop"

fun main() = application {
    val appIcon =
        remember {
            BitmapPainter(KeysteadBrand.loadIconImage().toComposeImageBitmap())
        }
    val windowState =
        rememberWindowState(
            width = (KeysteadWindowMetrics.WideBreakpointDp + 120).dp,
            height = 820.dp,
        )
    Window(
        onCloseRequest = ::exitApplication,
        title = "Keystead",
        icon = appIcon,
        state = windowState,
    ) {
        DisposableEffect(Unit) {
            val displayTransform = window.graphicsConfiguration.defaultTransform
            window.minimumSize =
                Dimension(
                    KeysteadWindowMetrics.minimumWidthPixels(displayTransform.scaleX),
                    KeysteadWindowMetrics.minimumHeightPixels(displayTransform.scaleY),
                )
            onDispose {}
        }
        top.focess.keystead.client.ui.KeysteadTheme {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                KeysteadClientApp(windowHandle = { WinDef.HWND(Native.getWindowPointer(window)) })
            }
        }
    }
}

@Composable
fun KeysteadClientApp(windowHandle: () -> WinDef.HWND? = { null }) {
    val languageSettings = remember {
        LanguageSettings(defaultClientDirectory.resolve("language.properties"))
    }
    var locale by remember { mutableStateOf(languageSettings.load() ?: AppLocale.ENGLISH) }
    val onLocaleChange: (AppLocale) -> Unit = { newLocale ->
        locale = newLocale
        languageSettings.save(newLocale)
    }
    val strings = locale.strings
    val vaultLocationSettings =
        remember {
            VaultLocationSettings(
                defaultClientDirectory.resolve("vault-location.properties"),
                Path.of(defaultVaultDirectory),
            )
        }
    var vaultDirectory by remember {
        mutableStateOf(vaultLocationSettings.load().toString())
    }
    val serverConnectionSettings =
        remember {
            ServerConnectionSettings(
                defaultClientDirectory.resolve("server-connection.properties"),
                "http://localhost:8080",
            )
        }
    var fingerprint by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<LocalVaultSession?>(null) }
    var secrets by remember { mutableStateOf<List<SecretListItem>>(emptyList()) }
    var selectedSecretId by remember { mutableStateOf<String?>(null) }
    var filterText by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }
    var filterCategory by remember { mutableStateOf("") }
    var filterProvider by remember { mutableStateOf("") }
    var filterSoftware by remember { mutableStateOf("") }
    var groupingMode by remember { mutableStateOf(SecretGroupingMode.NONE) }
    var revealedValue by remember { mutableStateOf("") }
    var revealGeneration by remember { mutableStateOf(0L) }
    val revealLifecycle = remember { RevealLifecycle() }
    val clipboardLifecycle = remember { ClipboardLifecycle(AwtClipboardPort()) }
    var clipboardTicket by remember { mutableStateOf<ClipboardClearTicket?>(null) }
    var showTotpCode by remember { mutableStateOf(false) }
    var totpCode by remember { mutableStateOf("") }
    var totpSecondsRemaining by remember { mutableStateOf(0) }
    val destructiveGate = remember { ConfirmationGate<DestructiveConfirmation>() }
    var conflictAssessment by remember { mutableStateOf<ConflictAssessment?>(null) }
    var recordInventory by remember { mutableStateOf<PersonalVaultRecordInventory?>(null) }
    var secretType by remember { mutableStateOf(SecretType.LOGIN_PASSWORD) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var software by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var structuredFields by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editingSecretId by remember { mutableStateOf<String?>(null) }
    var serverUrl by remember { mutableStateOf(serverConnectionSettings.load()) }
    var serverUsername by remember { mutableStateOf("") }
    var serverPassword by remember { mutableStateOf("") }
    var serverPasswordConfirmation by remember { mutableStateOf("") }
    var accountAuthUiState by remember { mutableStateOf(AccountAuthUiState()) }
    var serverAuthSession by remember { mutableStateOf<ServerAuthSession?>(null) }
    val vaultAccessLifecycle =
        remember {
            UserInitiatedAccessRequestLifecycle<
                EphemeralVaultAccessSession,
                ServerVaultAccessRequest,
            >()
        }
    var vaultAccessExchangeSession by remember {
        mutableStateOf<EphemeralVaultAccessSession?>(null)
    }
    val serverAvailabilityChecker = remember { ServerAvailabilityChecker() }
    var serverAvailability by remember { mutableStateOf(ServerAvailability.CHECKING) }
    var serverCheckGeneration by remember { mutableStateOf(0L) }
    var localUnlockCredential by remember { mutableStateOf<LocalUnlockCredential?>(null) }
    var deviceKeySlots by remember { mutableStateOf<List<DeviceKeySlot>>(emptyList()) }
    var deviceLoginAvailable by remember { mutableStateOf(false) }
    val secureStorageSettings = remember {
        SecureStorageSettings(defaultClientDirectory.resolve("secure-storage.properties"))
    }
    val localUnlockStorageSettings = remember {
        SecureStorageSettings(defaultClientDirectory.resolve("local-login-storage.properties"))
    }
    val secureStorageViewModel =
        remember {
            SecureStorageViewModel(
                secureStorageSettings,
                SecureStorageFactory(windowHandle = windowHandle),
            )
        }
    val localUnlockStorageViewModel =
        remember {
            SecureStorageViewModel(
                localUnlockStorageSettings,
                SecureStorageFactory(windowHandle = windowHandle),
            )
        }
    val localUnlockCredentialManager = remember {
        LocalUnlockCredentialManager(
            defaultClientDirectory.resolve("local-vault-login"),
            localUnlockStorageViewModel::selectedStorage,
        )
    }
    val localLoginEnrollmentStore = remember {
        LocalLoginEnrollmentStore(
            defaultClientDirectory.resolve("local-login-enrollments.properties"),
        )
    }
    var secureStorageModel by remember { mutableStateOf(secureStorageViewModel.model) }
    var localUnlockStorageModel by remember {
        mutableStateOf(localUnlockStorageViewModel.model)
    }
    var localUnlockDescriptor by remember {
        mutableStateOf<LocalUnlockCredentialDescriptor?>(null)
    }
    var ownVaultAccessRequest by remember { mutableStateOf<ServerVaultAccessRequest?>(null) }
    var pendingApprovalRequest by remember { mutableStateOf<ServerVaultAccessRequest?>(null) }
    var recoveryMethod by remember { mutableStateOf(RecoveryHubPresentation.defaultMethod) }
    var serverRecoveryTask by remember {
        mutableStateOf(ServerRecoveryTask.RESTORE_THIS_DEVICE)
    }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordConfirmation by remember { mutableStateOf("") }
    var backupNewMasterPassphrase by remember { mutableStateOf("") }
    var backupNewMasterPassphraseConfirmation by remember { mutableStateOf("") }
    var backupRestoreSelection by remember {
        mutableStateOf(BackupRestoreSelection(source = null, target = null))
    }
    var pendingBackupRestore by remember { mutableStateOf<BackupRestoreSelection?>(null) }
    var serverRestoreTarget by remember { mutableStateOf(vaultDirectory) }
    var serverRestoreNewMasterPassphrase by remember { mutableStateOf("") }
    var serverRestoreNewMasterPassphraseConfirmation by remember { mutableStateOf("") }
    val actionFeedbackState = remember { ActionFeedbackState(strings.vaultLocked) }
    var status by actionFeedbackState
    var unlockError by remember { mutableStateOf<String?>(null) }
    var currentDestination by remember {
        mutableStateOf(top.focess.keystead.client.ui.KeysteadDestination.SECRETS)
    }
    var inspectorSheetOpen by remember { mutableStateOf(false) }

    val shareExchange = remember { ShareExchange() }
    var shareTitle by remember { mutableStateOf("") }
    var sharePayload by remember { mutableStateOf("") }
    var sharePassphrase by remember { mutableStateOf("") }
    var shareTtl by remember { mutableStateOf(ShareExchange.ShareTtl.ONE_WEEK) }
    var shareBurn by remember { mutableStateOf(true) }
    var mintedShare by remember { mutableStateOf<ShareExchange.MintedShare?>(null) }
    var redeemCode by remember { mutableStateOf("") }
    var redeemPassphrase by remember { mutableStateOf("") }
    var redeemedContents by remember { mutableStateOf<ShareContents?>(null) }
    var outstandingShares by remember { mutableStateOf<List<ServerShareSummary>>(emptyList()) }

    fun clearSecretEditor() {
        title = ""
        username = ""
        password = ""
        url = ""
        category = ""
        provider = ""
        software = ""
        account = ""
        expiry = ""
        structuredFields = emptyMap()
        editingSecretId = null
    }

    fun reportUnlockError(message: String) {
        unlockError = message
        actionFeedbackState.error(message)
    }

    DisposableEffect(session) {
        val localVaultSession = session
        onDispose { localVaultSession?.close() }
    }
    DisposableEffect(serverAuthSession) {
        val authenticatedSession = serverAuthSession
        onDispose { authenticatedSession?.close() }
    }
    DisposableEffect(vaultAccessLifecycle) {
        onDispose { vaultAccessLifecycle.close() }
    }
    DisposableEffect(localUnlockCredentialManager) {
        onDispose { localUnlockCredentialManager.close() }
    }
    DisposableEffect(secureStorageViewModel) {
        onDispose { secureStorageViewModel.close() }
    }
    DisposableEffect(localUnlockStorageViewModel) {
        onDispose { localUnlockStorageViewModel.close() }
    }
    fun serverSessionStore(): RefreshTokenStore? {
        val storage = secureStorageViewModel.selectedStorage() ?: return null
        if (storage.capability == SecureStorageCapability.MEMORY_ONLY) return null
        return RefreshTokenStore(storage)
    }

    fun clearVaultAccessState() {
        vaultAccessLifecycle.onAccountAuthenticated()
        vaultAccessExchangeSession = null
        ownVaultAccessRequest = null
        recordInventory = null
    }

    fun beginVaultAccessExchange(authenticated: ServerAuthSession): String? {
        try {
            val request =
                vaultAccessLifecycle.requestByUser {
                    val exchange = EphemeralVaultAccessSession.create(serverUrl)
                    try {
                        StartedAccessRequest(
                            exchange,
                            VaultAccessWorkflow(authenticated.client()).request(exchange),
                        )
                    } catch (error: Exception) {
                        exchange.close()
                        throw error
                    }
                }
            vaultAccessExchangeSession = vaultAccessLifecycle.exchange
            ownVaultAccessRequest = request
            return null
        } catch (error: Exception) {
            clearVaultAccessState()
            return error.message ?: error::class.simpleName ?: "Unknown error"
        }
    }

    fun restoreServerSession() {
        val store = serverSessionStore() ?: return
        val persisted = store.load() ?: return
        val tokenSink: (String, java.time.Instant) -> Unit = { refreshToken, expiresAt ->
            store.save(
                PersistedAuthSession(
                    persisted.baseUrl,
                    persisted.username,
                    refreshToken,
                    expiresAt,
                ),
            )
        }
        val onRevoked: () -> Unit = { store.clear() }
        // Bounded connect timeout so an unreachable server cannot freeze startup; a slow read
        // still propagates as an exception and is caught below (store preserved for the next launch).
        val restoreHttp =
            java.net.http.HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build()
        try {
            val session =
                KeysteadServerAuthClient(persisted.baseUrl, restoreHttp).restore(
                    persisted.refreshToken,
                    persisted.refreshTokenExpiresAt,
                    tokenSink,
                    onRevoked,
                )
            serverAuthSession = session
            serverUrl = persisted.baseUrl
            serverUsername = persisted.username
            clearVaultAccessState()
            serverAvailability = ServerAvailability.ONLINE
            status = strings.signedInRestored
        } catch (error: KeysteadAuthenticationException) {
            serverAvailability = ServerAvailability.ONLINE
            store.clear()
            actionFeedbackState.error(strings.serverSessionExpired)
        } catch (error: Exception) {
            if (error is java.io.IOException) {
                serverAvailability = ServerAvailability.OFFLINE
            }
            actionFeedbackState.error(
                strings.couldNotRestoreServerSession(error.message ?: error::class.simpleName ?: ""),
            )
        }
    }

    LaunchedEffect(Unit) {
        try {
            localUnlockStorageModel =
                localUnlockStorageViewModel.initialize(
                    defaultClientDirectory.resolve("local-login-secure-storage"),
                    "$desktopStorageInstance-local-login",
                )
            localUnlockDescriptor = localUnlockCredentialManager.descriptor()
            localUnlockDescriptor?.let { descriptor ->
                if (localUnlockStorageModel.selectedMode == null) {
                    localUnlockStorageModel =
                        localUnlockStorageViewModel.adoptExistingLocalLogin(
                            descriptor.persistence,
                        )
                }
            }
        } catch (error: Exception) {
            actionFeedbackState.error(error.message ?: strings.localLoginCredentialUnavailable)
        }
        secureStorageModel =
            secureStorageViewModel.initialize(
                defaultClientDirectory.resolve("secure-storage"),
                desktopStorageInstance,
            )
        restoreServerSession()
    }
    LaunchedEffect(vaultDirectory, session, deviceKeySlots, localUnlockDescriptor) {
        val descriptor = localUnlockDescriptor
        if (descriptor == null) {
            deviceLoginAvailable = false
        } else {
            val inspected =
                if (session != null) {
                    LocalVaultDeviceSlots(session!!.fingerprintValue(), deviceKeySlots)
                } else {
                    val selectedVault = runCatching { Path.of(vaultDirectory) }.getOrNull()
                    if (selectedVault == null) {
                        null
                    } else {
                        withContext(Dispatchers.IO) {
                            runCatching { LocalVaultSession.inspectDeviceSlots(selectedVault) }
                                .getOrNull()
                        }
                    }
                }
            deviceLoginAvailable =
                inspected?.let { value ->
                    withContext(Dispatchers.IO) {
                        localLoginEnrollmentStore.isEnrolled(
                            vaultFingerprint = value.fingerprint,
                            slotKeyIds = value.slots.mapTo(mutableSetOf(), DeviceKeySlot::slotKeyId),
                            credentialFingerprint = descriptor.keyFingerprint,
                        )
                    }
                } == true
        }
    }
    LaunchedEffect(serverUrl, serverCheckGeneration) {
        serverAvailability = ServerAvailability.CHECKING
        delay(350)
        val checkedUrl = serverUrl
        if (checkedUrl.isNotBlank()) {
            withContext(Dispatchers.IO) {
                serverConnectionSettings.remember(checkedUrl)
            }
        }
        while (true) {
            val checked =
                withContext(Dispatchers.IO) {
                    serverAvailabilityChecker.check(checkedUrl)
                }
            if (serverUrl == checkedUrl) {
                serverAvailability = checked
            }
            delay(15_000)
        }
    }
    LaunchedEffect(revealGeneration, selectedSecretId) {
        if (revealedValue.isNotEmpty()) {
            delay(30_000)
            if (revealLifecycle.expire(java.time.Instant.now(), revealGeneration)) revealedValue = ""
        }
    }
    LaunchedEffect(clipboardTicket) {
        clipboardTicket?.let { ticket ->
            delay(java.time.Duration.between(java.time.Instant.now(), ticket.expiresAt).toMillis().coerceAtLeast(0))
            clipboardLifecycle.expire(java.time.Instant.now(), ticket)
            clipboardTicket = null
        }
    }
    DisposableEffect(Unit) { onDispose { clipboardLifecycle.dispose(java.time.Instant.now(), clipboardTicket) } }
    LaunchedEffect(selectedSecretId, showTotpCode, session) {
        if (!showTotpCode) {
            totpCode = ""
            totpSecondsRemaining = 0
            return@LaunchedEffect
        }
        val current = session ?: return@LaunchedEffect
        val selected = secrets.firstOrNull { it.id == selectedSecretId } ?: return@LaunchedEffect
        if (SecretType.valueOf(selected.type) != SecretType.MFA_SECRET) return@LaunchedEffect
        val uri = runCatching { current.revealField(selected.id, "otpauthUri") }.getOrNull()
        val period = MfaTotp.period(uri)
        var lastCounter = -1L
        while (true) {
            val now = java.time.Instant.now()
            val counter = now.epochSecond / period
            if (counter != lastCounter) {
                lastCounter = counter
                val seed = runCatching { current.revealField(selected.id, "seed") }.getOrNull()
                totpCode = seed?.let { MfaTotp.currentCode(it, uri, now) }.orEmpty()
            }
            totpSecondsRemaining = MfaTotp.secondsRemaining(period, now)
            delay(1_000)
        }
    }

    fun refresh(current: LocalVaultSession) {
        secrets = current.listSecrets()
        deviceKeySlots = current.deviceSlots()
        if (selectedSecretId !in secrets.map { it.id }) {
            selectedSecretId = null
            revealedValue = ""
        }
    }

    fun lockVault(nextStatus: String = strings.vaultLocked) {
        session?.close()
        session = null
        secrets = emptyList()
        selectedSecretId = null
        revealLifecycle.clear()
        revealedValue = ""
        clipboardLifecycle.dispose(java.time.Instant.now(), clipboardTicket)
        clipboardTicket = null
        clearSecretEditor()
        masterPassword = ""
        serverPassword = ""
        serverPasswordConfirmation = ""
        accountAuthUiState = accountAuthUiState.onInputChanged()
        shareTitle = ""
        sharePayload = ""
        sharePassphrase = ""
        redeemCode = ""
        redeemPassphrase = ""
        redeemedContents = null
        mintedShare = null
        outstandingShares = emptyList()
        deviceKeySlots = emptyList()
        backupPassword = ""
        backupPasswordConfirmation = ""
        backupNewMasterPassphrase = ""
        backupNewMasterPassphraseConfirmation = ""
        backupRestoreSelection = BackupRestoreSelection(source = null, target = null)
        pendingBackupRestore = null
        currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
        inspectorSheetOpen = false
        actionFeedbackState.info(nextStatus)
        recordInventory = null
        unlockError = null
    }

    fun runAction(
        onError: ((String) -> Unit)? = null,
        serverAction: Boolean = false,
        action: () -> Unit,
    ) {
        try {
            action()
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(
                        serverAvailability,
                        error = null,
                    )
            }
        } catch (error: KeysteadRevisionConflictException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            conflictAssessment = ConflictAssessment.from(error, strings)
            actionFeedbackState.error(SyncStatusFormatter.messageFor(error, strings))
        } catch (error: KeysteadAccountConflictException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            val message = strings.serverUserAlreadyExists
            actionFeedbackState.error(message)
            onError?.invoke(message)
        } catch (error: KeysteadAuthenticationException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            serverAuthSession?.close()
            serverAuthSession = null
            val message = strings.serverCredentialsRejected
            actionFeedbackState.error(message)
            onError?.invoke(message)
        } catch (error: java.io.IOException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            val message = strings.couldNotReachServer(error::class.simpleName ?: "IOException")
            actionFeedbackState.error(message)
            onError?.invoke(message)
        } catch (error: PersonalVaultMismatchException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            val message =
                strings.personalVaultMismatch(error.serverFingerprint, error.localFingerprint)
            actionFeedbackState.error(message)
            onError?.invoke(message)
            runCatching {
                val remote = serverAuthSession?.client()?.listAllPersonalRecords() ?: return@runCatching
                val current = session
                recordInventory =
                    PersonalVaultRecordInventory.compare(
                        localRecords = current?.currentPersonalRecords(),
                        remoteRecords = remote,
                        localFingerprint = current?.fingerprintValue(),
                    )
            }
        } catch (error: KeysteadServerException) {
            if (serverAction) {
                serverAvailability =
                    ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
            }
            val message = error.message ?: error::class.simpleName.orEmpty()
            actionFeedbackState.error(message)
            onError?.invoke(message)
        } catch (error: Exception) {
            val message = error.message ?: error::class.simpleName.orEmpty()
            actionFeedbackState.error(message)
            onError?.invoke(message)
        }
    }

    fun performDeleteSecret(secretId: String) {
        val current = session ?: return
        runAction {
            current.delete(secretId)
            if (selectedSecretId == secretId) {
                selectedSecretId = null
                revealLifecycle.clear()
                revealedValue = ""
                showTotpCode = false
                totpCode = ""
            }
            if (editingSecretId == secretId) {
                clearSecretEditor()
            }
            status = strings.deletedSecret
            refresh(current)
        }
    }

    fun serverClient(): KeysteadServerClient =
        serverAuthSession?.client()
            ?: throw IllegalStateException(strings.serverLoginRequiredFirst)

    fun loadRecordInventory() {
        val current = session
        recordInventory =
            PersonalVaultRecordInventory.compare(
                localRecords = current?.currentPersonalRecords(),
                remoteRecords = serverClient().listAllPersonalRecords(),
                localFingerprint = current?.fingerprintValue(),
            )
    }

    fun performRemoveServerRecords(secretIds: Set<String>) {
        if (secretIds.isEmpty()) return
        runAction(serverAction = true) {
            val removedEvents =
                secretIds.sumOf { secretId ->
                    serverClient().deletePersonalRecordHistory(secretId).deletedEvents
                }
            loadRecordInventory()
            status = strings.removedServerRecords(secretIds.size, removedEvents)
        }
    }

    fun authenticateServer(password: CharArray): ServerAuthSession {
        val store = serverSessionStore()
        val tokenSink: ((String, java.time.Instant) -> Unit)? =
            store?.let { s ->
                { refreshToken, expiresAt ->
                    s.save(
                        PersistedAuthSession(
                            serverUrl,
                            serverUsername,
                            refreshToken,
                            expiresAt,
                        ),
                    )
                }
            }
        val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
        return KeysteadServerAuthClient(serverUrl)
            .login(serverUsername, password, tokenSink, onRevoked)
    }

    fun loginToServer() {
        val passwordChars = serverPassword.toCharArray()
        accountAuthUiState = accountAuthUiState.onInputChanged()
        try {
            runAction(
                onError = { message ->
                    accountAuthUiState = accountAuthUiState.withFailure(message)
                },
                serverAction = true,
            ) {
                val authenticated = authenticateServer(passwordChars)
                serverAuthSession?.close()
                serverAuthSession = authenticated
                clearVaultAccessState()
                accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                status = strings.signedInToServer
            }
        } finally {
            Wipe.wipe(passwordChars)
            serverPassword = ""
            serverPasswordConfirmation = ""
        }
    }

    fun performDeleteVaultFile(vaultFile: String) {
        val target =
            runCatching { Path.of(vaultFile).toAbsolutePath().normalize() }
                .getOrElse {
                    actionFeedbackState.error(
                        strings.vaultFileDeleteFailed(
                            it.message ?: it::class.simpleName.orEmpty(),
                        ),
                    )
                    return
                }
        val current =
            runCatching { Path.of(vaultDirectory).toAbsolutePath().normalize() }
                .getOrNull()
        if (session == null || current != target) return

        lockVault()
        try {
            val deleted = VaultFileDeletionService().delete(target)
            runCatching { vaultLocationSettings.clear() }
            vaultDirectory = defaultVaultDirectory
            status = strings.vaultFileDeleted(deleted.fileName.toString())
        } catch (error: Exception) {
            actionFeedbackState.error(
                strings.vaultFileDeleteFailed(
                    error.message ?: error::class.simpleName.orEmpty(),
                ),
            )
        }
    }

    fun enableDeviceLoginIfReady(): Boolean {
        val current = session ?: return false
        val credential = localUnlockCredential ?: return false
        val descriptor = localUnlockDescriptor ?: return false
        if (!localUnlockCredentialManager.canEnrollVaultKey) return false
        val vaultFingerprint = current.fingerprintValue()
        val currentSlots = current.deviceSlots()
        if (
            localLoginEnrollmentStore.isEnrolled(
                vaultFingerprint,
                currentSlots.mapTo(mutableSetOf(), DeviceKeySlot::slotKeyId),
                descriptor.keyFingerprint,
            )
        ) {
            deviceKeySlots = currentSlots
            deviceLoginAvailable = true
            return false
        }
        val localSlot = current.replaceLocalLogin(credential)
        localLoginEnrollmentStore.remember(
            vaultFingerprint = vaultFingerprint,
            slotKeyId = localSlot,
            credentialFingerprint = descriptor.keyFingerprint,
        )
        deviceKeySlots = current.deviceSlots()
        deviceLoginAvailable = true
        return true
    }

    fun performRemoveDeviceLogin() {
        val current = session ?: return
        runAction {
            val vaultFingerprint = current.fingerprintValue()
            current.removeDeviceLogin()
            localLoginEnrollmentStore.clear(vaultFingerprint)
            deviceKeySlots = current.deviceSlots()
            deviceLoginAvailable = false
            status = strings.deviceLoginRemoved
        }
    }

    fun loadLocalUnlockCredential(onError: ((String) -> Unit)? = null) {
        runAction(onError = onError) {
            val descriptor =
                localUnlockCredentialManager.descriptor()
                    ?: throw IllegalStateException(strings.deviceLoginNotConfigured)
            if (localUnlockStorageModel.selectedMode == null) {
                localUnlockStorageModel =
                    localUnlockStorageViewModel.adoptExistingLocalLogin(
                        descriptor.persistence,
                    )
            }
            localUnlockCredential = localUnlockCredentialManager.loadExisting()
            localUnlockDescriptor = localUnlockCredentialManager.descriptor()
            status =
                if (enableDeviceLoginIfReady()) {
                    strings.deviceLoginEnabled
                } else {
                    strings.localLoginReadyStatus
                }
        }
    }

    fun createBiometricLocalLogin() {
        runAction {
            check(localUnlockDescriptor == null) { strings.identityStorageCannotChange }
            localUnlockStorageViewModel.selectBiometric()
            localUnlockStorageModel = localUnlockStorageViewModel.model
            localUnlockCredential =
                localUnlockCredentialManager.loadOrCreate(SecureStorageMode.BIOMETRIC)
            localUnlockDescriptor = localUnlockCredentialManager.descriptor()
            status =
                if (enableDeviceLoginIfReady()) {
                    strings.deviceLoginEnabled
                } else {
                    strings.localLoginReadyStatus
                }
        }
    }

    fun syncStateStore(vaultFile: Path = Path.of(vaultDirectory)): SyncStateStore =
        SyncStateStore(
            vaultFile.parent?.resolve("sync")
                ?: vaultFile.resolve("sync"),
        )

    fun performPullAndRetry() {
        val current = session ?: return
        runAction(serverAction = true) {
            val state = syncStateStore()
            val pulled = current.pullPendingPersonalRecordsFrom(serverClient(), state)
            val pushed = current.pushPendingPersonalRecordsTo(serverClient(), state)
            conflictAssessment = null
            status = strings.pulledAndRepushed(pulled.imported, pushed)
            if (pulled.rejected.isNotEmpty()) {
                actionFeedbackState.error(strings.rejectedServerRecords(pulled.rejected.size))
            }
            refresh(current)
        }
    }

    fun chooseExistingVaultFile() {
        val current = runCatching { Path.of(vaultDirectory).toAbsolutePath().normalize() }.getOrNull()
        val owner: java.awt.Frame? = null
        val dialog =
            java.awt.FileDialog(
                owner,
                strings.chooseExistingVaultDialogTitle,
                java.awt.FileDialog.LOAD,
            )
        dialog.directory = current?.parent?.toString()
        dialog.file = "*.kvault"
        dialog.filenameFilter = java.io.FilenameFilter { _, name ->
            name.endsWith(".kvault", ignoreCase = true)
        }
        dialog.isVisible = true
        val selectedName = dialog.file ?: return
        vaultDirectory =
            java.io.File(dialog.directory, selectedName)
                .toPath()
                .toAbsolutePath()
                .normalize()
                .toString()
        unlockError = null
    }

    fun chooseNewVaultFile() {
        val current = runCatching { Path.of(vaultDirectory).toAbsolutePath().normalize() }.getOrNull()
        val owner: java.awt.Frame? = null
        val dialog =
            java.awt.FileDialog(
                owner,
                strings.chooseNewVaultDialogTitle,
                java.awt.FileDialog.SAVE,
            )
        dialog.directory = current?.parent?.toString()
        dialog.file = current?.fileName?.toString() ?: "vault.kvault"
        dialog.isVisible = true
        val selectedName = dialog.file ?: return
        vaultDirectory =
            VaultFileSelection
                .newTarget(java.io.File(dialog.directory, selectedName).toPath())
                .toAbsolutePath()
                .normalize()
                .toString()
        unlockError = null
    }

    fun performExportBackup() {
        val current = session ?: return
        val owner: java.awt.Frame? = null
        val dialog = java.awt.FileDialog(owner, strings.exportBackupDialogTitle, java.awt.FileDialog.SAVE)
        dialog.file = "keystead-backup.ksbackup"
        dialog.isVisible = true
        val fileName = dialog.file ?: return
        val selected = java.io.File(dialog.directory, fileName)
        val target =
            if (selected.name.endsWith(".ksbackup", ignoreCase = true)) {
                selected
            } else {
                java.io.File(selected.parentFile, selected.name + ".ksbackup")
            }
        runAction {
            java.io.FileOutputStream(target).use { output ->
                VaultBackup.export(current, backupPassword.toCharArray(), output)
            }
            backupPassword = ""
            backupPasswordConfirmation = ""
            status = strings.exportedBackupTo(target.name)
        }
    }

    fun chooseBackupSource() {
        val owner: java.awt.Frame? = null
        val sourceDialog =
            java.awt.FileDialog(owner, strings.restoreBackupDialogTitle, java.awt.FileDialog.LOAD)
        sourceDialog.file = "*.ksbackup"
        sourceDialog.isVisible = true
        val sourceName = sourceDialog.file ?: return
        backupRestoreSelection =
            backupRestoreSelection.copy(
                source = java.io.File(sourceDialog.directory, sourceName).toPath(),
            )
    }

    fun chooseBackupRestoreTarget() {
        val source = backupRestoreSelection.source
        val baseName =
            if (source?.fileName?.toString()?.endsWith(".ksbackup", ignoreCase = true) == true) {
                source.fileName.toString().dropLast(".ksbackup".length)
            } else {
                "restored"
            }
        val owner: java.awt.Frame? = null
        val targetDialog =
            java.awt.FileDialog(owner, strings.restoreTargetDialogTitle, java.awt.FileDialog.SAVE)
        targetDialog.directory = source?.parent?.toString()
        targetDialog.file = "${baseName.ifBlank { "restored" }}-restored.kvault"
        targetDialog.isVisible = true
        val targetName = targetDialog.file ?: return
        val selectedTarget = java.io.File(targetDialog.directory, targetName)
        val target =
            if (selectedTarget.name.endsWith(".kvault", ignoreCase = true)) {
                selectedTarget
            } else {
                java.io.File(selectedTarget.parentFile, selectedTarget.name + ".kvault")
            }
        backupRestoreSelection = backupRestoreSelection.copy(target = target.toPath())
    }

    fun chooseServerRestoreTarget() {
        val current = runCatching { Path.of(serverRestoreTarget) }.getOrNull()
        val owner: java.awt.Frame? = null
        val dialog =
            java.awt.FileDialog(owner, strings.restoreTargetDialogTitle, java.awt.FileDialog.SAVE)
        dialog.directory = current?.parent?.toString()
        dialog.file = current?.fileName?.toString() ?: "keystead-restored.kvault"
        dialog.isVisible = true
        val selectedName = dialog.file ?: return
        val selected = java.io.File(dialog.directory, selectedName)
        val target =
            if (selected.name.endsWith(".kvault", ignoreCase = true)) {
                selected
            } else {
                java.io.File(selected.parentFile, selected.name + ".kvault")
            }
        serverRestoreTarget = target.toPath().toString()
    }

    fun performRestoreBackup(selection: BackupRestoreSelection) {
        if (!selection.sourceReady) {
            actionFeedbackState.error(strings.backupSourceInvalid)
            return
        }
        if (!selection.targetReady) {
            actionFeedbackState.error(strings.restoreTargetMustBeNew)
            return
        }
        val source = selection.source ?: return
        val target = selection.target ?: return
        runAction {
            val restored =
                Files.newInputStream(source).use { input ->
                    VaultBackup.restore(
                        target,
                        input,
                        backupPassword.toCharArray(),
                        backupNewMasterPassphrase.toCharArray(),
                    )
                }
            var adopted = false
            try {
                val remembered = vaultLocationSettings.rememberSuccessfulVault(target)
                session?.close()
                session = restored
                adopted = true
                vaultDirectory = remembered.toString()
                fingerprint = restored.fingerprintValue()
                selectedSecretId = null
                revealLifecycle.clear()
                revealedValue = ""
                clearSecretEditor()
                refresh(restored)
                enableDeviceLoginIfReady()
                backupPassword = ""
                backupPasswordConfirmation = ""
                backupNewMasterPassphrase = ""
                backupNewMasterPassphraseConfirmation = ""
                backupRestoreSelection = BackupRestoreSelection(source = null, target = null)
                pendingBackupRestore = null
                currentDestination =
                    top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                status = strings.restoredBackupTo(target.fileName.toString())
            } finally {
                if (!adopted) {
                    restored.close()
                }
            }
        }
    }

    val secretListQuery =
        SecretListQuery(
            text = filterText,
            type = filterType,
            category = filterCategory,
            provider = filterProvider,
            software = filterSoftware,
        )
    val visibleSecrets = SecretListFilter.apply(secrets, secretListQuery)
    val selectedSecret = secrets.firstOrNull { it.id == selectedSecretId }

    val addPanel: @Composable () -> Unit = {
        AddSecretPanel(
            enabled = session != null,
            selectedType = secretType,
            onSelectedTypeChange = {
                if (it != secretType) {
                    clearSecretEditor()
                    revealLifecycle.clear()
                    revealedValue = ""
                    secretType = it
                    category = SecretFormModel.specForOrNull(it)?.defaultCategory.orEmpty()
                    provider = SecretFormModel.specForOrNull(it)?.defaultProvider.orEmpty()
                    software = SecretFormModel.specForOrNull(it)?.defaultSoftware.orEmpty()
                }
            },
            title = title,
            onTitleChange = { title = it },
            username = username,
            onUsernameChange = { username = it },
            password = password,
            onPasswordChange = { password = it },
            onGeneratePassword = {
                runAction {
                    password = PasswordDraftGenerator.generate()
                    status = strings.generatedPassword
                }
            },
            url = url,
            onUrlChange = { url = it },
            category = category,
            onCategoryChange = { category = it },
            provider = provider,
            onProviderChange = { provider = it },
            software = software,
            onSoftwareChange = { software = it },
            account = account,
            onAccountChange = { account = it },
            expiry = expiry,
            onExpiryChange = { expiry = it },
            structuredFields = structuredFields,
            onStructuredFieldChange = { name, value ->
                structuredFields = structuredFields + (name to value)
            },
            onGenerateApiToken = {
                runAction {
                    val prefix =
                        when {
                            provider.equals("github", ignoreCase = true) -> "ghp"
                            software.equals("github.com", ignoreCase = true) -> "ghp"
                            else -> "api"
                        }
                    val draft = ApiTokenDraftGenerator.generate(prefix)
                    draft.software?.let { software = it }
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedApiToken
                }
            },
            onGenerateSshKey = {
                runAction {
                    val draft = SshKeyDraftGenerator.generate(account.ifBlank { title.ifBlank { null } })
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedSshKey
                }
            },
            onGenerateGpgKey = {
                runAction {
                    val passphrase =
                        structuredFields["passphrase"]?.takeIf { it.isNotBlank() }
                            ?: PasswordDraftGenerator.generate()
                    val draft =
                        GpgKeyDraftGenerator.generate(
                            identity = account.ifBlank { title.ifBlank { "Keystead User" } },
                            passphrase = passphrase.toCharArray(),
                        )
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedGpgKey
                }
            },
            onGenerateCertificate = {
                runAction {
                    val draft =
                        CertificateDraftGenerator.generate(
                            commonName = account.ifBlank { title.ifBlank { "keystead.local" } },
                        )
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedCertificate
                }
            },
            onGenerateMfaSecret = {
                runAction {
                    val draft =
                        MfaSecretDraftGenerator.generate(
                            issuer = title.ifBlank { "Keystead" },
                            accountName = account.ifBlank { title.ifBlank { "account" } },
                        )
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedMfaSecret
                }
            },
            onCancel = {
                clearSecretEditor()
                currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
            },
            onSave = {
                val current = session ?: return@AddSecretPanel
                runAction {
                    val editing = editingSecretId
                    if (editing != null) {
                        if (secretType == SecretType.LOGIN_PASSWORD) {
                            current.updateLogin(
                                editing,
                                title,
                                username,
                                password,
                                url.ifBlank { null },
                                category = category.ifBlank { null },
                                provider = provider.ifBlank { null },
                                software = software.ifBlank { null },
                                account = account.ifBlank { null },
                                expiry = expiry.ifBlank { null },
                            )
                        } else {
                            val spec = SecretFormModel.specFor(secretType)
                            current.updateStructuredSecret(
                                editing,
                                title = title,
                                fields = SecretFormModel.fieldValues(spec, structuredFields),
                                category = category.ifBlank { spec.defaultCategory },
                                provider = provider.ifBlank { spec.defaultProvider },
                                software = software.ifBlank { spec.defaultSoftware },
                                account = account.ifBlank { null },
                                expiry = expiry.ifBlank { null },
                            )
                        }
                        status = strings.updatedSecret
                    } else {
                        if (secretType == SecretType.LOGIN_PASSWORD) {
                            current.addLogin(
                                title,
                                username,
                                password,
                                url.ifBlank { null },
                                category = category.ifBlank { null },
                                provider = provider.ifBlank { null },
                                software = software.ifBlank { null },
                                account = account.ifBlank { null },
                                expiry = expiry.ifBlank { null },
                            )
                        } else {
                            val spec = SecretFormModel.specFor(secretType)
                            current.addStructuredSecret(
                                type = secretType,
                                title = title,
                                fields = SecretFormModel.fieldValues(spec, structuredFields),
                                category = category.ifBlank { spec.defaultCategory },
                                provider = provider.ifBlank { spec.defaultProvider },
                                software = software.ifBlank { spec.defaultSoftware },
                                account = account.ifBlank { null },
                                expiry = expiry.ifBlank { null },
                            )
                        }
                        status = strings.savedSecret
                    }
                    clearSecretEditor()
                    revealedValue = ""
                    refresh(current)
                    currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                }
            },
            editing = editingSecretId != null,
        )
    }
    val accountPanel: @Composable () -> Unit = {
        AccountPanel(
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            onCheckServer = { serverCheckGeneration += 1 },
            serverUrl = serverUrl,
            onServerUrlChange = {
                if (it != serverUrl) {
                    serverSessionStore()?.clear()
                    serverAuthSession?.close()
                    serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                }
                serverUrl = it
                accountAuthUiState = accountAuthUiState.onInputChanged()
            },
            username = serverUsername,
            onUsernameChange = {
                if (it != serverUsername) {
                    serverSessionStore()?.clear()
                    serverAuthSession?.close()
                    serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                }
                serverUsername = it
                accountAuthUiState = accountAuthUiState.onInputChanged()
            },
            password = serverPassword,
            onPasswordChange = {
                serverPassword = it
                accountAuthUiState = accountAuthUiState.onInputChanged()
            },
            passwordConfirmation = serverPasswordConfirmation,
            onPasswordConfirmationChange = {
                serverPasswordConfirmation = it
                accountAuthUiState = accountAuthUiState.onInputChanged()
            },
            authState = accountAuthUiState,
            onAuthModeChange = { mode ->
                accountAuthUiState = accountAuthUiState.select(mode)
                serverPassword = ""
                serverPasswordConfirmation = ""
            },
            onLogin = {
                loginToServer()
            },
            onRefresh = {
                val authenticated = serverAuthSession ?: return@AccountPanel
                runAction(serverAction = true) {
                    authenticated.refresh()
                    status = strings.serverSessionRefreshed
                }
            },
            onLogout = {
                val authenticated = serverAuthSession ?: return@AccountPanel
                try {
                    runAction(serverAction = true) {
                        authenticated.revoke()
                        status = strings.signedOutOfServer
                    }
                } finally {
                    serverSessionStore()?.clear()
                    serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                    accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                }
            },
            onLogoutAll = {
                val authenticated = serverAuthSession ?: return@AccountPanel
                try {
                    runAction(serverAction = true) {
                        authenticated.logoutAll()
                        status = strings.signedOutEverywhere
                    }
                } finally {
                    serverSessionStore()?.clear()
                    serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                    accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                }
            },
            onCreateAccount = {
                if (
                    !AccountAuthPresentation.canSubmit(
                        mode = AccountAuthMode.CREATE_ACCOUNT,
                        serverUrl = serverUrl,
                        username = serverUsername,
                        password = serverPassword,
                        passwordConfirmation = serverPasswordConfirmation,
                        serverAvailable = serverAvailability.isOnline,
                        authenticated = serverAuthSession != null,
                    )
                ) {
                    return@AccountPanel
                }
                val registrationPassword = serverPassword.toCharArray()
                val loginPassword = serverPassword.toCharArray()
                accountAuthUiState = accountAuthUiState.onInputChanged()
                try {
                    runAction(
                        onError = { message ->
                            accountAuthUiState = accountAuthUiState.withFailure(message)
                        },
                        serverAction = true,
                    ) {
                        val authClient = KeysteadServerAuthClient(serverUrl)
                        authClient.registerUser(serverUsername, registrationPassword)
                        val store = serverSessionStore()
                        val tokenSink: ((String, java.time.Instant) -> Unit)? =
                            store?.let { s ->
                                { refreshToken, expiresAt ->
                                    s.save(
                                        PersistedAuthSession(
                                            serverUrl,
                                            serverUsername,
                                            refreshToken,
                                            expiresAt,
                                        ),
                                    )
                                }
                            }
                        val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
                        val authenticated =
                            authClient.login(serverUsername, loginPassword, tokenSink, onRevoked)
                        serverAuthSession?.close()
                        serverAuthSession = authenticated
                        clearVaultAccessState()
                        accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                        status = strings.serverUserCreatedAndSignedIn
                    }
                } finally {
                    Wipe.wipe(registrationPassword)
                    Wipe.wipe(loginPassword)
                    serverPassword = ""
                    serverPasswordConfirmation = ""
                }
            },
        )
    }
    val syncPanel: @Composable () -> Unit = {
        SyncPanel(
            vaultOpen = session != null,
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            onCheckServer = { serverCheckGeneration += 1 },
            onUploadSelected = { secretIds ->
                val current = session ?: return@SyncPanel
                runAction(serverAction = true) {
                    val pushed =
                        current.pushSelectedPersonalRecordsTo(serverClient(), secretIds)
                    conflictAssessment = null
                    loadRecordInventory()
                    status = strings.uploadedSelectedRecords(pushed)
                }
            },
            onRequestRemoveSelected = { secretIds ->
                if (secretIds.isNotEmpty()) {
                    destructiveGate.request(
                        DestructiveConfirmation.RemoveServerRecords(secretIds),
                    )
                }
            },
            onPull = {
                val current = session ?: return@SyncPanel
                runAction(serverAction = true) {
                    val state = syncStateStore()
                    val pulled = current.pullPendingPersonalRecordsFrom(serverClient(), state)
                    conflictAssessment = null
                    loadRecordInventory()
                    status =
                        strings.pulledRecords(
                            pulled.imported,
                            state.lastPulledServerSequence(fingerprint).toString(),
                        )
                    if (pulled.rejected.isNotEmpty()) {
                        actionFeedbackState.error(strings.rejectedServerRecords(pulled.rejected.size))
                    }
                    refresh(current)
                    loadRecordInventory()
                }
            },
            onRefreshRecords = {
                runAction(serverAction = true) {
                    loadRecordInventory()
                    status = strings.refreshRecordInventory
                }
            },
            conflictAssessment = conflictAssessment,
            recordInventory = recordInventory,
            localRecordTitles = secrets.associate { it.id to it.title },
            onPullAndRetry = { performPullAndRetry() },
            onDismissConflict = {
                conflictAssessment = null
                status = strings.conflictDismissed
            },
        )
    }
    val backupPanel: @Composable () -> Unit = {
        BackupPanel(
            vaultOpen = session != null,
            backupPassword = backupPassword,
            onBackupPasswordChange = { backupPassword = it },
            backupPasswordConfirmation = backupPasswordConfirmation,
            onBackupPasswordConfirmationChange = { backupPasswordConfirmation = it },
            onExportBackup = { performExportBackup() },
        )
    }
    val portableBackupRestorePanel: @Composable () -> Unit = {
        PortableBackupRestorePanel(
            backupPassword = backupPassword,
            onBackupPasswordChange = { backupPassword = it },
            backupPasswordConfirmation = backupPasswordConfirmation,
            onBackupPasswordConfirmationChange = { backupPasswordConfirmation = it },
            newMasterPassphrase = backupNewMasterPassphrase,
            onNewMasterPassphraseChange = { backupNewMasterPassphrase = it },
            newMasterPassphraseConfirmation = backupNewMasterPassphraseConfirmation,
            onNewMasterPassphraseConfirmationChange = {
                backupNewMasterPassphraseConfirmation = it
            },
            restoreSelection = backupRestoreSelection,
            onChooseBackupSource = { chooseBackupSource() },
            onChooseRestoreTarget = { chooseBackupRestoreTarget() },
            onReviewRestore = {
                if (backupRestoreSelection.canReview) {
                    pendingBackupRestore = backupRestoreSelection
                }
            },
        )
    }
    val deviceAccessPanel: @Composable () -> Unit = {
        val localPersistence =
            localUnlockCredentialManager.currentPersistence
                ?: localUnlockDescriptor?.persistence
        val localPresentation =
            DeviceAccessPresentation.derive(
                secureStorage = localUnlockStorageModel,
                credentialPersistence = localPersistence,
                credentialLoaded = localUnlockCredential != null,
            )
        val localLoginPresentation =
            DeviceLoginPresentation.derive(
                vaultOpen = session != null,
                credentialLoaded = localUnlockCredential != null,
                enrollmentEligible = localUnlockCredentialManager.canEnrollVaultKey,
                localLoginEnrolled = deviceLoginAvailable,
            )
        LocalLoginPanel(
            secureStorage = localUnlockStorageModel,
            presentation = localPresentation,
            credentialLoaded = localUnlockCredential != null,
            localLogin = localLoginPresentation,
            onLoadCredential = { loadLocalUnlockCredential() },
            onCreateBiometricCredential = { createBiometricLocalLogin() },
            onRemoveLocalLogin = {
                destructiveGate.request(DestructiveConfirmation.RemoveDeviceLogin)
            },
        )
    }
    val settingsPanel: @Composable () -> Unit = {
        val vaultFileExists =
            runCatching {
                val path = Path.of(vaultDirectory)
                path.fileName.toString().endsWith(".kvault", ignoreCase = true) &&
                    Files.isRegularFile(path)
            }.getOrDefault(false)
        SettingsPanel(
            vaultFile = vaultDirectory,
            presentation =
                SettingsPresentation.derive(
                    vaultOpen = session != null,
                    vaultFileExists = vaultFileExists,
                ),
            locale = locale,
            onLocaleChange = onLocaleChange,
            onDeleteVaultFile = {
                destructiveGate.request(
                    DestructiveConfirmation.DeleteVaultFile(vaultDirectory),
                )
            },
        )
    }
    val listPanel: @Composable (Modifier) -> Unit = { modifier ->
        SecretListPanel(
            secrets = visibleSecrets,
            totalSecretCount = secrets.size,
            query = secretListQuery,
            onQueryTextChange = { filterText = it },
            onTypeChange = { filterType = it },
            onCategoryChange = { filterCategory = it },
            onProviderChange = { filterProvider = it },
            onSoftwareChange = { filterSoftware = it },
            onClearFilters = {
                filterText = ""
                filterType = null
                filterCategory = ""
                filterProvider = ""
                filterSoftware = ""
                status = strings.filtersCleared
            },
            groupingMode = groupingMode,
            onGroupingChange = { groupingMode = it },
            selectedSecretId = selectedSecretId,
            onSelect = {
                if (it != selectedSecretId) {
                    clearSecretEditor()
                    revealLifecycle.clear()
                    showTotpCode = false
                }
                selectedSecretId = it
                revealedValue = ""
                totpCode = ""
                inspectorSheetOpen = true
            },
            onAddSecret = {
                clearSecretEditor()
                selectedSecretId = null
                inspectorSheetOpen = false
                currentDestination = top.focess.keystead.client.ui.KeysteadDestination.ADD
            },
            modifier = modifier,
        )
    }
    val inspectorPanel: @Composable (Modifier) -> Unit = { modifier ->
        InspectorPanel(
            selectedSecret = selectedSecret,
            revealedValue = revealedValue,
            showTotpCode = showTotpCode,
            totpCode = totpCode,
            totpSecondsRemaining = totpSecondsRemaining,
            onReveal = {
                val current = session ?: return@InspectorPanel
                val selected = selectedSecret ?: return@InspectorPanel
                runAction {
                    clearSecretEditor()
                    revealedValue =
                        if (selected.type == SecretType.LOGIN_PASSWORD.name) {
                            current.revealPassword(selected.id)
                        } else {
                            current.revealField(
                                selected.id,
                                SecretFormModel.specFor(SecretType.valueOf(selected.type))
                                    .revealFieldName,
                            )
                    }
                    revealGeneration = revealLifecycle.reveal(selected.id, revealedValue, java.time.Instant.now())
                    status = strings.secretRevealed
                }
            },
            onHide = {
                revealLifecycle.clear()
                revealedValue = ""
            },
            onCopy = {
                revealedValue.takeIf { it.isNotEmpty() }?.let {
                    clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                    status = strings.copiedToClipboard
                }
            },
            onToggleTotpCode = {
                showTotpCode = !showTotpCode
                status = if (showTotpCode) strings.authCodeShown else strings.authCodeHidden
            },
            onCopyTotpCode = {
                totpCode.takeIf { it.isNotEmpty() }?.let {
                    clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                    status = strings.copiedCodeToClipboard
                }
            },
            onDelete = {
                val selected = selectedSecret ?: return@InspectorPanel
                destructiveGate.request(
                    DestructiveConfirmation.DeleteSecret(selected.id, selected.title)
                )
            },
            onEdit = {
                val current = session ?: return@InspectorPanel
                val selected = selectedSecret ?: return@InspectorPanel
                runAction {
                    revealLifecycle.clear()
                    revealedValue = ""
                    val snapshot = current.editSnapshot(selected.id)
                    val type = SecretType.valueOf(snapshot.type)
                    secretType = type
                    title = snapshot.title
                    username = snapshot.username
                    password = snapshot.password
                    url = snapshot.url
                    category = snapshot.category.orEmpty()
                    provider = snapshot.provider.orEmpty()
                    software = snapshot.software.orEmpty()
                    account = snapshot.account.orEmpty()
                    expiry = snapshot.expiry.orEmpty()
                    structuredFields = snapshot.fields
                    editingSecretId = snapshot.id
                    status = strings.loadedSecretForEdit
                    currentDestination = top.focess.keystead.client.ui.KeysteadDestination.ADD
                }
            },
            modifier = modifier,
        )
    }

    LaunchedEffect(serverAuthSession, serverAvailability, currentDestination) {
        if (serverAuthSession != null &&
            serverAvailability.isOnline &&
            currentDestination == top.focess.keystead.client.ui.KeysteadDestination.SHARE
        ) {
            runAction(serverAction = true) { outstandingShares = serverClient().listShares() }
        }
    }

    val sharePanel: @Composable () -> Unit = {
        SharePanel(
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            onCheckServer = { serverCheckGeneration += 1 },
            title = shareTitle,
            onTitleChange = { shareTitle = it },
            payload = sharePayload,
            onPayloadChange = { sharePayload = it },
            passphrase = sharePassphrase,
            onPassphraseChange = { sharePassphrase = it },
            ttl = shareTtl,
            onTtlChange = { shareTtl = it },
            burnAfterReading = shareBurn,
            onBurnChange = { shareBurn = it },
            mintedShare = mintedShare,
            onClearMinted = { mintedShare = null },
            onCopyCode = { code ->
                clipboardTicket = clipboardLifecycle.copy(code, java.time.Instant.now())
                status = strings.copiedShareCodeToClipboard
            },
            onMint = {
                val passphrase = sharePassphrase.toCharArray()
                try {
                    runAction(serverAction = true) {
                        val minted =
                            shareExchange.mint(
                                serverClient(),
                                shareTitle,
                                sharePayload,
                                passphrase,
                                shareTtl,
                                shareBurn,
                            )
                        mintedShare = minted
                        shareTitle = ""
                        sharePayload = ""
                        sharePassphrase = ""
                        outstandingShares = serverClient().listShares()
                        status = strings.shareMinted(minted.code)
                    }
                } finally {
                    Wipe.wipe(passphrase)
                }
            },
            redeemCode = redeemCode,
            onRedeemCodeChange = { redeemCode = it },
            redeemPassphrase = redeemPassphrase,
            onRedeemPassphraseChange = { redeemPassphrase = it },
            redeemedContents = redeemedContents,
            onClearRedeemed = { redeemedContents = null },
            onRedeem = {
                val passphrase = redeemPassphrase.toCharArray()
                val code = redeemCode.trim()
                try {
                    runAction(serverAction = true) {
                        val contents =
                            shareExchange.redeem(
                                KeysteadServerClient.forPublicRedeem(serverUrl),
                                code,
                                passphrase,
                            )
                        redeemedContents = contents
                        redeemCode = ""
                        redeemPassphrase = ""
                        status = strings.shareRedeemed
                    }
                } finally {
                    Wipe.wipe(passphrase)
                }
            },
            outstandingShares = outstandingShares,
            onRefreshShares = {
                runAction(serverAction = true) {
                    outstandingShares = serverClient().listShares()
                    status = strings.loadedShares(outstandingShares.size)
                }
            },
            onDeleteShare = { code ->
                runAction(serverAction = true) {
                    serverClient().deleteShare(code)
                    outstandingShares = outstandingShares.filterNot { it.code == code }
                    status = strings.deletedShare(code)
                }
            },
        )
    }

    val deviceUnlockModel =
        DeviceUnlockUiModel.derive(
            descriptor = localUnlockDescriptor,
            credentialLoaded = localUnlockCredential != null,
            loadedPersistence = localUnlockCredentialManager.currentPersistence,
            selectedMode = localUnlockStorageModel.selectedMode,
            biometricAvailability = localUnlockStorageModel.biometricAvailability,
            deviceLoginAvailable = deviceLoginAvailable,
        )
    val restoreTargetAvailable =
        runCatching {
            serverRestoreTarget.isNotBlank() &&
                !Files.exists(Path.of(serverRestoreTarget))
        }.getOrDefault(false)
    val serverVaultRestoreModel =
        ServerVaultRestoreModel.derive(
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            requestState = ownVaultAccessRequest?.state,
            approvedPackageAvailable = ownVaultAccessRequest?.approvedPackage != null,
            targetPathAvailable = restoreTargetAvailable,
            masterPassphraseReady =
                BackupFormModel.canUseNewMasterPassphrase(
                    serverRestoreNewMasterPassphrase,
                    serverRestoreNewMasterPassphraseConfirmation,
                ),
        )
    val vaultAccessApprovalContent: @Composable () -> Unit = {
        VaultAccessApprovalPanel(
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            vaultOpen = session != null,
            pendingAccessRequest = pendingApprovalRequest,
            onCheckServer = { serverCheckGeneration += 1 },
            onFindPendingAccessRequest = find@{
                runAction(serverAction = true) {
                    pendingApprovalRequest =
                        VaultAccessWorkflow(serverClient())
                            .pending()
                            .firstOrNull {
                                it.requestId != vaultAccessExchangeSession?.requestId
                            }
                            ?: throw IllegalStateException(strings.noPendingVaultAccessRequest)
                    status = strings.pendingVaultAccessRequestLoaded
                }
            },
            onApprovePendingAccessRequest = approve@{
                val current = session ?: return@approve
                val request = pendingApprovalRequest ?: return@approve
                runAction(serverAction = true) {
                    VaultAccessWorkflow(serverClient()).approve(request, current)
                    pendingApprovalRequest =
                        request.copy(
                            state = ServerVaultAccessRequestState.APPROVED,
                            approvedAt = java.time.Instant.now(),
                        )
                    status = strings.vaultAccessApproved
                }
            },
        )
    }
    val serverRestoreContent: @Composable () -> Unit = {
        ServerRestorePanel(
            model = serverVaultRestoreModel,
            serverAvailability = serverAvailability,
            onCheckServer = { serverCheckGeneration += 1 },
            request = ownVaultAccessRequest,
            targetPath = serverRestoreTarget,
            targetPathAvailable = restoreTargetAvailable,
            onChooseTarget = { chooseServerRestoreTarget() },
            newMasterPassphrase = serverRestoreNewMasterPassphrase,
            onNewMasterPassphraseChange = {
                serverRestoreNewMasterPassphrase = it
                unlockError = null
            },
            newMasterPassphraseConfirmation = serverRestoreNewMasterPassphraseConfirmation,
            onNewMasterPassphraseConfirmationChange = {
                serverRestoreNewMasterPassphraseConfirmation = it
                unlockError = null
            },
            onOpenAccount = {
                currentDestination =
                    top.focess.keystead.client.ui.KeysteadDestination.ACCOUNT
            },
            onCreateRequest = {
                runAction(serverAction = true) {
                    val authenticated = serverAuthSession
                        ?: throw IllegalStateException(strings.notSignedIn)
                    val error = beginVaultAccessExchange(authenticated)
                    if (error != null) throw IllegalStateException(error)
                    status = strings.vaultAccessRequestCreated
                }
            },
            onRefreshRequest = {
                val request = ownVaultAccessRequest ?: return@ServerRestorePanel
                runAction(serverAction = true) {
                    val refreshed =
                        VaultAccessWorkflow(serverClient()).refresh(request.requestId)
                    vaultAccessLifecycle.updateRequest(refreshed)
                    ownVaultAccessRequest = refreshed
                    status = strings.vaultAccessRequestUpdated
                }
            },
            onRestore = restore@{
                val approvedRequest = ownVaultAccessRequest
                val exchange = vaultAccessExchangeSession
                if (approvedRequest?.approvedPackage == null || exchange == null) {
                    reportUnlockError(
                        strings.serverVaultRestoreStatus(
                            serverVaultRestoreModel.copy(
                                stage = ServerVaultRestoreStage.WAITING_FOR_PACKAGE,
                            ),
                        ),
                    )
                    return@restore
                }
                if (
                    !BackupFormModel.canUseNewMasterPassphrase(
                        serverRestoreNewMasterPassphrase,
                        serverRestoreNewMasterPassphraseConfirmation,
                    )
                ) {
                    reportUnlockError(strings.masterPassphrasesDoNotMatch)
                    return@restore
                }
                if (serverRestoreTarget.isBlank()) {
                    reportUnlockError(strings.vaultFileMustNotBeBlank)
                    return@restore
                }
                val target =
                    runCatching { Path.of(serverRestoreTarget) }
                        .getOrElse {
                            reportUnlockError(strings.vaultFileMustNotBeBlank)
                            return@restore
                        }
                if (Files.exists(target)) {
                    reportUnlockError(
                        strings.serverVaultRestoreStatus(
                            serverVaultRestoreModel.copy(
                                stage = ServerVaultRestoreStage.TARGET_IN_USE,
                            ),
                        ),
                    )
                    return@restore
                }
                unlockError = null
                runAction(
                    onError = { unlockError = it },
                    serverAction = true,
                ) {
                    val result =
                        ServerVaultProvisioningService()
                            .restore(
                                file = target,
                                request = approvedRequest,
                                exchangeSession = exchange,
                                newMasterPassphrase =
                                    serverRestoreNewMasterPassphrase.toCharArray(),
                                client = serverClient(),
                                stateStore = syncStateStore(target),
                            )
                    session?.close()
                    session = result.session
                    vaultDirectory =
                        vaultLocationSettings
                            .rememberSuccessfulVault(target)
                            .toString()
                    fingerprint = requireNotNull(approvedRequest.approvedPackage).fingerprint
                    clearVaultAccessState()
                    selectedSecretId = null
                    revealLifecycle.clear()
                    revealedValue = ""
                    clearSecretEditor()
                    refresh(result.session)
                    serverRestoreNewMasterPassphrase = ""
                    serverRestoreNewMasterPassphraseConfirmation = ""
                    currentDestination =
                        top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                    status = strings.restoredVaultFromServer(result.pulledRecords)
                    if (result.rejectedRecords > 0) {
                        actionFeedbackState.error(
                            strings.rejectedServerRecords(result.rejectedRecords),
                        )
                    }
                }
            },
        )
    }
    CompositionLocalProvider(LocalStrings provides locale.strings) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = KeysteadWindowMetrics.modeForWidth(maxWidth.value)
        top.focess.keystead.client.ui.KeysteadAppShell(
            vaultOpen = session != null,
            destination = currentDestination,
            onDestinationChange = {
                if (it != currentDestination &&
                    currentDestination ==
                        top.focess.keystead.client.ui.KeysteadDestination.RECOVERY
                ) {
                    serverRestoreNewMasterPassphrase = ""
                    serverRestoreNewMasterPassphraseConfirmation = ""
                }
                if (it != currentDestination &&
                    it in
                        setOf(
                            top.focess.keystead.client.ui.KeysteadDestination.BACKUP,
                            top.focess.keystead.client.ui.KeysteadDestination.RECOVERY,
                        )
                ) {
                    backupPassword = ""
                    backupPasswordConfirmation = ""
                    backupNewMasterPassphrase = ""
                    backupNewMasterPassphraseConfirmation = ""
                    pendingBackupRestore = null
                }
                currentDestination = it
                if (it != top.focess.keystead.client.ui.KeysteadDestination.SECRETS) inspectorSheetOpen = false
            },
            serverAvailability = serverAvailability,
            feedback = actionFeedbackState.current,
            onDismissFeedback = actionFeedbackState::dismiss,
            layoutMode = layoutMode,
            inspectorSheetVisible = inspectorSheetOpen && selectedSecret != null,
            onDismissInspectorSheet = { inspectorSheetOpen = false },
            onLockVault = { lockVault() },
            secretsContent = { modifier -> listPanel(modifier) },
            inspectorContent = { modifier -> inspectorPanel(modifier) },
            addContent = { addPanel() },
            backupContent = { backupPanel() },
            deviceAccessContent = { deviceAccessPanel() },
            accountContent = { accountPanel() },
            syncContent = { syncPanel() },
            shareContent = { sharePanel() },
            recoveryContent = {
                RecoveryHub(
                    method = recoveryMethod,
                    onMethodChange = { next ->
                        if (next != recoveryMethod) {
                            backupPassword = ""
                            backupPasswordConfirmation = ""
                            backupNewMasterPassphrase = ""
                            backupNewMasterPassphraseConfirmation = ""
                            serverRestoreNewMasterPassphrase = ""
                            serverRestoreNewMasterPassphraseConfirmation = ""
                            pendingBackupRestore = null
                        }
                        recoveryMethod = next
                    },
                    portableBackupContent = { portableBackupRestorePanel() },
                    serverRestoreContent = {
                        ServerRecoveryHub(
                            task = serverRecoveryTask,
                            onTaskChange = { next ->
                                if (next != serverRecoveryTask) {
                                    serverRestoreNewMasterPassphrase = ""
                                    serverRestoreNewMasterPassphraseConfirmation = ""
                                }
                                serverRecoveryTask = next
                            },
                            restoreContent = { serverRestoreContent() },
                            approvalContent = { vaultAccessApprovalContent() },
                        )
                    },
                )
            },
            settingsContent = { settingsPanel() },
            unlockContent = {
                top.focess.keystead.client.ui.UnlockScreen(
                    vaultDirectory = vaultDirectory,
                    masterPassword = masterPassword,
                    errorMessage = unlockError,
                    deviceUnlock = deviceUnlockModel,
                    onVaultDirectoryChange = {
                        vaultDirectory = it
                        unlockError = null
                    },
                    onChooseExistingVault = { chooseExistingVaultFile() },
                    onChooseNewVaultLocation = { chooseNewVaultFile() },
                    onMasterPasswordChange = {
                        masterPassword = it
                        unlockError = null
                    },
                    onOpen = open@{
                        if (vaultDirectory.isBlank()) {
                            reportUnlockError(strings.vaultFileMustNotBeBlank)
                            return@open
                        }
                        unlockError = null
                        runAction(onError = { unlockError = it }) {
                            val opened =
                                LocalVaultSession.openOrCreate(
                                    Path.of(vaultDirectory),
                                    masterPassword.toCharArray(),
                                )
                            session?.close()
                            session = opened
                            vaultDirectory =
                                vaultLocationSettings
                                    .rememberSuccessfulVault(Path.of(vaultDirectory))
                                    .toString()
                            fingerprint = opened.fingerprintValue()
                            masterPassword = ""
                            selectedSecretId = null
                            revealLifecycle.clear()
                            revealedValue = ""
                            clearSecretEditor()
                            refresh(opened)
                            if (localUnlockDescriptor?.persistence ==
                                    LocalLoginPersistence.BIOMETRIC &&
                                localUnlockCredential == null
                            ) {
                                loadLocalUnlockCredential()
                            } else {
                                status =
                                    if (enableDeviceLoginIfReady()) {
                                        strings.deviceLoginEnabled
                                    } else {
                                        strings.vaultOpen
                                    }
                            }
                        }
                    },
                    onOpenWithDeviceKey = {
                        if (vaultDirectory.isBlank()) {
                            reportUnlockError(strings.vaultFileMustNotBeBlank)
                            return@UnlockScreen
                        }
                        if (localUnlockCredential == null && localUnlockDescriptor != null) {
                            unlockError = null
                            loadLocalUnlockCredential(onError = { unlockError = it })
                        }
                        val credential = localUnlockCredential
                        if (credential == null) {
                            unlockError =
                                unlockError
                                    ?: status.ifBlank { strings.deviceLoginNotConfigured }
                            return@UnlockScreen
                        }
                        unlockError = null
                        runAction(onError = { unlockError = it }) {
                            val opened = LocalVaultSession.openWithLocalLogin(
                                Path.of(vaultDirectory),
                                credential,
                            )
                            session?.close()
                            session = opened
                            vaultDirectory =
                                vaultLocationSettings
                                    .rememberSuccessfulVault(Path.of(vaultDirectory))
                                    .toString()
                            fingerprint = opened.fingerprintValue()
                            masterPassword = ""
                            selectedSecretId = null
                            revealLifecycle.clear()
                            revealedValue = ""
                            clearSecretEditor()
                            status = strings.vaultOpen
                            refresh(opened)
                        }
                    },
                )
            },
        )
    }
    }
    val pendingDestructive = destructiveGate.pending
    if (pendingDestructive != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { destructiveGate.cancel() },
            title = { androidx.compose.material3.Text(pendingDestructive.title(strings)) },
            text = { androidx.compose.material3.Text(pendingDestructive.message(strings)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        when (val confirmed = destructiveGate.confirm()) {
                            is DestructiveConfirmation.DeleteSecret -> performDeleteSecret(confirmed.secretId)
                            is DestructiveConfirmation.DeleteVaultFile ->
                                performDeleteVaultFile(confirmed.vaultFile)
                            is DestructiveConfirmation.RemoveServerRecords ->
                                performRemoveServerRecords(confirmed.secretIds)
                            DestructiveConfirmation.RemoveDeviceLogin -> performRemoveDeviceLogin()
                            null -> {}
                        }
                    },
                    colors =
                        androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        ),
                ) { androidx.compose.material3.Text(strings.confirm) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { destructiveGate.cancel() }) {
                    androidx.compose.material3.Text(strings.cancel)
                }
            },
        )
    }
    val restoreToConfirm = pendingBackupRestore
    if (restoreToConfirm != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingBackupRestore = null },
            title = { androidx.compose.material3.Text(strings.confirmBackupRestoreTitle) },
            text = {
                androidx.compose.material3.Text(
                    strings.confirmBackupRestoreMessage(
                        restoreToConfirm.source?.toString().orEmpty(),
                        restoreToConfirm.target?.toString().orEmpty(),
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        pendingBackupRestore = null
                        performRestoreBackup(restoreToConfirm)
                    },
                ) {
                    androidx.compose.material3.Text(strings.restoreBackup)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingBackupRestore = null }) {
                    androidx.compose.material3.Text(strings.cancel)
                }
            },
        )
    }
}

