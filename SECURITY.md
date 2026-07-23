# Security Policy

Keystead is a zero-knowledge vault: the server never sees plaintext secrets or
vault keys, and this repository holds the Keystead client (the Compose Desktop
application that owns the master password, vault key, and plaintext secret
boundary).

## Reporting a vulnerability

Do **not** open a public issue for a security vulnerability. Report it privately
so a fix can be prepared and released before disclosure:

- Open a private security advisory:
  <https://github.com/MidCoard/keystead-client/security/advisories/new>
- Or contact the project owner directly through a private channel.

Please include:

- A description of the issue and its security impact.
- The affected component and, if known, the symbol, screen, or workflow involved.
- A minimal reproduction or proof of concept.
- Any suggested remediation.

The project owner will acknowledge receipt and coordinate a fix and disclosure
timeline. Vulnerabilities must be reported privately before any public
disclosure.

## License

This repository is licensed under the Apache License, Version 2.0. Security
fixes and vulnerability reports accepted by the project are contributed and
released under the same license. See [LICENSE](LICENSE) for the full terms.
