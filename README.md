# Keystead Client

Keystead Client is the desktop application for creating, opening, and using an
encrypted Keystead vault. It keeps encryption on your computer and can connect
to a self-hosted Keystead Server to synchronize encrypted records across
verified devices.

The current application targets Kotlin/JVM Compose Desktop. Keystead Core and
Keystead Server are independent repositories; the client uses their vault and
sync contracts but remains responsible for every operation that exposes
plaintext.

## What you can do

- Create or open a password-protected local vault.
- Store logins, secure notes, API tokens, SSH keys, OpenPGP keys, MFA secrets,
  certificates, and generic structured secrets.
- Generate passwords, API tokens, SSH/OpenPGP key pairs, MFA seeds, and
  certificates from the application.
- Search and filter records by type and taxonomy fields.
- Reveal a protected field temporarily and copy it through an expiring
  clipboard workflow.
- Edit or delete records while preserving sync revisions and tombstones.
- Sign in to a Keystead Server with a short-lived bearer session.
- Enroll and verify a device using a challenge and local signing key.
- Push and pull encrypted records, handle revision conflicts, and synchronize
  deletions.
- Invite people to a vault, accept or decline invitations, assign owner/admin/
  editor/viewer roles, and package the vault key for every eligible device.
- Remove members and complete a mandatory, resumable key rotation before new
  writes continue.
- Protect the local device identity with Windows DPAPI, macOS Keychain, or
  Linux Secret Service, or explicitly choose a passphrase file or memory-only
  identity.
- Recover a server account and its vault access from an offline recovery kit or
  approval by an existing verified device.
- Export and restore encrypted backups with conflict reporting.

## How your data is protected

Your master password opens a random vault key stored in wrapped form. Secret
payloads are authenticated and encrypted locally before they are written to
disk or sent to a server. The server receives ciphertext and synchronization
state, not the plaintext secret or raw vault key.

The application also treats the unlocked desktop session as a sensitive state:

- closing or locking the vault closes the local vault handle;
- revealed values expire after a short timeout;
- selecting, editing, deleting, or locking clears reveal state; a pulled
  deletion also clears the selection and reveal;
- copied values are cleared after a timeout only if the clipboard still
  contains the value Keystead placed there;
- generated drafts are cleared after successful save, cancel, type change, or
  lock;
- server passwords are copied into a temporary array for login and cleared from
  UI state afterward;
- device private keys can be stored behind the signed-in operating-system
  user's native secret facility;
- native storage never silently falls back: passphrase-file and memory-only
  modes require an explicit choice;
- recovery kits, recovery private keys, and replacement-device packages are
  created or opened locally rather than by the server.

Compose text fields use immutable JVM strings while visible. Keystead bounds
their lifetime but cannot guarantee perfect erasure from managed memory. Any
malware controlling the desktop process while the vault is open can potentially
capture displayed or copied secrets.

## Typical workflow

1. Choose a local vault directory and vault ID.
2. Enter a master password and open or create the vault.
3. Select a secret type, complete its fields, and save it.
4. Select a record to reveal, copy, edit, or delete it. Reveal and clipboard
   state are temporary.
5. Optionally create a server account and sign in. The password is not retained
   as the normal request credential; the client uses a memory-only bearer
   session.
6. Choose OS-user-protected storage, a passphrase-encrypted identity file, or a
   non-persistent memory-only identity. Existing passphrase identities can be
   migrated only after save, reload, signature, and wrapping-key verification.
7. Create or unlock a local device identity, register its public proof and
   wrapping keys, complete the challenge, and publish eligible key packages.
8. Push local encrypted changes and pull remote pages. If the server has a newer
   revision, pull first and resolve the conflict locally.
9. Optionally manage members and recovery from the protection panel. A removed
   member blocks writes until the new vault key has been packaged for every
   remaining target and committed.
10. Lock the vault when finished. This clears the selected secret, reveal state,
   form fields, generated values, and relevant in-memory sessions.

## Supported secret types

| Type | Examples |
| --- | --- |
| Login/password | Website or application credentials |
| Secure note | Recovery instructions or sensitive text |
| API token | Service tokens with endpoint/account context |
| SSH key | Private/public key material and passphrase |
| OpenPGP key | Private/public key material and identity |
| MFA secret | Seed and `otpauth` URI |
| Certificate | X.509 certificate, private key, and passphrase |
| Generic secret | Custom fields allowed by the generic schema |

The form model is checked against the canonical schema supplied by Keystead
Core. Strict secret types reject unknown or incomplete protected fields rather
than silently discarding them.

## Synchronization and devices

The local vault works without a server. Connecting a server adds encrypted
multi-device synchronization, not server-side decryption.

Each device maintains two independent key roles:

- a proof key signs the enrollment challenge and establishes possession;
- a wrapping key receives a client-encrypted vault-key package.

Only verified, non-revoked devices with complete approved wrapping material are
eligible for packages. Revoking a device stops future device-bound sessions and
packages, but it cannot remove data that the device already decrypted or saved.

Sync is revision based. Deletes travel as tombstones, and pages advance using a
stored cursor. The client validates response shapes and fails closed on malformed
lifecycle rows. A revision conflict does not overwrite the newer server row;
the UI reports that a pull is required.

## Sharing and key rotation

