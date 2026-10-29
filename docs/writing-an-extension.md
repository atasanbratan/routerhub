# Writing an extension

An extension teaches RouterHub one router family. It is an Android application module with **no activities,
no services and no permissions**: only code that the app loads into its own process. Start from the
[Demo extension](https://github.com/atasanbratan/routerhub-extensions/tree/main/extensions/demo), which
exercises every part of the contract with no network.

## 1. The module

Create a new Android application module in the
[extensions repository](https://github.com/atasanbratan/routerhub-extensions) (or in your own repository):

```kotlin
android {
    namespace = "dev.example.routerhub.acme"
    defaultConfig {
        // Must equal the namespace: the manifest's relative entry class is resolved against it.
        applicationId = "dev.example.routerhub.acme"
        minSdk = 28
    }
    buildTypes { release { isMinifyEnabled = false } } // see "Rules" below
}

dependencies {
    // The platform the app hands to extensions at runtime. compileOnly = compile against it, do not package it.
    compileOnly("com.github.atasanbratan.routerhub:router-api:<tag>")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:<version>")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:<version>")
    compileOnly("com.squareup.okhttp3:okhttp:<version>")

    // Anything else is private to your extension: packaged in the APK and resolved child-first.
    implementation("org.jsoup:jsoup:<version>")
}
```

`router-api` comes from JitPack; add `maven("https://jitpack.io")` to your repositories. Use the versions the
extensions repository pins in its `gradle/libs.versions.toml`, so your extension links to the same copies
the app provides.

## 2. The manifest

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- the tag the app scans for -->
    <uses-feature android:name="dev.routerhub.extension" android:required="false" />

    <application android:allowBackup="false" android:hasCode="true" android:label="RouterHub: Acme">
        <!-- a public no-arg class implementing RouterExtension; a leading dot is relative to the package -->
        <meta-data android:name="dev.routerhub.extension.class" android:value=".AcmeExtension" />
        <!-- the router-api version you compiled against (see ExtensionContract.API_VERSION) -->
        <meta-data android:name="dev.routerhub.extension.api" android:value="3" />
    </application>
</manifest>
```

Several entry classes can be listed, separated by `;`. Permissions declared here are irrelevant: the code runs
with the app's UID, so the app holds the permissions.

## 3. The entry point and the factory

```kotlin
class AcmeExtension : RouterExtension {          // public, no-arg, cheap: no I/O, no threads
    override val factories = listOf(AcmeFactory())
}

internal class AcmeFactory : RouterFirmwareFactory {
    override val id = "acme.ax1800"              // globally unique and permanent: saved routers refer to it
    override val displayName = "AX1800"
    override val vendor = "Acme"
    override val protocol = Protocol.WEB         // WEB, SSH or TELNET: decides which ConnectionConfig you get
    override val capabilities = setOf(Capability.STATUS, Capability.CONNECTED_CLIENTS, Capability.REBOOT)

    override fun create(config: ConnectionConfig): RouterFirmware {
        val web = config as? ConnectionConfig.Web
            ?: throw InvalidConfigurationException("$displayName is managed over HTTP")
        return AcmeFirmware(web, capabilities)   // creating a session must not do I/O
    }
}
```

One extension can ship several factories, for example one per hardware revision or one per protocol.

## 4. The firmware

`RouterFirmware` is a live session with one router. The contract:

- **Every function is main-safe.** Do blocking work on your own dispatcher (`Dispatchers.IO`).
- **Cooperate with cancellation.** The app enforces timeouts by cancelling the calling coroutine, so blocking
  calls must be cancellable (`OkHttp Call.cancel()`, interruptible SSH reads, ...).
- **Only throw `RouterException` subtypes.** Anything else is reported as a bug in the extension.
  `WrongCredentialsException`, `RouterBusyException`, `RouterSessionExpiredException`,
  `FirmwareParseException`, `RouterUnreachableException` and the others each get a specific reaction in the UI.
- The app **serialises calls** on a session, so you need no internal locking.
- Optional operations have default bodies that throw `CapabilityNotSupportedException`. Override exactly the
  ones you list in `capabilities`.

```kotlin
internal class AcmeFirmware(
    private val config: ConnectionConfig.Web,
    override val capabilities: Set<Capability>,
) : RouterFirmware {

    override suspend fun login() { /* sign in; throw WrongCredentialsException if refused */ }

    override suspend fun getStatus() = RouterStatus(
        model = "AX1800",
        firmwareVersion = "1.2.3",
        uptimeSeconds = 86_400,
        wanIpv4 = "203.0.113.10",
        wifiRadios = listOf(WifiRadio(id = "wl0", band = WifiBand.GHZ_2_4, enabled = true, ssid = "Home")),
    )

    override suspend fun getConnectedClients() = listOf(
        ConnectedClient(macAddress = "02:00:00:00:00:01", ipAddress = "192.168.1.20", hostname = "Laptop"),
    )

    override suspend fun reboot() { /* ... */ }

    override suspend fun close() { /* log out and release sockets; idempotent, never throws */ }
}
```

Capabilities are `STATUS`, `DSL_STATS`, `CONNECTED_CLIENTS`, `REBOOT`, `WIFI_TOGGLE` and `SETTINGS`. The app
only offers controls for the ones you declare.

## 5. Settings pages (optional)

Add `Capability.SETTINGS` and implement `listSettingsPages()`, `readSettings()` and `writeSettings()`. You
describe the fields; the app renders the form. See [settings-pages.md](settings-pages.md) for the field types,
lists and the rules.

## 6. Test it without a router

Keep the router-specific parsing in **pure functions** and unit-test them against captured pages or command
output. Scrub the captures first: no real SSIDs, passwords, serial numbers, MAC addresses or hostnames. Use
locally administered MACs (`02:00:00:00:00:xx`) and documentation addresses (`192.0.2.0/24`,
`198.51.100.0/24`, `203.0.113.0/24`).

For code that sends requests, put a small interface in front of the transport and give the tests a fake that
records what was requested. Then you can assert the exact bytes a "Save" button would have sent, which is how
the TP-Link extension pins its request builders.

Real-hardware tests belong behind an environment variable and must restore what they change. The TP-Link
extension's `TpLinkLiveWriteTest` is a worked example.

## 7. Try it in the app

```bash
./gradlew :extensions:acme:assembleDebug
adb install -r extensions/acme/build/outputs/apk/debug/acme-debug.apk
```

Open **Browse → Extensions**. An extension signed with the same debug key as your app is shown as a
*Development build* and loads at once; others show **Trust**.

## Rules worth knowing

- **Do not minify.** The app finds your entry class by its name in the manifest meta-data, and R8 does not treat
  meta-data as a keep root. Extensions are small, so leave `isMinifyEnabled = false`.
- **Do not bundle the platform.** `router-api`, the Kotlin stdlib, coroutines and OkHttp come from the app. The
  app's copies always win, so bundling them only adds dead weight.
- **`router-api` is append-only.** New model fields go at the end with defaults, new interface methods need
  default bodies, and `API_VERSION` rises when extensions may start relying on something new. An extension
  compiled against v1 keeps working on a v3 app.
- **Secrets stay secret.** Never log credentials or return a stored secret to the app. Every `ConnectionConfig`
  redacts its `toString()`; keep it that way for anything you add.
- **Ids are forever.** Changing a factory `id` orphans every router saved with it.

See [architecture.md](architecture.md) for why these rules exist.
