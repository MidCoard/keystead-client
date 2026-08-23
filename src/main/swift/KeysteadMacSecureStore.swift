import Foundation
import LocalAuthentication
import Security

private let service = "top.focess.keystead.touch-id"

private enum HelperExit: Int32 {
    case unsupported = 10
    case unavailable = 11
    case locked = 12
    case accessDenied = 13
    case corrupt = 20
    case ioFailure = 21
    case notFound = 44
}

private func fail(_ code: HelperExit, _ diagnostic: String) -> Never {
    FileHandle.standardError.write(Data(diagnostic.utf8))
    exit(code.rawValue)
}

private func availability() -> Never {
    let context = LAContext()
    var error: NSError?
    if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
        exit(0)
    }
    guard let code = error.flatMap({ LAError.Code(rawValue: $0.code) }) else {
        fail(.unavailable, "mac-touch-id-unavailable")
    }
    switch code {
    case .biometryNotAvailable:
        fail(.unsupported, "mac-touch-id-not-available")
    case .biometryNotEnrolled:
        fail(.unavailable, "mac-touch-id-not-enrolled")
    case .biometryLockout:
        fail(.locked, "mac-touch-id-locked")
    case .authenticationFailed, .userCancel, .appCancel, .systemCancel, .userFallback:
        fail(.accessDenied, "mac-touch-id-authentication-denied")
    default:
        fail(.unavailable, "mac-touch-id-unavailable")
    }
}

private func accountArgument() -> String {
    guard CommandLine.arguments.count == 3 else {
        fail(.ioFailure, "mac-touch-id-account-missing")
    }
    let account = CommandLine.arguments[2]
    guard !account.isEmpty, account.utf8.count <= 255,
          account.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }) else {
        fail(.ioFailure, "mac-touch-id-account-invalid")
    }
    return account
}

private func dataProtectionQuery(account: String) -> [CFString: Any] {
    [
        kSecClass: kSecClassGenericPassword,
        kSecAttrService: service,
        kSecAttrAccount: account,
        kSecUseDataProtectionKeychain: true,
    ]
}

private func legacyQuery(account: String) -> [CFString: Any] {
    [
        kSecClass: kSecClassGenericPassword,
        kSecAttrService: service,
        kSecAttrAccount: account,
    ]
}

private func failAuthentication(_ error: Error?) -> Never {
    guard let nsError = error as NSError?,
          let code = LAError.Code(rawValue: nsError.code) else {
        fail(.unavailable, "mac-touch-id-unavailable")
    }
    switch code {
    case .biometryNotAvailable:
        fail(.unsupported, "mac-touch-id-not-available")
    case .biometryNotEnrolled:
        fail(.unavailable, "mac-touch-id-not-enrolled")
    case .biometryLockout:
        fail(.locked, "mac-touch-id-locked")
    case .authenticationFailed, .userCancel, .appCancel, .systemCancel, .userFallback:
        fail(.accessDenied, "mac-touch-id-authentication-denied")
    default:
        fail(.unavailable, "mac-touch-id-unavailable")
    }
}

private func authenticationReason() -> String {
    let fallback = "Unlock Keystead with Touch ID."
    guard let reason = ProcessInfo.processInfo.environment["KEYSTEAD_TOUCH_ID_REASON"],
          !reason.isEmpty,
          reason.utf8.count <= 160,
          reason.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }) else {
        return fallback
    }
    return reason
}

private func authenticate(reason: String) {
    let context = LAContext()
    context.localizedReason = reason
    var availabilityError: NSError?
    guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &availabilityError) else {
        failAuthentication(availabilityError)
    }
    let completed = DispatchSemaphore(value: 0)
    var authenticated = false
    var authenticationError: Error?
    context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: context.localizedReason) {
        success, error in
        authenticated = success
        authenticationError = error
        completed.signal()
    }
    completed.wait()
    if !authenticated {
        failAuthentication(authenticationError)
    }
}

