package top.focess.keystead.client

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef
import java.awt.Dimension
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
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
import top.focess.keystead.client.ui.SyncCompareDialog
import top.focess.keystead.client.ui.SyncComparisonItem
import top.focess.keystead.client.ui.SyncPanel
import top.focess.keystead.client.ui.buildSyncDiff
import top.focess.keystead.client.ui.toEncryptedSyncRecord
import top.focess.keystead.model.SecretType
import top.focess.keystead.share.ShareContents

private val defaultClientDirectory: Path = ClientDataDirectory.resolve()
private val defaultVaultDirectory: String =
    defaultClientDirectory.resolve("vaults").resolve("vault.kvault").toString()
private const val desktopStorageInstance = "keystead-desktop"

private data class VaultUiSnapshot(
    val secrets: List<SecretListItem>,
    val deviceKeySlots: List<DeviceKeySlot>,
)

private data class RecordInventorySnapshot(
    val serverRecords: List<PersonalVaultRecord>,
    val inventory: PersonalVaultRecordInventory,
)

private data class DeviceEnrollmentResult(
    val enabled: Boolean,
    val slots: List<DeviceKeySlot>,
)

private data class LocalUnlockLoadResult(
    val descriptor: LocalUnlockCredentialDescriptor?,
    val storageModel: SecureStorageUiModel,
    val enrollment: DeviceEnrollmentResult?,
)

private data class OpenedVaultResult(
    val session: LocalVaultSession,
    val rememberedPath: Path,
    val fingerprint: String,
    val snapshot: VaultUiSnapshot,
)

private data class RestoredServerAuth(
    val session: ServerAuthSession,
    val baseUrl: String,
    val username: String,
)

fun main(args: Array<String>) = application {
    val brandImage =
        remember {
            KeysteadBrand.loadIconImage().also(KeysteadBrand::installDesktopIcon)
        }
    val appIcon =
        remember(brandImage) {
            BitmapPainter(brandImage.toComposeImageBitmap())
        }
    val windowState =
        rememberWindowState(
            width = (KeysteadWindowMetrics.WideBreakpointDp + 120).dp,
            height = 820.dp,
        )
    var windowVisible by remember { mutableStateOf(true) }
    val desktopController = remember { DesktopAppController() }
    val trayEnabled = remember { DesktopTrayPolicy.isEnabled(isTraySupported, args.asList()) }
    val trayStrings = desktopController.locale.strings
    if (trayEnabled) {
        Tray(
            icon = appIcon,
            tooltip = trayStrings.appTitle,
            onAction = { windowVisible = true },
            menu = {
                Item(trayStrings.openApplication, onClick = { windowVisible = true })
                Item(trayStrings.lock, onClick = desktopController::lockVault)
                Separator()
                Item(trayStrings.quit, onClick = ::exitApplication)
            },
        )
    }
    Window(
        onCloseRequest = {
            when (DesktopClosePolicy.action(trayEnabled)) {
                DesktopCloseAction.LOCK_AND_HIDE -> {
                    desktopController.lockVault()
                    windowVisible = false
                }
                DesktopCloseAction.EXIT -> exitApplication()
            }
        },
        visible = windowVisible,
        title = "Keystead",
        icon = appIcon,
        state = windowState,
    ) {
        DisposableEffect(Unit) {
            window.minimumSize =
                Dimension(
                    KeysteadWindowMetrics.minimumWidthPixels(),
                    KeysteadWindowMetrics.minimumHeightPixels(),
                )
            onDispose {}
        }
        val systemClipboard = androidx.compose.ui.platform.LocalClipboard.current
        val retryingClipboard = remember { RetryingClipboard(systemClipboard) }
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalClipboard provides retryingClipboard
        ) {
            top.focess.keystead.client.ui.KeysteadTheme {
                androidx.compose.material3.Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                ) {
                    KeysteadClientApp(
                        windowHandle = { WinDef.HWND(Native.getWindowPointer(window)) },
                        desktopController = desktopController,
                    )
                }
            }
        }
    }
}

