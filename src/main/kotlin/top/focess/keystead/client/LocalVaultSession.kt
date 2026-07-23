package top.focess.keystead.client

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.SecretClassification
import top.focess.keystead.model.SecretId
import top.focess.keystead.model.SecretType
import top.focess.keystead.model.VaultId
import top.focess.keystead.model.KeyId
import top.focess.keystead.service.CreateVaultRequest
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.DeviceVaultKeyPackage
import top.focess.keystead.service.PreparedVaultKeyRotation
import top.focess.keystead.service.VaultHandle
import top.focess.keystead.recovery.RecoveryCryptoService
import top.focess.keystead.recovery.RecoveryPublicKey
import top.focess.keystead.recovery.RecoveryVaultKeyPackage
import top.focess.keystead.store.FileVaultStore

data class LoginListItem(
    val id: String,
    val title: String,
)

data class SecretListItem(
    val id: String,
    val title: String,
    val type: String,
    val category: String?,
    val provider: String?,
    val software: String?,
    val account: String?,
    val expiry: String? = null,
)

data class SecretEditSnapshot(
    val id: String,
    val type: String,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val category: String? = null,
    val provider: String? = null,
    val software: String? = null,
    val account: String? = null,
    val expiry: String? = null,
    val fields: Map<String, String> = emptyMap(),
) {
    override fun toString(): String = "SecretEditSnapshot(<redacted>)"
}

