package dev.routerhub.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PingerTest {
    @Test
    fun `only private IPv4 addresses are pinged`() {
        listOf("192.168.1.10", "10.0.0.5", "172.16.0.1", "172.31.255.254").forEach { assertTrue(it, Pinger.canPing(it)) }
        listOf(null, "", "8.8.8.8", "172.32.0.1", "192.168.1.300", "fe80::1", "printer.lan", "192.168.1").forEach {
            assertFalse("$it", Pinger.canPing(it))
        }
    }

    @Test
    fun `a reply counts for two missed rounds, not more`() {
        assertTrue(Pinger.isFresh(lastReply = 0, now = Pinger.INTERVAL_MILLIS * 2))
        assertFalse(Pinger.isFresh(lastReply = 0, now = Pinger.FRESH_MILLIS))
        assertFalse(Pinger.isFresh(lastReply = null, now = 0))
    }

    @Test
    fun `only the echo reply with our own sequence number counts`() {
        val reply = byteArrayOf(0, 0, 0, 0, 0, 0, 0x12, 0x34)
        assertTrue(Pinger.isReplyTo(reply, 8, 0x1234))
        assertFalse(Pinger.isReplyTo(reply, 8, 0x1235)) // a late reply to an earlier ping
        assertFalse(Pinger.isReplyTo(byteArrayOf(3, 1, 0, 0, 0, 0, 0x12, 0x34), 8, 0x1234)) // host unreachable
        assertFalse(Pinger.isReplyTo(reply, 4, 0x1234))
    }
}
