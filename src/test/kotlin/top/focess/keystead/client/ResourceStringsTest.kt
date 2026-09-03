package top.focess.keystead.client

import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import top.focess.keystead.client.i18n.AppLocale
import top.focess.keystead.client.i18n.Strings

class ResourceStringsTest {
    @Test
    fun englishAndChineseResourceKeysStayAligned() {
        val english = resourceKeys(Path.of("src/main/composeResources/values/strings.xml"))
        val chinese = resourceKeys(Path.of("src/main/composeResources/values-zh/strings.xml"))

        assertEquals(english, chinese)
        assertTrue(english.size >= 495)
    }

    @Test
    fun everySimpleAndEnumStringMemberResolvesForEveryLocale() {
        AppLocale.entries.forEach { locale ->
            val strings = locale.strings
            Strings::class.java.methods
                .filter { Modifier.isAbstract(it.modifiers) && it.returnType == String::class.java }
                .forEach { method ->
                    val parameterTypes = method.parameterTypes
                    when {
                        parameterTypes.isEmpty() -> method.invoke(strings)
                        parameterTypes.size == 1 && parameterTypes.single().isEnum ->
                            parameterTypes.single().enumConstants.forEach { method.invoke(strings, it) }
                        parameterTypes.all(::isSimple) ->
                            method.invoke(strings, *parameterTypes.map(::sampleValue).toTypedArray())
                    }
                }
        }
    }

    @Test
    fun specializedResourceBranchesResolveForEveryLocale() {
        AppLocale.entries.forEach { locale ->
            val strings = locale.strings
            DeviceUnlockState.entries.forEach {
                strings.deviceUnlockStatus(DeviceUnlockUiModel(it, provider = DeviceProtectionProvider.MAC_TOUCH_ID))
            }
            ServerVaultRestoreStage.entries.forEach {
                strings.serverVaultRestoreStatus(ServerVaultRestoreModel(it))
            }
            strings.storageStatus(SecureStorageUiModel(null, BiometricAvailability.NOT_CHECKED))
            strings.storageStatus(SecureStorageUiModel(SecureStorageMode.MEMORY_ONLY, BiometricAvailability.CHECKING))
            strings.storageStatus(
                SecureStorageUiModel(
                    SecureStorageMode.BIOMETRIC,
                    BiometricAvailability.AVAILABLE,
                    providerId = "mac-touch-id",
                    biometricActive = true,
                ),
            )
            strings.storageStatus(
                SecureStorageUiModel(
                    SecureStorageMode.BIOMETRIC,
                    BiometricAvailability.UNAVAILABLE,
                    providerId = "mac-touch-id",
                    diagnosticCode = "test-diagnostic",
                ),
            )
            strings.expiryReminders(2, 3)
            strings.expiryReminders(2, 0)
            strings.expiryReminders(0, 3)
            strings.expiryReminders(0, 0)
            strings.expiryLabel(SecretExpiryStatus.EXPIRED, -1)
            strings.expiryLabel(SecretExpiryStatus.EXPIRED, -2)
            strings.expiryLabel(SecretExpiryStatus.DUE_SOON, 0)
            strings.expiryLabel(SecretExpiryStatus.DUE_SOON, 2)
            strings.expiryLabel(SecretExpiryStatus.ACTIVE, 1)
            strings.expiryLabel(SecretExpiryStatus.ACTIVE, 2)
            strings.recordStateValue(null)
            strings.recordStateValue(true)
            strings.recordStateValue(false)
            strings.recordDeletionStates(null, true)
            strings.serverRecordMetadata("Login", 2, false)
            strings.serverRecordMetadata("Login", 2, true)
            strings.conflictMessage(KeysteadRevisionConflictException())
            strings.conflictMessage(
                KeysteadRevisionConflictException(
                    latestRevision = 3,
                    rejectedRevision = 2,
                    fingerprint = "vault",
                    secretId = "secret",
                    serverDeleted = true,
                    serverUpdatedAt = "now",
                ),
            )
        }
    }

    private fun resourceKeys(path: Path): Set<String> {
        val pattern = Regex("""<string name="([^"]+)">""")
        return pattern.findAll(Files.readString(path)).map { it.groupValues[1] }.toSet()
    }

    private fun isSimple(type: Class<*>): Boolean =
        type == String::class.java ||
            type == Int::class.javaPrimitiveType ||
            type == Long::class.javaPrimitiveType ||
            type.name == "java.lang.Integer" ||
            type.name == "java.lang.Long" ||
            type == Boolean::class.javaPrimitiveType ||
            type.name == "java.lang.Boolean"

    private fun sampleValue(type: Class<*>): Any =
        when (type) {
            String::class.java -> "sample"
            Int::class.javaPrimitiveType -> 2
            Long::class.javaPrimitiveType -> 2L
            Boolean::class.javaPrimitiveType -> false
            else -> when (type.name) {
                "java.lang.Integer" -> 2
                "java.lang.Long" -> 2L
                "java.lang.Boolean" -> false
                else -> error("Unsupported test parameter: $type")
            }
        }
}
