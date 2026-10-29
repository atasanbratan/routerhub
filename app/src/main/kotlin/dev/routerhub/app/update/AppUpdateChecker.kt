package dev.routerhub.app.update

import dev.routerhub.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A published release, as the About screen shows it. */
data class AppRelease(
    val version: String,
    /** The release notes, Markdown as written on GitHub. */
    val changelog: String,
    val releaseUrl: String,
    /** The APK itself, or the release page when the release carries no APK. */
    val downloadUrl: String,
)

sealed interface UpdateResult {
    data class NewUpdate(val release: AppRelease) : UpdateResult
    data object NoNewUpdate : UpdateResult
}

/**
 * Asks GitHub for the latest release of [repo] ("owner/name"), like Mihon's updater does. Releases are
 * tagged `v<versionName>` by .github/workflows/release.yml; drafts and pre-releases are not "latest",
 * so they are never offered.
 */
class AppUpdateChecker(
    private val repo: String = BuildConfig.GITHUB_REPO,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build(),
) {
    suspend fun check(currentVersion: String = BuildConfig.VERSION_NAME): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = client.newCall(request).execute().use { response ->
            when {
                // No release published yet.
                response.code == 404 -> return@withContext UpdateResult.NoNewUpdate
                !response.isSuccessful -> throw IOException("GitHub answered ${response.code}")
                else -> response.body?.string().orEmpty()
            }
        }
        val release = parseRelease(JSONObject(body))
        if (isNewer(release.version, currentVersion)) UpdateResult.NewUpdate(release) else UpdateResult.NoNewUpdate
    }

    private fun parseRelease(json: JSONObject): AppRelease {
        val releaseUrl = json.getString("html_url")
        val assets = json.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
        return AppRelease(
            version = json.getString("tag_name").removePrefix("v"),
            changelog = json.optString("body"),
            releaseUrl = releaseUrl,
            downloadUrl = apk?.optString("browser_download_url")?.takeIf { it.isNotEmpty() } ?: releaseUrl,
        )
    }

    companion object {
        /**
         * Compares dotted version numbers part by part ("0.10.0" is newer than "0.9.1"). A suffix such as
         * "-debug" is ignored, so a debug build is offered the release of the same version.
         */
        fun isNewer(latest: String, current: String): Boolean {
            val a = parts(latest)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        private fun parts(version: String): List<Int> =
            version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    }
}
