package top.focess.keystead.client

import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.util.Base64
import java.util.UUID
import top.focess.keystead.access.VaultAccessKeyContextCodec
import top.focess.keystead.access.VaultAccessRequestCodec
import top.focess.keystead.crypto.DefaultCryptoService
import top.focess.keystead.memory.SecretBuffer
import top.focess.keystead.memory.Wipe
import top.focess.keystead.model.SecretClassification
import top.focess.keystead.model.SecretId
import top.focess.keystead.model.SecretType
import top.focess.keystead.model.SlotType
import top.focess.keystead.model.VaultFingerprint
import top.focess.keystead.model.KeyId
import top.focess.keystead.service.DefaultVaultService
import top.focess.keystead.service.EncryptedSyncRecord
import top.focess.keystead.service.SyncImportReport
import top.focess.keystead.service.SyncPayloadView
import top.focess.keystead.service.SyncRecordPreview
import top.focess.keystead.service.FullVaultBackupService
import top.focess.keystead.service.DeviceVaultKeyPackage
import top.focess.keystead.service.SyncImportConflict
import top.focess.keystead.service.SyncImportRejection
import top.focess.keystead.service.SyncImportRejectionReason
import top.focess.keystead.service.VaultHandle
import top.focess.keystead.store.VaultFileFormat

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
    val username: String? = null,
    val url: String? = null,
    val labels: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val attributes: Map<String, String> = emptyMap(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val revision: Long? = null,
    val fields: List<SecretInspectorField> = emptyList(),
)

