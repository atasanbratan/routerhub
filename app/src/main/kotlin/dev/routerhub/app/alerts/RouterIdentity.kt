package dev.routerhub.app.alerts

import dev.routerhub.api.Protocol
import dev.routerhub.app.data.RouterProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * What a router shows before anyone logs in, boiled down to a short fingerprint. Background checks
 * compare it with the one taken while the user had their router open, and send no login to a router
 * that looks different: another network can use the same address. Not proof, as an identical model
 * at the same address looks the same, but it keeps the login away from most other routers.
 */
object RouterIdentity {
    private const val TIMEOUT_SECONDS = 5L
    private const val MAX_BYTES = 64L * 1024

    /** SSH pins the router's host key and refuses another router before any password is sent. */
    const val SSH = "ssh"

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** The fingerprint of [profile]'s router as it answers now, or `null` if it doesn't answer. */
    suspend fun of(profile: RouterProfile): String? = withContext(Dispatchers.IO) {
        when (profile.protocol) {
            Protocol.SSH -> SSH
            Protocol.WEB -> web(profile)
            Protocol.TELNET -> telnet(profile)
        }
    }

    private class Page(val server: String, val html: String)

    private fun web(profile: RouterProfile): String? = try {
        val url = "${if (profile.useHttps) "https" else "http"}://${profile.host}:${profile.port}/".toHttpUrl()
        val first = fetch(url)
        // Front pages that only forward to the login page, like OpenWrt's, say nothing about the router.
        val next = redirectOf(first.html)?.let(url::resolve)?.takeIf { it.host == url.host && it.port == url.port }
        val page = next?.let(::fetch) ?: first
        fromPage(page.server, page.html)
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun fetch(url: HttpUrl): Page = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
        Page(response.header("Server").orEmpty(), response.peekBody(MAX_BYTES).string())
    }

    private fun telnet(profile: RouterProfile): String? = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(profile.host, profile.port), (TIMEOUT_SECONDS * 1000).toInt())
            socket.soTimeout = (TIMEOUT_SECONDS * 1000).toInt()
            // The greeting up to the login prompt; the router sends it before anything is typed.
            val buffer = ByteArray(512)
            val read = socket.getInputStream().read(buffer)
            if (read <= 0) null else fromGreeting(String(buffer, 0, read, Charsets.ISO_8859_1))
        }
    } catch (_: IOException) {
        null
    }

    private val REFRESH = Regex("""<meta[^>]*http-equiv\s*=\s*["']?refresh["']?[^>]*content\s*=\s*["'][^"']*url\s*=\s*([^"'\s>]+)""", RegexOption.IGNORE_CASE)

    /** Where a `<meta http-equiv="refresh">` page sends the browser, if it is one. */
    fun redirectOf(html: String): String? = REFRESH.find(html)?.groupValues?.get(1)

    private val TITLE = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
    private val FIELD = Regex("""<input[^>]*\bname\s*=\s*["']?([\w.-]+)""", RegexOption.IGNORE_CASE)
    private val SCRIPT = Regex("""<script[^>]*\bsrc\s*=\s*["']?([^"'\s>?]+)""", RegexOption.IGNORE_CASE)

    /**
     * The parts of a login page that stay the same from one visit to the next: the server's name,
     * the title, and the names of its form fields and scripts. Values, tokens and times are left out.
     */
    fun fromPage(server: String, html: String): String {
        val title = TITLE.find(html)?.groupValues?.get(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val fields = FIELD.findAll(html).map { it.groupValues[1] }.toSortedSet()
        val scripts = SCRIPT.findAll(html).map { it.groupValues[1].substringAfterLast('/') }.toSortedSet()
        val parts = "${server.trim()}|$title|${fields.joinToString(",")}|${scripts.joinToString(",")}"
        // A page with none of these is compared by its text instead, without numbers, which change.
        val text = if (parts == "|||") html.filterNot { it.isDigit() }.replace(Regex("\\s+"), " ").trim() else ""
        return hash("web|$parts|$text")
    }

    /** A telnet greeting without digits, which some routers use for the time or a session number. */
    fun fromGreeting(greeting: String): String =
        hash("telnet|" + greeting.filterNot { it.isDigit() || it.isISOControl() }.replace(Regex("\\s+"), " ").trim())

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
