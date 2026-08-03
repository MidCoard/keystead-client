package top.focess.keystead.client

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import top.focess.keystead.memory.Wipe

internal class JnaWindowsHelloPort(
    private val windowHandle: () -> WinDef.HWND?,
    private val random: SecureRandom = SecureRandom(),
    private val library: () -> WebAuthnLibrary = { WebAuthnLibrary.INSTANCE },
) : WindowsHelloPort {
    override fun availability(): OsSecretStoreAvailability {
        if (!isWindows()) {
            return OsSecretStoreAvailability(
                OsSecretStoreStatus.UNSUPPORTED,
                "windows-hello-unsupported",
            )
        }
        val webAuthn =
            try {
                library()
            } catch (_: Throwable) {
                return OsSecretStoreAvailability(
                    OsSecretStoreStatus.UNSUPPORTED,
                    "windows-hello-library-unavailable",
                )
            }
        if (webAuthn.WebAuthNGetApiVersionNumber() < REQUIRED_API_VERSION) {
            return OsSecretStoreAvailability(
                OsSecretStoreStatus.UNSUPPORTED,
                "windows-hello-prf-unsupported",
            )
        }
        val available = IntByReference()
        val result = webAuthn.WebAuthNIsUserVerifyingPlatformAuthenticatorAvailable(available)
        if (result != S_OK) {
            return OsSecretStoreAvailability(
                OsSecretStoreStatus.UNAVAILABLE,
                "windows-hello-check-${result.hexCode()}",
            )
        }
        return if (available.value != 0) {
            OsSecretStoreAvailability(OsSecretStoreStatus.AVAILABLE, "windows-hello-available")
        } else {
            OsSecretStoreAvailability(OsSecretStoreStatus.UNAVAILABLE, "windows-hello-not-configured")
        }
    }

    override fun createCredential(
        relyingPartyId: String,
        userId: ByteArray,
        salt: ByteArray,
    ): WindowsHelloCredential {
        require(userId.isNotEmpty() && userId.size <= MAX_USER_ID_BYTES)
        require(salt.size == PRF_BYTES)
        val webAuthn = requireReady()
        val window = requireWindow()
        val userMemory = userId.memory()
        val saltMemory = salt.memory()
        val challenge = ByteArray(CHALLENGE_BYTES).also(random::nextBytes)
        var clientJson = ByteArray(0)
        val output = PointerByReference()
        try {
            val rp = WebAuthnRpInformation().apply {
                pwszId = WString(relyingPartyId)
                pwszName = WString("Keystead")
                write()
            }
            val user = WebAuthnUserInformation().apply {
                cbId = userId.size
                pbId = userMemory
                pwszName = WString("Keystead device")
                pwszDisplayName = WString("Keystead local login")
                write()
            }
            val coseParameter = WebAuthnCoseCredentialParameter().apply {
                pwszCredentialType = WString(PUBLIC_KEY_CREDENTIAL_TYPE)
                lAlg = COSE_ES256
                write()
            }
            val coseParameters = WebAuthnCoseCredentialParameters().apply {
                cCredentialParameters = 1
                pCredentialParameters = coseParameter.pointer
                write()
            }
            clientJson = clientData("webauthn.create", challenge)
            val clientMemory = clientJson.memory()
            val clientData = WebAuthnClientData().apply {
                cbClientDataJSON = clientJson.size
                pbClientDataJSON = clientMemory
                pwszHashAlgId = WString(SHA_256)
                write()
            }
            val prfEval = WebAuthnHmacSecretSalt().apply {
                cbFirst = salt.size
                pbFirst = saltMemory
                write()
            }
            val options = WebAuthnMakeCredentialOptions().apply {
                dwVersion = MAKE_OPTIONS_VERSION
                dwTimeoutMilliseconds = OPERATION_TIMEOUT_MILLIS
                dwAuthenticatorAttachment = AUTHENTICATOR_ATTACHMENT_PLATFORM
                dwUserVerificationRequirement = USER_VERIFICATION_REQUIRED
                dwAttestationConveyancePreference = ATTESTATION_NONE
                bPreferResidentKey = 1
                bEnablePrf = 1
                pPRFGlobalEval = prfEval.pointer
                write()
            }
            val result =
                webAuthn.WebAuthNAuthenticatorMakeCredential(
                    window,
                    rp,
                    user,
                    coseParameters,
                    clientData,
                    options,
                    output,
                )
            checkResult(result, "create")
            val pointer = output.value ?: invalidNative("windows-hello-create-empty")
            val attestation = WebAuthnCredentialAttestation(pointer).also(Structure::read)
            val credentialPointer = attestation.pbCredentialId
            val hmacPointer = attestation.pHmacSecret
            if (attestation.cbCredentialId !in 1..MAX_CREDENTIAL_ID_BYTES ||
                credentialPointer == null ||
                attestation.bPrfEnabled == 0 ||
                hmacPointer == null
            ) {
                invalidNative("windows-hello-create-invalid")
            }
            val hmacSecret = WebAuthnHmacSecretSalt(hmacPointer).also(Structure::read)
            if (hmacSecret.cbFirst != PRF_BYTES || hmacSecret.pbFirst == null) {
                invalidNative("windows-hello-create-prf-invalid")
            }
            return WindowsHelloCredential(
                credentialPointer.getByteArray(0, attestation.cbCredentialId),
                hmacSecret.pbFirst!!.getByteArray(0, hmacSecret.cbFirst),
            )
        } finally {
            output.value?.let { runCatching { webAuthn.WebAuthNFreeCredentialAttestation(it) } }
            userMemory.clear()
            saltMemory.clear()
            Wipe.wipe(challenge)
            Wipe.wipe(clientJson)
        }
    }

    override fun deriveSecret(
        relyingPartyId: String,
        credentialId: ByteArray,
        salt: ByteArray,
    ): ByteArray {
        require(credentialId.isNotEmpty() && credentialId.size <= MAX_CREDENTIAL_ID_BYTES)
        require(salt.size == PRF_BYTES)
        val webAuthn = requireReady()
        val window = requireWindow()
        val credentialMemory = credentialId.memory()
        val saltMemory = salt.memory()
        val challenge = ByteArray(CHALLENGE_BYTES).also(random::nextBytes)
        var clientJson = ByteArray(0)
        val output = PointerByReference()
        try {
            val credential = WebAuthnCredential().apply {
                cbId = credentialId.size
                pbId = credentialMemory
                pwszCredentialType = WString(PUBLIC_KEY_CREDENTIAL_TYPE)
                write()
            }
            val credentials = WebAuthnCredentials().apply {
                cCredentials = 1
                pCredentials = credential.pointer
                write()
            }
            val prfSalt = WebAuthnHmacSecretSalt().apply {
                cbFirst = salt.size
                pbFirst = saltMemory
                write()
            }
            val saltValues = WebAuthnHmacSecretSaltValues().apply {
                pGlobalHmacSalt = prfSalt.pointer
                write()
            }
            clientJson = clientData("webauthn.get", challenge)
            val clientMemory = clientJson.memory()
            val clientData = WebAuthnClientData().apply {
                cbClientDataJSON = clientJson.size
                pbClientDataJSON = clientMemory
                pwszHashAlgId = WString(SHA_256)
                write()
            }
            val options = WebAuthnGetAssertionOptions().apply {
                dwVersion = GET_OPTIONS_VERSION
                dwTimeoutMilliseconds = OPERATION_TIMEOUT_MILLIS
                CredentialList = credentials
                dwAuthenticatorAttachment = AUTHENTICATOR_ATTACHMENT_PLATFORM
                dwUserVerificationRequirement = USER_VERIFICATION_REQUIRED
                pHmacSecretSaltValues = saltValues.pointer
                write()
            }
            val result =
                webAuthn.WebAuthNAuthenticatorGetAssertion(
                    window,
                    WString(relyingPartyId),
                    clientData,
                    options,
                    output,
                )
            checkResult(result, "verify")
            val pointer = output.value ?: invalidNative("windows-hello-verify-empty")
            val assertion = WebAuthnAssertion(pointer).also(Structure::read)
            val hmacPointer = assertion.pHmacSecret ?: invalidNative("windows-hello-verify-prf-missing")
            val hmacSecret = WebAuthnHmacSecretSalt(hmacPointer).also(Structure::read)
            if (hmacSecret.cbFirst != PRF_BYTES || hmacSecret.pbFirst == null) {
                invalidNative("windows-hello-verify-prf-invalid")
            }
            return hmacSecret.pbFirst!!.getByteArray(0, hmacSecret.cbFirst)
        } finally {
            output.value?.let { runCatching { webAuthn.WebAuthNFreeAssertion(it) } }
            credentialMemory.clear()
            saltMemory.clear()
            Wipe.wipe(challenge)
            Wipe.wipe(clientJson)
        }
    }

    override fun deleteCredential(credentialId: ByteArray) {
        if (credentialId.isEmpty()) return
        val webAuthn = requireReady()
        val memory = credentialId.memory()
        try {
            val result = webAuthn.WebAuthNDeletePlatformCredential(credentialId.size, memory)
            if (result != S_OK && result != NTE_NOT_FOUND) checkResult(result, "delete")
        } finally {
            memory.clear()
        }
    }

    private fun requireReady(): WebAuthnLibrary {
        val availability = availability()
        if (availability.status != OsSecretStoreStatus.AVAILABLE) {
            val failure =
                if (availability.status == OsSecretStoreStatus.UNSUPPORTED) {
                    OsSecretStoreFailure.UNSUPPORTED
                } else {
                    OsSecretStoreFailure.UNAVAILABLE
                }
            throw OsSecretStoreException(failure, availability.diagnosticCode)
        }
        return library()
    }

    private fun requireWindow(): WinDef.HWND =
        windowHandle()
            ?: throw OsSecretStoreException(
                OsSecretStoreFailure.UNAVAILABLE,
                "windows-hello-window-unavailable",
            )

    private fun checkResult(result: Int, operation: String) {
        if (result == S_OK) return
        val failure =
            when (result) {
                HRESULT_CANCELLED -> OsSecretStoreFailure.ACCESS_DENIED
                NTE_NOT_FOUND -> OsSecretStoreFailure.CORRUPT
                else -> OsSecretStoreFailure.UNAVAILABLE
            }
        val code =
            if (result == HRESULT_CANCELLED) {
                "windows-hello-$operation-cancelled"
            } else {
                "windows-hello-$operation-${result.hexCode()}"
            }
        throw OsSecretStoreException(failure, code)
    }

    private fun invalidNative(code: String): Nothing =
        throw OsSecretStoreException(OsSecretStoreFailure.CORRUPT, code)

    private fun clientData(type: String, challenge: ByteArray): ByteArray {
        val encodedChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challenge)
        return """{"type":"$type","challenge":"$encodedChallenge","origin":"https://keystead.local","crossOrigin":false}"""
            .toByteArray(StandardCharsets.UTF_8)
    }

    private fun ByteArray.memory(): Memory =
        Memory(size.toLong().coerceAtLeast(1)).also { memory ->
            if (isNotEmpty()) memory.write(0, this, 0, size)
        }

    private fun Int.hexCode(): String = toUInt().toString(16)

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    private companion object {
        const val S_OK = 0
        const val REQUIRED_API_VERSION = 8
        const val MAKE_OPTIONS_VERSION = 8
        const val GET_OPTIONS_VERSION = 6
        const val OPERATION_TIMEOUT_MILLIS = 60_000
        const val AUTHENTICATOR_ATTACHMENT_PLATFORM = 1
        const val USER_VERIFICATION_REQUIRED = 1
        const val ATTESTATION_NONE = 1
        const val COSE_ES256 = -7
        const val MAX_USER_ID_BYTES = 64
        const val MAX_CREDENTIAL_ID_BYTES = 4096
        const val PRF_BYTES = 32
        const val CHALLENGE_BYTES = 32
        const val HRESULT_CANCELLED = 0x800704C7.toInt()
        const val NTE_NOT_FOUND = 0x80090011.toInt()
        const val PUBLIC_KEY_CREDENTIAL_TYPE = "public-key"
        const val SHA_256 = "SHA-256"
    }
}

