import CryptoKit
import Darwin
import Foundation
import LocalAuthentication
import Security

private let formatVersion = 1
private let maximumSecretBytes = 4096
private let maximumEnvelopeBytes = 64 * 1024
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

private struct SealedSecretEnvelope: Codable {
    let version: Int
    let privateKeyRepresentation: Data
    let peerPublicKey: Data
    let salt: Data
    let sealedSecret: Data
}

private func fail(_ code: HelperExit, _ diagnostic: String) -> Never {
    FileHandle.standardError.write(Data(diagnostic.utf8))
    exit(code.rawValue)
}

private func availability() -> Never {
    guard SecureEnclave.isAvailable else {
        fail(.unsupported, "mac-secure-enclave-not-available")
    }
    let context = LAContext()
    var error: NSError?
    if context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) {
        exit(0)
    }
    failAuthentication(error)
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

private func authenticationReason() -> String {
    guard let reason = ProcessInfo.processInfo.environment["KEYSTEAD_TOUCH_ID_REASON"],
          !reason.isEmpty,
          reason.utf8.count <= 160,
          reason.unicodeScalars.allSatisfy({ !CharacterSet.controlCharacters.contains($0) }) else {
        fail(.ioFailure, "mac-touch-id-authentication-reason-missing")
    }
    return reason
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

private func authenticate(context: LAContext, reason: String) {
    context.localizedReason = reason
    var availabilityError: NSError?
    guard context.canEvaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        error: &availabilityError
    ) else {
        failAuthentication(availabilityError)
    }
    let completed = DispatchSemaphore(value: 0)
    var authenticated = false
    var authenticationError: Error?
    context.evaluatePolicy(
        .deviceOwnerAuthenticationWithBiometrics,
        localizedReason: reason
    ) { success, error in
        authenticated = success
        authenticationError = error
        completed.signal()
    }
    completed.wait()
    if !authenticated {
        failAuthentication(authenticationError)
    }
}

private func authenticationError(_ error: Error) -> NSError? {
    var current = error as NSError
    while true {
        if current.domain == LAError.errorDomain { return current }
        guard let underlying = current.userInfo[NSUnderlyingErrorKey] as? NSError else { return nil }
        current = underlying
    }
}

private func storageDirectory() -> URL {
    guard let applicationSupport =
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first else {
        fail(.ioFailure, "mac-secure-enclave-directory-unavailable")
    }
    return applicationSupport
        .appendingPathComponent("Keystead", isDirectory: true)
        .appendingPathComponent("secure-enclave", isDirectory: true)
}

private func storageFile(account: String) -> URL {
    let digest = SHA256.hash(data: Data(account.utf8))
    let filename = digest.map { String(format: "%02x", $0) }.joined() + ".kse1"
    return storageDirectory().appendingPathComponent(filename, isDirectory: false)
}

private func authenticatedData(account: String) -> Data {
    Data((service + "|" + account + "|v1").utf8)
}

private func randomData(count: Int) -> Data {
    var data = Data(count: count)
    let status = data.withUnsafeMutableBytes { bytes in
        SecRandomCopyBytes(kSecRandomDefault, count, bytes.baseAddress!)
    }
    guard status == errSecSuccess else {
        fail(.ioFailure, "mac-secure-enclave-random-failed")
    }
    return data
}

private func derivedKey(
    sharedSecret: SharedSecret,
    salt: Data,
    account: String
) -> SymmetricKey {
    sharedSecret.hkdfDerivedSymmetricKey(
        using: SHA256.self,
        salt: salt,
        sharedInfo: authenticatedData(account: account),
        outputByteCount: 32
    )
}

