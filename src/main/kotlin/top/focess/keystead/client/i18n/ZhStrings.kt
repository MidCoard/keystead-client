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

internal object ZhStrings : Strings {
    override val appTitle = "Keystead"

    override val confirm = "确认"
    override val cancel = "取消"
    override val clear = "清除"
    override val delete = "删除"
    override val edit = "编辑"
    override val copy = "复制"
    override val reveal = "显示"
    override val hide = "隐藏"
    override val refresh = "刷新"
    override val previous = "上一页"
    override val next = "下一页"
    override val dismiss = "忽略"
    override val decline = "拒绝"
    override val remove = "移除"
    override val removeServerRecordsTitle = "移除服务器副本？"
    override fun removeServerRecordsMessage(count: Int) =
        "要移除所选 $count 条记录在服务器上的全部历史吗？本地保险库不会改变。" +
            "仍保存这些记录的其他客户端以后可以重新上传；服务器只保留一条脱敏审计事件。"
    override fun removedServerRecords(records: Int, events: Long) =
        "已移除 $records 条所选记录对应的 $events 条服务器事件；本地记录没有改变。"

    override fun destinationLabel(destination: KeysteadDestination) = when (destination) {
        KeysteadDestination.SECRETS -> "密钥"
        KeysteadDestination.ADD -> "新增"
        KeysteadDestination.BACKUP -> "备份"
        KeysteadDestination.DEVICE_ACCESS -> "本机登录"
        KeysteadDestination.ACCOUNT -> "账户"
        KeysteadDestination.SYNC -> "同步"
        KeysteadDestination.SHARE -> "分享"
        KeysteadDestination.RECOVERY -> "恢复"
        KeysteadDestination.SETTINGS -> "设置"
    }

    override fun destinationZoneLabel(zone: KeysteadZone) = when (zone) {
        KeysteadZone.LOCAL_VAULT -> "本地保险库"
        KeysteadZone.CONNECTED -> "在线功能"
        KeysteadZone.SYSTEM -> "系统"
        KeysteadZone.INTERNAL -> ""
    }

    override val lock = "锁定"
    override val vaultLocked = "保险库已锁定"
    override val vaultOpen = "保险库已打开"