internal interface WebAuthnLibrary : Library {
    fun WebAuthNGetApiVersionNumber(): Int

    fun WebAuthNIsUserVerifyingPlatformAuthenticatorAvailable(available: IntByReference): Int

    fun WebAuthNAuthenticatorMakeCredential(
        window: WinDef.HWND,
        relyingParty: WebAuthnRpInformation,
        user: WebAuthnUserInformation,
        parameters: WebAuthnCoseCredentialParameters,
        clientData: WebAuthnClientData,
        options: WebAuthnMakeCredentialOptions,
        output: PointerByReference,
    ): Int

    fun WebAuthNAuthenticatorGetAssertion(
        window: WinDef.HWND,
        relyingPartyId: WString,
        clientData: WebAuthnClientData,
        options: WebAuthnGetAssertionOptions,
        output: PointerByReference,
    ): Int

    fun WebAuthNFreeCredentialAttestation(attestation: Pointer)

    fun WebAuthNFreeAssertion(assertion: Pointer)

    fun WebAuthNDeletePlatformCredential(credentialIdSize: Int, credentialId: Pointer): Int

    companion object {
        val INSTANCE: WebAuthnLibrary by lazy {
            Native.load("webauthn", WebAuthnLibrary::class.java)
        }
    }
}

@Structure.FieldOrder("dwVersion", "pwszId", "pwszName", "pwszIcon")
internal open class WebAuthnRpInformation : Structure() {
    @JvmField var dwVersion: Int = 1
    @JvmField var pwszId: WString? = null
    @JvmField var pwszName: WString? = null
    @JvmField var pwszIcon: WString? = null
}

