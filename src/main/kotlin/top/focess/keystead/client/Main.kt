package top.focess.keystead.client

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import kotlinx.coroutines.delay
import top.focess.keystead.memory.Wipe
import top.focess.keystead.client.ui.AddSecretPanel
import top.focess.keystead.client.ui.InspectorPanel
import top.focess.keystead.client.ui.LifecyclePanel
import top.focess.keystead.client.ui.SecretListPanel
import top.focess.keystead.client.ui.SharePanel
import top.focess.keystead.client.ui.SyncPanel
import top.focess.keystead.client.ui.collaborationStatus
import top.focess.keystead.client.ui.storageStatus
import top.focess.keystead.model.SecretType
import top.focess.keystead.share.ShareContents

private val defaultVaultDirectory: String =
    Path.of(System.getProperty("user.home"), ".keystead-client", "vault.kvault").toString()
private val defaultClientDirectory: Path =
    Path.of(System.getProperty("user.home"), ".keystead-client")
private const val desktopStorageInstance = "keystead-desktop"

fun main() = application {
    val windowState =
        rememberWindowState(
            width = (KeysteadWindowMetrics.WideBreakpointDp + 120).dp,
            height = 820.dp,
        )
    Window(onCloseRequest = ::exitApplication, title = "Keystead", state = windowState) {
        DisposableEffect(Unit) {
            window.minimumSize =
                Dimension(
                    KeysteadWindowMetrics.MinimumWidthDp,
                    KeysteadWindowMetrics.MinimumHeightDp,
                )
            onDispose {}
        }
        top.focess.keystead.client.ui.KeysteadTheme {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                KeysteadClientApp()
            }
        }
    }
}