class LocalVaultSession private constructor(
    private val service: DefaultVaultService,
    private var handle: VaultHandle,
) : AutoCloseable {
    private val syncPageLimit = 100

    internal fun vaultIdValue(): String = handle.vaultId().value().toString()
    internal fun vaultKeyIdValue(): String = handle.vaultKeyId().value()
    internal fun prepareVaultKeyRotation(): PreparedVaultKeyRotation = handle.prepareVaultKeyRotation()
    internal fun wrapCurrentVaultKey(publicKey: ByteArray, context: ByteArray): DeviceVaultKeyPackage =
        handle.wrapVaultKeyPackageForDevice(publicKey, context)
    internal fun wrapRecoveryVaultKey(
        recovery: RecoveryCryptoService,
        recoveryKey: RecoveryPublicKey,
        username: String,
    ): RecoveryVaultKeyPackage =
        recovery.wrapVaultKey(handle, recoveryKey, username, vaultIdValue())

    internal fun resumeVaultKeyRotation(
        keyPackage: ServerRotationPackage,
        identity: LocalDeviceIdentity,
    ): PreparedVaultKeyRotation {
        val encrypted = Base64.getDecoder().decode(keyPackage.encryptedVaultKey)
        val privateKey = identity.privateKey()
        val context = vaultKeyPackageContext(vaultIdValue(), identity.deviceId)
        val packageAlgorithm =
            if (keyPackage.keyAlgorithm == identity.keyAlgorithm) {
                DefaultVaultService.DEVICE_KEY_PACKAGE_ALGORITHM
            } else {
                keyPackage.keyAlgorithm
            }
        return try {
            handle.resumeVaultKeyRotation(
                DeviceVaultKeyPackage(KeyId(keyPackage.vaultKeyId), packageAlgorithm, encrypted),
                privateKey,
                context,
            )
        } finally {
            Wipe.wipe(encrypted)
            Wipe.wipe(privateKey)
            Wipe.wipe(context)
        }
    }

    internal fun commitPreparedRotation(
        prepared: PreparedVaultKeyRotation,
        keyPackage: DeviceVaultKeyPackage,
    ) {
        val previous = handle
        handle = prepared.commitWithDevicePackage(keyPackage)
        previous.close()
    }

    fun addLogin(
        title: String,
        username: String,
        password: String,
        url: String?,
        category: String? = null,
        provider: String? = null,
        software: String? = null,
        account: String? = null,
        expiry: String? = null,
    ): String {
        SecretBuffer.fromChars(username.toCharArray()).use { usernameBuffer ->
            SecretBuffer.fromChars(password.toCharArray()).use { passwordBuffer ->
                val secretId =
                    handle.saveLogin { draft ->
                        draft.title(title)
                            .classification(SecretClassification(category, provider, software, account))
                            .username(usernameBuffer)
                            .password(passwordBuffer)
                        if (!url.isNullOrBlank()) {
                            draft.url(url)
                        }
                        if (!expiry.isNullOrBlank()) {
                            draft.attribute("expiry", expiry)
                        }
                    }
                return secretId.value().toString()
            }
        }
    }

    fun updateLogin(
        secretId: String,
        title: String,
        username: String,
        password: String,
        url: String?,
        category: String? = null,
        provider: String? = null,
        software: String? = null,
        account: String? = null,
        expiry: String? = null,
    ) {
        SecretBuffer.fromChars(username.toCharArray()).use { usernameBuffer ->
            SecretBuffer.fromChars(password.toCharArray()).use { passwordBuffer ->
                handle.updateLogin(SecretId(UUID.fromString(secretId))) { draft ->
                    draft.title(title)
                        .classification(SecretClassification(category, provider, software, account))
                        .username(usernameBuffer)
                        .password(passwordBuffer)
                    if (!url.isNullOrBlank()) {
                        draft.url(url)
                    }
                    if (!expiry.isNullOrBlank()) {
                        draft.attribute("expiry", expiry)
                    }
                }
            }
        }
    }

    fun listLogins(): List<LoginListItem> =
        handle.listSecrets()
            .filter { it.type() == SecretType.LOGIN_PASSWORD }
            .map { LoginListItem(it.id().value().toString(), it.title()) }

    fun addStructuredSecret(
        type: SecretType,
        title: String,
        fields: Map<String, String>,
        category: String? = null,
        provider: String? = null,
        software: String? = null,
        account: String? = null,
        expiry: String? = null,
    ): String {
        val buffers = fields.mapValues { SecretBuffer.fromChars(it.value.toCharArray()) }
        return try {
            val secretId =
                handle.saveSecret(type) { draft ->
                    draft.title(title)
                        .classification(SecretClassification(category, provider, software, account))
                    if (!expiry.isNullOrBlank()) {
                        draft.attribute("expiry", expiry)
                    }
                    buffers.forEach { (name, buffer) -> draft.field(name, buffer) }
                }
            secretId.value().toString()
        } finally {
            buffers.values.forEach { it.close() }
        }
    }

    fun updateStructuredSecret(
        secretId: String,
        title: String,
        fields: Map<String, String>,
        category: String? = null,
        provider: String? = null,
        software: String? = null,
        account: String? = null,
        expiry: String? = null,
    ) {
        val buffers = fields.mapValues { SecretBuffer.fromChars(it.value.toCharArray()) }
        return try {
            handle.updateSecret(SecretId(UUID.fromString(secretId))) { draft ->
                draft.title(title)
                    .classification(SecretClassification(category, provider, software, account))
                if (!expiry.isNullOrBlank()) {
                    draft.attribute("expiry", expiry)
                }
                buffers.forEach { (name, buffer) -> draft.field(name, buffer) }
            }
        } finally {
            buffers.values.forEach { it.close() }
        }
    }

    fun listSecrets(): List<SecretListItem> =
        handle.listSecrets()
            .map {
                SecretListItem(
                    id = it.id().value().toString(),
                    title = it.title(),
                    type = it.type().name,
                    category = it.classification().category(),
                    provider = it.classification().provider(),
                    software = it.classification().software(),
                    account = it.classification().account(),
                    expiry = it.profile().attributes()["expiry"],
                )
            }

    fun revealField(secretId: String, fieldName: String): String {
        val output = arrayOfNulls<CharArray>(1)
        handle.withSecret(SecretId(UUID.fromString(secretId))) { view ->
            view.withField(fieldName) { value -> output[0] = value.copyOf() }
        }
        val chars = output[0] ?: CharArray(0)
        return try {
            String(chars)
        } finally {
            Wipe.wipe(chars)
        }
    }

    fun revealPassword(secretId: String): String {
        val output = arrayOfNulls<CharArray>(1)
        handle.withLogin(SecretId(UUID.fromString(secretId))) { view ->
            view.withPassword { password -> output[0] = password.copyOf() }
        }
        val chars = output[0] ?: CharArray(0)
        return try {
            String(chars)
        } finally {
            Wipe.wipe(chars)
        }
    }

    fun editSnapshot(secretId: String): SecretEditSnapshot {
        val metadata = handle.listSecrets().first { it.id().value().toString() == secretId }
        return if (metadata.type() == SecretType.LOGIN_PASSWORD) {
            loginEditSnapshot(secretId)
        } else {
            structuredEditSnapshot(secretId)
        }
    }

    fun delete(secretId: String) {
        handle.deleteSecret(SecretId(UUID.fromString(secretId)))
    }

    fun pushRecordsTo(client: KeysteadServerClient, sinceRevision: Long): Int {
        val records = handle.exportRecordsSince(sinceRevision)
        pushRecords(client, records)
        return records.size
    }

    fun pushPendingRecordsTo(client: KeysteadServerClient, stateStore: SyncStateStore): Int {
        val vaultId = handle.vaultId().value().toString()
        val records = handle.exportRecordsSince(stateStore.lastPushedRevision(vaultId))
        pushRecords(client, records)
        records.maxOfOrNull { it.revision() }?.let { stateStore.recordPushed(vaultId, it) }
        return records.size
    }

    private fun pushRecords(client: KeysteadServerClient, records: List<EncryptedSyncRecord>) {
        records.forEach { record ->
            if (record.deleted()) {
                client.deleteRecord(record.vaultId(), record.secretId(), record.revision())
            } else {
                client.putRecord(
                    record.vaultId(),
                    record.secretId(),
                    ServerEncryptedRecord(
                        revision = record.revision(),
                        secretType = record.secretType(),
                        encryptedProfile = record.encryptedProfile(),
                        envelope = record.envelope(),
                        deleted = false,
                    ),
                )
            }
        }
    }

    fun pullRecordsFrom(client: KeysteadServerClient, sinceRevision: Long): Int {
        return pullRecordPagesFrom(client, sinceRevision).imported
    }

    fun pullPendingRecordsFrom(client: KeysteadServerClient, stateStore: SyncStateStore): Int {
        val vaultId = handle.vaultId().value().toString()
        val result = pullRecordPagesFrom(client, stateStore.lastPulledRevision(vaultId))
        stateStore.recordPulled(vaultId, result.highestRevision)
        return result.imported
    }

    private fun pullRecordPagesFrom(client: KeysteadServerClient, sinceRevision: Long): PullResult {
        val vaultId = handle.vaultId().value().toString()
        var cursor = sinceRevision
        var highestRevision = sinceRevision
        var imported = 0
        do {
            val page = client.listRecordPage(vaultId, cursor, syncPageLimit)
            imported += importServerRecords(page.records)
            highestRevision = maxOf(highestRevision, page.highestRevision)
            val nextCursor = page.nextSinceRevision ?: page.highestRevision
            if (page.hasMore && nextCursor <= cursor) {
                throw IllegalStateException(
                    "Server record page did not advance cursor for vault $vaultId from revision $cursor",
                )
            }
            cursor = nextCursor
        } while (page.hasMore)
        return PullResult(imported, highestRevision)
    }

    private fun importServerRecords(records: List<ServerEncryptedRecord>): Int {
        val vaultId = handle.vaultId().value().toString()
        return handle.importRecords(
            records.map { record ->
                EncryptedSyncRecord(
                    vaultId,
                    requireNotNull(record.secretId) { "Server record is missing secretId" },
                    record.revision,
                    record.secretType,
                    record.encryptedProfile,
                    record.envelope,
                    record.deleted,
                )
            },
        )
    }

    private data class PullResult(
        val imported: Int,
        val highestRevision: Long,
    )

    private fun loginEditSnapshot(secretId: String): SecretEditSnapshot {
        var title = ""
        var username = ""
        var password = ""
        var url = ""
        var category: String? = null
        var provider: String? = null
        var software: String? = null
        var account: String? = null
        var expiry: String? = null
        handle.withLogin(SecretId(UUID.fromString(secretId))) { view ->
            val metadata = view.metadata()
            title = metadata.title()
            category = metadata.classification().category()
            provider = metadata.classification().provider()
            software = metadata.classification().software()
            account = metadata.classification().account()
            expiry = metadata.profile().attributes()["expiry"]
            url = view.url().orElse("")
            view.withUsername { username = String(it) }
            view.withPassword { password = String(it) }
        }
        return SecretEditSnapshot(
            id = secretId,
            type = SecretType.LOGIN_PASSWORD.name,
            title = title,
            username = username,
            password = password,
            url = url,
            category = category,
            provider = provider,
            software = software,
            account = account,
            expiry = expiry,
        )
    }

    private fun structuredEditSnapshot(secretId: String): SecretEditSnapshot {
        var title = ""
        var type = ""
        var category: String? = null
        var provider: String? = null
        var software: String? = null
        var account: String? = null
        var expiry: String? = null
        var fields = emptyMap<String, String>()
        handle.withSecret(SecretId(UUID.fromString(secretId))) { view ->
            val metadata = view.metadata()
            title = metadata.title()
            type = metadata.type().name
            category = metadata.classification().category()
            provider = metadata.classification().provider()
            software = metadata.classification().software()
            account = metadata.classification().account()
            expiry = metadata.profile().attributes()["expiry"]
            fields =
                view.fieldNames().associateWith { name ->
                    var value = ""
                    view.withField(name) { chars -> value = String(chars) }
                    value
                }
        }
        return SecretEditSnapshot(
            id = secretId,
            type = type,
            title = title,
            category = category,
            provider = provider,
            software = software,
            account = account,
            expiry = expiry,
            fields = fields,
        )
    }

    private fun publishVaultKeyPackage(
        client: KeysteadServerClient,
        deviceId: String,
        keyAlgorithm: String,
        devicePublicKey: ByteArray,
    ): ServerVaultKeyPackage {
        require(keyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
            "Unsupported device wrapping key algorithm"
        }
        val vaultId = handle.vaultId().value().toString()
        val context = vaultKeyPackageContext(vaultId, deviceId)
        val wrapped = handle.wrapVaultKeyPackageForDevice(devicePublicKey, context)
        val encryptedVaultKey = wrapped.encryptedVaultKey()
        return try {
            val keyPackage =
                ServerVaultKeyPackage(
                    vaultId = vaultId,
                    deviceId = deviceId,
                    vaultKeyId = wrapped.vaultKeyId().value(),
                    keyAlgorithm = wrapped.keyAlgorithm(),
                    encryptedVaultKey = Base64.getEncoder().encodeToString(encryptedVaultKey),
                )
            client.putVaultKeyPackage(vaultId, deviceId, keyPackage)
            keyPackage
        } finally {
            Wipe.wipe(encryptedVaultKey)
        }
    }

    internal fun publishVaultKeyPackage(
        client: KeysteadServerClient,
        identity: LocalDeviceIdentity,
        enrolledDevice: ServerDevice,
    ): ServerVaultKeyPackage {
        val proofPublicKey = identity.proofPublicKey()
        val wrappingPublicKey = identity.publicKey()
        return try {
            check(enrolledDevice.deviceId == identity.deviceId) {
                "Enrolled device does not match the local identity"
            }
            check(enrolledDevice.keyAlgorithm == identity.proofKeyAlgorithm)
            check(
                enrolledDevice.publicKey ==
                    Base64.getEncoder().encodeToString(proofPublicKey),
            )
            check(enrolledDevice.wrappingKeyAlgorithm == identity.keyAlgorithm)
            check(
                enrolledDevice.wrappingPublicKey ==
                    Base64.getEncoder().encodeToString(wrappingPublicKey),
            )
            publishVaultKeyPackage(client, enrolledDevice)
        } finally {
            Wipe.wipe(proofPublicKey)
            Wipe.wipe(wrappingPublicKey)
        }
    }

    internal fun publishVaultKeyPackage(
        client: KeysteadServerClient,
        enrolledDevice: ServerDevice,
    ): ServerVaultKeyPackage {
        check(enrolledDevice.canReceiveVaultKeyPackage) {
            "Device is not eligible for a vault key package"
        }
        check(enrolledDevice.wrappingKeyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM) {
            "Unsupported device wrapping key algorithm"
        }
        val publicKey =
            Base64.getDecoder().decode(requireNotNull(enrolledDevice.wrappingPublicKey))
        return try {
            publishVaultKeyPackage(
                client = client,
                deviceId = enrolledDevice.deviceId,
                keyAlgorithm = requireNotNull(enrolledDevice.wrappingKeyAlgorithm),
                devicePublicKey = publicKey,
            )
        } finally {
            Wipe.wipe(publicKey)
        }
    }

    fun publishVaultKeyPackagesForRegisteredDevices(client: KeysteadServerClient): Int {
        var published = 0
        client.listDevices()
            .filter {
                it.canReceiveVaultKeyPackage &&
                    it.wrappingKeyAlgorithm == DefaultCryptoService.DEVICE_KEY_ALGORITHM
            }
            .forEach { device ->
                publishVaultKeyPackage(client, device)
                published++
            }
        return published
    }

    fun rotateVaultKey(masterPassword: CharArray): String {
        val password = masterPassword.copyOf()
        return try {
            val replacement = service.rotateVaultKey(handle.vaultId(), password)
            val previous = handle
            handle = replacement
            previous.close()
            replacement.vaultKeyId().value()
        } finally {
            Wipe.wipe(password)
            Wipe.wipe(masterPassword)
        }
    }

    fun rotateVaultKeyAndPublish(
        client: KeysteadServerClient,
        masterPassword: CharArray,
    ): String {
        val vaultKeyId = rotateVaultKey(masterPassword)
        client.rotateVaultKey(handle.vaultId().value().toString(), vaultKeyId)
        publishVaultKeyPackagesForRegisteredDevices(client)
        return vaultKeyId
    }

    fun publishAutomationVaultKeyPackage(
        client: KeysteadServerClient,
        principalId: String,
        publicKey: ByteArray,
    ): ServerAutomationVaultKeyPackage {
        val vaultId = handle.vaultId().value().toString()
        val context = automationVaultKeyPackageContext(vaultId, principalId)
        val wrapped = handle.wrapVaultKeyPackageForDevice(publicKey, context)
        val encryptedVaultKey = wrapped.encryptedVaultKey()
        return try {
            ServerAutomationVaultKeyPackage(
                vaultKeyId = wrapped.vaultKeyId().value(),
                keyAlgorithm = wrapped.keyAlgorithm(),
                encryptedVaultKey = Base64.getEncoder().encodeToString(encryptedVaultKey),
            ).also { client.putAutomationVaultKeyPackage(vaultId, principalId, it) }
        } finally {
            Wipe.wipe(encryptedVaultKey)
            Wipe.wipe(context)
        }
    }

    override fun close() {
        handle.close()
    }

    companion object {
        fun vaultKeyPackageContext(vaultId: String, deviceId: String): ByteArray =
            "keystead-vault-key-package-v1|vault:$vaultId|device:$deviceId"
                .toByteArray(StandardCharsets.UTF_8)

        fun automationVaultKeyPackageContext(vaultId: String, principalId: String): ByteArray =
            "keystead-automation-vault-key-package-v1|vault:$vaultId|principal:$principalId"
                .toByteArray(StandardCharsets.UTF_8)

        fun openOrCreate(
            directory: Path,
            vaultId: UUID,
            masterPassword: CharArray,
        ): LocalVaultSession {
            Files.createDirectories(directory)
            val service = DefaultVaultService(FileVaultStore(directory))
            val id = VaultId(vaultId)
            val password = masterPassword.copyOf()
            return try {
                val handle =
                    if (Files.exists(directory.resolve("vault.properties"))) {
                        service.openVault(id, password)
                    } else {
                        service.createVault(CreateVaultRequest(id), password)
                    }
                LocalVaultSession(service, handle)
            } finally {
                Wipe.wipe(password)
                Wipe.wipe(masterPassword)
            }
        }

        fun openProvisionedFromServer(
            directory: Path,
            vaultId: UUID,
            deviceId: String,
            devicePrivateKey: ByteArray,
            client: KeysteadServerClient,
        ): LocalVaultSession {
            Files.createDirectories(directory)
            val service = DefaultVaultService(FileVaultStore(directory))
            val id = VaultId(vaultId)
            val privateKey = devicePrivateKey.copyOf()
            val context = vaultKeyPackageContext(vaultId.toString(), deviceId)
            var wrappedVaultKey: ByteArray? = null
            return try {
                val handle =
                    if (Files.exists(directory.resolve("vault.properties"))) {
                        service.openVaultWithDeviceKey(id, privateKey, context)
                    } else {
                        val keyPackage =
                            client.listVaultKeyPackages(vaultId.toString())
                                .firstOrNull { it.deviceId == deviceId }
                                ?: throw IllegalStateException(
                                    "Server did not return a vault key package for device $deviceId",
                                )
                        wrappedVaultKey = Base64.getDecoder().decode(keyPackage.encryptedVaultKey)
                        service.provisionVault(id, wrappedVaultKey, privateKey, context)
                    }
                LocalVaultSession(service, handle)
            } finally {
                Wipe.wipe(privateKey)
                Wipe.wipe(context)
                Wipe.wipe(wrappedVaultKey)
            }
        }

        fun openProvisionedFromServer(
            directory: Path,
            vaultId: UUID,
            identity: LocalDeviceIdentity,
            client: KeysteadServerClient,
        ): LocalVaultSession {
            val privateKey = identity.privateKey()
            return try {
                openProvisionedFromServer(
                    directory = directory,
                    vaultId = vaultId,
                    deviceId = identity.deviceId,
                    devicePrivateKey = privateKey,
                    client = client,
                )
            } finally {
                Wipe.wipe(privateKey)
            }
        }

        fun openFirstProvisionedFromServer(
            directory: Path,
            identity: LocalDeviceIdentity,
            client: KeysteadServerClient,
        ): LocalVaultSession {
            val vault =
                client.listVaults()
                    .firstOrNull { serverVault ->
                        client.listVaultKeyPackages(serverVault.vaultId)
                            .any { it.deviceId == identity.deviceId }
                    }
                    ?: throw IllegalStateException(
                        "Server did not return a provisioned vault for device ${identity.deviceId}",
                    )
            return openProvisionedFromServer(
                directory = directory,
                vaultId = UUID.fromString(vault.vaultId),
                identity = identity,
                client = client,
            )
        }
    }
}