    override val vaultLockedHeading = "你的保险库已锁定"
    override val masterPassword = "主密码"
    override val openOrCreateVault = "打开或创建保险库"
    override val advancedVaultLocation = "高级（保险库位置）"
    override val vaultFile = "保险库文件"
    override val chooseExistingVault = "打开已有文件…"
    override val chooseNewVaultLocation = "选择新位置…"
    override val chooseExistingVaultDialogTitle = "打开已有的 Keystead 保险库"
    override val chooseNewVaultDialogTitle = "选择新的 Keystead 保险库位置"
    override val vaultLocationHelp =
        "保险库成功打开后，此路径会成为下次启动的默认位置。更改路径不会移动或删除旧文件。"
    override val vaultFileMustNotBeBlank = "保险库文件不能为空"
    override val unlockWithDeviceLogin = "使用本机登录解锁"
    override val localLoginCredentialUnavailable =
        "本机登录凭据不可用。请在“本机登录”页面重新加载或创建。"
    override fun deviceUnlockStatus(model: DeviceUnlockUiModel) = when (model.state) {
        DeviceUnlockState.NOT_CONFIGURED -> "尚未配置本机登录。"
        DeviceUnlockState.DEVICE_LOGIN_NOT_ENABLED ->
            "此保险库未启用设备登录。请使用主密码打开。"
        DeviceUnlockState.LOADED -> "本机登录凭据已加载并可用。"
        DeviceUnlockState.BIOMETRIC_NOT_SELECTED -> "本机登录使用 Windows Hello。请在“本机登录”页面选择它。"
        DeviceUnlockState.BIOMETRIC_UNAVAILABLE -> "Windows Hello 不可用或尚未配置；Keystead 不会绕过验证。"
        DeviceUnlockState.BIOMETRIC_READY -> "Windows Hello 已就绪；打开保险库前将由 Windows 验证您。"
    }
    override val chooseDeviceStorageFirst = "请先选择本机登录保护方式"
    override val identityStorageCannotChange = "本机登录已经配置。如需更换保护方式，请先移除现有本机登录。"
    override val restoreAnotherDevice = "从 Keystead 服务器恢复"
    override val restoreAnotherDeviceIntro =
        "服务器只保存加密 records，不保存 DEK。请主动申请恢复权限，再由另一台已经打开此保险库的设备核对请求指纹并授权。"
    override val restoreStepServer = "服务器"
    override val restoreStepIdentity = "请求"
    override val restoreStepAccount = "账户"
    override val restoreStepVault = "保险库"
    override val connectAndVerifyDevice = "连接并验证此设备"
    override val checkVaultAccess = "检查保险库访问权限"
    override val noVaultPackageInstruction =
        "此设备尚无可用的保险库密钥包。请创建访问请求，并在受信任设备的“恢复”页面批准。"
    override val availableVault = "可用保险库"
    override val createLocalVaultFromServer = "创建本地保险库并下载记录"
    override val restoreCreatesLocalFile =
        "请设置新的本地主口令。本机登录完全可选，可在恢复完成后再启用。"
    override fun serverVaultRestoreStatus(model: ServerVaultRestoreModel): String =
        when (model.stage) {
            ServerVaultRestoreStage.SIGN_IN_REQUIRED -> "请先在“账户”页面登录，再从服务器恢复。"
            ServerVaultRestoreStage.SERVER_OFFLINE -> "暂时无法连接服务器，因此不能进行服务器恢复。"
            ServerVaultRestoreStage.ACCESS_REQUEST_REQUIRED ->
                "请创建一次性恢复请求，然后在另一台已登录并打开保险库的设备上核对指纹并批准。"
            ServerVaultRestoreStage.WAITING_FOR_APPROVAL -> "正在等待受信任设备批准此请求。"
            ServerVaultRestoreStage.REQUEST_EXPIRED -> "此请求已过期；请重新创建批准请求。"
            ServerVaultRestoreStage.WAITING_FOR_PACKAGE ->
                "批准正在完成；请刷新此请求。"
            ServerVaultRestoreStage.READY_TO_RESTORE -> "加密的保险库访问权限已就绪。"
            ServerVaultRestoreStage.TARGET_IN_USE -> "此保险库路径已存在；请选择一个新文件。"
            ServerVaultRestoreStage.MASTER_PASSPHRASE_REQUIRED ->
                "请为重建后的本地保险库设置并确认新的主口令。"
        }
    override fun availableServerVaults(count: Int) = "可恢复 $count 个保险库。"
    override fun restoredVaultFromServer(pulled: Int) =
        "已恢复本地保险库，并下载 $pulled 条加密记录"
    override fun rejectedServerRecords(count: Int) =
        "已忽略 $count 条无法由此保险库密钥验证的服务器记录。"

    override val editSecret = "编辑密钥项"
    override val newSecret = "新建密钥项"
    override val requiredFieldsMarked = "必填项以 * 标记。"
    override val fieldTitle = "标题"
    override val fieldUrl = "网址"
    override val fieldUsername = "用户名"
    override val fieldPassword = "密码"
    override val fieldCategory = "分类"
    override val fieldProvider = "提供商"
    override val fieldSoftware = "软件"
    override val fieldAccount = "账号"
    override val fieldExpiry = "过期时间（可选，YYYY-MM-DD）"
    override val generate = "生成"
    override val generateApiToken = "生成 API 令牌"
    override val generateSshKey = "生成 SSH 密钥"
    override val generateGpgKey = "生成 GPG 密钥"
    override val generateCertificate = "生成证书"
    override val generateMfaSecret = "生成 MFA 密钥"
    override val atLeastOneFieldRequired = "至少需要填写一个字段才能保存。"
    override val openVaultFirst = "请先打开保险库"
    override val updateSelected = "更新所选"
    override val saveSecret = "保存密钥项"
    override val cancelClear = "取消 / 清除"

    override fun secretTypeLabel(type: SecretType) = when (type) {
        SecretType.LOGIN_PASSWORD -> "登录密码"
        SecretType.SECURE_NOTE -> "安全笔记"
        SecretType.SSH_KEY -> "SSH 密钥"
        SecretType.API_TOKEN -> "API 令牌"
        SecretType.GPG_KEY -> "GPG 密钥"
        SecretType.MFA_SECRET -> "MFA 密钥"
        SecretType.CERTIFICATE -> "证书"
        SecretType.GENERIC_SECRET -> "通用"
    }

    override fun shortSecretTypeLabel(type: SecretType) = when (type) {
        SecretType.LOGIN_PASSWORD -> "登录"
        SecretType.SSH_KEY -> "SSH"
        SecretType.API_TOKEN -> "API"
        SecretType.GPG_KEY -> "GPG"
        SecretType.MFA_SECRET -> "MFA"
        SecretType.CERTIFICATE -> "证书"
        SecretType.GENERIC_SECRET -> "通用"
        SecretType.SECURE_NOTE -> "笔记"
    }