@Composable
fun KeysteadClientApp() {
    var vaultDirectory by remember { mutableStateOf(defaultVaultDirectory) }
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
    var serverUrl by remember { mutableStateOf("http://localhost:8080") }
    var serverUsername by remember { mutableStateOf("") }
    var serverPassword by remember { mutableStateOf("") }
    var serverAuthSession by remember { mutableStateOf<ServerAuthSession?>(null) }
    var deviceId by remember { mutableStateOf("laptop-1") }
    var devicePassphrase by remember { mutableStateOf("") }
    var deviceIdentity by remember { mutableStateOf<LocalDeviceIdentity?>(null) }
    var serverDeviceState by remember { mutableStateOf<ServerDevice?>(null) }
    var revokedDeviceId by remember { mutableStateOf<String?>(null) }
    val secureStorageSettings = remember {
        SecureStorageSettings(defaultClientDirectory.resolve("secure-storage.properties"))
    }
    val secureStorageViewModel = remember { SecureStorageViewModel(secureStorageSettings) }
    var secureStorageModel by remember { mutableStateOf(secureStorageViewModel.model) }
    var collaborationState by remember { mutableStateOf<CollaborationUiState>(CollaborationUiState.Loading) }
    var recoveryKit by remember { mutableStateOf("") }
    var replacementRequest by remember { mutableStateOf<ServerRecoveryDeviceRequest?>(null) }
    var status by remember { mutableStateOf("Vault locked") }
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

    DisposableEffect(session) {
        val localVaultSession = session
        onDispose { localVaultSession?.close() }
    }
    DisposableEffect(deviceIdentity) {
        val localIdentity = deviceIdentity
        onDispose { localIdentity?.close() }
    }
    DisposableEffect(serverAuthSession) {
        val authenticatedSession = serverAuthSession
        onDispose { authenticatedSession?.close() }
    }
    DisposableEffect(secureStorageViewModel) {
        onDispose { secureStorageViewModel.close() }
    }
    fun serverSessionStore(): RefreshTokenStore? {
        val storage = secureStorageViewModel.selectedStorage() ?: return null
        if (storage.capability == SecureStorageCapability.MEMORY_ONLY) return null
        return RefreshTokenStore(storage)
    }

    fun restoreServerSession() {
        val store = serverSessionStore() ?: return
        val persisted = store.load() ?: return
        val tokenSink: (String, java.time.Instant) -> Unit = { refreshToken, expiresAt ->
            store.save(
                PersistedAuthSession(
                    persisted.baseUrl,
                    persisted.username,
                    persisted.deviceId,
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
            status = "Signed in to Keystead Server (restored)"
        } catch (error: KeysteadAuthenticationException) {
            store.clear()
            status = "Server session expired; sign in again"
        } catch (error: Exception) {
            status = "Could not restore server session: ${error.message ?: error::class.simpleName}"
        }
    }

    LaunchedEffect(Unit) {
        val persisted = secureStorageSettings.load()
        secureStorageModel =
            secureStorageViewModel.checkNative(defaultClientDirectory.resolve("secure-storage"), desktopStorageInstance)
        if (persisted?.mode == SecureStorageMode.NATIVE &&
            secureStorageModel.state == SecureStorageUiState.NATIVE_AVAILABLE
        ) {
            secureStorageViewModel.selectNative()
            secureStorageModel = secureStorageViewModel.model
        }
        restoreServerSession()
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
        if (selectedSecretId !in secrets.map { it.id }) {
            selectedSecretId = null
            revealedValue = ""
        }
    }

    fun unloadDeviceIdentity() {
        deviceIdentity?.close()
        deviceIdentity = null
    }

    fun runAction(onError: ((String) -> Unit)? = null, action: () -> Unit) {
        try {
            action()
        } catch (error: KeysteadRevisionConflictException) {
            conflictAssessment = ConflictAssessment.from(error)
            status = SyncStatusFormatter.messageFor(error)
        } catch (error: KeysteadAuthenticationException) {
            serverAuthSession?.close()
            serverAuthSession = null
            serverDeviceState = null
            unloadDeviceIdentity()
            status = error.message ?: "Server authentication failed"
            onError?.invoke(status)
        } catch (error: RuntimeException) {
            status = error.message ?: error::class.simpleName.orEmpty()
            onError?.invoke(status)
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
            status = "Deleted secret"
            refresh(current)
        }
    }

    fun performRevokeDevice() {
        val identity = deviceIdentity ?: return
        val authenticated = serverAuthSession ?: return
        val knownDevice = serverDeviceState ?: return
        try {
            runAction {
                val revoked =
                    DeviceRevocationService().revoke(authenticated, identity, knownDevice)
                serverDeviceState = null
                revokedDeviceId = revoked.deviceId
                status = "Device revoked"
            }
        } finally {
            serverAuthSession = null
            deviceIdentity = null
        }
    }

    fun serverClient(): KeysteadServerClient =
        serverAuthSession?.client()
            ?: throw IllegalStateException("Log in to Keystead Server first")

    fun loginToServer(deviceId: String?) {
        val passwordChars = serverPassword.toCharArray()
        try {
            runAction {
                val store = serverSessionStore()
                val tokenSink: ((String, java.time.Instant) -> Unit)? =
                    store?.let { s ->
                        { refreshToken, expiresAt ->
                            s.save(
                                PersistedAuthSession(
                                    serverUrl,
                                    serverUsername,
                                    deviceId,
                                    refreshToken,
                                    expiresAt,
                                ),
                            )
                        }
                    }
                val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
                val authenticated =
                    KeysteadServerAuthClient(serverUrl)
                        .login(serverUsername, passwordChars, deviceId, tokenSink, onRevoked)
                serverAuthSession?.close()
                serverAuthSession = authenticated
                status =
                    if (deviceId == null) {
                        "Signed in to Keystead Server"
                    } else {
                        "Signed in with verified device"
                    }
            }
        } finally {
            Wipe.wipe(passwordChars)
            serverPassword = ""
        }
    }

    fun identityDirectory(): Path =
        Path.of(vaultDirectory).parent?.resolve("device") ?: Path.of(vaultDirectory).resolve("device")

    fun syncStateStore(): SyncStateStore =
        SyncStateStore(
            Path.of(vaultDirectory).parent?.resolve("sync")
                ?: Path.of(vaultDirectory).resolve("sync"),
        )

    fun performPullAndRetry() {
        val current = session ?: return
        runAction {
            val state = syncStateStore()
            val pulled = current.pullPendingRecordsFrom(serverClient(), state)
            val pushed = current.pushPendingRecordsTo(serverClient(), state)
            conflictAssessment = null
            status = "Pulled $pulled and re-pushed $pushed records"
            refresh(current)
        }
    }

    fun performExportBackup() {
        val current = session ?: return
        val owner: java.awt.Frame? = null
        val dialog = java.awt.FileDialog(owner, "Export Keystead backup", java.awt.FileDialog.SAVE)
        dialog.file = "keystead-backup.json"
        dialog.isVisible = true
        val fileName = dialog.file ?: return
        val target = java.io.File(dialog.directory, fileName)
        runAction {
            java.io.FileOutputStream(target).use { output ->
                VaultBackup.export(current, output)
            }
            status = "Exported backup to ${target.name}"
        }
    }

    fun performRestoreBackup() {
        val current = session ?: return
        val owner: java.awt.Frame? = null
        val dialog = java.awt.FileDialog(owner, "Restore Keystead backup", java.awt.FileDialog.LOAD)
        dialog.file = "keystead-backup.json"
        dialog.isVisible = true
        val fileName = dialog.file ?: return
        val source = java.io.File(dialog.directory, fileName)
        runAction {
            val report =
                java.io.FileInputStream(source).use { input ->
                    VaultBackup.restore(current, input)
                }
            status = BackupReportFormatter.summarize(report)
            refresh(current)
        }
    }

    fun rotationStateStore(): VaultRotationStateStore =
        VaultRotationStateStore(
            (Path.of(vaultDirectory).parent ?: Path.of(vaultDirectory))
                .resolve("rotation-$fingerprint.properties"),
        )

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
                    status = "Generated password"
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
                    status = "Generated API token"
                }
            },
            onGenerateSshKey = {
                runAction {
                    val draft = SshKeyDraftGenerator.generate(account.ifBlank { title.ifBlank { null } })
                    software = draft.software
                    structuredFields = structuredFields + draft.fields
                    status = "Generated SSH key"
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
                    status = "Generated GPG key"
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
                    status = "Generated certificate"
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
                    status = "Generated MFA secret"
                }
            },
            onCancel = { clearSecretEditor() },
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
                        status = "Updated secret"
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
                        status = "Saved secret"
                    }
                    clearSecretEditor()
                    revealedValue = ""
                    refresh(current)
                }
            },
            editing = editingSecretId != null,
        )
    }
    val syncPanel: @Composable () -> Unit = {
        SyncPanel(
            vaultOpen = session != null,
            authenticated = serverAuthSession != null,
            serverUrl = serverUrl,
            onServerUrlChange = {
                if (it != serverUrl) {
                    serverDeviceState = null
                    revokedDeviceId = null
                }
                serverUrl = it
            },
            username = serverUsername,
            onUsernameChange = {
                if (it != serverUsername) {
                    serverDeviceState = null
                    revokedDeviceId = null
                }
                serverUsername = it
            },
            password = serverPassword,
            onPasswordChange = { serverPassword = it },
            deviceId = deviceId,
            onDeviceIdChange = {
                if (it != deviceId) {
                    deviceIdentity?.close()
                    deviceIdentity = null
                    serverDeviceState = null
                    revokedDeviceId = null
                }
                deviceId = it
            },
            devicePassphrase = devicePassphrase,
            onDevicePassphraseChange = { devicePassphrase = it },
            devicePassphraseRequired =
                secureStorageSettings.load()?.mode != SecureStorageMode.NATIVE &&
                    secureStorageSettings.load()?.mode != SecureStorageMode.MEMORY_ONLY,
            identityLoaded = deviceIdentity != null,
            identityName = deviceIdentity?.deviceId.orEmpty(),
            deviceRegistered = serverDeviceState != null,
            deviceTrustLabel =
                when {
                    revokedDeviceId == deviceId || serverDeviceState?.revokedAt != null -> "revoked"
                    serverDeviceState?.verifiedAt != null -> "verified"
                    serverDeviceState != null -> "registered, proof pending"
                    else -> "not enrolled"
                },
            onLogin = {
                loginToServer(null)
            },
            onDeviceLogin = {
                val identity = deviceIdentity ?: return@SyncPanel
                loginToServer(identity.deviceId)
            },
            onRefresh = {
                val authenticated = serverAuthSession ?: return@SyncPanel
                runAction {
                    authenticated.refresh()
                    status = "Server session refreshed"
                }
            },
            onLogout = {
                val authenticated = serverAuthSession ?: return@SyncPanel
                try {
                    runAction {
                        authenticated.revoke()
                        status = "Signed out of Keystead Server"
                    }
                } finally {
                    serverAuthSession = null
                    unloadDeviceIdentity()
                }
            },
            onLogoutAll = {
                val authenticated = serverAuthSession ?: return@SyncPanel
                try {
                    runAction {
                        authenticated.logoutAll()
                        status = "Signed out on every device"
                    }
                } finally {
                    serverAuthSession = null
                    unloadDeviceIdentity()
                }
            },
            onRegisterUser = {
                val registrationPassword = serverPassword.toCharArray()
                val loginPassword = serverPassword.toCharArray()
                try {
                    runAction {
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
                                            null,
                                            refreshToken,
                                            expiresAt,
                                        ),
                                    )
                                }
                            }
                        val onRevoked: (() -> Unit)? = store?.let { s -> { s.clear() } }
                        val authenticated =
                            authClient.login(serverUsername, loginPassword, null, tokenSink, onRevoked)
                        serverAuthSession?.close()
                        serverAuthSession = authenticated
                        serverDeviceState = null
                        revokedDeviceId = null
                        status = "Server user created and signed in"
                    }
                } finally {
                    Wipe.wipe(registrationPassword)
                    Wipe.wipe(loginPassword)
                    serverPassword = ""
                }
            },
            onCreateServerVault = {
                session ?: return@SyncPanel
                runAction {
                    serverClient().putVault(
                        fingerprint,
                        ServerVaultMetadata.opaque(fingerprint),
                    )
                    status = "Server vault ready"
                }
            },
            onListServerVaults = {
                runAction {
                    val vaults = serverClient().listVaults()
                    status =
                        if (vaults.isEmpty()) {
                            "No server vaults"
                        } else {
                            "Server vaults: ${vaults.joinToString { it.fingerprint }}"
                        }
                }
            },
            onLoadIdentity = {
                val passphraseChars = devicePassphrase.toCharArray()
                try {
                    runAction {
                        val loaded = when (secureStorageSettings.load()?.mode) {
                            SecureStorageMode.NATIVE -> {
                                val storage = secureStorageViewModel.selectedStorage()
                                    ?: throw IllegalStateException("OS secure storage is not available")
                                DeviceIdentityStore(
                                    identityDirectory(),
                                    secureStorage = storage,
                                ).createOrLoadNative(deviceId)
                            }
                            SecureStorageMode.MEMORY_ONLY ->
                                DeviceIdentityStore(identityDirectory()).createMemoryOnly(deviceId)
                            SecureStorageMode.PASSPHRASE_FILE, null ->
                                DeviceIdentityStore(identityDirectory())
                                    .createOrLoad(deviceId, passphraseChars)
                        }
                        deviceIdentity?.close()
                        deviceIdentity = loaded
                        serverDeviceState = null
                        status = "Device identity ready"
                    }
                } finally {
                    Wipe.wipe(passphraseChars)
                    devicePassphrase = ""
                }
            },
            onUnloadIdentity = {
                unloadDeviceIdentity()
                status = "Device identity locked"
            },
            onEnrollDevice = {
                val identity = deviceIdentity ?: return@SyncPanel
                runAction {
                    val client = serverClient()
                    val enrolled = DeviceEnrollmentService().enroll(client, identity)
                    serverDeviceState = enrolled
                    revokedDeviceId = null
                    val current = session
                    if (current == null) {
                        status = "Device verified"
                    } else {
                        current.publishVaultKeyPackage(client, identity, enrolled)
                        status = "Device verified and vault key package published"
                    }
                }
            },
            onRevokeDevice = {
                if (deviceIdentity == null || serverAuthSession == null || serverDeviceState == null) {
                    return@SyncPanel
                }
                destructiveGate.request(DestructiveConfirmation.RevokeDevice)
            },
            onPublishKeyPackage = {
                val current = session ?: return@SyncPanel
                runAction {
                    val published = current.publishVaultKeyPackagesForRegisteredDevices(serverClient())
                    status = "Published $published vault key packages"
                }
            },
            onPush = {
                val current = session ?: return@SyncPanel
                runAction {
                    val state = syncStateStore()
                    val pushed = current.pushPendingRecordsTo(serverClient(), state)
                    conflictAssessment = null
                    status =
                        "Pushed $pushed records; cursor ${state.lastPushedRevision(fingerprint)}"
                }
            },
            onPull = {
                val current = session ?: return@SyncPanel
                runAction {
                    val state = syncStateStore()
                    val pulled = current.pullPendingRecordsFrom(serverClient(), state)
                    conflictAssessment = null
                    status =
                        "Pulled $pulled records; cursor ${state.lastPulledRevision(fingerprint)}"
                    refresh(current)
                }
            },
            onOpenProvisioned = {
                val identity = deviceIdentity ?: return@SyncPanel
                runAction {
                    val opened =
                        LocalVaultSession.openFirstProvisionedFromServer(
                            Path.of(vaultDirectory),
                            identity,
                            serverClient(),
                        )
                    session?.close()
                    session = opened
                    fingerprint = opened.fingerprintValue()
                    status = "Provisioned vault open"
                    refresh(opened)
                }
            },
            conflictAssessment = conflictAssessment,
            onPullAndRetry = { performPullAndRetry() },
            onDismissConflict = {
                conflictAssessment = null
                status = "Conflict dismissed"
            },
            onExportBackup = { performExportBackup() },
            onRestoreBackup = { performRestoreBackup() },
            )
    }
    val protectionPanel: @Composable () -> Unit = {
        LifecyclePanel(
                authenticated = serverAuthSession != null,
                vaultOpen = session != null,
                identityLoaded = deviceIdentity != null,
                secureStorage = secureStorageModel,
                collaboration = collaborationState,
                recoveryKit = recoveryKit,
                replacementRequest = replacementRequest,
                onCheckNativeStorage = {
                    runAction {
                        secureStorageModel = secureStorageViewModel.checkNative(
                            defaultClientDirectory.resolve("secure-storage"),
                            desktopStorageInstance,
                        )
                        status = storageStatus(secureStorageModel)
                    }
                },
                onSelectNativeStorage = {
                    runAction {
                        secureStorageViewModel.selectNative()
                        secureStorageModel = secureStorageViewModel.model
                        status = "OS-user-protected storage selected"
                    }
                },
                onSelectPassphraseStorage = {
                    secureStorageModel = secureStorageViewModel.selectPassphrase()
                    status = "Passphrase-protected device storage selected"
                },
                onSelectMemoryStorage = {
                    secureStorageModel = secureStorageViewModel.selectMemory()
                    status = "Memory-only device identity selected; it will not survive restart"
                },
                onMigrateIdentity = {
                    val passphraseChars = devicePassphrase.toCharArray()
                    try {
                        runAction {
                            unloadDeviceIdentity()
                            secureStorageViewModel.migrateIdentity { storage ->
                                DeviceIdentityStore(
                                    identityDirectory(),
                                    secureStorage = storage,
                                ).migrateToNative(deviceId, passphraseChars)
                            }
                            secureStorageModel = secureStorageViewModel.model
                            deviceIdentity = DeviceIdentityStore(
                                identityDirectory(),
                                secureStorage = secureStorageViewModel.selectedStorage(),
                            ).createOrLoadNative(deviceId)
                            status = "Device identity moved to OS-user-protected storage"
                        }
                    } finally {
                        Wipe.wipe(passphraseChars)
                        devicePassphrase = ""
                    }
                },
                onRefreshCollaboration = {
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).refresh(fingerprint)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onAcceptInvitation = {
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).accept(fingerprint)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onDeclineInvitation = {
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).decline(fingerprint)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onInviteMember = { member, role ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).invite(fingerprint, member, role)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onChangeMemberRole = { member, role ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).changeRole(fingerprint, member, role)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onRemoveMember = { member ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).remove(fingerprint, member)
                        status = "Member removed; rotate the vault key before resuming writes"
                    }
                },
                onPublishCollaborationPackages = {
                    val current = session ?: return@LifecyclePanel
                    runAction {
                        val count = CollaborativeVaultService(serverClient())
                            .publishUncoveredRecipientPackages(current)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(fingerprint)
                        status = "Published $count missing member device packages"
                    }
                },
                onRotateVault = {
                    val current = session ?: return@LifecyclePanel
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    runAction {
                        val membership = VaultRotationClient(serverClient()).listMemberships()
                            .firstOrNull { it.fingerprint == current.fingerprintValue() }
                            ?: throw IllegalStateException("Vault membership was not found")
                        collaborationState = CollaborationUiState.Rotating(
                            current.fingerprintValue(), 0, 0, false,
                            membership.keyLifecycleState == ServerVaultKeyLifecycleState.ROTATION_REQUIRED,
                        )
                        val rotated = VaultRotationWorkflow(serverClient(), rotationStateStore())
                            .rotate(current, identity, membership.lifecycleVersion)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(fingerprint)
                        status = "Vault key rotation ${rotated.state.name.lowercase()}"
                    }
                },
                onResumeRotation = {
                    val current = session ?: return@LifecyclePanel
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    runAction {
                        val rotated = VaultRotationWorkflow(serverClient(), rotationStateStore())
                            .resume(current, identity)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(fingerprint)
                        status = "Vault key rotation ${rotated.state.name.lowercase()}"
                    }
                },
                onEnrollRecoveryKit = {
                    val current = session ?: return@LifecyclePanel
                    runAction {
                        val result = RecoveryEnrollmentWorkflow(serverClient()).enroll(
                            serverUsername,
                            current,
                            java.time.Instant.now().toEpochMilli(),
                        )
                        recoveryKit = result.recoveryKit
                        status = "Recovery kit created; store the one-time kit offline"
                    }
                },
                onCopyRecoveryKit = {
                    recoveryKit.takeIf(String::isNotBlank)?.let {
                        clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                        status = "Recovery kit copied temporarily"
                    }
                },
                onOfflineRecover = { encodedKit, replacementPassword ->
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    val passwordChars = replacementPassword.toCharArray()
                    runAction {
                        session?.close()
                        session = null
                        val recoveryRoot = defaultClientDirectory.resolve("recovered-vaults")
                        val completion = OfflineRecoveryWorkflow(
                            KeysteadServerClient(serverUrl, "recovery", "recovery"),
                        ).recover(serverUsername, encodedKit, passwordChars, identity, recoveryRoot)
                        completion.recoveredVaultFingerprints.firstOrNull()?.let { recoveredId ->
                            fingerprint = recoveredId
                            vaultDirectory = recoveryRoot.resolve("$recoveredId.kvault").toString()
                        }
                        status = "Account and ${completion.recoveredVaultFingerprints.size} vaults recovered"
                    }
                },
                onRequestVerifiedDeviceRecovery = {
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    runAction {
                        replacementRequest = VerifiedDeviceRecoveryWorkflow(
                            KeysteadServerClient(serverUrl, "recovery", "recovery"),
                        ).request(serverUsername, identity)
                        status = "Verified-device recovery request created"
                    }
                },
                onApproveVerifiedDeviceRecovery = {
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    val current = session ?: return@LifecyclePanel
                    runAction {
                        val workflow = VerifiedDeviceRecoveryWorkflow(serverClient())
                        val request = replacementRequest
                            ?: RecoveryClient(serverClient()).listDeviceRecoveryRequests()
                                .firstOrNull { it.state == ServerRecoveryDeviceRequestState.PENDING }
                            ?: throw IllegalStateException("No pending device recovery request")
                        workflow.approve(request, identity, listOf(current))
                        status = "Replacement device recovery approved"
                    }
                },
                onCompleteVerifiedDeviceRecovery = { replacementPassword ->
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    val request = replacementRequest ?: return@LifecyclePanel
                    val passwordChars = replacementPassword.toCharArray()
                    runAction {
                        val completion = VerifiedDeviceRecoveryWorkflow(
                            KeysteadServerClient(serverUrl, "recovery", "recovery"),
                        ).complete(request, identity, passwordChars)
                        status = "Account recovered; ${completion.recoveredVaultFingerprints.size} vault packages are ready"
                    }
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
                status = "Filters cleared"
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
                    status = "Secret revealed"
                }
            },
            onCopy = {
                revealedValue.takeIf { it.isNotEmpty() }?.let {
                    clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                    status = "Copied to clipboard"
                }
            },
            onToggleTotpCode = {
                showTotpCode = !showTotpCode
                status = if (showTotpCode) "Authentication code shown" else "Authentication code hidden"
            },
            onCopyTotpCode = {
                totpCode.takeIf { it.isNotEmpty() }?.let {
                    clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now())
                    status = "Copied code to clipboard"
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
                    status = "Loaded secret for edit"
                    currentDestination = top.focess.keystead.client.ui.KeysteadDestination.ADD
                }
            },
            modifier = modifier,
        )
    }

    LaunchedEffect(serverAuthSession, currentDestination) {
        if (serverAuthSession != null &&
            currentDestination == top.focess.keystead.client.ui.KeysteadDestination.SHARE
        ) {
            runAction { outstandingShares = serverClient().listShares() }
        }
    }

    val sharePanel: @Composable () -> Unit = {
        SharePanel(
            authenticated = serverAuthSession != null,
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
                status = "Copied share code to clipboard (clears in 30s)"
            },
            onMint = {
                val passphrase = sharePassphrase.toCharArray()
                try {
                    runAction {
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
                        status = "Share minted: ${minted.code}"
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
                    runAction {
                        val contents =
                            shareExchange.redeem(
                                KeysteadServerClient.forPublicRedeem(serverUrl),
                                code,
                                passphrase,
                            )
                        redeemedContents = contents
                        redeemCode = ""
                        redeemPassphrase = ""
                        status = "Share redeemed"
                    }
                } finally {
                    Wipe.wipe(passphrase)
                }
            },
            outstandingShares = outstandingShares,
            onRefreshShares = {
                runAction {
                    outstandingShares = serverClient().listShares()
                    status = "Loaded ${outstandingShares.size} share(s)"
                }
            },
            onDeleteShare = { code ->
                runAction {
                    serverClient().deleteShare(code)
                    outstandingShares = outstandingShares.filterNot { it.code == code }
                    status = "Deleted share $code"
                }
            },
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutMode = KeysteadWindowMetrics.modeForWidth(maxWidth.value)
        top.focess.keystead.client.ui.KeysteadAppShell(
            vaultOpen = session != null,
            destination = currentDestination,
            onDestinationChange = {
                currentDestination = it
                if (it != top.focess.keystead.client.ui.KeysteadDestination.SECRETS) inspectorSheetOpen = false
            },
            status = status,
            layoutMode = layoutMode,
            inspectorSheetVisible = inspectorSheetOpen && selectedSecret != null,
            onDismissInspectorSheet = { inspectorSheetOpen = false },
            onLockVault = {
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
                shareTitle = ""
                sharePayload = ""
                sharePassphrase = ""
                redeemCode = ""
                redeemPassphrase = ""
                redeemedContents = null
                mintedShare = null
                outstandingShares = emptyList()
                unloadDeviceIdentity()
                currentDestination = top.focess.keystead.client.ui.KeysteadDestination.SECRETS
                inspectorSheetOpen = false
                status = "Vault locked"
                unlockError = null
            },
            secretsContent = { modifier -> listPanel(modifier) },
            inspectorContent = { modifier -> inspectorPanel(modifier) },
            addContent = { addPanel() },
            syncContent = { syncPanel() },
            protectionContent = { protectionPanel() },
            shareContent = { sharePanel() },
            unlockContent = {
                top.focess.keystead.client.ui.UnlockScreen(
                    vaultDirectory = vaultDirectory,
                    masterPassword = masterPassword,
                    errorMessage = unlockError,
                    onVaultDirectoryChange = {
                        vaultDirectory = it
                        unlockError = null
                    },
                    onMasterPasswordChange = {
                        masterPassword = it
                        unlockError = null
                    },
                    onOpen = open@{
                        if (vaultDirectory.isBlank()) {
                            unlockError = "Vault file must not be blank"
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
                            fingerprint = opened.fingerprintValue()
                            masterPassword = ""
                            selectedSecretId = null
                            revealLifecycle.clear()
                            revealedValue = ""
                            clearSecretEditor()
                            status = "Vault open"
                            refresh(opened)
                        }
                    },
                )
            },
        )
    }
    val pendingDestructive = destructiveGate.pending
    if (pendingDestructive != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { destructiveGate.cancel() },
            title = { androidx.compose.material3.Text(pendingDestructive.title) },
            text = { androidx.compose.material3.Text(pendingDestructive.message) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    when (val confirmed = destructiveGate.confirm()) {
                        is DestructiveConfirmation.DeleteSecret -> performDeleteSecret(confirmed.secretId)
                        DestructiveConfirmation.RevokeDevice -> performRevokeDevice()
                        null -> {}
                    }
                }) { androidx.compose.material3.Text("Confirm") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { destructiveGate.cancel() }) {
                    androidx.compose.material3.Text("Cancel")
                }
            },
        )
    }
}