Sharing is whole-vault and device specific. An invitation alone carries no key.
After the recipient accepts, the membership waits in a pending state until an
owner or administrator encrypts the current vault key separately for an
eligible recipient device. The server stores that opaque package, and the
recipient opens it using the device private key held by this client.

Roles govern server operations:

| Role | Vault access |
| --- | --- |
| Owner | Controls the vault, membership, packages, and rotation |
| Administrator | Manages members and packages and can read/write encrypted rows |
| Editor | Reads and writes encrypted rows |
| Viewer | Reads encrypted rows but cannot publish changes |

Package coverage shows which verified devices still need the current key.
Keystead publishes to every uncovered supported device rather than assuming
that the signed-in user's device is the only recipient.

Removing an active member immediately removes that member's server access and
packages, then blocks new writes with `ROTATION_REQUIRED`. The client prepares
the next key locally, obtains the server's exact device/automation/recovery
target snapshot, wraps the key for each target, and waits for complete
coverage. It then commits the locally re-encrypted vault followed by the server
generation. A public checkpoint file stores only vault, generation, device,
and key IDs, so the operation can resume after interruption without persisting
a raw key.

Rotation protects future versions. It cannot erase information that a former
member already decrypted, exported, photographed, or copied.

## Account and vault recovery

Keystead offers two recovery paths, and both preserve the zero-knowledge
boundary.

### Offline recovery kit

When you create a kit, the client generates recovery key material and an
account-recovery credential. The server receives only the credential hash, the
recovery public key, an encrypted recovery private key, and the vault key
wrapped for that recovery enrollment. The displayed kit is a one-time handoff:
store it offline, separate from the computer and server.

During recovery, the kit authenticates a short-lived session and decrypts the
recovery private key locally. The client unwraps each current vault key in a
temporary store, immediately rewraps it for the replacement device, and asks
the server to change the account password and enroll that device atomically.
Temporary key material and directories are removed after the operation.

### Existing verified-device approval

A replacement device creates a canonical request containing its public proof
and wrapping keys. An existing verified device reviews the fingerprint, wraps
the current vault keys for the replacement device, and signs the request. The
replacement device signs the same request to claim a single-use recovery
session, sets a new server password, and becomes verified. The approving device
never sends a raw vault key or private key to the server.

Recovery cannot reconstruct information from nothing. If the offline kit,
every verified device, all usable local headers, and every backup are lost,
vault access is permanently lost by design.

## Reveal, clipboard, and local storage safety

Reveal is deliberately separate from selecting a record. Pressing **Reveal**
decrypts the default protected field for that record type and starts a bounded
timer. **Copy** is available only while a value is revealed.

Clipboard clearing is conditional: Keystead stores a SHA-256 digest and clears
the clipboard at expiry only when the current contents still match. If you copy
something else, Keystead leaves your replacement untouched. The desktop AWT
clipboard API is best effort; operating systems and clipboard-history tools may
retain copies outside the application's control.

## OS-level device identity protection

The native mode generates a random 256-bit storage key, protects that key with
the current operating-system user facility, and uses AES-256-GCM to encrypt the
client's bounded secure-storage container. Device wrapping and proof private
keys are stored in that container; the metadata file contains only public keys,
algorithms, device ID, format version, and capability.

| Platform | Native provider | Scope |
| --- | --- | --- |
| Windows | DPAPI with `CRYPTPROTECT_UI_FORBIDDEN` and instance entropy | Current Windows user |
| macOS | Keychain generic-password APIs through Security.framework | Current login Keychain user |
| Linux | Secret Service over D-Bus | Current unlocked desktop collection |

The application calls native APIs in-process. It does not pass secrets through
shell commands or process arguments. Availability is verified by a random
write/read/delete probe. A locked, denied, missing, or corrupt provider is
reported with a stable non-secret diagnostic and does not trigger fallback.

OS-user protection is not the same as biometric gating. Keystead does not claim
Windows Hello, Touch ID, or Linux biometric verification; another process with
the same user authority may be within the platform provider's trust boundary.
Passphrase-file mode keeps format-1 and format-2 identities readable, and
memory-only mode deliberately discards the identity at exit.

## Run the desktop app

Requires JDK 21.

```bash
./gradlew run
```

On Windows:

```powershell
.\gradlew.bat run
```

The default UI is configured for a local server at `http://localhost:8080`, but
the server is optional for local-only vault use.

## Verification

```bash
./gradlew test --no-daemon --rerun-tasks
```

The current complete client suite contains 160 tests covering local sessions,
typed forms, bearer authentication, device enrollment, sync pagination and
tombstones, backup flows, reveal/clipboard lifecycle, secure storage, response
validation, and redaction behavior.

## Current scope

- Desktop JVM only; there are no Android or iOS applications.
- No browser extension, browser autofill, desktop auto-type, or passkey storage.
- No biometric-gated unlock or passkey/WebAuthn login.
- No email-alias service, breach monitoring, or attachment workflow.
- Collaboration is whole-vault; there are no per-record ACLs, public links, or
  asynchronous emergency-access grants.
- Native packages are built per desktop platform, and Linux native protection
  requires a working Secret Service session.

Keystead Client is currently intended for technical users evaluating a
local-first, self-hostable encrypted vault and for self-hosters testing the
complete account, device, collaboration, rotation, and recovery lifecycle.
