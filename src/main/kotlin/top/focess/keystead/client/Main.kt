package top.focess.keystead.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import java.nio.file.Path
import java.util.UUID
import kotlinx.coroutines.delay
import top.focess.keystead.memory.Wipe
import top.focess.keystead.client.ui.AddSecretPanel
import top.focess.keystead.client.ui.LifecyclePanel
import top.focess.keystead.client.ui.SecretListPanel
import top.focess.keystead.client.ui.SyncPanel
import top.focess.keystead.client.ui.collaborationStatus
import top.focess.keystead.client.ui.storageStatus
import top.focess.keystead.model.SecretType

private val defaultVaultDirectory: String =
    Path.of(System.getProperty("user.home"), ".keystead-client", "vault").toString()
private val defaultClientDirectory: Path =
    Path.of(System.getProperty("user.home"), ".keystead-client")
private const val defaultVaultId = "50000000-0000-0000-0000-000000000001"
private const val desktopStorageInstance = "keystead-desktop"

private val Ink = Color(0xFF17202A)
private val Muted = Color(0xFF6B7280)
private val Canvas = Color(0xFFF3F5F2)
private val Panel = Color(0xFFFFFFFF)
private val Rail = Color(0xFF101820)
private val Mint = Color(0xFF2CB67D)
private val Blue = Color(0xFF3D5AFE)
private val Amber = Color(0xFFE6A700)
private val Border = Color(0xFFD7DDE5)

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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
                    KeysteadClientApp()
                }
            }
        }
    }
}

