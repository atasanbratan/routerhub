package dev.routerhub.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RouterIdentityTest {
    private fun page(title: String = "OpenWrt - LuCI", token: String = "a1b2c3", field: String = "luci_password") = """
        <html><head><title>$title</title><script src="/luci-static/resources/luci.js?v=$token"></script></head>
        <body><form><input type="hidden" name="token" value="$token"><input name="luci_username" value="root">
        <input type="password" name="$field"></form><p>Generated at 12:3${token.length}</p></body></html>
    """

    @Test
    fun `the same router looks the same from visit to visit`() {
        assertEquals(RouterIdentity.fromPage("uhttpd", page(token = "a1b2c3")), RouterIdentity.fromPage("uhttpd", page(token = "ffee99887766")))
    }

    @Test
    fun `another title, form or server is another router`() {
        val mine = RouterIdentity.fromPage("uhttpd", page())
        assertNotEquals(mine, RouterIdentity.fromPage("uhttpd", page(title = "Cafe-Router - LuCI")))
        assertNotEquals(mine, RouterIdentity.fromPage("uhttpd", page(field = "password")))
        assertNotEquals(mine, RouterIdentity.fromPage("lighttpd", page()))
    }

    @Test
    fun `a telnet greeting is compared without its numbers`() {
        assertEquals(
            RouterIdentity.fromGreeting("BCM96338 ADSL Router\r\nSession 4711\r\nLogin: "),
            RouterIdentity.fromGreeting("BCM96338 ADSL Router\r\nSession 12\r\nLogin: "),
        )
        assertNotEquals(RouterIdentity.fromGreeting("BCM96338 ADSL Router\r\nLogin: "), RouterIdentity.fromGreeting("ZXHN H108N\r\nLogin: "))
    }

    @Test
    fun `a page that only forwards says where to`() {
        val front = """<meta http-equiv="Expires" content="0" /><meta http-equiv="refresh" content="0; URL=cgi-bin/luci/" />"""
        assertEquals("cgi-bin/luci/", RouterIdentity.redirectOf(front))
        assertEquals(null, RouterIdentity.redirectOf(page()))
    }

    @Test
    fun `a bare page is compared by its text`() {
        assertEquals(RouterIdentity.fromPage("", "<p>Router 1</p>"), RouterIdentity.fromPage("", "<p>Router 22</p>"))
        assertNotEquals(RouterIdentity.fromPage("", "<p>Router</p>"), RouterIdentity.fromPage("", "<p>Modem</p>"))
    }
}