data class SecretInspectorField(
    val name: String,
    val secret: Boolean,
    val value: String? = null,
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

data class DeviceKeySlot(
    val slotKeyId: String,
)

data class LocalVaultDeviceSlots(
    val fingerprint: String,
    val slots: List<DeviceKeySlot>,
)

data class PersonalVaultPullResult(
    val imported: Int,
    val skipped: Int,
    val conflicts: List<SyncImportConflict>,
    val rejected: List<SyncImportRejection>,
    val highestSequence: Long,
)

class LocalVaultSession private constructor(
    private val service: DefaultVaultService,
    private var handle: VaultHandle,
    private val file: Path,
) : AutoCloseable {
    private val syncPageLimit = 100

    internal fun fingerprintValue(): String = handle.vaultFingerprint().toHexString()
    internal fun vaultKeyIdValue(): String = handle.vaultKeyId().value()
    internal fun vaultFile(): Path = file

    internal fun exportFullBackup(
        backupPassword: CharArray,
        output: OutputStream,
    ) {
        val password = backupPassword.copyOf()
        try {
            FullVaultBackupService().export(handle, password, output)
        } finally {
            Wipe.wipe(password)
            Wipe.wipe(backupPassword)
        }
    }

    internal fun wrapCurrentVaultKey(publicKey: ByteArray, context: ByteArray): DeviceVaultKeyPackage =
        handle.wrapVaultKeyPackageForDevice(publicKey, context)
    /** Enrolls the dedicated local-unlock credential without using the server transfer identity. */
    internal fun enrollLocalLogin(credential: LocalUnlockCredential): String {
        val publicKey = credential.publicKey()
        val context = vaultKeyPackageContext(fingerprintValue(), credential.bindingId)
        return try {
            handle.addDeviceKey(publicKey, context).value()
        } finally {
            Wipe.wipe(publicKey)
            Wipe.wipe(context)
        }
    }

    /** Enables the single local-login entry only when this vault has no DEVICE slot yet. */
    internal fun ensureLocalLogin(credential: LocalUnlockCredential): Boolean {
        if (deviceSlots().isNotEmpty()) return false
        enrollLocalLogin(credential)
        return true
    }

    /**
     * Replaces every legacy DEVICE slot with one dedicated local-login slot. The new slot is added
     * first so a server-provisioned vault, whose transfer slot may be its only slot, never loses its
     * final access path during the transition.
     */
    internal fun replaceLocalLogin(credential: LocalUnlockCredential): String {
        val legacySlots = deviceSlots()
        val localSlot = enrollLocalLogin(credential)
        legacySlots.forEach { slot -> revokeDeviceKey(slot.slotKeyId) }
        return localSlot
    }

    /** Replaces server-provisioning DEVICE slots with a local master-passphrase slot. */
    internal fun installMasterPassphrase(passphrase: CharArray): Int {
        require(passphrase.isNotEmpty()) { "Vault master passphrase must not be empty" }
        handle.addPassphrase(passphrase)
        return removeDeviceLogin()
    }

    /** Removes a DEVICE key slot by its key id, revoking passphrase-less unlock for that device. */
    internal fun revokeDeviceKey(slotKeyId: String) {
        handle.removeDeviceKey(KeyId(slotKeyId))
    }

    /** Removes every current and legacy DEVICE slot while retaining passphrase access. */
    internal fun removeDeviceLogin(): Int {
        val slots = deviceSlots()
        slots.forEach { slot -> revokeDeviceKey(slot.slotKeyId) }
        return slots.size
    }

    /**
     * Lists the DEVICE key slots currently enrolled on the vault, by slot key id. Reads the
     * plaintext header directly; the on-disk lock guards a sibling ".lock" file rather than the
     * data file, so this is safe to call while the vault handle is open.
     */
    internal fun deviceSlots(): List<DeviceKeySlot> {
        val header = VaultFileFormat.readHeader(Files.readAllBytes(file))
        return header.slots()
            .filter { it.slotType() == SlotType.DEVICE }
            .map { DeviceKeySlot(it.slotKeyId().value()) }
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
        secretBufferFromString(username).use { usernameBuffer ->
            secretBufferFromString(password).use { passwordBuffer ->
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
        secretBufferFromString(username).use { usernameBuffer ->
            secretBufferFromString(password).use { passwordBuffer ->
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
            .filter { it.secretType() == SecretType.LOGIN_PASSWORD }
            .map { LoginListItem(it.secretId().value().toString(), it.title()) }

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
        val buffers = fields.mapValues { secretBufferFromString(it.value) }
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
        val buffers = fields.mapValues { secretBufferFromString(it.value) }
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
            .map { metadata ->
                var username: String? = null
                var url: String? = null
                var inspectorFields = emptyList<SecretInspectorField>()
                if (metadata.secretType() == SecretType.LOGIN_PASSWORD) {
                    handle.withLogin(metadata.secretId()) { view ->
                        url = view.url().orElse(null)
                        view.withUsername { username = String(it) }
                    }
                } else {
                    val fieldSpecs =
                        SecretFormModel.specForOrNull(metadata.secretType())
                            ?.fields
                            ?.associateBy(SecretFieldSpec::name)
                            .orEmpty()
                    handle.withSecret(metadata.secretId()) { view ->
                        inspectorFields =
                            view.orderedFieldNames().map { name ->
                                val secret = fieldSpecs[name]?.secret ?: true
                                var value: String? = null
                                if (!secret) {
                                    view.withField(name) { chars -> value = String(chars) }
                                }
                                SecretInspectorField(name, secret, value)
                            }
                    }
                }
                SecretListItem(
                    id = metadata.secretId().value().toString(),
                    title = metadata.title(),
                    type = metadata.secretType().name,
                    category = metadata.classification().category(),
                    provider = metadata.classification().provider(),
                    software = metadata.classification().software(),
                    account = metadata.classification().account(),
                    expiry = metadata.profile().attributes()["expiry"],
                    username = username,
                    url = url,
                    labels = metadata.classification().labels(),
                    tags = metadata.tags(),
                    attributes = metadata.profile().attributes(),
                    createdAt = metadata.createdAt().toString(),
                    updatedAt = metadata.updatedAt().toString(),
                    revision = metadata.revision(),
                    fields = inspectorFields,
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
        var output = ""
        withPassword(secretId) { password -> output = String(password) }
        return output
    }

    internal fun withPassword(
        secretId: String,
        action: (CharArray) -> Unit,
    ) {
        handle.withLogin(SecretId(UUID.fromString(secretId))) { view ->
            view.withPassword { password -> action(password) }
        }
    }

    fun revealUsername(secretId: String): String {
        val output = arrayOfNulls<CharArray>(1)
        handle.withLogin(SecretId(UUID.fromString(secretId))) { view ->
            view.withUsername { username -> output[0] = username.copyOf() }
        }
        val chars = output[0] ?: CharArray(0)
        return try {
            String(chars)
        } finally {
            Wipe.wipe(chars)
        }
    }

    fun editSnapshot(secretId: String): SecretEditSnapshot {
        val metadata = handle.listSecrets().first { it.secretId().value().toString() == secretId }
        return if (metadata.secretType() == SecretType.LOGIN_PASSWORD) {
            loginEditSnapshot(secretId)
        } else {
            structuredEditSnapshot(secretId)
        }
    }

    fun delete(secretId: String) {
        handle.deleteSecret(SecretId(UUID.fromString(secretId)))
    }

    /**
     * Re-saves the selected local value unchanged so it receives the next vault revision. This is
     * used only to resolve an equal-revision, divergent server head after an idempotent re-upload
     * returns an older matching event.
     */
    fun promoteLocalRecord(secretId: String): Long {
        val id = SecretId(UUID.fromString(secretId))
        val metadata = handle.listSecrets().first { it.secretId() == id }
        when (metadata.secretType()) {
            SecretType.LOGIN_PASSWORD -> promoteLogin(id)
            else -> promoteStructured(id)
        }
        return handle.listSecrets().first { it.secretId() == id }.revision()
    }

    private fun promoteLogin(id: SecretId) {
        var title = ""
        var classification = SecretClassification.none()
        var tags = emptySet<String>()
        var attributes = emptyMap<String, String>()
        var url: String? = null
        var username = CharArray(0)
        var password = CharArray(0)
        var notes = CharArray(0)
        try {
            handle.withLogin(id) { view ->
                val metadata = view.metadata()
                title = metadata.title()
                classification = metadata.classification()
                tags = metadata.tags()
                attributes = metadata.profile().attributes()
                url = view.url().orElse(null)
                view.withUsername { username = it.copyOf() }
                view.withPassword { password = it.copyOf() }
                view.withNotes { notes = it.copyOf() }
            }
            SecretBuffer.fromChars(username).use { usernameBuffer ->
                SecretBuffer.fromChars(password).use { passwordBuffer ->
                    SecretBuffer.fromChars(notes).use { notesBuffer ->
                        handle.updateLogin(id) { draft ->
                            draft.title(title)
                                .classification(classification)
                                .username(usernameBuffer)
                                .password(passwordBuffer)
                                .url(url)
                                .notes(notesBuffer)
                            tags.forEach(draft::tag)
                            attributes.forEach(draft::attribute)
                        }
                    }
                }
            }
        } finally {
            Wipe.wipe(username)
            Wipe.wipe(password)
            Wipe.wipe(notes)
        }
    }

    private fun promoteStructured(id: SecretId) {
        var title = ""
        var classification = SecretClassification.none()
        var tags = emptySet<String>()
        var attributes = emptyMap<String, String>()
        val values = linkedMapOf<String, CharArray>()
        try {
            handle.withSecret(id) { view ->
                val metadata = view.metadata()
                title = metadata.title()
                classification = metadata.classification()
                tags = metadata.tags()
                attributes = metadata.profile().attributes()
                view.orderedFieldNames().forEach { name ->
                    view.withField(name) { values[name] = it.copyOf() }
                }
            }
            val buffers = values.mapValues { SecretBuffer.fromChars(it.value) }
            try {
                handle.updateSecret(id) { draft ->
                    draft.title(title).classification(classification)
                    tags.forEach(draft::tag)
                    attributes.forEach(draft::attribute)
                    buffers.forEach(draft::field)
                }
            } finally {
                buffers.values.forEach(SecretBuffer::close)
            }
        } finally {
            values.values.forEach(Wipe::wipe)
        }
    }

    /** Publishes a complete idempotent snapshot to the account's one personal event stream. */
    fun pushAllPersonalRecordsTo(client: KeysteadServerClient): Int =
        pushPersonalRecords(client, handle.exportRecordsSince(0))

    /** Publishes only the selected current records without advancing the global sync cursor. */
    fun pushSelectedPersonalRecordsTo(
        client: KeysteadServerClient,
        secretIds: Set<String>,
    ): Int =
        pushPersonalRecords(
            client,
            handle.exportRecordsSince(0).filter { it.secretId() in secretIds },
        )

    internal fun currentPersonalRecords(): List<EncryptedSyncRecord> =
        handle.exportRecordsSince(0)

    fun previewSyncRecordFields(record: EncryptedSyncRecord): Map<String, String>? {
        var captured: Map<String, String>? = null
        try {
            handle.previewSyncRecord(record) { preview ->
                when (preview) {
                    is SyncRecordPreview.Deleted -> captured = mapOf("deleted" to "true")
                    is SyncRecordPreview.Active ->
                        captured = captureActivePayload(preview.metadata().title(), preview.payload())
                }
            }
        } catch (_: Exception) {
            captured = null
        }
        return captured
    }

    fun localRecordFields(secretId: String): Map<String, String> {
        val metadata =
            handle.listSecrets().firstOrNull { it.secretId().value().toString() == secretId }
                ?: return emptyMap()
        val fields = LinkedHashMap<String, String>()
        fields["title"] = metadata.title()
        try {
            val id = SecretId(UUID.fromString(secretId))
            when (metadata.secretType()) {
                SecretType.LOGIN_PASSWORD ->
                    handle.withLogin(id) { view ->
                        view.url().ifPresent { fields["url"] = it }
                        view.withUsername { fields["username"] = String(it) }
                        view.withPassword { fields["password"] = String(it) }
                        view.withNotes { fields["notes"] = String(it) }
                    }
                SecretType.SECURE_NOTE ->
                    handle.withSecureNote(id) { view ->
                        view.withBody { fields["body"] = String(it) }
                    }
                else ->
                    handle.withSecret(id) { view ->
                        view.orderedFieldNames().forEach { name ->
                            view.withField(name) { fields[name] = String(it) }
                        }
                    }
            }
        } catch (_: Exception) {
            // Local record missing or undecodable; return the fields captured so far.
        }
        return fields
    }

    fun importSelectedSyncRecords(records: List<EncryptedSyncRecord>): SyncImportReport =
        handle.importRecordsWithReport(records)

    private fun captureActivePayload(title: String, payload: SyncPayloadView): Map<String, String> {
        val fields = LinkedHashMap<String, String>()
        fields["title"] = title
        when (payload) {
            is SyncPayloadView.Login -> {
                payload.view().url().ifPresent { fields["url"] = it }
                payload.view().withUsername { fields["username"] = String(it) }
                payload.view().withPassword { fields["password"] = String(it) }
                payload.view().withNotes { fields["notes"] = String(it) }
            }
            is SyncPayloadView.Note -> payload.view().withBody { fields["body"] = String(it) }
            is SyncPayloadView.Structured -> {
                payload.view().orderedFieldNames().forEach { name ->
                    payload.view().withField(name) { fields[name] = String(it) }
                }
            }
        }
        return fields
    }

    fun pushPendingPersonalRecordsTo(
        client: KeysteadServerClient,
        stateStore: SyncStateStore,
    ): Int {
        val fingerprint = fingerprintValue()
        val records = handle.exportRecordsSince(stateStore.lastPushedRevision(fingerprint))
        val published = pushPersonalRecords(client, records)
        records.maxOfOrNull { it.revision() }?.let { stateStore.recordPushed(fingerprint, it) }
        return published
    }

    private fun pushPersonalRecords(
        client: KeysteadServerClient,
        records: List<EncryptedSyncRecord>,
    ): Int {
        records.forEach { record ->
            val unsigned =
                PersonalVaultRecordEvent(
                    eventId = "pending",
                    fingerprint = record.fingerprint(),
                    secretId = record.secretId(),
                    revision = record.revision(),
                    secretType = record.secretType(),
                    encryptedProfile = record.encryptedProfile(),
                    envelope = record.envelope(),
                    deleted = record.deleted(),
                    contentKey = record.contentKey(),
                )
            client.appendPersonalRecord(
                unsigned.copy(eventId = PersonalRecordEventId.of(unsigned)),
            )
        }
        return records.size
    }

    fun pullPendingPersonalRecordsFrom(
        client: KeysteadServerClient,
        stateStore: SyncStateStore,
    ): PersonalVaultPullResult {
        val fingerprint = fingerprintValue()
        var cursor = stateStore.lastPulledServerSequence(fingerprint)
        var imported = 0
        var skipped = 0
        val conflicts = mutableListOf<SyncImportConflict>()
        val rejected = mutableListOf<SyncImportRejection>()
        do {
            val page = client.listPersonalRecordPage(cursor, syncPageLimit)
            // Legacy pre-KVE2 events carry no content key and can never verify; reject
            // them individually instead of failing the whole page.
            val (legacy, current) = page.records.partition { it.contentKey.isBlank() }
            legacy.forEach { record ->
                rejected +=
                    SyncImportRejection(
                        record.secretId,
                        record.revision,
                        SyncImportRejectionReason.UNVERIFIABLE,
                    )
            }
            val report =
                handle.importRecordsWithReport(
                    current.map { record ->
                        EncryptedSyncRecord(
                            record.fingerprint,
                            record.secretId,
                            record.revision,
                            record.secretType,
                            record.encryptedProfile,
                            record.envelope,
                            record.deleted,
                            record.contentKey,
                        )
                    },
                )
            imported += report.imported()
            skipped += report.skipped()
            conflicts += report.conflicts()
            rejected += report.rejected()
            val nextCursor = page.nextSequence ?: page.highestSequence
            if (page.hasMore && nextCursor <= cursor) {
                throw IllegalStateException(
                    "Server personal record page did not advance from sequence $cursor",
                )
            }
            cursor = nextCursor
        } while (page.hasMore)
        stateStore.recordPulledServerSequence(fingerprint, cursor)
        return PersonalVaultPullResult(
            imported = imported,
            skipped = skipped,
            conflicts = conflicts,
            rejected = rejected,
            highestSequence = cursor,
        )
    }

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
            type = metadata.secretType().name
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

    fun rotateVaultKey(masterPassword: CharArray): String {
        val password = masterPassword.copyOf()
        return try {
            // The file-based rotation reopens the vault file, which the v2 OneFileVaultStore guards
            // with a process-wide lock held by the current handle. Close the handle first so the
            // rotation can reacquire the lock; the on-disk state is consistent, so reopening is safe.
            val previous = handle
            previous.close()
            val replacement = service.rotateVaultKey(file, password)
            handle = replacement
            replacement.vaultKeyId().value()
        } finally {
            Wipe.wipe(password)
            Wipe.wipe(masterPassword)
        }
    }

    override fun close() {
        handle.close()
    }

    private fun secretBufferFromString(value: String): SecretBuffer {
        val chars = value.toCharArray()
        return try {
            SecretBuffer.fromChars(chars)
        } finally {
            Wipe.wipe(chars)
        }
    }

    companion object {
        fun vaultKeyPackageContext(fingerprint: String, bindingId: String): ByteArray =
            "keystead-vault-key-package-v1|vault:$fingerprint|device:$bindingId"
                .toByteArray(StandardCharsets.UTF_8)

        /** Reads only non-secret header metadata so locked-vault UI can verify local enrollment. */
        internal fun inspectDeviceSlots(file: Path): LocalVaultDeviceSlots? {
            if (!Files.isRegularFile(file)) return null
            val header = VaultFileFormat.readHeader(Files.readAllBytes(file))
            return LocalVaultDeviceSlots(
                fingerprint = header.fingerprint().toHexString(),
                slots =
                    header.slots()
                        .filter { it.slotType() == SlotType.DEVICE }
                        .map { DeviceKeySlot(it.slotKeyId().value()) },
            )
        }

        fun openOrCreate(file: Path, masterPassword: CharArray): LocalVaultSession {
            val service = DefaultVaultService()
            val password = masterPassword.copyOf()
            return try {
                val handle =
                    if (Files.exists(file)) {
                        service.openVault(file, password)
                    } else {
                        file.parent?.let { Files.createDirectories(it) }
                        service.createVault(file, password)
                    }
                LocalVaultSession(service, handle, file)
            } finally {
                Wipe.wipe(password)
                Wipe.wipe(masterPassword)
            }
        }

        fun restoreFullBackup(
            file: Path,
            input: InputStream,
            backupPassword: CharArray,
            newMasterPassphrase: CharArray,
        ): LocalVaultSession {
            val backup = backupPassword.copyOf()
            val master = newMasterPassphrase.copyOf()
            val crypto = DefaultCryptoService()
            val clock = Clock.systemUTC()
            val service = DefaultVaultService(crypto, clock)
            return try {
                val handle =
                    FullVaultBackupService(crypto, clock)
                        .restore(file, input, backup, master)
                LocalVaultSession(service, handle, file)
            } finally {
                Wipe.wipe(backup)
                Wipe.wipe(master)
                Wipe.wipe(backupPassword)
                Wipe.wipe(newMasterPassphrase)
            }
        }

        /** Opens a vault with the dedicated local credential; server transfer identities are not accepted. */
        fun openWithLocalLogin(
            file: Path,
            credential: LocalUnlockCredential,
        ): LocalVaultSession {
            val service = DefaultVaultService()
            val privateKey = credential.privateKey()
            var context: ByteArray? = null
            return try {
                val fingerprint =
                    VaultFileFormat.readHeader(Files.readAllBytes(file)).fingerprint().toHexString()
                context = vaultKeyPackageContext(fingerprint, credential.bindingId)
                val handle = service.openVaultWithDeviceKey(file, privateKey, context)
                LocalVaultSession(service, handle, file)
            } finally {
                Wipe.wipe(privateKey)
                context?.let { Wipe.wipe(it) }
            }
        }

        fun openProvisionedFromServer(
            file: Path,
            request: ServerVaultAccessRequest,
            exchangeSession: EphemeralVaultAccessSession,
        ): LocalVaultSession {
            require(file.fileName.toString().endsWith(".kvault", ignoreCase = true)) {
                "Server restore target must use the .kvault extension"
            }
            check(!Files.exists(file)) {
                "Server restore target must be a new vault file"
            }
            check(request.state == ServerVaultAccessRequestState.APPROVED) {
                "Vault access request has not been approved"
            }
            check(request.requestId == exchangeSession.requestId) {
                "Approved request does not belong to this login session"
            }
            val packageValue =
                request.approvedPackage
                    ?: throw IllegalStateException("Approved request did not include a vault key package")
            val canonical = Base64.getUrlDecoder().decode(request.canonicalRequest)
            var publicKey: ByteArray? = null
            var encryptedVaultKey: ByteArray? = null
            var context: ByteArray? = null
            try {
                val decoded = VaultAccessRequestCodec.decode(canonical)
                check(decoded.requestId() == request.requestId)
                check(decoded.accountId() == request.accountId)
                check(decoded.serverOrigin() == request.serverOrigin)
                check(decoded.keyAlgorithm() == request.keyAlgorithm)
                check(VaultAccessRequestCodec.fingerprint(decoded) == request.fingerprint)
                check(decoded.keyAlgorithm() == exchangeSession.keyAlgorithm)
                check(decoded.requestId() == exchangeSession.requestId)
                publicKey = decoded.exchangePublicKey()
                check(requireNotNull(publicKey).contentEquals(exchangeSession.publicKey)) {
                    "Approved request uses a different exchange key"
                }
                check(packageValue.keyAlgorithm == DefaultVaultService.DEVICE_KEY_PACKAGE_ALGORITHM) {
                    "Vault access key package algorithm is unsupported"
                }
                encryptedVaultKey = Base64.getDecoder().decode(packageValue.encryptedVaultKey)
                context =
                    VaultAccessKeyContextCodec.encode(
                        canonical,
                        packageValue.fingerprint,
                        packageValue.vaultKeyId,
                    )
                val keyPackage =
                    DeviceVaultKeyPackage(
                        VaultFingerprint.fromHexString(packageValue.fingerprint),
                        KeyId(packageValue.vaultKeyId),
                        packageValue.keyAlgorithm,
                        requireNotNull(encryptedVaultKey),
                    )
                var opened: LocalVaultSession? = null
                exchangeSession.withPrivateKey { privateKey ->
                    file.parent?.let { Files.createDirectories(it) }
                    val vaultService = DefaultVaultService()
                    opened =
                        LocalVaultSession(
                            vaultService,
                            vaultService.provisionVault(
                                file,
                                keyPackage,
                                privateKey,
                                requireNotNull(context),
                            ),
                            file,
                        )
                }
                return requireNotNull(opened)
            } finally {
                Wipe.wipe(canonical)
                publicKey?.let(Wipe::wipe)
                encryptedVaultKey?.let(Wipe::wipe)
                context?.let(Wipe::wipe)
            }
        }

    }
}