@Structure.FieldOrder("dwVersion", "cbId", "pbId", "pwszName", "pwszIcon", "pwszDisplayName")
internal open class WebAuthnUserInformation : Structure() {
    @JvmField var dwVersion: Int = 1
    @JvmField var cbId: Int = 0
    @JvmField var pbId: Pointer? = null
    @JvmField var pwszName: WString? = null
    @JvmField var pwszIcon: WString? = null
    @JvmField var pwszDisplayName: WString? = null
}

@Structure.FieldOrder("dwVersion", "cbClientDataJSON", "pbClientDataJSON", "pwszHashAlgId")
internal open class WebAuthnClientData : Structure() {
    @JvmField var dwVersion: Int = 1
    @JvmField var cbClientDataJSON: Int = 0
    @JvmField var pbClientDataJSON: Pointer? = null
    @JvmField var pwszHashAlgId: WString? = null
}

@Structure.FieldOrder("dwVersion", "pwszCredentialType", "lAlg")
internal open class WebAuthnCoseCredentialParameter : Structure() {
    @JvmField var dwVersion: Int = 1
    @JvmField var pwszCredentialType: WString? = null
    @JvmField var lAlg: Int = 0
}

@Structure.FieldOrder("cCredentialParameters", "pCredentialParameters")
internal open class WebAuthnCoseCredentialParameters : Structure() {
    @JvmField var cCredentialParameters: Int = 0
    @JvmField var pCredentialParameters: Pointer? = null
}