@Composable
fun KeysteadClientApp() {
    var vaultDirectory by remember { mutableStateOf(defaultVaultDirectory) }
    var vaultId by remember { mutableStateOf(defaultVaultId) }
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

    fun runAction(action: () -> Unit) {
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
        } catch (error: RuntimeException) {
            status = error.message ?: error::class.simpleName.orEmpty()
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
        dialog.file = "keystead-backup.zip"
        dialog.isVisible = true
        val fileName = dialog.file ?: return
        val target = java.io.File(dialog.directory, fileName)
        runAction {
            java.io.FileOutputStream(target).use { output ->
                VaultBackup.export(Path.of(vaultDirectory), current.vaultIdValue(), output)
            }
            status = "Exported backup to ${target.name}"
        }
    }

    fun performRestoreBackup() {
        val current = session ?: return
        val owner: java.awt.Frame? = null
        val dialog = java.awt.FileDialog(owner, "Restore Keystead backup", java.awt.FileDialog.LOAD)
        dialog.file = "keystead-backup.zip"
        dialog.isVisible = true
        val fileName = dialog.file ?: return
        val source = java.io.File(dialog.directory, fileName)
        runAction {
            val report =
                java.io.FileInputStream(source).use { input ->
                    VaultBackup.restore(Path.of(vaultDirectory), current.vaultIdValue(), input)
                }
            status = BackupReportFormatter.summarize(report)
            refresh(current)
        }
    }

    fun rotationStateStore(): VaultRotationStateStore =
        VaultRotationStateStore(
            (Path.of(vaultDirectory).parent ?: Path.of(vaultDirectory))
                .resolve("rotation-$vaultId.properties"),
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

    val vaultPanel: @Composable (Modifier, Boolean) -> Unit = { modifier, compact ->
        VaultAccessPanel(
            isOpen = session != null,
            vaultDirectory = vaultDirectory,
            vaultId = vaultId,
            masterPassword = masterPassword,
            compact = compact,
            onVaultDirectoryChange = { vaultDirectory = it },
            onVaultIdChange = { vaultId = it },
            onMasterPasswordChange = { masterPassword = it },
            onOpen = {
                runAction {
                    val opened =
                        LocalVaultSession.openOrCreate(
                            Path.of(vaultDirectory),
                            UUID.fromString(vaultId),
                            masterPassword.toCharArray(),
                        )
                    session?.close()
                    session = opened
                    masterPassword = ""
                    selectedSecretId = null
                    revealLifecycle.clear()
                    revealedValue = ""
                    clearSecretEditor()
                    status = "Vault open"
                    refresh(opened)
                }
            },
            onClose = {
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
                unloadDeviceIdentity()
                status = "Vault locked"
            },
            modifier = modifier,
        )
    }
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
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                        vaultId,
                        ServerVaultMetadata.opaque(vaultId),
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
                            "Server vaults: ${vaults.joinToString { it.vaultId }}"
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
                        "Pushed $pushed records; cursor ${state.lastPushedRevision(vaultId)}"
                }
            },
            onPull = {
                val current = session ?: return@SyncPanel
                runAction {
                    val state = syncStateStore()
                    val pulled = current.pullPendingRecordsFrom(serverClient(), state)
                    conflictAssessment = null
                    status =
                        "Pulled $pulled records; cursor ${state.lastPulledRevision(vaultId)}"
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
                    status = "Provisioned vault open"
                    refresh(opened)
                }
            },
            conflictAssessment = conflictAssessment,
            onPullAndRetry = { performPullAndRetry() },
            onDismissConflict = { conflictAssessment = null },
            onExportBackup = { performExportBackup() },
            onRestoreBackup = { performRestoreBackup() },
            )
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
                        collaborationState = CollaborationViewModel(serverClient()).refresh(vaultId)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onAcceptInvitation = {
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).accept(vaultId)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onDeclineInvitation = {
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).decline(vaultId)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onInviteMember = { member, role ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).invite(vaultId, member, role)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onChangeMemberRole = { member, role ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).changeRole(vaultId, member, role)
                        status = collaborationStatus(collaborationState)
                    }
                },
                onRemoveMember = { member ->
                    runAction {
                        collaborationState = CollaborationViewModel(serverClient()).remove(vaultId, member)
                        status = "Member removed; rotate the vault key before resuming writes"
                    }
                },
                onPublishCollaborationPackages = {
                    val current = session ?: return@LifecyclePanel
                    runAction {
                        val count = CollaborativeVaultService(serverClient())
                            .publishUncoveredRecipientPackages(current)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(vaultId)
                        status = "Published $count missing member device packages"
                    }
                },
                onRotateVault = {
                    val current = session ?: return@LifecyclePanel
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    runAction {
                        val membership = VaultRotationClient(serverClient()).listMemberships()
                            .firstOrNull { it.vaultId == current.vaultIdValue() }
                            ?: throw IllegalStateException("Vault membership was not found")
                        collaborationState = CollaborationUiState.Rotating(
                            current.vaultIdValue(), 0, 0, false,
                            membership.keyLifecycleState == ServerVaultKeyLifecycleState.ROTATION_REQUIRED,
                        )
                        val rotated = VaultRotationWorkflow(serverClient(), rotationStateStore())
                            .rotate(current, identity, membership.lifecycleVersion)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(vaultId)
                        status = "Vault key rotation ${rotated.state.name.lowercase()}"
                    }
                },
                onResumeRotation = {
                    val current = session ?: return@LifecyclePanel
                    val identity = deviceIdentity ?: return@LifecyclePanel
                    runAction {
                        val rotated = VaultRotationWorkflow(serverClient(), rotationStateStore())
                            .resume(current, identity)
                        collaborationState = CollaborationViewModel(serverClient()).refresh(vaultId)
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
                        completion.recoveredVaultIds.firstOrNull()?.let { recoveredId ->
                            vaultId = recoveredId
                            vaultDirectory = recoveryRoot.resolve(recoveredId).toString()
                        }
                        status = "Account and ${completion.recoveredVaultIds.size} vaults recovered"
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
                        status = "Account recovered; ${completion.recoveredVaultIds.size} vault packages are ready"
                    }
                },
            )
        }
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
                revealedValue.takeIf { it.isNotEmpty() }?.let { clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now()) }
            },
            onToggleTotpCode = { showTotpCode = !showTotpCode },
            onCopyTotpCode = {
                totpCode.takeIf { it.isNotEmpty() }?.let { clipboardTicket = clipboardLifecycle.copy(it, java.time.Instant.now()) }
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
                }
            },
            modifier = modifier,
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Canvas)) {
        when (KeysteadWindowMetrics.modeForWidth(maxWidth.value)) {
            KeysteadLayoutMode.WIDE ->
                WideWorkspace(
                    vaultPanel = vaultPanel,
                    addPanel = addPanel,
                    syncPanel = syncPanel,
                    listPanel = listPanel,
                    inspectorPanel = inspectorPanel,
                    status = status,
                    recordCount = secrets.size,
                )
            KeysteadLayoutMode.COMPACT ->
                CompactWorkspace(
                    vaultPanel = vaultPanel,
                    addPanel = addPanel,
                    syncPanel = syncPanel,
                    listPanel = listPanel,
                    inspectorPanel = inspectorPanel,
                    status = status,
                    recordCount = secrets.size,
                )
        }
    }
    val pendingDestructive = destructiveGate.pending
    if (pendingDestructive != null) {
        AlertDialog(
            onDismissRequest = { destructiveGate.cancel() },
            title = { Text(pendingDestructive.title) },
            text = { Text(pendingDestructive.message) },
            confirmButton = {
                TextButton(onClick = {
                    when (val confirmed = destructiveGate.confirm()) {
                        is DestructiveConfirmation.DeleteSecret -> performDeleteSecret(confirmed.secretId)
                        DestructiveConfirmation.RevokeDevice -> performRevokeDevice()
                        null -> {}
                    }
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { destructiveGate.cancel() }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WideWorkspace(
    vaultPanel: @Composable (Modifier, Boolean) -> Unit,
    addPanel: @Composable () -> Unit,
    syncPanel: @Composable () -> Unit,
    listPanel: @Composable (Modifier) -> Unit,
    inspectorPanel: @Composable (Modifier) -> Unit,
    status: String,
    recordCount: Int,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        vaultPanel(Modifier.width(312.dp).fillMaxHeight(), false)
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WorkspaceHeader(status = status, recordCount = recordCount)
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1.05f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    addPanel()
                    syncPanel()
                    listPanel(Modifier.weight(1f))
                }
                inspectorPanel(Modifier.weight(0.85f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun CompactWorkspace(
    vaultPanel: @Composable (Modifier, Boolean) -> Unit,
    addPanel: @Composable () -> Unit,
    syncPanel: @Composable () -> Unit,
    listPanel: @Composable (Modifier) -> Unit,
    inspectorPanel: @Composable (Modifier) -> Unit,
    status: String,
    recordCount: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        vaultPanel(Modifier.fillMaxWidth(), true)
        WorkspaceHeader(status = status, recordCount = recordCount)
        addPanel()
        syncPanel()
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            listPanel(Modifier.weight(1f).heightIn(min = 260.dp, max = 420.dp))
            inspectorPanel(Modifier.weight(1f).heightIn(min = 260.dp))
        }
    }
}

@Composable
private fun VaultAccessPanel(
    isOpen: Boolean,
    vaultDirectory: String,
    vaultId: String,
    masterPassword: String,
    compact: Boolean,
    onVaultDirectoryChange: (String) -> Unit,
    onVaultIdChange: (String) -> Unit,
    onMasterPasswordChange: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.background(Rail).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Keystead", style = MaterialTheme.typography.h4, color = Color.White, fontWeight = FontWeight.Bold)
            Text("1 Vault access", color = Color(0xFFB7C2CC), style = MaterialTheme.typography.body2)
        }
        StatePill(if (isOpen) "open" else "locked", if (isOpen) Mint else Amber)
        Divider(color = Color(0xFF2A3642))
        FieldLabel("Vault folder")
        RailTextField(vaultDirectory, onVaultDirectoryChange, !isOpen)
        FieldLabel("Vault ID")
        RailTextField(vaultId, onVaultIdChange, !isOpen)
        FieldLabel("Master password")
        OutlinedTextField(
            value = masterPassword,
            onValueChange = onMasterPasswordChange,
            enabled = !isOpen,
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (compact) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (isOpen) {
                    OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Close vault") }
                } else {
                    Button(onClick = onOpen, enabled = masterPassword.isNotBlank(), modifier = Modifier.weight(1f)) {
                        Text("Open or create vault")
                    }
                }
                Text("Local mode", color = Color(0xFF93A1AF), style = MaterialTheme.typography.caption, modifier = Modifier.align(Alignment.CenterVertically))
            }
        } else {
            if (isOpen) {
                OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close vault") }
            } else {
                Button(onClick = onOpen, enabled = masterPassword.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Open or create vault")
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Local mode", color = Color(0xFF93A1AF), style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
private fun WorkspaceHeader(status: String, recordCount: Int) {
    Card(backgroundColor = Panel, elevation = 0.dp, border = BorderStroke(1.dp, Border)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Secrets", style = MaterialTheme.typography.h5, color = Ink, fontWeight = FontWeight.Bold)
                Text("Local vault", color = Muted)
            }
            Metric("Records", recordCount.toString(), Blue)
            Spacer(Modifier.width(12.dp))
            Metric("Status", status, Mint)
        }
    }
}

@Composable
private fun InspectorPanel(
    selectedSecret: SecretListItem?,
    revealedValue: String,
    showTotpCode: Boolean,
    totpCode: String,
    totpSecondsRemaining: Int,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
    onToggleTotpCode: () -> Unit,
    onCopyTotpCode: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    PanelCard(modifier = modifier.fillMaxHeight()) {
        SectionTitle("Selected secret")
        if (selectedSecret == null) {
            EmptyInspector()
            return@PanelCard
        }
        val type = SecretType.valueOf(selectedSecret.type)
        val revealLabel =
            if (type == SecretType.LOGIN_PASSWORD) "Password"
            else SecretFormModel.specFor(type).fields
                .first { it.name == SecretFormModel.specFor(type).revealFieldName }
                .label
        Text(selectedSecret.title, style = MaterialTheme.typography.h5, color = Ink, fontWeight = FontWeight.Bold)
        Text(typeLabel(selectedSecret.type), color = Blue, fontWeight = FontWeight.SemiBold)
        Text(selectedSecret.id, style = MaterialTheme.typography.caption, color = Muted)
        if (selectedSecret.category != null ||
            selectedSecret.provider != null ||
            selectedSecret.software != null ||
            selectedSecret.account != null
        ) {
            Text(
                listOfNotNull(
                    selectedSecret.category,
                    selectedSecret.provider,
                    selectedSecret.software,
                    selectedSecret.account,
                )
                    .joinToString(" / "),
                color = Muted,
            )
        }
        if (type == SecretType.MFA_SECRET) {
            SectionTitle("Current code")
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (showTotpCode) totpCode.ifEmpty { "…" } else "••••••",
                    style = MaterialTheme.typography.h5,
                    fontFamily = FontFamily.Monospace,
                    color = Ink,
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (showTotpCode) "Authentication code shown" else "Authentication code hidden"
                        },
                )
                Text(
                    "${totpSecondsRemaining}s",
                    color = if (showTotpCode && totpSecondsRemaining <= 5) Amber else Muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onToggleTotpCode, modifier = Modifier.weight(1f)) {
                    Text(if (showTotpCode) "Hide code" else "Show code")
                }
                OutlinedButton(
                    onClick = onCopyTotpCode,
                    enabled = showTotpCode && totpCode.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Copy code") }
            }
        }
        OutlinedTextField(
            value = revealedValue,
            onValueChange = {},
            label = { Text(revealLabel) },
            readOnly = true,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onReveal, modifier = Modifier.weight(1f)) { Text("Reveal") }
            OutlinedButton(onClick = onCopy, enabled = revealedValue.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Copy") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text("Delete") }
        }
    }
}

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = modifier, backgroundColor = Panel, elevation = 0.dp, border = BorderStroke(1.dp, Border)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}


@Composable
private fun EmptyInspector() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No secret selected", color = Ink, fontWeight = FontWeight.SemiBold)
        Text("Select a saved secret.", color = Muted)
    }
}

