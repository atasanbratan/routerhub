package dev.routerhub.app.extension

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray

/**
 * The extension repositories Browse lists extensions from, as Mihon's "extension repos": each is the
 * address of an `index.min.json`. The official repository is there until the user removes it.
 */
class ExtensionRepos(context: Context) {
    private val prefs = context.getSharedPreferences("extension_repos", Context.MODE_PRIVATE)

    private val _urls = MutableStateFlow(load())
    val urls: StateFlow<List<String>> = _urls.asStateFlow()

    /** Adds [input] and answers why it couldn't, if it couldn't. */
    fun add(input: String): AddResult {
        val url = normalize(input) ?: return AddResult.INVALID
        if (url in _urls.value) return AddResult.EXISTS
        save(_urls.value + url)
        return AddResult.ADDED
    }

    fun remove(url: String) = save(_urls.value - url)

    fun restoreOfficial() {
        if (OFFICIAL !in _urls.value) save(listOf(OFFICIAL) + _urls.value)
    }

    private fun load(): List<String> {
        val stored = prefs.getString(KEY, null) ?: return listOf(OFFICIAL)
        val array = runCatching { JSONArray(stored) }.getOrNull() ?: return listOf(OFFICIAL)
        return (0 until array.length()).map { array.getString(it) }
    }

    private fun save(urls: List<String>) {
        _urls.value = urls
        prefs.edit { putString(KEY, JSONArray(urls).toString()) }
    }

    enum class AddResult { ADDED, INVALID, EXISTS }

    companion object {
        private const val KEY = "urls"

        /** Published by the extensions repository's Publish workflow, on its `repo` branch. */
        const val OFFICIAL = "https://raw.githubusercontent.com/atasanbratan/routerhub-extensions/repo/index.min.json"

        /**
         * The index address for what the user typed: an https link to the index itself, or to the folder
         * it is in. Plain http is refused, since the index decides which APK is downloaded.
         */
        fun normalize(input: String): String? {
            val trimmed = input.trim().removeSuffix("/")
            val withIndex = if (trimmed.endsWith(".json")) trimmed else "$trimmed/index.min.json"
            val url = withIndex.toHttpUrlOrNull() ?: return null
            if (!url.isHttps || url.host.isEmpty()) return null
            return url.toString()
        }
    }
}
