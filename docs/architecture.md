# Architecture

RouterHub has three layers, and the boundaries between them are the point of the project.

```
router-api/   Pure-JVM contract: RouterFirmware, ConnectionConfig, models, exceptions, settings types
app/          Host shell: discovery → compatibility → signature check → class loading → sessions → UI
extensions/   (separate repository) one APK per router family, loaded into the app at runtime
```

- **`router-api`** has no Android, no OkHttp and no coroutines runtime in it. The smaller the contract,
  the fewer things an app and an extension built months apart can disagree about.
- **`app`** holds everything generic: the Compose UI, the encrypted router profiles, the session engine
  and the extension loader. It never contains a line of vendor logic.
- **Extensions** live in [routerhub-extensions](https://github.com/atasanbratan/routerhub-extensions) and
  consume `router-api` from JitPack, the way Mihon's extensions consume its `extensions-lib`.

## How an extension gets loaded

```
PackageManager.getInstalledPackages(GET_CONFIGURATIONS | GET_META_DATA | GET_SIGNING_CERTIFICATES)
  └─ filter: <uses-feature android:name="dev.routerhub.extension"/>          ExtensionDiscovery
       └─ meta-data dev.routerhub.extension.api within the supported range?  ExtensionLoader (1)
            └─ every APK signer's SHA-256 is official, development or trusted?   SignatureVerifier (2)
                 └─ ExtensionClassLoader(sourceDir + splits, parent = host)  ExtensionLoader (3)
                      └─ Class.forName(meta-data dev.routerhub.extension.class) as RouterExtension
                           └─ factories → FirmwareSession.open(factory, config)
```

Steps 1 and 2 only read `PackageManager` metadata. No extension bytecode runs until both have passed.

## The rules that make cross-APK loading work

1. **Shared types must have one definition.** A class's identity is its name *and* its class loader.
   `RouterFirmware`, the models, the exceptions and `kotlin.coroutines.Continuation` (hidden in every
   `suspend` signature) must all come from the host. `ExtensionClassLoader` forces its
   `SHARED_PACKAGES` (`java.`, `android.`, `kotlin.`, `kotlinx.coroutines.`, `okhttp3.`, `okio.` and
   `dev.routerhub.api.`) to the host and resolves everything else child-first.
2. **Extensions take the platform as `compileOnly`**: `router-api`, `kotlin-stdlib`,
   `kotlinx-coroutines-core` and `okhttp`. Libraries the host does not provide (Jsoup, JSch) go in
   `implementation` and are bundled.
3. **The host must not obfuscate the platform.** See `app/proguard-rules.pro`.
4. **Do not load code through `createPackageContext(CONTEXT_INCLUDE_CODE)`.** That class loader's parent
   is the boot class loader, so every cast to `RouterFirmware` would fail. Use `createPackageContext`
   (flags = 0) only for the extension's resources.
5. **Load straight from `ApplicationInfo.sourceDir`.** Android 14+ refuses writable dex files, and
   updates land in a new directory, so the path you verified is the code you load.
6. **`router-api` changes are append-only.** New model fields go at the end, with defaults
   (`@JvmOverloads` keeps the old constructors). New interface methods need default bodies
   (`jvmDefault = NO_COMPATIBILITY`). Anything else means raising
   `ExtensionContract.MIN_SUPPORTED_API_VERSION`.

`API_VERSION` rises whenever extensions may start depending on something new (v2: settings pages and
`TELNET`; v3: editable lists). The host loads every version from `MIN_SUPPORTED_API_VERSION` up to its own,
so an extension that declares v1 keeps working on a v3 host.

## Trust and signatures

Loaded extensions run **in-process with the host's UID and permissions** and receive router credentials.
There is no sandbox, so the signature check is the security boundary:

- **Official**: the signer is listed in `routerhub.officialSigners` (a Gradle property that ends up in
  `BuildConfig`). Set it in `~/.gradle/gradle.properties` or pass `-P` on CI.
- **Development**: debug builds of the app also trust their own signing key, so extensions you build
  locally just work.
- **Trusted by you**: unknown signers stay unloaded until you tap *Trust*. That grant is bound to
  package, version code and signer, so every update asks again.

SSH host keys use trust-on-first-use. The first connection throws `SshHostKeyUnknownException` with the
fingerprint, the UI shows it and saves it in the router profile, and the connection is retried with it
pinned. A later change throws `SshHostKeyMismatchException` and is never retried automatically.

Cleartext HTTP is allowed app-wide (`network_security_config.xml`) because router admin pages live on plain
HTTP on private addresses. Telnet is unencrypted and has no host identity to pin: offer it only where
nothing better exists.

## Sessions

`FirmwareSession` wraps one `RouterFirmware` for the UI:

- Calls are serialised with a mutex, so an extension never needs its own locking.
- Every call has a timeout, enforced by cancelling the calling coroutine. Extensions therefore have to
  cooperate with cancellation.
- Reads are retried once after `RouterSessionExpiredException` (the host calls `login()` again).
- **Writes are never replayed.** A write that may have applied is reported and re-read instead of repeated,
  so a flaky connection cannot apply a change twice.

## Settings pages

Extensions describe their configuration pages declaratively and the app renders them generically. See
[settings-pages.md](settings-pages.md).
