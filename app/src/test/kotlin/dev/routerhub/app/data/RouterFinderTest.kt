package dev.routerhub.app.data

import dev.routerhub.api.Capability
import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.Protocol
import dev.routerhub.api.RouterFirmware
import dev.routerhub.api.RouterFirmwareFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouterFinderTest {
    @Test
    fun `LuCI's login page and header mean OpenWrt`() {
        assertEquals("OpenWrt", RouterFinder.vendorOf("<title>OpenWrt - LuCI</title>"))
        assertEquals("OpenWrt", RouterFinder.vendorOf("x-luci-login-required: yes"))
    }

    @Test
    fun `Tenda and TP-Link are told apart`() {
        assertEquals("Tenda", RouterFinder.vendorOf("<title>Tenda Web Master</title>"))
        assertEquals("TP-Link", RouterFinder.vendorOf("""WWW-Authenticate: Basic realm="TD-W8960N""""))
        assertEquals("TP-Link", RouterFinder.vendorOf("<title>TP-LINK Wireless Router</title>"))
    }

    @Test
    fun `an unknown page names no vendor`() {
        assertNull(RouterFinder.vendorOf("<title>Welcome</title>"))
    }

    private class Factory(override val id: String, override val displayName: String, override val vendor: String, override val protocol: Protocol) : RouterFirmwareFactory {
        override val capabilities = emptySet<Capability>()
        override val defaultPort = 80
        override fun create(config: ConnectionConfig): RouterFirmware = error("not used")
    }

    private val factories = listOf(
        Factory("adsl-telnet", "TD-W8960N", "TP-Link", Protocol.TELNET),
        Factory("adsl", "TD-W8960N", "TP-Link", Protocol.WEB),
        Factory("wr", "TL-WR940N", "TP-Link", Protocol.WEB),
        Factory("tenda", "AC10 (AC1200)", "Tenda", Protocol.WEB),
    )

    @Test
    fun `the model the page names is suggested, otherwise the vendor's web firmware`() {
        val wr = FoundRouter("192.168.0.1", "TP-Link", "<HTML><HEAD><TITLE>TL-WR940N</TITLE>")
        assertEquals("wr", RouterFinder.suggest(wr, factories)?.id)
        assertEquals("adsl", RouterFinder.suggest(FoundRouter("192.168.1.1", "TP-Link", "<title>TP-LINK</title>"), factories)?.id)
        assertEquals("tenda", RouterFinder.suggest(FoundRouter("192.168.0.1", "Tenda", "<title>Tenda Web Master</title>"), factories)?.id)
        assertNull(RouterFinder.suggest(FoundRouter("192.168.0.1", null, "<title>Welcome</title>"), factories))
    }
}
