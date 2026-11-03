package dev.routerhub.app.data

import android.content.Context
import android.net.ConnectivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

/** A router that answered on this network. [vendor] matches `RouterFirmwareFactory.vendor`, if it could be told. */
data class FoundRouter(val host: String, val vendor: String?)

/**
 * Looks for the router on the current network. It only fetches public login pages and never sends a
 * password. The gateway comes from the link properties, which need no location permission.
 */
class RouterFinder(context: Context) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .callTimeout(TIMEOUT_MILLIS * 2, TimeUnit.MILLISECONDS)
        .build()

    private fun gateway(): String? {
        val links = connectivity.getLinkProperties(connectivity.activeNetwork) ?: return null
        return links.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    }

    suspend fun find(): FoundRouter? = withContext(Dispatchers.IO) {
        var unnamed: FoundRouter? = null
        for (host in (listOfNotNull(gateway()) + COMMON_ADDRESSES).distinct()) {
            val pages = PATHS.mapNotNull { fetch(host, it) }
            if (pages.isEmpty()) continue
            val vendor = vendorOf(pages.joinToString("\n"))
            if (vendor != null) return@withContext FoundRouter(host, vendor)
            if (unnamed == null) unnamed = FoundRouter(host, null)
        }
        unnamed
    }

    private fun fetch(host: String, path: String): String? = try {
        client.newCall(Request.Builder().url("http://$host$path").build()).execute().use { response ->
            response.headers.joinToString("\n") { (name, value) -> "$name: $value" } + "\n" + response.peekBody(MAX_BYTES).string()
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    companion object {
        private const val TIMEOUT_MILLIS = 1_500L
        private const val MAX_BYTES = 64L * 1024
        private val PATHS = listOf("/", "/cgi-bin/luci/")
        private val COMMON_ADDRESSES = listOf("192.168.1.1", "192.168.0.1", "10.0.0.1")

        private val SIGNS = listOf(
            "OpenWrt" to Regex("""luci|openwrt""", RegexOption.IGNORE_CASE),
            "Tenda" to Regex("""tenda""", RegexOption.IGNORE_CASE),
            "TP-Link" to Regex("""tp-?link|\bT[DL]-[WR]""", RegexOption.IGNORE_CASE),
        )

        /** Which vendor a login page belongs to. Only vendors with an extension are worth telling apart. */
        fun vendorOf(page: String): String? = SIGNS.firstOrNull { (_, sign) -> sign.containsMatchIn(page) }?.first
    }
}