    override fun secretFieldLabel(fieldName: String) = when (fieldName) {
        "note" -> "笔记"
        "publicKey" -> "公钥"
        "privateKey" -> "私钥"
        "passphrase" -> "口令"
        "token" -> "令牌"
        "seed" -> "种子"
        "otpauthUri" -> "otpauth URI"
        "certificate" -> "证书"
        "value" -> "值"
        else -> fieldName
    }

    override val secretsTitle = "密钥"
    override fun secretsShown(shown: Int, total: Int) = "已显示 $shown / 共 $total"
    override val filters = "筛选"
    override val search = "搜索"
    override val all = "全部"
    override val clearFilters = "清除筛选"
    override val noSavedSecrets = "暂无已保存的密钥项"
    override val savedSecretsAppearHere = "已保存的密钥项将显示在这里。"
    override fun expiryReminders(expired: Int, dueSoon: Int): String {
        val parts = buildList {
            if (expired > 0) add("$expired 项已过期")
            if (dueSoon > 0) add("$dueSoon 项即将过期")
        }
        return "过期提醒：${parts.joinToString("，")}"
    }
    override val expiryReviewRotate = "请检查并轮换这些密钥项。"
    override val selectedSecret = "已选密钥项"
    override val currentCode = "当前验证码"
    override val authCodeShown = "已显示验证码"
    override val authCodeHidden = "已隐藏验证码"
    override val hideCode = "隐藏验证码"
    override val showCode = "显示验证码"
    override val copyCode = "复制验证码"
    override val noSecretSelected = "未选择密钥项"
    override val selectASecret = "请从列表中选择一个密钥项。"
    override fun secretRowLabel(title: String, typeLabel: String) = "密钥项：$title，$typeLabel"

    override fun groupingLabel(mode: SecretGroupingMode) = when (mode) {
        SecretGroupingMode.NONE -> "无"
        SecretGroupingMode.TYPE -> "按类型"
        SecretGroupingMode.CATEGORY -> "按分类"
        SecretGroupingMode.PROVIDER -> "按提供商"
    }

    override fun groupingBucketLabel(mode: SecretGroupingMode) = when (mode) {
        SecretGroupingMode.CATEGORY -> "无分类"
        SecretGroupingMode.PROVIDER -> "无提供商"
        else -> "其他"
    }

    override fun expiryLabel(status: SecretExpiryStatus, daysRemaining: Long) = when (status) {
        SecretExpiryStatus.EXPIRED -> {
            val days = -daysRemaining
            if (days == 1L) "已过期 1 天" else "已过期 $days 天"
        }
        SecretExpiryStatus.DUE_SOON ->
            if (daysRemaining == 0L) "今天到期" else "$daysRemaining 天后到期"
        SecretExpiryStatus.ACTIVE ->
            if (daysRemaining == 1L) "1 天后到期" else "$daysRemaining 天后到期"
    }

