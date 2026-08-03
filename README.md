# Keystead Client

Keystead Client is the Compose Desktop application for a local-first personal secret vault. It owns local vault files, local encryption and decryption, optional local login, encrypted personal-record synchronization, portable backups, server-assisted reconstruction, and one-off sharing.

Plaintext secrets and the vault data-encryption key (DEK) remain on devices controlled by the user. Keystead Server stores an account's append-only encrypted record stream and short-lived vault-access exchange messages. It never receives the raw DEK, a vault master password, a local-login credential, or biometric data.

## Product model

One server account has one personal encrypted record stream. There are no team vaults, roles, members, invitations, or persistent server device identities.

The main areas have separate prerequisites:

- **Secrets** opens or creates a local `.kvault` and manages its records.
- **Local login** is visible only after a local vault is open. It can add or remove one optional local unlock method for that vault.
- **Account** configures the server URL and owns account creation, sign-in, refresh, and sign-out. It does not require an open vault.
- **Sync** pushes and pulls the signed-in account's encrypted personal records. It requires both an open vault and a server session.
- **Recovery** restores a new local `.kvault` from a portable backup or from a same-account approval on another device.
- **Backup** creates a portable encrypted backup of the open local vault.
- **Share** creates or redeems a one-off encrypted share through the server.
- **Settings** controls language and local vault-file deletion.

A server outage disables only server-backed actions. Local vault unlock, secrets, local login, settings, and portable-backup restore remain local.

## Local vault and local login

A `.kvault` is an encrypted vault, not a JSON export. Its header contains a random DEK wrapped for its configured local unlock methods. Its records are encrypted and authenticated with that DEK.

Every local vault has a master-password unlock method. Local login is an optional convenience unlock method for the same vault:

- On Windows, the local private key can be protected by Windows Hello.
- If biometric-gated storage is unavailable, the local private key can be encrypted with a separate local-login passphrase.
- Only one local-login credential is maintained. It has no computer name, `deviceId`, server registration, proof key, or server-visible public key.
- It is never used for server sign-in or server reconstruction.

Local login is not a defense against a fully compromised, already signed-in desktop session. It is a convenient replacement for typing the vault master password when the operating-system biometric check or local-login passphrase can be satisfied.

## Personal-record synchronization

The server stores one encrypted record event stream per account. Events are append-only during normal synchronization. It does not store a vault header and cannot verify which DEK produced an event.

The client therefore validates every downloaded event locally against the open vault's DEK. Authentic events are imported. Events that fail authentication or do not belong to the open vault are ignored and reported to the user. Deletions are synchronized as authenticated tombstone events.

Sync is available only while the local vault is open. After refreshing the record comparison, the user explicitly selects records to upload. Uploading is idempotent and sends only the selected current encrypted records; it does not advance a global cursor past unselected records.

The same selection can remove server copies. After confirmation, the server physically removes all stored events for those selected record IDs while leaving the local vault unchanged. This privacy operation is different from a synchronized tombstone: another client that still has the record can upload it again, and the server retains a redacted purge audit event.

Different devices can synchronize the same account only after they possess the same DEK. Server-assisted reconstruction supplies that DEK without revealing it to the server.

## Restore from Keystead Server

Server restore is independent from local login and biometrics.

On each server sign-in, the client creates a new memory-only asymmetric exchange key pair and a random request UUID. It uploads only the UUID and public key. The displayed fingerprint binds both values.

On the new device:

1. Configure the server URL and sign in to the account.
2. Open **Recovery → Keystead Server** and keep the access request available.
3. Compare the request fingerprint with an existing device through an independent channel.

On an existing device with the same account and the correct local vault open:

1. Open **Recovery → Approve another device**.
2. Select the pending request and compare its fingerprint.
3. Approve it. The client encrypts the open vault's DEK to the request's ephemeral public key and uploads the encrypted package. It also pushes the current encrypted record snapshot.

Back on the new device, choose a new `.kvault` location and a new local master password. The client downloads and decrypts the DEK package only in memory, reconstructs the local vault, downloads the encrypted records, validates each record with that DEK, and stores only authentic records. The ephemeral private key is destroyed when the session ends.

The server cannot approve a request, decrypt the DEK package, or reconstruct the vault by itself. If no existing device has the correct DEK and no portable backup exists, the encrypted server records cannot be recovered.

## Portable backup

A portable `.ksbackup` is a password-protected complete backup of one vault. Restore first asks for the backup file and a new `.kvault` target. It never silently overwrites an existing target. The backup password decrypts the backup, and a new local master password protects the restored local vault.

Portable backups contain sensitive encrypted material. Use an independent strong password and keep the backup separately from the computer.

## Build and run

Requirements:

- JDK 25 to run Gradle. Kotlin bytecode is explicitly targeted to JVM 24 because the current Kotlin compiler does not yet expose a JVM 25 target; this avoids the implicit-fallback warning while remaining runnable on JDK 25.
- Keystead Core `0.4.4-SNAPSHOT` published to Maven Local.
- Keystead Server only for connected features.

Publish Core locally:

```powershell
cd D:\IdeaProjects\keystead
.\gradlew.bat :keystead-core:publishToMavenLocal --no-daemon
```

Run the client:

```powershell
cd D:\IdeaProjects\keystead-client
.\gradlew.bat run --no-daemon
```

For two isolated test windows, set `KEYSTEAD_CLIENT_HOME` to a different directory before starting each process. This changes only client settings, local-login metadata, refresh-token storage, and the default vault path for that process.

Run tests:

```powershell
.\gradlew.bat test --no-daemon
```

Run the opt-in two-client end-to-end test against a real server:

```powershell
$env:KEYSTEAD_LIVE_TEST_URL='http://localhost:8080'
.\gradlew.bat test --tests top.focess.keystead.client.LiveTwoClientVaultFlowTest --no-daemon
```

That test creates two independent account sessions and two different local vault files. The first session approves the second session's ephemeral request, and the second session must reconstruct the same DEK and decrypt a synchronized record.

The protobuf runtime is a transitive dependency of Google Tink's key serialization; Keystead does not use protobuf as its client/server protocol. The `sun.misc.Unsafe` warning on newer JDKs comes from that dependency. Removing protobuf without replacing Tink would remove part of the cryptographic implementation, so the supported mitigation is to use the supported JDK and keep Tink/protobuf updated.

## Security limits

- Compose text fields use managed JVM strings while values are visible, so perfect erasure cannot be guaranteed.
- Clipboard clearing is best effort and cannot control operating-system clipboard history.
- Biometric protection cannot make a compromised live desktop process trustworthy.
- Server availability cannot replace possession of the correct DEK.
- Deleting the last usable local vault and portable backup can permanently lose access to the encrypted server records.
