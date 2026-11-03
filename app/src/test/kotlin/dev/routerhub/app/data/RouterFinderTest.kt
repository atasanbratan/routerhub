package dev.routerhub.app.data

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
}
