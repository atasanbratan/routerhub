package dev.routerhub.app.basic

import dev.routerhub.api.ConnectionConfig
import dev.routerhub.api.RouterUnreachableException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

class BasicFirmwareTest {
    @Test
    fun `the page title is read, tidied and kept short`() {
        assertEquals("Archer C6 Login", BasicFirmware.titleOf("<html><head><TITLE>\n  Archer C6\n Login </TITLE>"))
        assertNull(BasicFirmware.titleOf("<title>   </title>"))
        assertNull(BasicFirmware.titleOf("no title here"))
        assertEquals(80, BasicFirmware.titleOf("<title>${"x".repeat(200)}</title>")!!.length)
    }

    @Test
    fun `status says how fast the page answers and what it says about the router`() = runBlocking {
        // A one-page web server: answers every request with a router's login page.
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (_: IOException) {
                    break
                }
                socket.use {
                    val reader = it.getInputStream().bufferedReader()
                    while (reader.readLine().orEmpty().isNotEmpty()) Unit
                    val body = "<html><title>TP-LINK Wireless Router</title></html>"
                    val head = "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\nConnection: close\r\n\r\n"
                    it.getOutputStream().write((head + body).toByteArray())
                }
            }
        }
        try {
            val firmware = BasicFactory.create(ConnectionConfig.Web("127.0.0.1", "admin", "", server.localPort))
            firmware.login()
            val status = firmware.getStatus()
            assertEquals("TP-Link", status.model)
            assertEquals("TP-LINK Wireless Router", status.extras[BasicFirmware.PAGE_TITLE])
            assertTrue(status.extras.getValue(BasicFirmware.RESPONSE_TIME).endsWith(" ms"))
        } finally {
            server.close()
        }
    }

    @Test
    fun `a router whose page doesn't answer is reported as unreachable`() {
        val port = ServerSocket(0).use { it.localPort } // free, and nothing listens on it now
        val firmware = BasicFactory.create(ConnectionConfig.Web("127.0.0.1", "admin", "", port, timeoutMillis = 2_000))
        assertThrows(RouterUnreachableException::class.java) { runBlocking { firmware.login() } }
    }
}
