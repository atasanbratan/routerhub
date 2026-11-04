package dev.routerhub.app.extension

import android.content.Context
import android.content.pm.PackageManager
import dev.routerhub.api.ExtensionContract
import dev.routerhub.app.update.ApkInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** One repository's index, as far as Browse needs it. */
data class StoreRepo(val url: String, val name: String, val signingKey: String?)

/** An extension a repository offers. */
data class AvailableExtension(
    val repo: StoreRepo,
    val name: String,
    val packageName: String,
    val versionCode: Long,
    val versionName: String,
    val apiVersion: Int,
    val apkUrl: String,
    val size: Long,
    val sha256: String,
) {
    /** Whether this app can run it; the loader would refuse it otherwise. */
    val compatible: Boolean get() = apiVersion in ExtensionContract.MIN_SUPPORTED_API_VERSION..ExtensionContract.API_VERSION
}

/** Where an install started from Browse has got to. */
sealed interface InstallStep {
    data object Downloading : InstallStep
    data class Failed(val reason: InstallFailure) : InstallStep
}

enum class InstallFailure { DOWNLOAD, CHECKSUM, SIGNER, NO_INSTALLER }

/**
 * The extension store: reads the indexes of [repos] and installs what they offer through the system
 * package installer. Nothing here grants trust. An installed extension goes through the same signature
 * check as one installed by hand, so an index can't make the app load code the user didn't trust.
 */
