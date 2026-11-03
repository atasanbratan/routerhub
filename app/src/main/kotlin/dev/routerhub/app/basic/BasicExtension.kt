package dev.routerhub.app.basic

import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.InvalidConfigurationException
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterExtension
import dev.routerhub.api.RouterFirmware
import dev.routerhub.api.RouterFirmwareFactory
import dev.routerhub.api.RouterStatus
import dev.routerhub.api.RouterUnreachableException
import dev.routerhub.app.data.RouterFinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Basic mode, for routers no extension supports. It never logs in: it only checks that the router's
 * web page answers, and reads what that page says about the router. Built into the app, so it is
 * there in every build. Its labels are translated like an extension's, from i18n files next to it.
 */
class BasicExtension : RouterExtension {
    override val factories: List<RouterFirmwareFactory> = listOf(BasicFactory)
}

object BasicFactory : RouterFirmwareFactory {
    const val ID = "dev.routerhub.basic"

    /** Where people ask for an extension for their router. */
    const val SUPPORT_REQUEST_URL = "https://github.com/atasanbratan/routerhub-extensions/issues/new?template=router_support.yml"
    override val id = ID
    override val displayName = "Any router (basic mode)"

    /** Starts [displayName], so the app shows the name alone instead of "vendor name". */
    override val vendor = "Any router"
    override val protocol = Protocol.WEB
    override val capabilities = setOf(Capability.STATUS)

    override fun create(config: ConnectionConfig): RouterFirmware {
        val web = config as? ConnectionConfig.Web ?: throw InvalidConfigurationException("Basic mode needs a web address")
        return BasicFirmware(web)
    }
}

internal class BasicFirmware(private val config: ConnectionConfig.Web) : RouterFirmware {
    override val capabilities = BasicFactory.capabilities
    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMillis, TimeUnit.MILLISECONDS)
        .build()
    private val url = "${if (config.useHttps) "https" else "http"}://${config.host}:${config.port}/"

    /** Nothing to log in to: this only checks that the page answers. */
    override suspend fun login() {
        fetch()
    }

    override suspend fun getStatus(): RouterStatus {
        val page = fetch()
        val title = titleOf(page.body)
        val brand = RouterFinder.vendorOf(page.headers + "\n" + page.body)
        return RouterStatus(
            model = brand ?: title,
            extras = buildMap {
                put(RESPONSE_TIME, "${page.millis} ms")
                title?.let { put(PAGE_TITLE, it) }
            },
        )
    }

    override suspend fun close() {}

    private class Page(val headers: String, val body: String, val millis: Long)

    private suspend fun fetch(): Page = withContext(Dispatchers.IO) {
        val started = System.nanoTime()
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                Page(
                    response.headers.joinToString("\n") { (name, value) -> "$name: $value" },
                    response.peekBody(MAX_BYTES).string(),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started),
                )
            }
        } catch (e: IOException) {
            throw RouterUnreachableException("The router's web page at $url doesn't answer", e)
        }
    }

    companion object {
        const val RESPONSE_TIME = "Web page answers in"
        const val PAGE_TITLE = "Web page title"
        private const val MAX_BYTES = 64L * 1024
        private val TITLE = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)

        /** The page's title, if it says more than nothing. */
        fun titleOf(html: String): String? =
            TITLE.find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
    }
}