@Structure.FieldOrder("dwVersion", "cbId", "pbId", "pwszCredentialType")
internal open class WebAuthnCredential() : Structure() {
    @JvmField var dwVersion: Int = 1
    @JvmField var cbId: Int = 0
    @JvmField var pbId: Pointer? = null
    @JvmField var pwszCredentialType: WString? = null

    constructor(pointer: Pointer) : this() {
        useMemory(pointer)
    }
}

@Structure.FieldOrder("cCredentials", "pCredentials")
internal open class WebAuthnCredentials : Structure() {
    @JvmField var cCredentials: Int = 0
    @JvmField var pCredentials: Pointer? = null
}

@Structure.FieldOrder("cExtensions", "pExtensions")
internal open class WebAuthnExtensions : Structure() {
    @JvmField var cExtensions: Int = 0
    @JvmField var pExtensions: Pointer? = null
}

@Structure.FieldOrder("cbFirst", "pbFirst", "cbSecond", "pbSecond")
internal open class WebAuthnHmacSecretSalt() : Structure() {
    @JvmField var cbFirst: Int = 0
    @JvmField var pbFirst: Pointer? = null
    @JvmField var cbSecond: Int = 0
    @JvmField var pbSecond: Pointer? = null

    constructor(pointer: Pointer) : this() {
        useMemory(pointer)
    }
}

