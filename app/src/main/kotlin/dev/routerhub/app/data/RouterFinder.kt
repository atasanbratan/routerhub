package dev.routerhub.app.data

import android.content.Context
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.app.alerts.HomeNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * A router that answered on this network. [vendor] matches `RouterFirmwareFactory.vendor`, if it could
 * be told; [page] is what it answered, which often names the model.
 */
data class FoundRouter(val host: String, val vendor: String?, val page: String)

/**
 * Looks for the router on the current network. It only fetches public login pages and never sends a
 * password. The gateway comes from the link properties, which need no location permission.
 */
class RouterFinder(context: Context) {
    private val context = context.applicationContext

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .callTimeout(TIMEOUT_MILLIS * 2, TimeUnit.MILLISECONDS)
        .build()

    private fun gateways(): List<String> = HomeNetwork.gateways(HomeNetwork.localLinks(context))

    suspend fun find(): FoundRouter? = withContext(Dispatchers.IO) {
        var unnamed: FoundRouter? = null
        for (host in (gateways() + COMMON_ADDRESSES).distinct()) {
            val pages = PATHS.mapNotNull { fetch(host, it) }
            if (pages.isEmpty()) continue
            val page = pages.joinToString("\n")
            val vendor = vendorOf(page)
            if (vendor != null) return@withContext FoundRouter(host, vendor, page)
            if (unnamed == null) unnamed = FoundRouter(host, null, page)
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

        /**
         * The firmware to suggest for [found]: of its vendor's, the one whose model the page names
         * (a TL-WR940N's login page is titled "TL-WR940N"), otherwise the first that works over the web.
         */
        fun suggest(found: FoundRouter, factories: Collection<RouterFirmwareFactory>): RouterFirmwareFactory? {
            val vendor = found.vendor ?: return null
            val candidates = factories.filter { it.vendor == vendor }.sortedBy { if (it.protocol == Protocol.WEB) 0 else 1 }
            return candidates.firstOrNull { model(it).let { name -> name.length >= 3 && found.page.contains(name, ignoreCase = true) } }
                ?: candidates.firstOrNull()
        }

        /** "AC10 (AC1200)" names the model AC10. */
        private fun model(factory: RouterFirmwareFactory) = factory.displayName.substringBefore(" (").trim()
    }
}