    override val settingsTitle = "设置"
    override val settingsIntro =
        "设备存储与会话信息。保险库是本地加密文件；服务器只能看到加密记录和指纹。"
    override val groupSession = "会话"
    override val groupAbout = "关于"
    override val groupLanguage = "语言"
    override val groupVaultFile = "保险库文件"
    override val languageHelp = "选择界面语言，立即生效。"
    override val deleteVaultFile = "删除保险库文件…"
    override val deleteVaultFileHelp = "从这台电脑永久删除当前已打开的加密保险库文件。"
    override val memoryOnly = "仅本次会话（内存）"
    override val memoryStorageDescription = "私钥仅存于内存——明确锁定设备身份或退出应用后即丢失。"
    override val deviceAccessIntro = "使用 Windows Hello 打开这个本地保险库。本机登录永远不会连接 Keystead 服务器。"
    override val createProtectedIdentity = "设置 Windows Hello"
    override val verifyLocalLogin = "使用 Windows Hello 验证"
    override val deviceLogin = "本机登录"
    override val deviceLoginEnabledLabel = "已启用"
    override val deviceLoginNotEnabledLabel = "未启用"
    override val deviceLoginEnabledHelp = "已启用。此保险库可在这台电脑上免输主密码打开。"
    override val deviceLoginIdentityLocked = "加载本机凭据后，可为当前已打开的保险库启用本机登录。"
    override val deviceLoginReady = "未启用。请使用主密码再次打开保险库，以重新启用设备登录。"
    override val deviceLoginVaultLocked = "现在可先准备本机凭据；打开或重建保险库后会自动绑定。"
    override val deviceLoginUnavailable = "本机登录需要持久且受保护的存储。"
    override val deviceLoginNotConfigured = "尚未配置本机登录。"
    override val localLoginReadyStatus = "本机登录凭据已就绪"
    override val deviceLoginAlreadyEnabled = "此保险库已经启用本机登录。"
    override val removeDeviceLogin = "移除本机登录…"
    override val removeDeviceLoginTitle = "移除本机登录？"
    override val removeDeviceLoginMessage =
        "下次打开这个保险库时必须输入主密码。此操作会清除全部本机登录项。"
    override val deviceLoginEnabled = "已为此保险库启用本机登录"
    override val deviceLoginRemoved = "本机登录已移除；下次请使用主密码"
    override fun deviceProtectionLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "受 Windows Hello 保护"
            DeviceProtectionProvider.UNKNOWN -> "生物验证保护不可用"
        }
    override fun deviceProtectionAvailableLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "Windows Hello 可用"
            DeviceProtectionProvider.UNKNOWN -> "生物验证保护可用"
        }
    override fun deviceProtectionUnavailableLabel(provider: DeviceProtectionProvider) =
        when (provider) {
            DeviceProtectionProvider.WINDOWS_HELLO -> "Windows Hello 不可用或尚未配置"
            DeviceProtectionProvider.UNKNOWN -> "此平台不提供生物验证保护"
        }
    override val notSet = "（未设置）"
    override val notSignedIn = "（未登录）"
    override val noneValue = "（无）"
    override fun sessionServer(url: String) = "服务器：${url.ifBlank { notSet }}"
    override fun sessionUser(user: String) = "用户：${user.ifBlank { notSignedIn }}"
    override fun sessionVaultFile(file: String) =
        "当前保险库文件：${file.ifBlank { noneValue }}"
    override fun vaultFileDeleted(file: String) = "已删除保险库文件 $file"
    override fun vaultFileDeleteFailed(reason: String) = "无法删除保险库文件：$reason"
    override val aboutText =
        "Keystead——本地优先、零知识密钥保险库。密钥项在本设备加密后才会离开本设备。"
    override fun storageStatus(model: SecureStorageUiModel): String {
        val selected =
            when (model.selectedMode) {
                SecureStorageMode.BIOMETRIC ->
                    if (model.biometricActive) "已选：Windows Hello"
                    else "已选：Windows Hello（未激活）"
                SecureStorageMode.MEMORY_ONLY -> "已选：仅内存"
                null -> "已选：尚未选择存储方式"
            }
        val availability =
            when (model.biometricAvailability) {
                BiometricAvailability.NOT_CHECKED -> "尚未检查 Windows Hello"
                BiometricAvailability.CHECKING -> "正在检查 Windows Hello"
                BiometricAvailability.AVAILABLE ->
                    "生物验证可用：${model.providerId ?: "Windows Hello"}"
                BiometricAvailability.UNAVAILABLE ->
                    "生物验证不可用：${model.diagnosticCode ?: "provider-unavailable"}"
            }
        return "$selected。$availability。"
    }

    override val recoveryHubIntro = "通过便携备份或 Keystead 服务器恢复一个新的本地保险库。"
    override val recoverFromBackup = "便携备份"
    override val recoverFromServer = "Keystead 服务器"
    override val serverRecoveryIntro =
        "服务器恢复仅使用本次登录生成的一次性交换密钥；本机生物登录信息绝不会发送给服务器。"
    override val restoreThisDeviceTask = "在本机重建"
    override val approveAnotherDeviceTask = "授权恢复请求"
    override val approveAnotherDeviceIntro =
        "打开服务器 records 所属的保险库，核对请求指纹，再使用请求方的一次性公钥加密 DEK。"
    override val trustedDeviceRequestHelp =
        "请在另一台已经打开此保险库的设备上选择“恢复 → Keystead 服务器 → 授权恢复请求”。"
    override val createApprovalRequest = "申请恢复权限"
    override val waitingForApproval = "正在等待另一台设备批准此请求。"
    override val checkApprovalStatus = "刷新申请状态"
    override val trustedDeviceApprovalHelp =
        "必须与申请设备上显示的请求指纹逐字核对。确认后才会发送由该次一次性公钥加密的 DEK。"
    override val findPendingRequest = "刷新待授权请求"
    override val approveVaultAccess = "确认并发送解锁密钥"
    override val vaultAccessApprovalSignInHelp = "请先在“账户”页面登录，再查看待授权请求。"
    override val vaultAccessApprovalUnlockHelp = "授权前，请打开服务器 records 所属的本地保险库。"
    override fun vaultAccessRequestState(state: ServerVaultAccessRequestState) =
        when (state) {
            ServerVaultAccessRequestState.PENDING -> "等待授权"
            ServerVaultAccessRequestState.APPROVED -> "已授权"
            ServerVaultAccessRequestState.EXPIRED -> "已过期"
        }
    override val shareTitle = "分享"
    override val shareNotSignedInHelp =
        "在“账户”页面登录 Keystead 服务器后即可生成分享；兑换收到的分享无需登录。"
    override val groupMintShare = "生成分享"
    override val groupRedeemShare = "兑换分享"
    override val groupYourShares = "你在服务器上的分享"
    override val payloadLabel = "内容（要分享的密钥）"
    override val tempPassphraseLabel = "临时口令（至少 12 个字符，3 种字符类）"
    override val passphrasePolicyHint = "至少 12 个字符，涵盖小写/大写/数字/符号中的至少 3 类。"
    override val expires = "过期时间"
    override val burnAfterReading = "阅后即焚（仅可兑换一次）"
    override val mintShare = "生成分享"
    override val shareReady = "分享已就绪"
    override val shareOutOfBandOnce = "请通过带外方式发送此验证码和口令。接收者仅可兑换一次。"
    override val shareOutOfBand = "请通过带外方式发送此验证码和口令。"
    override val shareCodeField = "分享验证码"
    override val tempPassphraseShort = "临时口令"
    override val someSharesBurnNote = "部分分享阅后即焚——请仔细输入口令，因为它只能兑换一次。"
    override val redeemShare = "兑换分享"
    override val shareOpened = "分享已打开"
    override val payloadLabelShort = "内容："
    override val noOutstandingShares = "没有未完成的分享。可在上方生成，或刷新以检查服务器。"
    override val burnsAfterReading = "阅后即焚"
    override fun shareCode(code: String) = "验证码：$code"
    override fun shareExpires(at: String) = "过期时间：$at"
    override fun shareOpenedTitle(title: String) = "标题：$title"
    override fun shareOpenedType(type: String) = "类型：$type"
    override fun shareOpenedNote(note: String) = "备注：$note"
    override fun shareOpenedCreated(at: String) = "创建时间：$at"
    override fun shareCreatedExpires(created: String, expires: String) = "创建于 $created——过期于 $expires"
    override fun pageOf(current: Int, total: Int) = "第 $current / $total 页"
    override fun shareTtlLabel(ttl: ShareExchange.ShareTtl) = when (ttl) {
        ShareExchange.ShareTtl.ONE_HOUR -> "1 小时"
        ShareExchange.ShareTtl.ONE_DAY -> "1 天"
        ShareExchange.ShareTtl.ONE_WEEK -> "1 周"
        ShareExchange.ShareTtl.THIRTY_DAYS -> "30 天"
    }

    override val serverSync = "服务器同步"
    override val serverChecking = "正在检查服务器"
    override val serverOnline = "服务器在线"
    override val serverUnavailable = "服务器不可用"
    override val serverUnavailableHelp = "在线功能已停用。请启动服务器或检查地址，然后重试。"
    override val connectedOffline = "离线"
    override val connectedOfflineHelp = "服务器不可用。请检查服务器地址或启动服务器，然后重试。"
    override val checkAgain = "重新检查"
    override val serverRequired = "需要服务器"
    override val loginRequired = "需要登录"
    override val syncNotSignedInHelp = "请先在“账户”页面登录，再拉取、上传和比较加密 records。"
    override val worksOffline = "可离线使用"
    override val groupServerSignIn = "服务器登录"
    override val groupVaultsAndSync = "保险库与同步"
    override val groupBackup = "备份"
    override val serverUrl = "服务器地址"
    override val user = "用户"
    override val serverPassword = "账户密码"
    override val confirmServerPassword = "确认账户密码"
    override val serverPasswordRequirement = "密码需为 12–72 个字符，且不超过 72 个 UTF-8 字节。"
    override val signIn = "登录"
    override val signedIn = "已登录"
    override val createAccount = "创建账户"
    override val accountSignInIntro = "登录后即可使用此 Keystead 服务器。"
    override val accountCreateIntro = "在此服务器创建账户，成功后会自动登录。"
    override val signInFailed = "无法登录"
    override val createAccountFailed = "无法创建账户"
    override val serverCredentialsRejected = "用户名或密码不正确。"
    override val serverUserAlreadyExists = "此用户名对应的账户已经存在。"
    override fun signedInAs(username: String) = "已登录为 $username"
    override val refreshSession = "刷新会话"
    override val signOut = "退出登录"
    override val signOutEverywhere = "在所有设备退出"
    override val createUser = "创建用户"
    override val push = "推送"
    override val pull = "拉取"
    override val pullAndRetry = "拉取并重试"
    override val pullConfirmTitle = "拉取服务器记录？"
    override val pullConfirmMessage = "将新的服务器记录导入此保险库。使用其他保险库密钥加密的记录将被拒绝。"
    override val compareSyncTitle = "比较并同步"
    override val compareSyncEmpty = "没有可比较的服务器记录。"
    override val compareAcceptSelected = "接受所选"
    override val compareAcceptAll = "全部接受"
    override val pullLatest = "拉取最新"
    override val recordInventory = "服务器记录清单"
    override val refreshRecordInventory = "刷新记录清单"
    override val recordSelectionHelp =
        "请明确选择记录。上传使用本地加密记录；移除只删除对应的服务器历史。"
    override val selectAllRecords = "全选"
    override val clearRecordSelection = "清除选择"
    override fun selectedRecordSummary(selected: Int, uploadable: Int, removable: Int) =
        "已选 $selected 条 · $uploadable 条可上传 · $removable 条存在服务器副本"
    override fun selectedUploadSummary(selected: Int, uploadable: Int) =
        "已选 $selected 条 · $uploadable 条可上传"
    override fun uploadSelectedRecords(count: Int) = "上传所选（$count）"
    override fun removeSelectedServerCopies(count: Int) = "移除服务器副本（$count）"
    override fun uploadedSelectedRecords(count: Int) =
        "已上传 $count 条所选加密记录；未选择的记录没有上传。"
    override val recordInventoryEmpty = "此账户在服务器上还没有加密记录事件。"
    override val unlockVaultToCompare =
        "请先打开本地保险库，再加载或比较服务器记录。"
    override val currentRecordComparison = "当前 records 比较"
    override val serverRecordHistory = "服务器事件历史"
    override val historyRemoveHelp =
        "勾选任一事件即选中整条记录。移除将删除所选记录的全部服务器事件；本地记录保持不变。"
    override val recordIdentifierHash = "记录标识哈希"
    override val clientSidePane = "本机（本地）"
    override val serverSidePane = "服务器"
    override val revisionLabel = "修订版本"
    override val recordStateLabel = "状态"
    override fun recordStateValue(deleted: Boolean?) =
        when (deleted) {
            null -> "不存在"
            true -> "已删除"
            false -> "有效"
        }
    override val localBadge = "本机"
    override val serverBadge = "服务器"
    override val otherVaultBadge = "其他保险库"
    override val uploadRecord = "上传"
    override val removeServerCopy = "移除服务器副本"
    override val localContentHash = "本地事件哈希"
    override val serverComputedContentHash = "服务器事件哈希（本机计算）"
    override val serverAdvertisedContentHash = "服务器事件哈希（服务器声明）"
    override val localProfileCiphertextHash = "本地资料密文哈希"
    override val serverProfileCiphertextHash = "服务器资料密文哈希"
    override val localEnvelopeCiphertextHash = "本地内容密文哈希"
    override val serverEnvelopeCiphertextHash = "服务器内容密文哈希"
    override val hashVerified = "哈希已验证"
    override val hashInvalid = "哈希不匹配"
    override fun remoteRecordSummary(events: Int, current: Int) =
        "服务器共有 $events 条加密事件，$current 个 records 具有当前远端状态。"
    override fun recordComparisonStatus(status: RecordComparisonStatus) =
        when (status) {
            RecordComparisonStatus.MATCHED -> "一致"
            RecordComparisonStatus.LOCAL_ONLY -> "仅本地"
            RecordComparisonStatus.SERVER_ONLY -> "仅服务器"
            RecordComparisonStatus.LOCAL_NEWER -> "本地较新"
            RecordComparisonStatus.SERVER_NEWER -> "服务器较新"
            RecordComparisonStatus.HASH_MISMATCH -> "哈希异常"
        }
    override fun recordRevisions(local: Long?, server: Long?) =
        "本地版本 ${local ?: "—"} · 服务器版本 ${server ?: "—"}"
    override fun recordContentHashes(local: String?, server: String?) =
        "本地事件哈希：${local ?: "—"}\n服务器事件哈希（本机计算）：${server ?: "—"}"
    override fun recordDeletionStates(local: Boolean?, server: Boolean?) =
        "本地状态：${recordState(local)} · 服务器状态：${recordState(server)}"
    override fun serverSequence(sequence: Long?) =
        "服务器序列：${sequence ?: "—"}"
    override fun serverRecordMetadata(typeLabel: String, revision: Long, deleted: Boolean) =
        "$typeLabel · 服务器版本 $revision${if (deleted) " · 已删除" else ""}"

    private fun recordState(deleted: Boolean?): String =
        when (deleted) {
            null -> "不存在"
            true -> "已删除"
            false -> "有效"
        }
    override fun personalVaultMismatch(serverFingerprint: String, localFingerprint: String) =
        "服务器 records 属于另一个保险库。服务器指纹：$serverFingerprint；当前本地指纹：$localFingerprint。系统已阻止推送和授权，因为这会把一个保险库的 DEK 与另一个保险库的 records 配在一起。请打开正确的本地保险库，或者在申请恢复的设备上把服务器保险库恢复到一个新文件。"
    override val exportBackup = "导出备份"
    override val restoreBackup = "恢复备份"
    override val fullBackupIntro = "便携备份是一份完整加密的保险库副本。"
    override val createPortableBackup = "创建便携备份"
    override val restorePortableBackup = "恢复便携备份"
    override val createPortableBackupHelp =
        "使用独立的备份密码保护 .ksbackup。恢复时不需要这台电脑、设备登录、服务器或原保险库口令。"
    override val restorePortableBackupHelp =
        "选择一个 .ksbackup 和新的 .kvault 位置。恢复不会覆盖已有文件，也不会连接服务器。"
    override val openVaultToCreateBackup = "请先打开要备份的保险库。"
    override val backupSourceFile = "备份文件（.ksbackup）"
    override val chooseBackupSource = "选择备份"
    override val restoreTargetVault = "新保险库位置（.kvault）"
    override val chooseRestoreTarget = "选择位置"
    override val backupSourceInvalid = "请选择一个存在的 .ksbackup 文件。"
    override val restoreTargetMustBeNew = "请选择新的 .kvault 文件；绝不会覆盖已有文件。"
    override val backupPassword = "备份密码"
    override val confirmBackupPassword = "确认备份密码"
    override val newVaultMasterPassphrase = "新保险库主口令"
    override val confirmNewVaultMasterPassphrase = "确认新主口令"
    override val backupPasswordsDoNotMatch = "两次输入的备份密码不一致"
    override val masterPassphrasesDoNotMatch = "两次输入的新主口令不一致"
    override val reviewBackupRestore = "检查恢复内容"
    override val confirmBackupRestoreTitle = "创建恢复后的保险库？"
    override fun confirmBackupRestoreMessage(source: String, target: String) =
        "备份文件：\n$source\n\n新保险库：\n$target\n\n此操作只会创建一个新的本地保险库，不会替换或修改任何已有保险库。"

    override val conflictDeletedTitle = "冲突：服务器上已删除"
    override val conflictNewerTitle = "冲突：服务器上有更新数据"
    override val conflictDeletedWarning =
        "此密钥项已在服务器上删除。拉取将丢弃你的本地更改。拉取以接受删除，或取消并重新保存以保留本地副本。"
    override fun conflictMessage(error: KeysteadRevisionConflictException): String {
        val latest = error.serverRevision ?: error.latestRevision
        val rejected = error.clientRevision ?: error.rejectedRevision
        val prefix = conflictPrefix(error)
        if (latest != null && rejected != null) {
            return "${prefix}服务器版本为 $latest，拒绝了本地版本 $rejected。请先拉取再推送。"
        }
        val message = error.message ?: "服务器有更新的版本。"
        if (message.contains("pull", ignoreCase = true)) {
            return message
        }
        return "$message 请先拉取再推送。"
    }
    private fun conflictPrefix(error: KeysteadRevisionConflictException): String {
        val fingerprint = error.fingerprint
        val secretId = error.secretId
        if (fingerprint == null || secretId == null) {
            return ""
        }
        val state =
            if (error.serverDeleted == true) {
                "已在服务器上删除"
            } else {
                "服务器上有更新副本"
            }
        val updatedAt = error.serverUpdatedAt?.let { "（$it）" } ?: ""
        return "保险库 $fingerprint 中的密钥项 $secretId $state$updatedAt。"
    }

    override val deleteSecretTitle = "删除密钥项"
    override fun deleteSecretMessage(title: String) = "删除“$title”？此操作无法撤销。"
    override val deleteVaultFileTitle = "删除保险库文件？"
    override fun deleteVaultFileMessage(file: String) =
        "从这台电脑永久删除“$file”？此操作无法撤销。"

    override val signedInRestored = "已恢复 Keystead 服务器登录会话"
    override val serverSessionExpired = "服务器会话已过期；请重新登录"
    override fun couldNotRestoreServerSession(message: String) = "无法恢复服务器会话：$message"
    override val serverAuthFailed = "服务器认证失败"
    override fun couldNotReachServer(errorType: String) = "无法连接 Keystead 服务器（$errorType）"
    override val deletedSecret = "已删除密钥项"
    override val deviceRevoked = "已撤销设备"
    override val signedInToServer = "已登录 Keystead 服务器"
    override fun signedInWithoutRestoreRequest(reason: String) =
        "已登录，但无法创建恢复请求：$reason"
    override val signedInWithVerifiedDevice = "已用已验证设备登录"
    override fun pulledAndRepushed(pulled: Int, pushed: Int) = "已拉取 $pulled 条并重新推送 $pushed 条记录"
    override fun exportedBackupTo(name: String) = "已导出备份到 $name"
    override fun restoredBackupTo(name: String) = "已将备份恢复为 $name"
    override val generatedPassword = "已生成密码"
    override val generatedApiToken = "已生成 API 令牌"
    override val generatedSshKey = "已生成 SSH 密钥"
    override val generatedGpgKey = "已生成 GPG 密钥"
    override val generatedCertificate = "已生成证书"
    override val generatedMfaSecret = "已生成 MFA 密钥"
    override val updatedSecret = "已更新密钥项"
    override val savedSecret = "已保存密钥项"
    override val serverSessionRefreshed = "服务器会话已刷新"
    override val signedOutOfServer = "已退出 Keystead 服务器"
    override val signedOutEverywhere = "已在所有设备退出"
    override val serverUserCreatedAndSignedIn = "服务器用户已创建并登录"
    override val serverVaultReady = "服务器保险库已就绪"
    override val noServerVaults = "没有服务器保险库"
    override fun serverVaultsList(fingerprints: String) = "服务器保险库：$fingerprints"
    override fun publishedKeyPackages(count: Int) = "已发布 $count 个保险库密钥包"
    override fun pushedRecords(pushed: Int, cursor: String) = "已推送 $pushed 条记录；游标 $cursor"
    override fun pulledRecords(pulled: Int, cursor: String) = "已拉取 $pulled 条记录；游标 $cursor"
    override val provisionedVaultOpen = "已打开配发的保险库"
    override val conflictDismissed = "已忽略冲突"
    override val memberRemovedRotateNote = "已移除成员；在恢复写入前请轮换保险库密钥"
    override fun publishedMissingPackages(count: Int) = "已发布 $count 个缺失的成员设备密钥包"
    override fun vaultKeyRotation(stateName: String) = "保险库密钥轮换：$stateName"
    override val vaultAccessRequestCreated = "恢复权限申请已发送"
    override val vaultAccessRequestUpdated = "恢复权限申请状态已刷新"
    override val pendingVaultAccessRequestLoaded = "已载入待授权的恢复请求"
    override val vaultAccessApproved = "已核对请求并发送加密的 DEK"
    override val filtersCleared = "已清除筛选"
    override val secretRevealed = "已显示密钥项"
    override val copiedToClipboard = "已复制到剪贴板"
    override val copiedCodeToClipboard = "已复制验证码到剪贴板"
    override val loadedSecretForEdit = "已加载密钥项以编辑"
    override val copiedShareCodeToClipboard = "已复制分享验证码到剪贴板（30 秒后清除）"
    override fun shareMinted(code: String) = "分享已生成：$code"
    override val shareRedeemed = "分享已兑换"
    override fun loadedShares(count: Int) = "已加载 $count 个分享"
    override fun deletedShare(code: String) = "已删除分享 $code"
    override val exportBackupDialogTitle = "导出 Keystead 备份"
    override val restoreBackupDialogTitle = "恢复 Keystead 备份"
    override val restoreTargetDialogTitle = "选择新保险库位置"

    override val serverLoginRequiredFirst = "请先登录 Keystead 服务器"
    override val vaultMembershipNotFound = "未找到保险库成员关系"
    override val noPendingVaultAccessRequest = "没有待处理的保险库访问请求"
}