@Structure.FieldOrder("pGlobalHmacSalt", "cCredWithHmacSecretSaltList", "pCredWithHmacSecretSaltList")
internal open class WebAuthnHmacSecretSaltValues : Structure() {
    @JvmField var pGlobalHmacSalt: Pointer? = null
    @JvmField var cCredWithHmacSecretSaltList: Int = 0
    @JvmField var pCredWithHmacSecretSaltList: Pointer? = null
}

@Structure.FieldOrder(
    "dwVersion",
    "dwTimeoutMilliseconds",
    "CredentialList",
    "Extensions",
    "dwAuthenticatorAttachment",
    "bRequireResidentKey",
    "dwUserVerificationRequirement",
    "dwAttestationConveyancePreference",
    "dwFlags",
    "pCancellationId",
    "pExcludeCredentialList",
    "dwEnterpriseAttestation",
    "dwLargeBlobSupport",
    "bPreferResidentKey",
    "bBrowserInPrivateMode",
    "bEnablePrf",
    "pLinkedDevice",
    "cbJsonExt",
    "pbJsonExt",
    "pPRFGlobalEval",
    "cCredentialHints",
    "ppwszCredentialHints",
    "bThirdPartyPayment",
)
internal open class WebAuthnMakeCredentialOptions : Structure() {
    @JvmField var dwVersion: Int = 8
    @JvmField var dwTimeoutMilliseconds: Int = 0
    @JvmField var CredentialList: WebAuthnCredentials = WebAuthnCredentials()
    @JvmField var Extensions: WebAuthnExtensions = WebAuthnExtensions()
    @JvmField var dwAuthenticatorAttachment: Int = 0
    @JvmField var bRequireResidentKey: Int = 0
    @JvmField var dwUserVerificationRequirement: Int = 0
    @JvmField var dwAttestationConveyancePreference: Int = 0
    @JvmField var dwFlags: Int = 0
    @JvmField var pCancellationId: Pointer? = null
    @JvmField var pExcludeCredentialList: Pointer? = null
    @JvmField var dwEnterpriseAttestation: Int = 0
    @JvmField var dwLargeBlobSupport: Int = 0
    @JvmField var bPreferResidentKey: Int = 0
    @JvmField var bBrowserInPrivateMode: Int = 0
    @JvmField var bEnablePrf: Int = 0
    @JvmField var pLinkedDevice: Pointer? = null
    @JvmField var cbJsonExt: Int = 0
    @JvmField var pbJsonExt: Pointer? = null
    @JvmField var pPRFGlobalEval: Pointer? = null
    @JvmField var cCredentialHints: Int = 0
    @JvmField var ppwszCredentialHints: Pointer? = null
    @JvmField var bThirdPartyPayment: Int = 0
}

@Structure.FieldOrder(
    "dwVersion",
    "dwTimeoutMilliseconds",
    "CredentialList",
    "Extensions",
    "dwAuthenticatorAttachment",
    "dwUserVerificationRequirement",
    "dwFlags",
    "pwszU2fAppId",
    "pbU2fAppId",
    "pCancellationId",
    "pAllowCredentialList",
    "dwCredLargeBlobOperation",
    "cbCredLargeBlob",
    "pbCredLargeBlob",
    "pHmacSecretSaltValues",
    "bBrowserInPrivateMode",
)
internal open class WebAuthnGetAssertionOptions : Structure() {
    @JvmField var dwVersion: Int = 6
    @JvmField var dwTimeoutMilliseconds: Int = 0
    @JvmField var CredentialList: WebAuthnCredentials = WebAuthnCredentials()
    @JvmField var Extensions: WebAuthnExtensions = WebAuthnExtensions()
    @JvmField var dwAuthenticatorAttachment: Int = 0
    @JvmField var dwUserVerificationRequirement: Int = 0
    @JvmField var dwFlags: Int = 0
    @JvmField var pwszU2fAppId: WString? = null
    @JvmField var pbU2fAppId: Pointer? = null
    @JvmField var pCancellationId: Pointer? = null
    @JvmField var pAllowCredentialList: Pointer? = null
    @JvmField var dwCredLargeBlobOperation: Int = 0
    @JvmField var cbCredLargeBlob: Int = 0
    @JvmField var pbCredLargeBlob: Pointer? = null
    @JvmField var pHmacSecretSaltValues: Pointer? = null
    @JvmField var bBrowserInPrivateMode: Int = 0
}