class ExtensionStore(
    private val context: Context,
    private val repos: ExtensionRepos,
    private val scope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient.Builder()
        // Short, so a blocked address gives way to its mirror quickly.
        .connectTimeout(8, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val _available = MutableStateFlow<List<AvailableExtension>>(emptyList())
    val available: StateFlow<List<AvailableExtension>> = _available.asStateFlow()

    /** The repositories that last answered, by index address, for their names. */
    private val _loadedRepos = MutableStateFlow<Map<String, StoreRepo>>(emptyMap())
    val loadedRepos: StateFlow<Map<String, StoreRepo>> = _loadedRepos.asStateFlow()

    /** Index addresses that failed on the last refresh. */
    private val _failedRepos = MutableStateFlow<List<String>>(emptyList())
    val failedRepos: StateFlow<List<String>> = _failedRepos.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Installs in progress or failed, by package name. */
    private val _installs = MutableStateFlow<Map<String, InstallStep>>(emptyMap())
    val installs: StateFlow<Map<String, InstallStep>> = _installs.asStateFlow()

    private var refreshedFor: List<String>? = null
    private var refreshedAt = 0L

    /** Refreshes unless the same repositories were read in the last hour. */
    fun refreshIfStale() {
        val urls = repos.urls.value
        if (urls == refreshedFor && System.currentTimeMillis() - refreshedAt < STALE_MILLIS) return
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        scope.launch { refreshNow() }
    }

    /** Reads every repository and returns once done; for the background update check. */
    suspend fun refreshNow() {
        val urls = repos.urls.value
        _loading.value = true
        try {
            val results = coroutineScope {
                urls.map { url -> async(Dispatchers.IO) { url to runCatching { fetch(url) } } }.awaitAll()
            }
            _available.value = results.flatMap { (_, result) -> result.getOrNull()?.second.orEmpty() }
            _loadedRepos.value = results.mapNotNull { (url, result) -> result.getOrNull()?.let { url to it.first } }.toMap()
            _failedRepos.value = results.filter { (_, result) -> result.isFailure }.map { it.first }
            refreshedFor = urls
            refreshedAt = System.currentTimeMillis()
        } finally {
            _loading.value = false
        }
    }

    /**
     * Reads the index at [url], or at its mirror when [url] can't be reached: some networks block
     * raw.githubusercontent.com but not jsDelivr. The APKs then come from the mirror too.
     */
    private fun fetch(url: String): Pair<StoreRepo, List<AvailableExtension>> {
        val mirror = mirrorOf(url)
        val body = try {
            get(url)
        } catch (e: IOException) {
            if (mirror == null) throw e
            return parseIndex(url, get(mirror), base = mirror)
        }
        return parseIndex(url, body)
    }

    private fun get(url: String): String = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        response.body?.string().orEmpty()
    }

    fun install(extension: AvailableExtension) {
        if (_installs.value[extension.packageName] is InstallStep.Downloading) return
        setStep(extension.packageName, InstallStep.Downloading)
        scope.launch {
            try {
                val apk = withContext(Dispatchers.IO) { download(extension) }
                startInstaller(apk)
                // The system installer asks the user from here. Whether they install or cancel, the
                // package scan is what changes the row, so the button comes back either way.
                clear(extension.packageName)
            } catch (e: InstallException) {
                setStep(extension.packageName, InstallStep.Failed(e.reason))
            }
        }
    }

    fun installAll(extensions: List<AvailableExtension>) = extensions.forEach(::install)

    /** Forgets a failed or finished install, so its row shows its button again. */
    fun clear(packageName: String) = _installs.update { it - packageName }

    private fun setStep(packageName: String, step: InstallStep) = _installs.update { it + (packageName to step) }

    private fun download(extension: AvailableExtension): File {
        val dir = File(context.cacheDir, APK_DIR).apply { mkdirs() }
        // One file per package: a new download replaces the last one, so the folder never grows.
        val file = File(dir, "${extension.packageName}.apk")
        // Like the index: a raw GitHub address that answered a moment ago can still be blocked now.
        val sources = listOfNotNull(extension.apkUrl, mirrorOf(extension.apkUrl))
        val downloaded = sources.any { url ->
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body ?: throw IOException("empty answer")
                    file.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                true
            } catch (e: IOException) {
                false
            }
        }
        if (!downloaded) {
            file.delete()
            throw InstallException(InstallFailure.DOWNLOAD)
        }
        if (sha256(file) != extension.sha256.lowercase()) {
            file.delete()
            throw InstallException(InstallFailure.CHECKSUM)
        }
        val expected = extension.repo.signingKey?.let(SignatureVerifier::normalizeFingerprint)
        if (expected != null && expected !in archiveSigners(file)) {
            file.delete()
            throw InstallException(InstallFailure.SIGNER)
        }
        return file
    }

    private fun archiveSigners(file: File): List<String> {
        val info = context.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return emptyList()
        return SignatureVerifier.currentSigners(info).map { SignatureVerifier.sha256Hex(it.toByteArray()) }
    }

    private fun startInstaller(apk: File) {
        if (!ApkInstaller.install(context, apk)) throw InstallException(InstallFailure.NO_INSTALLER)
    }

    private class InstallException(val reason: InstallFailure) : Exception(reason.name)

    companion object {
        private const val STALE_MILLIS = 60 * 60 * 1000L
        private const val APK_DIR = "store"

        /** Of each package, the newest version this app can run, or the newest at all when it can run none. */
        fun offers(available: List<AvailableExtension>): Map<String, AvailableExtension> =
            available.groupBy { it.packageName }.mapValues { (_, offers) ->
                offers.filter { it.compatible }.maxByOrNull { it.versionCode } ?: offers.maxBy { it.versionCode }
            }

        /** The installed extensions a repository has a newer version of that this app can run. */
        fun updates(installed: List<ExtensionState>, available: List<AvailableExtension>): List<Pair<ExtensionState, AvailableExtension>> {
            val offers = offers(available)
            return installed.mapNotNull { state ->
                val offer = offers[state.info.packageName] ?: return@mapNotNull null
                if (offer.compatible && offer.versionCode > state.info.versionCode) state to offer else null
            }
        }

        private val RAW_GITHUB = Regex("""^https://raw\.githubusercontent\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$""")

        /** The jsDelivr copy of a file on raw.githubusercontent.com, or null for any other address. */
        fun mirrorOf(url: String): String? = RAW_GITHUB.matchEntire(url)?.destructured?.let { (owner, repo, branch, path) ->
            "https://cdn.jsdelivr.net/gh/$owner/$repo@$branch/$path"
        }

        /**
         * Reads an index written by the extensions repository's publish-repo.py, configured as [url] and
         * downloaded from [base]; APK paths are relative to where it was downloaded from.
         */
        fun parseIndex(url: String, body: String, base: String = url): Pair<StoreRepo, List<AvailableExtension>> {
            val json = JSONObject(body)
            val baseUrl = base.toHttpUrl()
            val repo = StoreRepo(
                url = url,
                name = json.optString("name").ifBlank { url.toHttpUrl().host },
                signingKey = json.optString("signingKey").ifBlank { null },
            )
            val list = json.getJSONArray("extensions")
            val extensions = (0 until list.length()).mapNotNull { i ->
                val item = list.getJSONObject(i)
                val apk = baseUrl.resolve(item.getString("apk")) ?: return@mapNotNull null
                // The APK comes from the same kind of address as the index: never plain http.
                if (!apk.isHttps) return@mapNotNull null
                AvailableExtension(
                    repo = repo,
                    name = item.getString("name"),
                    packageName = item.getString("packageName"),
                    versionCode = item.getLong("versionCode"),
                    versionName = item.getString("versionName"),
                    apiVersion = item.getInt("apiVersion"),
                    apkUrl = apk.toString(),
                    size = item.optLong("size"),
                    sha256 = item.getString("sha256"),
                )
            }
            return repo to extensions
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