private func save(account: String) -> Never {
    guard SecureEnclave.isAvailable else {
        fail(.unsupported, "mac-secure-enclave-not-available")
    }
    var secret = FileHandle.standardInput.readDataToEndOfFile()
    defer { secret.resetBytes(in: 0..<secret.count) }
    guard !secret.isEmpty, secret.count <= maximumSecretBytes else {
        fail(.ioFailure, "mac-touch-id-secret-invalid")
    }

    var accessError: Unmanaged<CFError>?
    guard let accessControl = SecAccessControlCreateWithFlags(
        nil,
        kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
        [.privateKeyUsage, .biometryCurrentSet],
        &accessError
    ) else {
        fail(.unavailable, "mac-secure-enclave-access-control-failed")
    }

    do {
        let enclaveKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
            accessControl: accessControl,
            authenticationContext: nil
        )
        let peerKey = P256.KeyAgreement.PrivateKey()
        let sharedSecret = try peerKey.sharedSecretFromKeyAgreement(with: enclaveKey.publicKey)
        let salt = randomData(count: 32)
        let key = derivedKey(sharedSecret: sharedSecret, salt: salt, account: account)
        guard let combined = try AES.GCM.seal(
            secret,
            using: key,
            authenticating: authenticatedData(account: account)
        ).combined else {
            fail(.ioFailure, "mac-secure-enclave-seal-failed")
        }
        let envelope = SealedSecretEnvelope(
            version: formatVersion,
            privateKeyRepresentation: enclaveKey.dataRepresentation,
            peerPublicKey: peerKey.publicKey.x963Representation,
            salt: salt,
            sealedSecret: combined
        )
        let encoder = PropertyListEncoder()
        encoder.outputFormat = .binary
        let encoded = try encoder.encode(envelope)
        guard encoded.count <= maximumEnvelopeBytes else {
            fail(.ioFailure, "mac-secure-enclave-envelope-invalid")
        }
        let directory = storageDirectory()
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        guard chmod(directory.path, S_IRWXU) == 0 else {
            fail(.ioFailure, "mac-secure-enclave-permissions-failed")
        }
        let file = storageFile(account: account)
        try encoded.write(to: file, options: .atomic)
        guard chmod(file.path, S_IRUSR | S_IWUSR) == 0 else {
            try? FileManager.default.removeItem(at: file)
            fail(.ioFailure, "mac-secure-enclave-permissions-failed")
        }
        exit(0)
    } catch {
        if let authentication = authenticationError(error) {
            failAuthentication(authentication)
        }
        fail(.ioFailure, "mac-secure-enclave-save-failed")
    }
}

private func load(account: String) -> Never {
    guard SecureEnclave.isAvailable else {
        fail(.unsupported, "mac-secure-enclave-not-available")
    }
    let file = storageFile(account: account)
    guard FileManager.default.fileExists(atPath: file.path) else {
        fail(.notFound, "mac-secure-enclave-secret-not-found")
    }

    let envelope: SealedSecretEnvelope
    do {
        let encoded = try Data(contentsOf: file, options: .mappedIfSafe)
        guard !encoded.isEmpty, encoded.count <= maximumEnvelopeBytes else {
            fail(.corrupt, "mac-secure-enclave-envelope-invalid")
        }
        envelope = try PropertyListDecoder().decode(SealedSecretEnvelope.self, from: encoded)
    } catch {
        fail(.corrupt, "mac-secure-enclave-envelope-invalid")
    }
    guard envelope.version == formatVersion,
          !envelope.privateKeyRepresentation.isEmpty,
          envelope.privateKeyRepresentation.count <= 4096,
          envelope.peerPublicKey.count == 65,
          envelope.salt.count == 32,
          envelope.sealedSecret.count > 28,
          envelope.sealedSecret.count <= maximumSecretBytes + 28 else {
        fail(.corrupt, "mac-secure-enclave-envelope-invalid")
    }

    let context = LAContext()
    let reason = authenticationReason()
    context.localizedReason = reason
    context.touchIDAuthenticationAllowableReuseDuration = 0
    authenticate(context: context, reason: reason)
    do {
        let enclaveKey = try SecureEnclave.P256.KeyAgreement.PrivateKey(
            dataRepresentation: envelope.privateKeyRepresentation,
            authenticationContext: context
        )
        let peerPublicKey = try P256.KeyAgreement.PublicKey(
            x963Representation: envelope.peerPublicKey
        )
        let sharedSecret = try enclaveKey.sharedSecretFromKeyAgreement(with: peerPublicKey)
        let key = derivedKey(sharedSecret: sharedSecret, salt: envelope.salt, account: account)
        let box = try AES.GCM.SealedBox(combined: envelope.sealedSecret)
        var secret = try AES.GCM.open(
            box,
            using: key,
            authenticating: authenticatedData(account: account)
        )
        defer { secret.resetBytes(in: 0..<secret.count) }
        guard !secret.isEmpty, secret.count <= maximumSecretBytes else {
            fail(.corrupt, "mac-touch-id-secret-invalid")
        }
        FileHandle.standardOutput.write(secret)
        exit(0)
    } catch {
        if let authentication = authenticationError(error) {
            failAuthentication(authentication)
        }
        fail(.corrupt, "mac-secure-enclave-open-failed")
    }
}

private func delete(account: String) -> Never {
    let file = storageFile(account: account)
    guard FileManager.default.fileExists(atPath: file.path) else {
        fail(.notFound, "mac-secure-enclave-secret-not-found")
    }
    do {
        try FileManager.default.removeItem(at: file)
        exit(0)
    } catch {
        fail(.ioFailure, "mac-secure-enclave-delete-failed")
    }
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