@Composable
fun KeysteadClientApp(
    windowHandle: () -> WinDef.HWND? = { null },
    desktopController: DesktopAppController = DesktopAppController(),
) {
    val globalSettingsStore = remember {
        ClientSettingsStore(defaultClientDirectory.resolve("settings.json"))
    }
    val vaultLocationSettings =
        remember {
            VaultLocationSettings(
                globalSettingsStore,
                Path.of(defaultVaultDirectory),
            )
        }
    var vaultDirectory by remember {
        mutableStateOf(vaultLocationSettings.load().toString())
    }
    var settingsScope by remember(vaultDirectory) {
        mutableStateOf(globalSettingsStore.load().scopeFor(vaultDirectory))
    }
    val effectiveSettingsStore = remember(settingsScope, vaultDirectory) {
        if (settingsScope == SettingsScope.VAULT_LOCAL) {
            ClientSettingsStore(
                Path.of(vaultDirectory).toAbsolutePath().normalize().parent.resolve("settings.json"),
            )
        } else {
            globalSettingsStore
        }
    }
    val effectiveConfigPath = remember(settingsScope, vaultDirectory) {
        if (settingsScope == SettingsScope.VAULT_LOCAL) {
            Path.of(vaultDirectory).toAbsolutePath().normalize().parent.resolve("settings.json").toString()
        } else {
            defaultClientDirectory.resolve("settings.json").toString()
        }
    }
    val onSettingsScopeChange: (SettingsScope) -> Unit = { newScope ->
        settingsScope = newScope
        val settings = globalSettingsStore.load()
        settings.vaultScopes = (settings.vaultScopes ?: emptyMap()) + (vaultDirectory to newScope.name)
        globalSettingsStore.save(settings)
    }
    val languageSettings = remember(effectiveSettingsStore) {
        LanguageSettings(effectiveSettingsStore)
    }
    var locale by remember(languageSettings) { mutableStateOf(languageSettings.load() ?: AppLocale.ENGLISH) }
    val onLocaleChange: (AppLocale) -> Unit = { newLocale ->
        locale = newLocale
        languageSettings.save(newLocale)
    }
    val autoLockSettings = remember(effectiveSettingsStore) {
        AutoLockSettings(effectiveSettingsStore)
    }
    var autoLockTimeout by remember(effectiveSettingsStore) {
        mutableStateOf(autoLockSettings.load())
    }
    val userIdleTracker = remember { UserIdleTracker() }
    val recordUserActivity = remember(userIdleTracker) {
        { userIdleTracker.recordActivity() }
    }
    DisposableEffect(userIdleTracker) {
        val listener = DesktopUserActivityListener(recordUserActivity)
        onDispose(listener::close)
    }
    val strings by rememberUpdatedState(locale.strings)
    androidx.compose.runtime.SideEffect { desktopController.locale = locale }
    val currentTouchIdAuthenticationReason by
        rememberUpdatedState(strings.touchIdAuthenticationReason)
    val serverConnectionSettings =
        remember(effectiveSettingsStore) {
            ServerConnectionSettings(
                effectiveSettingsStore,
                "http://localhost:22144",
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
    var revealedFieldName by remember { mutableStateOf<String?>(null) }
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
    var serverRecords by remember { mutableStateOf<List<PersonalVaultRecord>>(emptyList()) }
    var secretType by remember { mutableStateOf(SecretType.LOGIN_PASSWORD) }
    var title by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var passwordDraft by remember { mutableStateOf(PasswordDraftState()) }
    var passwordBreachResult by remember { mutableStateOf<PasswordBreachResult>(PasswordBreachResult.NotChecked) }
    var passwordCheckToken by remember { mutableStateOf<Any?>(null) }
    val passwordChecker = remember { PwnedPasswordChecker() }
    val passwordBreachAuditor = remember(passwordChecker) { PasswordBreachAuditor(passwordChecker) }
    var savedPasswordBreachFindings by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val uiScope = rememberCoroutineScope()
    var autoLockSaveJob by remember(effectiveSettingsStore) {
        mutableStateOf<kotlinx.coroutines.Job?>(null)
    }
    DisposableEffect(effectiveSettingsStore) {
        onDispose { autoLockSaveJob?.cancel() }
    }
    val actionGate = remember { UiActionGate<UiActionGroup>() }
    var activeActionGroups by remember { mutableStateOf(emptySet<UiActionGroup>()) }
    var vaultLockState by remember { mutableStateOf(VaultLockState()) }
    val vaultActionGroups =
        remember {
            setOf(
                UiActionGroup.VAULT,
                UiActionGroup.SYNC,
                UiActionGroup.RECOVERY,
                UiActionGroup.DEVICE_LOGIN,
                UiActionGroup.BACKUP,
            )
        }
    val serverActionGroups =
        remember {
            setOf(
                UiActionGroup.ACCOUNT,
                UiActionGroup.SYNC,
                UiActionGroup.SHARE,
                UiActionGroup.RECOVERY,
            )
        }
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
    val secureStorageSettings = remember(effectiveSettingsStore) {
        SecureStorageSettings(effectiveSettingsStore, false)
    }
    val localUnlockStorageSettings = remember(effectiveSettingsStore) {
        SecureStorageSettings(effectiveSettingsStore, true)
    }
    val secureStorageViewModel =
        remember {
            SecureStorageViewModel(
                secureStorageSettings,
                SecureStorageFactory(
                    windowHandle = windowHandle,
                    touchIdAuthenticationReason = { currentTouchIdAuthenticationReason },
                ),
            )
        }
    val localUnlockStorageViewModel =
        remember {
            SecureStorageViewModel(
                localUnlockStorageSettings,
                SecureStorageFactory(
                    windowHandle = windowHandle,
                    touchIdAuthenticationReason = { currentTouchIdAuthenticationReason },
                ),
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
    var pendingPull by remember { mutableStateOf(false) }
    var pendingSyncComparison by remember { mutableStateOf<List<SyncComparisonItem>?>(null) }
    val syncAccept = remember { mutableStateMapOf<String, Boolean>() }
    var serverRestoreTarget by remember { mutableStateOf(vaultDirectory) }
    var serverRestoreNewMasterPassphrase by remember { mutableStateOf("") }
    var serverRestoreNewMasterPassphraseConfirmation by remember { mutableStateOf("") }
    val actionFeedbackState = remember { ActionFeedbackState(strings.vaultLocked) }
    var status by actionFeedbackState
    var unlockError by remember(locale) { mutableStateOf<String?>(null) }
    LaunchedEffect(locale) {
        actionFeedbackState.reset(if (session == null) strings.vaultLocked else strings.vaultOpen)
        accountAuthUiState = accountAuthUiState.onInputChanged()
    }
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
        passwordDraft = PasswordDraftState()
        passwordBreachResult = PasswordBreachResult.NotChecked
        passwordCheckToken = null
        url = ""
        category = ""
        provider = ""
        software = ""
        account = ""
        expiry = ""
        structuredFields = emptyMap()
        editingSecretId = null
    }

    fun checkPasswordDraft() {
        if (
            passwordDraft.value.isEmpty() ||
            passwordBreachResult == PasswordBreachResult.Checking
        ) return
        val checkedPassword = passwordDraft.value.toCharArray()
        val query =
            try {
                passwordChecker.prepare(checkedPassword)
            } finally {
                Wipe.wipe(checkedPassword)
            }
        val checkToken = Any()
        passwordCheckToken = checkToken
        passwordBreachResult = PasswordBreachResult.Checking
        uiScope.launch {
            val result =
                try {
                    val count = query.use { withContext(Dispatchers.IO) { it.breachCount() } }
                    if (count == 0) PasswordBreachResult.NotFound
                    else PasswordBreachResult.Found(count)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    PasswordBreachResult.Failed
                }
            if (passwordCheckToken === checkToken) {
                passwordBreachResult = result
                if (result is PasswordBreachResult.Found) {
                    actionFeedbackState.error(strings.passwordFoundInBreaches(result.count))
                }
            }
        }
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

    fun beginVaultAccessExchange(
        authenticated: ServerAuthSession,
        baseUrl: String,
    ): Pair<EphemeralVaultAccessSession, ServerVaultAccessRequest> {
        val request =
                vaultAccessLifecycle.requestByUser {
                    val exchange = EphemeralVaultAccessSession.create(baseUrl)
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
        return requireNotNull(vaultAccessLifecycle.exchange) to request
    }

    fun restoreServerSession(store: RefreshTokenStore): RestoredServerAuth? {
        val persisted = store.load() ?: return null
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
        val restoredSession =
            KeysteadServerAuthClient(persisted.baseUrl, restoreHttp).restore(
                persisted.refreshToken,
                persisted.refreshTokenExpiresAt,
                tokenSink,
                onRevoked,
            )
        return RestoredServerAuth(
            session = restoredSession,
            baseUrl = persisted.baseUrl,
            username = persisted.username,
        )
    }

    LaunchedEffect(Unit) {
        try {
            val (storageModel, descriptor) =
                withContext(Dispatchers.IO) {
                    var model =
                        localUnlockStorageViewModel.initialize(
                            defaultClientDirectory.resolve("local-login-secure-storage"),
                            "$desktopStorageInstance-local-login",
                        )
                    val loadedDescriptor = localUnlockCredentialManager.descriptor()
                    loadedDescriptor?.let {
                        if (model.selectedMode == null) {
                            model =
                        localUnlockStorageViewModel.adoptExistingLocalLogin(
                                    it.persistence,
                        )
                        }
                    }
                    model to loadedDescriptor
                }
            localUnlockStorageModel = storageModel
            localUnlockDescriptor = descriptor
        } catch (error: Exception) {
            actionFeedbackState.error(strings.errorMessage(error))
        }
        secureStorageModel =
            withContext(Dispatchers.IO) {
                secureStorageViewModel.initialize(
                    defaultClientDirectory.resolve("secure-storage"),
                    desktopStorageInstance,
                )
            }
        serverSessionStore()?.let { store ->
            try {
                val restored = withContext(Dispatchers.IO) { restoreServerSession(store) }
                if (restored != null) {
                    serverAuthSession = restored.session
                    serverUrl = restored.baseUrl
                    serverUsername = restored.username
                    clearVaultAccessState()
                    serverAvailability = ServerAvailability.ONLINE
                    status = strings.signedInRestored
                }
            } catch (error: KeysteadAuthenticationException) {
                serverAvailability = ServerAvailability.ONLINE
                withContext(Dispatchers.IO) { store.clear() }
                actionFeedbackState.error(strings.serverSessionExpired)
            } catch (error: Exception) {
                if (error is java.io.IOException) {
                    serverAvailability = ServerAvailability.OFFLINE
                }
                actionFeedbackState.error(
                    strings.couldNotRestoreServerSession(
                        strings.errorMessage(error),
                    ),
                )
            }
        }
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
            if (revealLifecycle.expire(java.time.Instant.now(), revealGeneration)) {
                revealedFieldName = null
                revealedValue = ""
            }
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
        val uri =
            withContext(Dispatchers.IO) {
                runCatching { current.revealField(selected.id, "otpauthUri") }.getOrNull()
            }
        val period = MfaTotp.period(uri)
        var lastCounter = -1L
        while (true) {
            val now = java.time.Instant.now()
            val counter = now.epochSecond / period
            if (counter != lastCounter) {
                lastCounter = counter
                val seed =
                    withContext(Dispatchers.IO) {
                        runCatching { current.revealField(selected.id, "seed") }.getOrNull()
                    }
                totpCode = seed?.let { MfaTotp.currentCode(it, uri, now) }.orEmpty()
            }
            totpSecondsRemaining = MfaTotp.secondsRemaining(period, now)
            delay(1_000)
        }
    }
    val savedPasswordAuditKey =
        remember(secrets) {
            secrets
                .filter { it.type == SecretType.LOGIN_PASSWORD.name }
                .map { it.id to it.revision }
        }
    LaunchedEffect(session, savedPasswordAuditKey) {
        val current = session
        if (current == null || savedPasswordAuditKey.isEmpty()) {
            savedPasswordBreachFindings = emptyMap()
            return@LaunchedEffect
        }
        val secretsToCheck = secrets.filter { it.type == SecretType.LOGIN_PASSWORD.name }
        delay(750)
        val result =
            withContext(Dispatchers.IO) {
                passwordBreachAuditor.audit(current, secretsToCheck)
            }
        if (session === current) {
            val activeSecretIds = secretsToCheck.mapTo(hashSetOf()) { it.id }
            val previousFindings = savedPasswordBreachFindings
            val retainedFindings =
                previousFindings.filterKeys { it in activeSecretIds && it !in result.checkedSecretIds }
            savedPasswordBreachFindings = retainedFindings + result.findings
            val newlyDetected = result.findings.any { (id, count) -> previousFindings[id] != count }
            if (newlyDetected) {
                actionFeedbackState.error(strings.passwordBreachAuditFound(result.findings.size))
            }
        }
    }

    fun readVaultUiSnapshot(current: LocalVaultSession): VaultUiSnapshot =
        VaultUiSnapshot(
            secrets = current.listSecrets(),
            deviceKeySlots = current.deviceSlots(),
        )

    fun applyVaultUiSnapshot(snapshot: VaultUiSnapshot) {
        secrets = snapshot.secrets
        deviceKeySlots = snapshot.deviceKeySlots
        if (selectedSecretId !in secrets.map { it.id }) {
            selectedSecretId = null
            revealedFieldName = null
            revealedValue = ""
        }
    }

    fun finishPendingLock() {
        if (!vaultLockState.shouldClose(activeActionGroups.any(vaultActionGroups::contains))) return
        session?.close()
        session = null
        localUnlockCredentialManager.unload()
        vaultLockState = vaultLockState.completed()
    }

    fun lockVault(nextStatus: String = strings.vaultLocked) {
        vaultLockState = vaultLockState.request()
        showTotpCode = false
        totpCode = ""
        totpSecondsRemaining = 0
        localUnlockCredential = null
        secrets = emptyList()
        selectedSecretId = null
        revealLifecycle.clear()
        revealedFieldName = null
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
        serverRestoreNewMasterPassphrase = ""
        serverRestoreNewMasterPassphraseConfirmation = ""
        backupRestoreSelection = BackupRestoreSelection(source = null, target = null)
        pendingBackupRestore = null
        currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
        inspectorSheetOpen = false
        actionFeedbackState.info(nextStatus)
        recordInventory = null
        savedPasswordBreachFindings = emptyMap()
        pendingSyncComparison = null
        syncAccept.clear()
        unlockError = null
        finishPendingLock()
    }

    LaunchedEffect(session, autoLockTimeout) {
        val openedSession = session ?: return@LaunchedEffect
        userIdleTracker.recordActivity()
        while (session === openedSession) {
            delay(500)
            if (
                AutoLockPolicy.shouldLock(
                    vaultOpen = session === openedSession,
                    idleTimeoutReached = userIdleTracker.isIdleFor(autoLockTimeout.duration),
                    vaultOperationActive = activeActionGroups.any(vaultActionGroups::contains),
                )
            ) {
                lockVault(strings.vaultAutoLocked)
                return@LaunchedEffect
            }
        }
    }

    DisposableEffect(desktopController) {
        desktopController.onLockVault = { lockVault() }
        onDispose { desktopController.onLockVault = {} }
    }

    fun <T> runAction(
        group: UiActionGroup,
        onError: ((String) -> Unit)? = null,
        serverAction: Boolean = false,
        isCurrent: () -> Boolean = { true },
        onDiscard: (T) -> Unit = {},
        onFinally: () -> Unit = {},
        work: () -> T,
        onSuccess: (T) -> Unit,
    ) {
        if (vaultLockState.pending) return
        val operationGeneration = vaultLockState.generation
        fun resultIsCurrent() = vaultLockState.accepts(operationGeneration) && isCurrent()
        val conflicts =
            buildSet {
                add(group)
                if (group in vaultActionGroups) addAll(vaultActionGroups)
                if (group in serverActionGroups) addAll(serverActionGroups)
            }
        if (!actionGate.tryStart(group, conflicts)) return
        activeActionGroups = activeActionGroups + group
        uiScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                if (resultIsCurrent()) {
                    onSuccess(result)
                    if (serverAction) {
                        serverAvailability =
                            ServerAvailabilityTransitions.afterServerAction(
                                serverAvailability,
                                error = null,
                            )
                    }
                } else {
                    onDiscard(result)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: KeysteadRevisionConflictException) {
                if (!resultIsCurrent()) return@launch
                if (serverAction) {
                    serverAvailability =
                        ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
                }
                conflictAssessment = ConflictAssessment.from(error, strings)
                actionFeedbackState.error(SyncStatusFormatter.messageFor(error, strings))
            } catch (error: KeysteadAccountConflictException) {
                if (!resultIsCurrent()) return@launch
                if (serverAction) {
                    serverAvailability =
                        ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
                }
                val message = strings.serverUserAlreadyExists
                actionFeedbackState.error(message)
                onError?.invoke(message)
            } catch (error: KeysteadAuthenticationException) {
                if (!resultIsCurrent()) return@launch
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
                if (!resultIsCurrent()) return@launch
                if (serverAction) {
                    serverAvailability =
                        ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
                }
                val message = strings.errorMessage(error)
                actionFeedbackState.error(message)
                onError?.invoke(message)
            } catch (error: PersonalVaultMismatchException) {
                if (!resultIsCurrent()) return@launch
                if (serverAction) {
                    serverAvailability =
                        ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
                }
                val message =
                    strings.personalVaultMismatch(error.serverFingerprint, error.localFingerprint)
                actionFeedbackState.error(message)
                onError?.invoke(message)
                val authenticated = serverAuthSession
                val current = session
                val snapshot =
                    runCatching {
                        withContext(Dispatchers.IO) {
                            val remote = authenticated?.client()?.listAllPersonalRecords()
                                ?: return@withContext null
                            RecordInventorySnapshot(
                                remote,
                                PersonalVaultRecordInventory.compare(
                                    localRecords = current?.currentPersonalRecords(),
                                    remoteRecords = remote,
                                    localFingerprint = current?.fingerprintValue(),
                                    canonicalContentKey = current?.let { it::canonicalSyncContentKey },
                                ),
                            )
                        }
                    }.getOrNull()
                if (snapshot != null && resultIsCurrent() && serverAuthSession === authenticated && session === current) {
                    serverRecords = snapshot.serverRecords
                    recordInventory = snapshot.inventory
                }
            } catch (error: KeysteadServerException) {
                if (!resultIsCurrent()) return@launch
                if (serverAction) {
                    serverAvailability =
                        ServerAvailabilityTransitions.afterServerAction(serverAvailability, error)
                }
                val message = strings.errorMessage(error)
                actionFeedbackState.error(message)
                onError?.invoke(message)
            } catch (error: Exception) {
                if (!resultIsCurrent()) return@launch
                val message = strings.errorMessage(error)
                actionFeedbackState.error(message)
                onError?.invoke(message)
            } finally {
                try {
                    onFinally()
                } finally {
                    actionGate.finish(group)
                    activeActionGroups = activeActionGroups - group
                    finishPendingLock()
                }
            }
        }
    }

    fun actionBusy(group: UiActionGroup): Boolean {
        if (vaultLockState.pending) return true
        val conflicts =
            buildSet {
                add(group)
                if (group in vaultActionGroups) addAll(vaultActionGroups)
                if (group in serverActionGroups) addAll(serverActionGroups)
            }
        return activeActionGroups.any(conflicts::contains)
    }

    fun performDeleteSecret(secretId: String) {
        val current = session ?: return
        runAction(
            group = UiActionGroup.VAULT,
            isCurrent = { session === current },
            work = {
            current.delete(secretId)
                readVaultUiSnapshot(current)
            },
        ) { snapshot ->
            if (selectedSecretId == secretId) {
                selectedSecretId = null
                revealLifecycle.clear()
                revealedFieldName = null
                revealedValue = ""
                showTotpCode = false
                totpCode = ""
            }
            if (editingSecretId == secretId) {
                clearSecretEditor()
            }
            status = strings.deletedSecret
            applyVaultUiSnapshot(snapshot)
        }
    }

    fun loadRecordInventory(
        current: LocalVaultSession?,
        client: KeysteadServerClient,
    ): RecordInventorySnapshot {
        val remote = client.listAllPersonalRecords()
        return RecordInventorySnapshot(
            serverRecords = remote,
            inventory =
                PersonalVaultRecordInventory.compare(
                    localRecords = current?.currentPersonalRecords(),
                    remoteRecords = remote,
                    localFingerprint = current?.fingerprintValue(),
                    canonicalContentKey = current?.let { it::canonicalSyncContentKey },
                ),
        )
    }

    fun applyRecordInventory(snapshot: RecordInventorySnapshot) {
        serverRecords = snapshot.serverRecords
        recordInventory = snapshot.inventory
    }

    fun performRemoveServerRecords(secretIds: Set<String>) {
        if (secretIds.isEmpty()) return
        val current = session
        val authenticated = serverAuthSession ?: return
        val client = authenticated.client()
        runAction(
            group = UiActionGroup.SYNC,
            serverAction = true,
            isCurrent = { session === current && serverAuthSession === authenticated },
            work = {
            val removedEvents =
                secretIds.sumOf { secretId ->
                        client.deletePersonalRecordHistory(secretId).deletedEvents
                }
                removedEvents to loadRecordInventory(current, client)
            },
        ) { (removedEvents, snapshot) ->
            applyRecordInventory(snapshot)
            status = strings.removedServerRecords(secretIds.size, removedEvents)
        }
    }

    fun authenticateServer(
        baseUrl: String,
        username: String,
        password: CharArray,
        store: RefreshTokenStore?,
    ): ServerAuthSession {
        val tokenSink: ((String, java.time.Instant) -> Unit)? =
            store?.let { s ->
                { refreshToken, expiresAt ->
                    s.save(
                        PersistedAuthSession(
                            baseUrl,
                            username,
                            refreshToken,
                            expiresAt,
                        ),
                    )
                }
            }
        val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
        return KeysteadServerAuthClient(baseUrl)
            .login(username, password, tokenSink, onRevoked)
    }

    fun loginToServer() {
        val passwordChars = serverPassword.toCharArray()
        val expectedUrl = serverUrl
        val expectedUsername = serverUsername
        val store = serverSessionStore()
        accountAuthUiState = accountAuthUiState.onInputChanged()
        runAction(
            group = UiActionGroup.ACCOUNT,
            onError = { message ->
                accountAuthUiState = accountAuthUiState.withFailure(message)
            },
            serverAction = true,
            isCurrent = { serverUrl == expectedUrl && serverUsername == expectedUsername },
            onDiscard = ServerAuthSession::close,
            onFinally = { Wipe.wipe(passwordChars) },
            work = { authenticateServer(expectedUrl, expectedUsername, passwordChars, store) },
        ) { authenticated ->
            serverAuthSession?.close()
            serverAuthSession = authenticated
            clearVaultAccessState()
            accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
            serverPassword = ""
            serverPasswordConfirmation = ""
            status = strings.signedInToServer
        }
    }

    fun performDeleteVaultFile(vaultFile: String) {
        val target =
            runCatching { Path.of(vaultFile).toAbsolutePath().normalize() }
                .getOrElse {
                    actionFeedbackState.error(
                        strings.vaultFileDeleteFailed(
                            strings.errorMessage(it),
                        ),
                    )
                    return
                }
        val current =
            runCatching { Path.of(vaultDirectory).toAbsolutePath().normalize() }
                .getOrNull()
        if (session == null || current != target) return

        lockVault()
        runAction(
            group = UiActionGroup.VAULT,
            work = {
                val deleted = VaultFileDeletionService().delete(target)
                runCatching { vaultLocationSettings.clear() }
                deleted
            },
        ) { deleted ->
            vaultDirectory = defaultVaultDirectory
            status = strings.vaultFileDeleted(deleted.fileName.toString())
        }
    }

    fun enrollDeviceLoginIfReady(
        current: LocalVaultSession?,
        descriptor: LocalUnlockCredentialDescriptor?,
        credential: LocalUnlockCredential,
    ): DeviceEnrollmentResult? {
        current ?: return null
        descriptor ?: return null
        if (!localUnlockCredentialManager.canEnrollVaultKey) return null
        val vaultFingerprint = current.fingerprintValue()
        val currentSlots = current.deviceSlots()
        if (
            localLoginEnrollmentStore.isEnrolled(
                vaultFingerprint,
                currentSlots.mapTo(mutableSetOf(), DeviceKeySlot::slotKeyId),
                descriptor.keyFingerprint,
            )
        ) {
            return DeviceEnrollmentResult(enabled = false, slots = currentSlots)
        }
        val localSlot = current.replaceLocalLogin(credential)
        localLoginEnrollmentStore.remember(
            vaultFingerprint = vaultFingerprint,
            slotKeyId = localSlot,
            credentialFingerprint = descriptor.keyFingerprint,
        )
        return DeviceEnrollmentResult(enabled = true, slots = current.deviceSlots())
    }

    fun performRemoveDeviceLogin() {
        val current = session ?: return
        runAction(
            group = UiActionGroup.DEVICE_LOGIN,
            isCurrent = { session === current },
            work = {
            val vaultFingerprint = current.fingerprintValue()
            current.removeDeviceLogin()
            localLoginEnrollmentStore.clear(vaultFingerprint)
                current.deviceSlots()
            },
        ) { slots ->
            deviceKeySlots = slots
            deviceLoginAvailable = false
            status = strings.deviceLoginRemoved
        }
    }

    fun loadLocalUnlockCredential(onError: ((String) -> Unit)? = null) {
        val current = session
        val initialStorageModel = localUnlockStorageModel
        runAction(
            group = UiActionGroup.DEVICE_LOGIN,
            onError = onError,
            isCurrent = { session === current },
            work = {
            val descriptor =
                localUnlockCredentialManager.descriptor()
                    ?: throw IllegalStateException(strings.deviceLoginNotConfigured)
                val storageModel =
                    if (initialStorageModel.selectedMode == null) {
                    localUnlockStorageViewModel.adoptExistingLocalLogin(
                        descriptor.persistence,
                    )
                    } else {
                        initialStorageModel
                    }
                val enrollment =
                    localUnlockCredentialManager.useExistingOnce { credential ->
                        enrollDeviceLoginIfReady(current, descriptor, credential)
                    }
                LocalUnlockLoadResult(
                    descriptor = localUnlockCredentialManager.descriptor(),
                    storageModel = storageModel,
                    enrollment = enrollment,
                )
            },
        ) { result ->
            localUnlockStorageModel = result.storageModel
            localUnlockDescriptor = result.descriptor
            result.enrollment?.let {
                deviceKeySlots = it.slots
                deviceLoginAvailable = true
            }
            status =
                if (result.enrollment?.enabled == true) {
                    strings.deviceLoginEnabled
                } else {
                    strings.localLoginReadyStatus
                }
        }
    }

    fun createBiometricLocalLogin() {
        check(localUnlockDescriptor == null) { strings.identityStorageCannotChange }
        val current = session
        runAction(
            group = UiActionGroup.DEVICE_LOGIN,
            isCurrent = { session === current && localUnlockDescriptor == null },
            work = {
                localUnlockStorageViewModel.selectBiometric()
                val storageModel = localUnlockStorageViewModel.model
                var descriptor: LocalUnlockCredentialDescriptor? = null
                val enrollment =
                    localUnlockCredentialManager.useOrCreateOnce(SecureStorageMode.BIOMETRIC) { credential ->
                        descriptor = localUnlockCredentialManager.descriptor()
                        enrollDeviceLoginIfReady(current, descriptor, credential)
                    }
                LocalUnlockLoadResult(descriptor, storageModel, enrollment)
            },
        ) { result ->
            localUnlockStorageModel = result.storageModel
            localUnlockDescriptor = result.descriptor
            result.enrollment?.let {
                deviceKeySlots = it.slots
                deviceLoginAvailable = true
            }
            status =
                if (result.enrollment?.enabled == true) {
                    strings.deviceLoginEnabled
                } else {
                    strings.localLoginReadyStatus
                }
        }
    }

    fun syncStateStore(vaultFile: Path = Path.of(vaultDirectory)): SyncStateStore =
        SyncStateStore.forVault(vaultFile, serverUrl, serverUsername)

    fun performPullAndRetry() {
        val current = session ?: return
        val authenticated = serverAuthSession ?: return
        val client = authenticated.client()
        val state = syncStateStore()
        runAction(
            group = UiActionGroup.SYNC,
            serverAction = true,
            isCurrent = { session === current && serverAuthSession === authenticated },
            work = {
                val pulled = current.pullPendingPersonalRecordsFrom(client, state)
                val pushed = current.pushPendingPersonalRecordsTo(client, state)
                Triple(pulled, pushed, readVaultUiSnapshot(current))
            },
        ) { (pulled, pushed, vaultSnapshot) ->
            conflictAssessment = null
            status = strings.pulledAndRepushed(pulled.imported, pushed)
            if (pulled.rejected.isNotEmpty()) {
                actionFeedbackState.error(strings.rejectedServerRecords(pulled.rejected.size))
            }
            applyVaultUiSnapshot(vaultSnapshot)
        }
    }

    fun performPull() {
        val current = session ?: return
        val authenticated = serverAuthSession ?: return
        val client = authenticated.client()
        val state = syncStateStore()
        runAction(
            group = UiActionGroup.SYNC,
            serverAction = true,
            isCurrent = { session === current && serverAuthSession === authenticated },
            work = {
                val pulled = current.pullPendingPersonalRecordsFrom(client, state)
                Triple(
                    pulled,
                    readVaultUiSnapshot(current),
                    loadRecordInventory(current, client),
                )
            },
        ) { (pulled, vaultSnapshot, inventorySnapshot) ->
            conflictAssessment = null
            applyRecordInventory(inventorySnapshot)
            status =
                strings.pulledRecords(
                    pulled.imported,
                    state.lastPulledServerSequence(fingerprint).toString(),
                )
            if (pulled.rejected.isNotEmpty()) {
                actionFeedbackState.error(strings.rejectedServerRecords(pulled.rejected.size))
            }
            applyVaultUiSnapshot(vaultSnapshot)
        }
    }

    fun prepareSyncComparison() {
        val current = session ?: return
        val inventory = recordInventory
        val remote = serverRecords
        val comparisons = inventory?.comparisons
        if (inventory == null || comparisons == null) return
        runAction(
            group = UiActionGroup.SYNC,
            serverAction = true,
            isCurrent = { session === current && recordInventory === inventory },
            work = {
            val items =
                comparisons
                    .filter {
                        it.status == RecordComparisonStatus.SERVER_NEWER ||
                            it.status == RecordComparisonStatus.SERVER_ONLY ||
                            it.status == RecordComparisonStatus.HASH_MISMATCH ||
                            it.status == RecordComparisonStatus.CONFLICT
                    }
                    .mapNotNull { entry ->
                        val serverPvr =
                            remote
                                .filter { it.secretId == entry.secretId }
                                .maxWithOrNull(compareBy<PersonalVaultRecord> { it.revision }.thenBy { it.serverSequence })
                                ?: return@mapNotNull null
                        val serverRecord = serverPvr.toEncryptedSyncRecord()
                        val serverFields =
                            current.previewSyncRecordFields(serverRecord) ?: return@mapNotNull null
                        val localFields = current.localRecordFields(entry.secretId)
                        SyncComparisonItem(
                            secretId = entry.secretId,
                            title = serverFields["title"] ?: localFields["title"] ?: entry.secretId,
                            secretType = entry.secretType,
                            status = entry.status,
                            fields = buildSyncDiff(localFields, serverFields),
                            serverRecord = serverRecord,
                        )
                    }
                items
            },
        ) { items ->
            syncAccept.clear()
            items.forEach { syncAccept[it.secretId] = false }
            pendingSyncComparison = items
        }
    }

    fun performAcceptSync(items: List<SyncComparisonItem>) {
        val current = session
        pendingSyncComparison = null
        syncAccept.clear()
        if (items.isEmpty() || current == null) return
        val authenticated = serverAuthSession ?: return
        val client = authenticated.client()
        runAction(
            group = UiActionGroup.SYNC,
            serverAction = true,
            isCurrent = { session === current && serverAuthSession === authenticated },
            work = {
            val report = current.importSelectedSyncRecords(items.map { it.serverRecord })
                Triple(
                    report,
                    readVaultUiSnapshot(current),
                    loadRecordInventory(current, client),
                )
            },
        ) { (report, vaultSnapshot, inventorySnapshot) ->
            conflictAssessment = null
            status = strings.pulledRecords(report.imported, report.imported.toString())
            if (report.rejected.isNotEmpty()) {
                actionFeedbackState.error(strings.rejectedServerRecords(report.rejected.size))
            }
            applyVaultUiSnapshot(vaultSnapshot)
            applyRecordInventory(inventorySnapshot)
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
        val password = backupPassword.toCharArray()
        runAction(
            group = UiActionGroup.BACKUP,
            isCurrent = { session === current },
            onFinally = { Wipe.wipe(password) },
            work = {
            java.io.FileOutputStream(target).use { output ->
                    VaultBackup.export(current, password, output)
            }
                target.name
            },
        ) { targetName ->
            backupPassword = ""
            backupPasswordConfirmation = ""
            status = strings.exportedBackupTo(targetName)
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
        val backupPassphrase = backupPassword.toCharArray()
        val newMasterPassphrase = backupNewMasterPassphrase.toCharArray()
        runAction(
            group = UiActionGroup.BACKUP,
            onFinally = {
                Wipe.wipe(backupPassphrase)
                Wipe.wipe(newMasterPassphrase)
            },
            onDiscard = { it.session.close() },
            work = {
            val restored =
                Files.newInputStream(source).use { input ->
                    VaultBackup.restore(
                        target,
                        input,
                            backupPassphrase,
                            newMasterPassphrase,
                    )
                }
            try {
                val remembered = vaultLocationSettings.rememberSuccessfulVault(target)
                    OpenedVaultResult(
                        session = restored,
                        rememberedPath = remembered,
                        fingerprint = restored.fingerprintValue(),
                        snapshot = readVaultUiSnapshot(restored),
                    )
                } catch (error: Exception) {
                    restored.close()
                    throw error
                }
            },
        ) { result ->
                session?.close()
                session = result.session
                vaultDirectory = result.rememberedPath.toString()
                fingerprint = result.fingerprint
                selectedSecretId = null
                revealLifecycle.clear()
                revealedFieldName = null
                revealedValue = ""
                clearSecretEditor()
                applyVaultUiSnapshot(result.snapshot)
                if (localUnlockDescriptor != null) {
                    loadLocalUnlockCredential()
                }
                backupPassword = ""
                backupPasswordConfirmation = ""
                backupNewMasterPassphrase = ""
                backupNewMasterPassphraseConfirmation = ""
                backupRestoreSelection = BackupRestoreSelection(source = null, target = null)
                pendingBackupRestore = null
                currentDestination =
                    top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                status = strings.restoredBackupTo(target.fileName.toString())
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
            enabled = session != null && !actionBusy(UiActionGroup.VAULT),
            selectedType = secretType,
            onSelectedTypeChange = {
                if (it != secretType) {
                    clearSecretEditor()
                    revealLifecycle.clear()
                    revealedFieldName = null
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
            usernameSuggestions = secrets.mapNotNull(SecretListItem::username),
            password = passwordDraft.value,
            onPasswordChange = {
                passwordDraft = passwordDraft.edited(it)
                passwordBreachResult = PasswordBreachResult.NotChecked
                passwordCheckToken = null
            },
            passwordVisible = passwordDraft.visible,
            onPasswordVisibilityChange = {
                passwordDraft = if (it) passwordDraft.show() else passwordDraft.hide()
            },
            passwordStrength = PasswordStrengthEvaluator.evaluate(passwordDraft.value),
            passwordBreachResult = passwordBreachResult,
            onCheckPassword = { checkPasswordDraft() },
            onPasswordEditingFinished = { checkPasswordDraft() },
            onGeneratePassword = { options ->
                passwordDraft = PasswordDraftState()
                passwordBreachResult = PasswordBreachResult.NotChecked
                passwordCheckToken = null
                PasswordDraftGenerator.generate(options) { generated ->
                    passwordDraft = passwordDraft.generated(String(generated))
                }
                status = strings.generatedPassword
                checkPasswordDraft()
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
            },
            onGenerateSshKey = {
                val identity = account.ifBlank { title.ifBlank { null } }
                runAction(
                    group = UiActionGroup.VAULT,
                    work = { SshKeyDraftGenerator.generate(identity) },
                ) { draft ->
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedSshKey
                }
            },
            onGenerateGpgKey = {
                val passphrase =
                        structuredFields["passphrase"]?.takeIf { it.isNotBlank() }
                            ?: PasswordDraftGenerator.generate()
                val passphraseChars = passphrase.toCharArray()
                val identity = account.ifBlank { title.ifBlank { "Keystead User" } }
                runAction(
                    group = UiActionGroup.VAULT,
                    onFinally = { Wipe.wipe(passphraseChars) },
                    work = {
                        GpgKeyDraftGenerator.generate(
                                identity = identity,
                                passphrase = passphraseChars,
                        )
                    },
                ) { draft ->
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedGpgKey
                }
            },
            onGenerateCertificate = {
                val commonName = account.ifBlank { title.ifBlank { "keystead.local" } }
                runAction(
                    group = UiActionGroup.VAULT,
                    work = {
                        CertificateDraftGenerator.generate(
                                commonName = commonName,
                        )
                    },
                ) { draft ->
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedCertificate
                }
            },
            onGenerateMfaSecret = {
                val draft =
                        MfaSecretDraftGenerator.generate(
                            issuer = title.ifBlank { "Keystead" },
                            accountName = account.ifBlank { title.ifBlank { "account" } },
                        )
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = strings.generatedMfaSecret
            },
            onCancel = {
                clearSecretEditor()
                currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
            },
            onSave = {
                val current = session ?: return@AddSecretPanel
                val editing = editingSecretId
                val formType = secretType
                val formTitle = title
                val formUsername = username
                val formPassword = passwordDraft.value
                val formUrl = url
                val formCategory = category
                val formProvider = provider
                val formSoftware = software
                val formAccount = account
                val formExpiry = expiry
                val formFields = structuredFields
                runAction(
                    group = UiActionGroup.VAULT,
                    isCurrent = { session === current },
                    work = {
                    if (editing != null) {
                            if (formType == SecretType.LOGIN_PASSWORD) {
                            current.updateLogin(
                                editing,
                                    formTitle,
                                    formUsername,
                                    formPassword,
                                    formUrl.ifBlank { null },
                                    category = formCategory.ifBlank { null },
                                    provider = formProvider.ifBlank { null },
                                    software = formSoftware.ifBlank { null },
                                    account = formAccount.ifBlank { null },
                                    expiry = formExpiry.ifBlank { null },
                            )
                        } else {
                                val spec = SecretFormModel.specFor(formType)
                            current.updateStructuredSecret(
                                editing,
                                    title = formTitle,
                                    fields = SecretFormModel.fieldValues(spec, formFields),
                                    category = formCategory.ifBlank { spec.defaultCategory },
                                    provider = formProvider.ifBlank { spec.defaultProvider },
                                    software = formSoftware.ifBlank { spec.defaultSoftware },
                                    account = formAccount.ifBlank { null },
                                    expiry = formExpiry.ifBlank { null },
                            )
                        }
                    } else {
                            if (formType == SecretType.LOGIN_PASSWORD) {
                            current.addLogin(
                                    formTitle,
                                    formUsername,
                                    formPassword,
                                    formUrl.ifBlank { null },
                                    category = formCategory.ifBlank { null },
                                    provider = formProvider.ifBlank { null },
                                    software = formSoftware.ifBlank { null },
                                    account = formAccount.ifBlank { null },
                                    expiry = formExpiry.ifBlank { null },
                            )
                        } else {
                                val spec = SecretFormModel.specFor(formType)
                            current.addStructuredSecret(
                                    type = formType,
                                    title = formTitle,
                                    fields = SecretFormModel.fieldValues(spec, formFields),
                                    category = formCategory.ifBlank { spec.defaultCategory },
                                    provider = formProvider.ifBlank { spec.defaultProvider },
                                    software = formSoftware.ifBlank { spec.defaultSoftware },
                                    account = formAccount.ifBlank { null },
                                    expiry = formExpiry.ifBlank { null },
                            )
                        }
                    }
                        readVaultUiSnapshot(current)
                    },
                ) { snapshot ->
                    status = if (editing != null) strings.updatedSecret else strings.savedSecret
                    clearSecretEditor()
                    revealedFieldName = null
                    revealedValue = ""
                    applyVaultUiSnapshot(snapshot)
                    currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                }
            },
            editing = editingSecretId != null,
        )
    }
    val accountPanel: @Composable () -> Unit = {
        AccountPanel(
            busy = actionBusy(UiActionGroup.ACCOUNT),
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
                runAction(
                    group = UiActionGroup.ACCOUNT,
                    serverAction = true,
                    isCurrent = { serverAuthSession === authenticated },
                    work = { authenticated.refresh() },
                ) {
                    status = strings.serverSessionRefreshed
                }
            },
            onLogout = {
                val authenticated = serverAuthSession ?: return@AccountPanel
                runAction(
                    group = UiActionGroup.ACCOUNT,
                    serverAction = true,
                    onFinally = {
                    serverSessionStore()?.clear()
                        if (serverAuthSession === authenticated) serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                    accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                    },
                    work = { authenticated.revoke() },
                ) { status = strings.signedOutOfServer }
            },
            onLogoutAll = {
                val authenticated = serverAuthSession ?: return@AccountPanel
                runAction(
                    group = UiActionGroup.ACCOUNT,
                    serverAction = true,
                    onFinally = {
                    serverSessionStore()?.clear()
                        if (serverAuthSession === authenticated) serverAuthSession = null
                    clearVaultAccessState()
                    pendingApprovalRequest = null
                    accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                    },
                    work = { authenticated.logoutAll() },
                ) { status = strings.signedOutEverywhere }
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
                val expectedUrl = serverUrl
                val expectedUsername = serverUsername
                val store = serverSessionStore()
                accountAuthUiState = accountAuthUiState.onInputChanged()
                runAction(
                    group = UiActionGroup.ACCOUNT,
                    onError = { message ->
                        accountAuthUiState = accountAuthUiState.withFailure(message)
                    },
                    serverAction = true,
                    isCurrent = { serverUrl == expectedUrl && serverUsername == expectedUsername },
                    onDiscard = ServerAuthSession::close,
                    onFinally = {
                        Wipe.wipe(registrationPassword)
                        Wipe.wipe(loginPassword)
                    },
                    work = {
                        val authClient = KeysteadServerAuthClient(expectedUrl)
                        authClient.registerUser(expectedUsername, registrationPassword)
                        val tokenSink: ((String, java.time.Instant) -> Unit)? =
                            store?.let { s ->
                                { refreshToken, expiresAt ->
                                    s.save(
                                        PersistedAuthSession(
                                            expectedUrl,
                                            expectedUsername,
                                            refreshToken,
                                            expiresAt,
                                        ),
                                    )
                                }
                            }
                        val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
                        authClient.login(expectedUsername, loginPassword, tokenSink, onRevoked)
                    },
                ) { authenticated ->
                        serverAuthSession?.close()
                        serverAuthSession = authenticated
                        clearVaultAccessState()
                        accountAuthUiState = accountAuthUiState.select(AccountAuthMode.SIGN_IN)
                        status = strings.serverUserCreatedAndSignedIn
                    serverPassword = ""
                    serverPasswordConfirmation = ""
                }
            },
        )
    }
    val syncPanel: @Composable () -> Unit = {
        SyncPanel(
            busy = actionBusy(UiActionGroup.SYNC),
            vaultOpen = session != null,
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            onCheckServer = { serverCheckGeneration += 1 },
            onUploadSelected = { secretIds ->
                val current = session ?: return@SyncPanel
                val authenticated = serverAuthSession ?: return@SyncPanel
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.SYNC,
                    serverAction = true,
                    isCurrent = { session === current && serverAuthSession === authenticated },
                    work = {
                    var latestInventory: RecordInventorySnapshot? = null
                    val pushed =
                        SelectedRecordUploadCoordinator.upload(
                            secretIds = secretIds,
                                push = { current.pushSelectedPersonalRecordsTo(client, it) },
                            refreshComparisons = {
                                    loadRecordInventory(current, client)
                                        .also { latestInventory = it }
                                        .inventory.comparisons.orEmpty()
                            },
                            promote = current::promoteLocalRecord,
                        )
                        Triple(
                            pushed,
                            readVaultUiSnapshot(current),
                            latestInventory ?: loadRecordInventory(current, client),
                        )
                    },
                ) { (pushed, vaultSnapshot, inventorySnapshot) ->
                    conflictAssessment = null
                    applyVaultUiSnapshot(vaultSnapshot)
                    applyRecordInventory(inventorySnapshot)
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
            onPull = { prepareSyncComparison() },
            onRefreshRecords = {
                val current = session
                val authenticated = serverAuthSession ?: return@SyncPanel
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.SYNC,
                    serverAction = true,
                    isCurrent = { session === current && serverAuthSession === authenticated },
                    work = { loadRecordInventory(current, client) },
                ) { snapshot ->
                    applyRecordInventory(snapshot)
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
            busy = actionBusy(UiActionGroup.BACKUP),
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
            busy = actionBusy(UiActionGroup.BACKUP),
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
            busy = actionBusy(UiActionGroup.DEVICE_LOGIN),
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
            autoLockTimeout = autoLockTimeout,
            onAutoLockTimeoutChange = { timeout ->
                autoLockTimeout = timeout
                userIdleTracker.recordActivity()
                autoLockSaveJob?.cancel()
                autoLockSaveJob =
                    uiScope.launch {
                        delay(150)
                        withContext(Dispatchers.IO) { autoLockSettings.save(timeout) }
                    }
            },
            settingsScope = settingsScope,
            onSettingsScopeChange = onSettingsScopeChange,
            configFilePath = effectiveConfigPath,
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
            breachFindings = savedPasswordBreachFindings,
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
                revealedFieldName = null
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
            revealedFieldName = revealedFieldName,
            revealedValue = revealedValue,
            showTotpCode = showTotpCode,
            totpCode = totpCode,
            totpSecondsRemaining = totpSecondsRemaining,
            onReveal = { fieldName ->
                val current = session ?: return@InspectorPanel
                val selected = selectedSecret ?: return@InspectorPanel
                clearSecretEditor()
                runAction(
                    group = UiActionGroup.VAULT,
                    isCurrent = {
                        session === current && selectedSecretId == selected.id
                    },
                    work = {
                        if (selected.type == SecretType.LOGIN_PASSWORD.name && fieldName == "password") {
                            current.revealPassword(selected.id)
                        } else {
                            current.revealField(selected.id, fieldName)
                        }
                    },
                ) { value ->
                    revealedValue = value
                    revealedFieldName = fieldName
                    revealGeneration =
                        revealLifecycle.reveal(
                            "${selected.id}:$fieldName",
                            value,
                            java.time.Instant.now(),
                        )
                    status = strings.secretRevealed
                }
            },
            onHide = {
                revealLifecycle.clear()
                revealedFieldName = null
                revealedValue = ""
            },
            onCopy = { fieldName ->
                val selected = selectedSecret ?: return@InspectorPanel
                val value =
                    when {
                        fieldName == "password" && revealedFieldName == fieldName -> revealedValue
                        else -> {
                            val field = selected.fields.firstOrNull { it.name == fieldName }
                            if (field?.secret == false) field.value.orEmpty()
                            else if (revealedFieldName == fieldName) revealedValue
                            else ""
                        }
                    }
                value.takeIf { it.isNotEmpty() }?.let {
                    clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                    status = strings.copiedToClipboard
                }
            },
            onCopyUsername = {
                selectedSecret?.username?.takeIf { it.isNotEmpty() }?.let {
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
                runAction(
                    group = UiActionGroup.VAULT,
                    isCurrent = {
                        session === current && selectedSecretId == selected.id
                    },
                    work = { current.editSnapshot(selected.id) },
                ) { snapshot ->
                    revealLifecycle.clear()
                    revealedFieldName = null
                    revealedValue = ""
                    val type = SecretType.valueOf(snapshot.type)
                    secretType = type
                    title = snapshot.title
                    username = snapshot.username
                    passwordDraft = PasswordDraftState(snapshot.password)
                    passwordBreachResult = PasswordBreachResult.NotChecked
                    passwordCheckToken = null
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
                    if (type == SecretType.LOGIN_PASSWORD && snapshot.password.isNotEmpty()) {
                        checkPasswordDraft()
                    }
                }
            },
            modifier = modifier,
        )
    }

    LaunchedEffect(serverAuthSession, serverAvailability, currentDestination) {
        if (session != null &&
            serverAuthSession != null &&
            serverAvailability.isOnline &&
            currentDestination == top.focess.keystead.client.ui.KeysteadDestination.SHARE
        ) {
            val authenticated = serverAuthSession ?: return@LaunchedEffect
            val client = authenticated.client()
            runAction(
                group = UiActionGroup.SHARE,
                serverAction = true,
                isCurrent = { serverAuthSession === authenticated },
                work = { client.listShares() },
            ) { outstandingShares = it }
        }
    }

    val sharePanel: @Composable () -> Unit = {
        SharePanel(
            busy = actionBusy(UiActionGroup.SHARE),
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
                val authenticated = serverAuthSession ?: return@SharePanel
                val client = authenticated.client()
                val expectedTitle = shareTitle
                val expectedPayload = sharePayload
                val expectedTtl = shareTtl
                val expectedBurn = shareBurn
                runAction(
                    group = UiActionGroup.SHARE,
                    serverAction = true,
                    isCurrent = { serverAuthSession === authenticated },
                    onFinally = { Wipe.wipe(passphrase) },
                    work = {
                        val minted =
                            shareExchange.mint(
                                    client,
                                    expectedTitle,
                                    expectedPayload,
                                passphrase,
                                    expectedTtl,
                                    expectedBurn,
                            )
                        minted to client.listShares()
                    },
                ) { (minted, shares) ->
                        mintedShare = minted
                        shareTitle = ""
                        sharePayload = ""
                        sharePassphrase = ""
                        outstandingShares = shares
                        status = strings.shareMinted(minted.code)
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
                val expectedServerUrl = serverUrl
                runAction(
                    group = UiActionGroup.SHARE,
                    serverAction = true,
                    isCurrent = { serverUrl == expectedServerUrl },
                    onFinally = { Wipe.wipe(passphrase) },
                    work = {
                            shareExchange.redeem(
                                KeysteadServerClient.forPublicRedeem(expectedServerUrl),
                                code,
                                passphrase,
                            )
                    },
                ) { contents ->
                        redeemedContents = contents
                        redeemCode = ""
                        redeemPassphrase = ""
                        status = strings.shareRedeemed
                }
            },
            outstandingShares = outstandingShares,
            onRefreshShares = {
                val authenticated = serverAuthSession ?: return@SharePanel
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.SHARE,
                    serverAction = true,
                    isCurrent = { serverAuthSession === authenticated },
                    work = { client.listShares() },
                ) { shares ->
                    outstandingShares = shares
                    status = strings.loadedShares(outstandingShares.size)
                }
            },
            onDeleteShare = { code ->
                val authenticated = serverAuthSession ?: return@SharePanel
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.SHARE,
                    serverAction = true,
                    isCurrent = { serverAuthSession === authenticated },
                    work = { client.deleteShare(code) },
                ) {
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
            providerId = localUnlockStorageModel.providerId,
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
            requestExpiresAt = ownVaultAccessRequest?.expiresAt,
            targetPathAvailable = restoreTargetAvailable,
            masterPassphraseReady =
                BackupFormModel.canUseNewMasterPassphrase(
                    serverRestoreNewMasterPassphrase,
                    serverRestoreNewMasterPassphraseConfirmation,
                ),
        )
    val vaultAccessApprovalContent: @Composable () -> Unit = {
        VaultAccessApprovalPanel(
            busy = actionBusy(UiActionGroup.RECOVERY),
            authenticated = serverAuthSession != null,
            serverAvailability = serverAvailability,
            vaultOpen = session != null,
            pendingAccessRequest = pendingApprovalRequest,
            onCheckServer = { serverCheckGeneration += 1 },
            onFindPendingAccessRequest = find@{
                val authenticated = serverAuthSession ?: return@find
                val client = authenticated.client()
                val ownRequestId = vaultAccessExchangeSession?.requestId
                runAction(
                    group = UiActionGroup.RECOVERY,
                    serverAction = true,
                    isCurrent = { serverAuthSession === authenticated },
                    work = {
                        VaultAccessWorkflow(client)
                            .pending()
                            .firstOrNull {
                                    it.requestId != ownRequestId
                            }
                            ?: throw IllegalStateException(strings.noPendingVaultAccessRequest)
                    },
                ) { request ->
                    pendingApprovalRequest = request
                    status = strings.pendingVaultAccessRequestLoaded
                }
            },
            onApprovePendingAccessRequest = approve@{
                val current = session ?: return@approve
                val request = pendingApprovalRequest ?: return@approve
                val authenticated = serverAuthSession ?: return@approve
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.RECOVERY,
                    serverAction = true,
                    isCurrent = {
                        session === current &&
                            serverAuthSession === authenticated &&
                            pendingApprovalRequest?.requestId == request.requestId
                    },
                    work = {
                        VaultAccessWorkflow(client).approve(request, current)
                    },
                ) {
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
            busy = actionBusy(UiActionGroup.RECOVERY),
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
                val authenticated = serverAuthSession ?: return@ServerRestorePanel
                val expectedServerUrl = serverUrl
                runAction(
                    group = UiActionGroup.RECOVERY,
                    serverAction = true,
                    isCurrent = {
                        serverAuthSession === authenticated && serverUrl == expectedServerUrl
                    },
                    onDiscard = { (exchange, _) -> exchange.close() },
                    work = { beginVaultAccessExchange(authenticated, expectedServerUrl) },
                ) { (exchange, request) ->
                    vaultAccessExchangeSession = exchange
                    ownVaultAccessRequest = request
                    status = strings.vaultAccessRequestCreated
                }
            },
            onRefreshRequest = {
                val request = ownVaultAccessRequest ?: return@ServerRestorePanel
                val authenticated = serverAuthSession ?: return@ServerRestorePanel
                val client = authenticated.client()
                runAction(
                    group = UiActionGroup.RECOVERY,
                    serverAction = true,
                    isCurrent = {
                        serverAuthSession === authenticated &&
                            ownVaultAccessRequest?.requestId == request.requestId
                    },
                    work = { VaultAccessWorkflow(client).refresh(request.requestId) },
                ) { refreshed ->
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
                val authenticated = serverAuthSession ?: return@restore
                val client = authenticated.client()
                val stateStore = syncStateStore(target)
                val newMasterPassphrase = serverRestoreNewMasterPassphrase.toCharArray()
                runAction(
                    group = UiActionGroup.RECOVERY,
                    onError = { unlockError = it },
                    serverAction = true,
                    isCurrent = {
                        serverAuthSession === authenticated &&
                            ownVaultAccessRequest?.requestId == approvedRequest.requestId
                    },
                    onDiscard = { (_, opened) -> opened.session.close() },
                    onFinally = { Wipe.wipe(newMasterPassphrase) },
                    work = {
                    val result =
                        ServerVaultProvisioningService()
                            .restore(
                                file = target,
                                request = approvedRequest,
                                exchangeSession = exchange,
                                    newMasterPassphrase = newMasterPassphrase,
                                    client = client,
                                    stateStore = stateStore,
                            )
                        val opened =
                            try {
                                OpenedVaultResult(
                                    session = result.session,
                                    rememberedPath = vaultLocationSettings.rememberSuccessfulVault(target),
                                    fingerprint = requireNotNull(approvedRequest.approvedPackage).fingerprint,
                                    snapshot = readVaultUiSnapshot(result.session),
                                )
                            } catch (error: Exception) {
                                result.session.close()
                                throw error
                            }
                        result to opened
                    },
                ) { (result, opened) ->
                    session?.close()
                    session = opened.session
                    vaultDirectory = opened.rememberedPath.toString()
                    fingerprint = opened.fingerprint
                    clearVaultAccessState()
                    selectedSecretId = null
                    revealLifecycle.clear()
                    revealedFieldName = null
                    revealedValue = ""
                    clearSecretEditor()
                    applyVaultUiSnapshot(opened.snapshot)
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
            vaultOpen = vaultLockState.exposesVault(session != null),
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
                    busy = actionBusy(UiActionGroup.VAULT),
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
                        val requestedPath = Path.of(vaultDirectory)
                        val password = masterPassword.toCharArray()
                        runAction(
                            group = UiActionGroup.VAULT,
                            onError = { unlockError = it },
                            isCurrent = { Path.of(vaultDirectory) == requestedPath },
                            onDiscard = { it.session.close() },
                            onFinally = { Wipe.wipe(password) },
                            work = {
                            val opened =
                                LocalVaultSession.openOrCreate(
                                        requestedPath,
                                        password,
                                )
                                try {
                                    OpenedVaultResult(
                                        session = opened,
                                        rememberedPath =
                                            vaultLocationSettings.rememberSuccessfulVault(requestedPath),
                                        fingerprint = opened.fingerprintValue(),
                                        snapshot = readVaultUiSnapshot(opened),
                                    )
                                } catch (error: Exception) {
                                    opened.close()
                                    throw error
                                }
                            },
                        ) { opened ->
                            session?.close()
                            session = opened.session
                            vaultDirectory = opened.rememberedPath.toString()
                            fingerprint = opened.fingerprint
                            masterPassword = ""
                            selectedSecretId = null
                            revealLifecycle.clear()
                            revealedFieldName = null
                            revealedValue = ""
                            clearSecretEditor()
                            applyVaultUiSnapshot(opened.snapshot)
                            if (localUnlockDescriptor?.persistence ==
                                    LocalLoginPersistence.BIOMETRIC
                            ) {
                                loadLocalUnlockCredential()
                            } else {
                                status = strings.vaultOpen
                            }
                        }
                    },
                    onOpenWithDeviceKey = {
                        if (vaultDirectory.isBlank()) {
                            reportUnlockError(strings.vaultFileMustNotBeBlank)
                            return@UnlockScreen
                        }
                        if (localUnlockDescriptor == null) {
                            unlockError =
                                unlockError
                                    ?: status.ifBlank { strings.deviceLoginNotConfigured }
                            return@UnlockScreen
                        }
                        unlockError = null
                        val requestedPath = Path.of(vaultDirectory)
                        runAction(
                            group = UiActionGroup.VAULT,
                            onError = { unlockError = it },
                            isCurrent = { Path.of(vaultDirectory) == requestedPath },
                            onDiscard = { it.session.close() },
                            work = {
                                localUnlockCredentialManager.useExistingOnce { credential ->
                                    val opened = LocalVaultSession.openWithLocalLogin(
                                        requestedPath,
                                        credential,
                                    )
                                    try {
                                        OpenedVaultResult(
                                            session = opened,
                                            rememberedPath =
                                                vaultLocationSettings.rememberSuccessfulVault(requestedPath),
                                            fingerprint = opened.fingerprintValue(),
                                            snapshot = readVaultUiSnapshot(opened),
                                        )
                                    } catch (error: Exception) {
                                        opened.close()
                                        throw error
                                    }
                                }
                            },
                        ) { opened ->
                                    session?.close()
                                    session = opened.session
                                    vaultDirectory = opened.rememberedPath.toString()
                                    fingerprint = opened.fingerprint
                                    masterPassword = ""
                                    selectedSecretId = null
                                    revealLifecycle.clear()
                                    revealedFieldName = null
                                    revealedValue = ""
                                    clearSecretEditor()
                                    status = strings.vaultOpen
                                    applyVaultUiSnapshot(opened.snapshot)
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
    val pendingComparison = pendingSyncComparison
    if (pendingComparison != null) {
        SyncCompareDialog(
            items = pendingComparison,
            accept = syncAccept,
            onAcceptChange = { id, value -> syncAccept[id] = value },
            onAcceptAll = {
                val all = pendingComparison
                all.forEach { syncAccept[it.secretId] = true }
                performAcceptSync(all)
            },
            onAcceptSelected = {
                performAcceptSync(pendingComparison.filter { syncAccept[it.secretId] == true })
            },
            onCancel = {
                pendingSyncComparison = null
                syncAccept.clear()
            },
            strings = strings,
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