@Composable
private fun StatePill(value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), border = BorderStroke(1.dp, color)) {
        Text(value, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Color.White)
    }
}

@Composable
private fun Metric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = Muted, style = MaterialTheme.typography.caption)
        Text(value, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FieldLabel(value: String) {
    Text(value, color = Color(0xFFB7C2CC), style = MaterialTheme.typography.caption, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.h6, color = Ink, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun StepTitle(step: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = Blue.copy(alpha = 0.12f), border = BorderStroke(1.dp, Blue.copy(alpha = 0.35f))) {
            Text(step, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Blue, fontWeight = FontWeight.SemiBold)
        }
        SectionTitle(value)
    }
}

@Composable
private fun RailTextField(value: String, onValueChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
}

private fun typeLabel(type: String): String =
    SecretFormModel.specForOrNull(SecretType.valueOf(type))?.label ?: "Login"

private fun shortTypeLabel(type: String): String =
    when (SecretType.valueOf(type)) {
        SecretType.LOGIN_PASSWORD -> "Login"
        SecretType.SSH_KEY -> "SSH"
        SecretType.API_TOKEN -> "API"
        SecretType.GPG_KEY -> "GPG"
        SecretType.MFA_SECRET -> "MFA"
        SecretType.CERTIFICATE -> "Cert"
        SecretType.GENERIC_SECRET -> "Generic"
        SecretType.SECURE_NOTE -> "Note"
    }