@Structure.FieldOrder(
    "dwVersion",
    "pwszFormatType",
    "cbAuthenticatorData",
    "pbAuthenticatorData",
    "cbAttestation",
    "pbAttestation",
    "dwAttestationDecodeType",
    "pvAttestationDecode",
    "cbAttestationObject",
    "pbAttestationObject",
    "cbCredentialId",
    "pbCredentialId",
    "Extensions",
    "dwUsedTransport",
    "bEpAtt",
    "bLargeBlobSupported",
    "bResidentKey",
    "bPrfEnabled",
    "cbUnsignedExtensionOutputs",
    "pbUnsignedExtensionOutputs",
    "pHmacSecret",
)
internal open class WebAuthnCredentialAttestation() : Structure() {
    @JvmField var dwVersion: Int = 0
    @JvmField var pwszFormatType: WString? = null
    @JvmField var cbAuthenticatorData: Int = 0
    @JvmField var pbAuthenticatorData: Pointer? = null
    @JvmField var cbAttestation: Int = 0
    @JvmField var pbAttestation: Pointer? = null
    @JvmField var dwAttestationDecodeType: Int = 0
    @JvmField var pvAttestationDecode: Pointer? = null
    @JvmField var cbAttestationObject: Int = 0
    @JvmField var pbAttestationObject: Pointer? = null
    @JvmField var cbCredentialId: Int = 0
    @JvmField var pbCredentialId: Pointer? = null
    @JvmField var Extensions: WebAuthnExtensions = WebAuthnExtensions()
    @JvmField var dwUsedTransport: Int = 0
    @JvmField var bEpAtt: Int = 0
    @JvmField var bLargeBlobSupported: Int = 0
    @JvmField var bResidentKey: Int = 0
    @JvmField var bPrfEnabled: Int = 0
    @JvmField var cbUnsignedExtensionOutputs: Int = 0
    @JvmField var pbUnsignedExtensionOutputs: Pointer? = null
    @JvmField var pHmacSecret: Pointer? = null

    constructor(pointer: Pointer) : this() {
        useMemory(pointer)
    }
}

@Structure.FieldOrder(
    "dwVersion",
    "cbAuthenticatorData",
    "pbAuthenticatorData",
    "cbSignature",
    "pbSignature",
    "Credential",
    "cbUserId",
    "pbUserId",
    "Extensions",
    "cbCredLargeBlob",
    "pbCredLargeBlob",
    "dwCredLargeBlobStatus",
    "pHmacSecret",
)
internal open class WebAuthnAssertion() : Structure() {
    @JvmField var dwVersion: Int = 0
    @JvmField var cbAuthenticatorData: Int = 0
    @JvmField var pbAuthenticatorData: Pointer? = null
    @JvmField var cbSignature: Int = 0
    @JvmField var pbSignature: Pointer? = null
    @JvmField var Credential: WebAuthnCredential = WebAuthnCredential()
    @JvmField var cbUserId: Int = 0
    @JvmField var pbUserId: Pointer? = null
    @JvmField var Extensions: WebAuthnExtensions = WebAuthnExtensions()
    @JvmField var cbCredLargeBlob: Int = 0
    @JvmField var pbCredLargeBlob: Pointer? = null
    @JvmField var dwCredLargeBlobStatus: Int = 0
    @JvmField var pHmacSecret: Pointer? = null

    constructor(pointer: Pointer) : this() {
        useMemory(pointer)
    }
}
