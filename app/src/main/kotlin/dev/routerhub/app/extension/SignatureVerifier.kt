package dev.routerhub.app.extension

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import androidx.core.content.edit
import java.security.MessageDigest

enum class TrustLevel {
    /** Signed with one of the project's official release keys. */
    OFFICIAL,

    /** Signed with the same key as this host build (debug builds only), for local development. */
    DEVELOPMENT,

    /** Unknown key the user explicitly chose to trust for this exact package version. */
    USER_TRUSTED,

    /** Compiled into this build of the app (the Play build); there is no APK to check. */
    BUILT_IN,
}

sealed interface SignatureVerdict {
    val signerSha256: List<String>

    data class Trusted(val level: TrustLevel, override val signerSha256: List<String>) : SignatureVerdict
    data class Untrusted(override val signerSha256: List<String>) : SignatureVerdict
}

/**
 * Decides whether an extension's code may be loaded, BEFORE any of it runs.
 *
 * This is the actual security boundary of the plugin system. Loaded extension code runs inside our
 * process with our permissions and sees the router credentials we hand it, so there is no sandbox
 * behind this check.
 */
class SignatureVerifier(
    officialSigners: Collection<String>,
    developmentSigners: Collection<String>,
    private val userTrust: UserTrustStore,
) {
    private val official = officialSigners.mapNotNull(::normalizeFingerprint).toSet()
    private val development = developmentSigners.mapNotNull(::normalizeFingerprint).toSet()

    fun verify(packageInfo: PackageInfo): SignatureVerdict {
        val signers = currentSigners(packageInfo).map { sha256Hex(it.toByteArray()) }
        if (signers.isEmpty()) return SignatureVerdict.Untrusted(signers)

        // With several signers (rare, v1-signed APKs), require ALL of them to be trusted, so an
        // attacker can't co-sign an APK with our key plus theirs.
        return when {
            signers.all { it in official } -> SignatureVerdict.Trusted(TrustLevel.OFFICIAL, signers)
            signers.all { it in development } -> SignatureVerdict.Trusted(TrustLevel.DEVELOPMENT, signers)
            userTrust.isTrusted(packageInfo, signers) ->
                SignatureVerdict.Trusted(TrustLevel.USER_TRUSTED, signers)
            else -> SignatureVerdict.Untrusted(signers)
        }
    }

    companion object {
        /**
         * The certificate(s) that signed the APK actually installed right now.
         *
         * We use apkContentsSigners and deliberately ignore signingCertificateHistory (key rotation
         * lineage): a key that was rotated away, possibly because it leaked, must no longer be able
         * to vouch for code. When we rotate our own key, the new fingerprint goes into the official
         * list instead.
         */
        fun currentSigners(packageInfo: PackageInfo): List<Signature> =
            packageInfo.signingInfo?.apkContentsSigners?.toList().orEmpty()

        /** Fingerprints of the certificate that signed this host app. */
        fun hostSigners(context: Context): List<String> {
            val info = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            return currentSigners(info).map { sha256Hex(it.toByteArray()) }
        }

        fun sha256Hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Accepts `AB:CD:…` (keytool / apksigner output) or plain hex; returns lower-case hex. */
        fun normalizeFingerprint(raw: String): String? =
            raw.replace(":", "").trim().lowercase().takeIf { it.matches(SHA256_HEX) }

        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

/**
 * Remembers extensions the user explicitly trusted despite an unknown signer (Mihon's model).
 *
 * Trust is bound to package + versionCode + signer, so any update or re-signing asks again.
 */
class UserTrustStore(context: Context) {
    private val prefs = context.getSharedPreferences("extension_trust", Context.MODE_PRIVATE)

    fun isTrusted(packageInfo: PackageInfo, signers: List<String>): Boolean =
        token(packageInfo.packageName, packageInfo.longVersionCode, signers) in trustedTokens()

    fun trust(info: ExtensionInfo) {
        val token = token(info.packageName, info.versionCode, info.signerSha256)
        // Replace older grants for the same package rather than accumulating them.
        val updated = trustedTokens().filterNot { it.startsWith("${info.packageName}:") }.toSet() + token
        prefs.edit { putStringSet(KEY, updated) }
    }

    fun revoke(packageName: String) {
        prefs.edit { putStringSet(KEY, trustedTokens().filterNot { it.startsWith("$packageName:") }.toSet()) }
    }

    private fun trustedTokens(): Set<String> = prefs.getStringSet(KEY, null).orEmpty()

    private fun token(packageName: String, versionCode: Long, signers: List<String>) =
        "$packageName:$versionCode:${signers.sorted().joinToString(",")}"

    private companion object {
        const val KEY = "trusted"
    }
}