private func failKeychain(_ status: OSStatus, operation: String) -> Never {
    switch status {
    case errSecNotAvailable:
        fail(.unavailable, "mac-touch-id-keychain-unavailable")
    case errSecInteractionNotAllowed:
        fail(.locked, "mac-touch-id-interaction-not-allowed")
    case errSecAuthFailed, errSecUserCanceled:
        fail(.accessDenied, "mac-touch-id-authentication-denied")
    case errSecDecode:
        fail(.corrupt, "mac-touch-id-keychain-corrupt")
    default:
        fail(.ioFailure, "mac-touch-id-\(operation)-failed")
    }
}

private func save(account: String) -> Never {
    let secret = FileHandle.standardInput.readDataToEndOfFile()
    guard !secret.isEmpty, secret.count <= 4096 else {
        fail(.ioFailure, "mac-touch-id-secret-invalid")
    }
    var accessError: Unmanaged<CFError>?
    guard let access = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        .biometryCurrentSet,
        &accessError
    ) else {
        fail(.unavailable, "mac-touch-id-access-control-failed")
    }
    var query = dataProtectionQuery(account: account)
    query[kSecValueData] = secret
    query[kSecAttrAccessControl] = access
    var status = SecItemAdd(query as CFDictionary, nil)
    if status == errSecMissingEntitlement {
        var legacyAccess: SecAccess?
        let accessStatus = SecAccessCreate("Keystead Touch ID key" as CFString, nil, &legacyAccess)
        guard accessStatus == errSecSuccess, let legacyAccess else {
            failKeychain(accessStatus, operation: "legacy-access")
        }
        query = legacyQuery(account: account)
        query[kSecValueData] = secret
        query[kSecAttrAccess] = legacyAccess
        status = SecItemAdd(query as CFDictionary, nil)
    }
    guard status == errSecSuccess else {
        failKeychain(status, operation: "save")
    }
    exit(0)
}

private func load(account: String) -> Never {
    let reason = authenticationReason()
    let context = LAContext()
    context.localizedReason = reason
    var query = dataProtectionQuery(account: account)
    query[kSecReturnData] = true
    query[kSecMatchLimit] = kSecMatchLimitOne
    query[kSecUseAuthenticationContext] = context
    var result: CFTypeRef?
    var status = SecItemCopyMatching(query as CFDictionary, &result)
    if status == errSecMissingEntitlement || status == errSecItemNotFound {
        authenticate(reason: reason)
        query = legacyQuery(account: account)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne
        result = nil
        status = SecItemCopyMatching(query as CFDictionary, &result)
    }
    if status == errSecItemNotFound {
        fail(.notFound, "mac-touch-id-secret-not-found")
    }
    guard status == errSecSuccess else {
        failKeychain(status, operation: "load")
    }
    guard let data = result as? Data, !data.isEmpty, data.count <= 4096 else {
        fail(.corrupt, "mac-touch-id-secret-invalid")
    }
    FileHandle.standardOutput.write(data)
    exit(0)
}

private func delete(account: String) -> Never {
    var status = SecItemDelete(dataProtectionQuery(account: account) as CFDictionary)
    if status == errSecMissingEntitlement || status == errSecItemNotFound {
        status = SecItemDelete(legacyQuery(account: account) as CFDictionary)
    }
    if status == errSecItemNotFound {
        fail(.notFound, "mac-touch-id-secret-not-found")
    }
    guard status == errSecSuccess else {
        failKeychain(status, operation: "delete")
    }
    exit(0)
}

guard CommandLine.arguments.count >= 2 else {
    fail(.ioFailure, "mac-touch-id-command-missing")
}

switch CommandLine.arguments[1] {
case "availability":
    availability()
case "save":
    save(account: accountArgument())
case "load":
    load(account: accountArgument())
case "delete":
    delete(account: accountArgument())
default:
    fail(.ioFailure, "mac-touch-id-command-invalid")
}
