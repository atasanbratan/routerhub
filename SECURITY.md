# Security policy

RouterHub stores router credentials and loads third-party code into its own process, so security reports
are taken seriously.

## Supported versions

Fixes go into the latest release and into `main`. The project is pre-1.0, so older versions are not patched.

## Reporting a vulnerability

**Please do not open a public issue.** Report it privately:

1. Open the repository's **Security** tab.
2. Choose **Report a vulnerability** and describe what you found: what it affects, how to reproduce it and
   what you think the impact is.

You will get a first answer within about a week. This is a volunteer project, so that is a best effort, not a
promise. Once a fix is released you will be credited, unless you prefer not to be.

## What counts

- Bypassing the extension signature check or the trust prompt, or loading code that was not verified
- A way for an extension to read another extension's data, or the app's data, across the class loader boundary
- Router credentials leaking: into logs, crash reports, backups, `toString()` output or the network
- A write being replayed, or sent to a different router than the one selected
- SSH host key checks being skipped or weakened

## What does not

- Flaws in a router's own firmware. Report those to the vendor.
- The fact that an extension runs inside the app's process. That is by design and documented in
  [docs/architecture.md](docs/architecture.md): trusting an extension means trusting its code.
- Attacks that need you to trust a malicious extension first.

Telnet is unencrypted and cleartext HTTP is allowed on purpose, because router admin pages usually live on
plain HTTP on a private network. The docs say so wherever it matters.
