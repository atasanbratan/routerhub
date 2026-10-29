package dev.routerhub.app.extension

import android.content.Context
import dev.routerhub.api.RouterExtension
import dev.routerhub.api.RouterFirmwareFactory

data class ExtensionInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val apiVersion: Int,
    /** SHA-256 of each certificate that signed the installed APK, lower-case hex. */
    val signerSha256: List<String>,
)

/**
 * An extension compiled into the app instead of installed as an APK, as in the Play build, where
 * discovering and loading APKs isn't allowed. [create] runs once, when the extension list is built.
 */
class BundledExtension(
    val packageName: String,
    val label: String,
    val create: () -> RouterExtension,
)

/** Outcome of inspecting one installed extension package. */
sealed interface ExtensionState {
    val info: ExtensionInfo

    data class Loaded(
        override val info: ExtensionInfo,
        val trust: TrustLevel,
        val factories: List<RouterFirmwareFactory>,
        /** Resources-only context of the extension package, for its own icons, strings and assets. */
        val resources: Context,
    ) : ExtensionState

    /** Signed by an unknown key. Its code was NOT loaded; the user may choose to trust it. */
    data class Untrusted(override val info: ExtensionInfo) : ExtensionState

    /** Built against a router-api version this host can't run. Its code was NOT loaded. */
    data class Incompatible(override val info: ExtensionInfo, val reason: String) : ExtensionState

    /** Trusted and compatible, but its code threw while loading. */
    data class Failed(override val info: ExtensionInfo, val error: Throwable) : ExtensionState
}
