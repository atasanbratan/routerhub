package dev.routerhub.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeNetworkTest {
    private fun ip(text: String) = HomeNetwork.ipv4(text)!!

    @Test
    fun `a router in the phone's subnet is on this network`() {
        assertTrue(HomeNetwork.inSubnet(ip("192.168.1.1"), ip("192.168.1.57"), 24))
        assertTrue(HomeNetwork.inSubnet(ip("10.0.0.1"), ip("10.0.200.9"), 16))
    }

    @Test
    fun `a router in another subnet is not`() {
        assertFalse(HomeNetwork.inSubnet(ip("192.168.0.1"), ip("192.168.1.57"), 24))
        assertFalse(HomeNetwork.inSubnet(ip("192.168.1.1"), ip("192.168.1.57"), 0))
    }

    @Test
    fun `addresses parse, names and broken ones don't`() {
        assertEquals((192 shl 24) or (168 shl 16) or (1 shl 8) or 1, HomeNetwork.ipv4("192.168.1.1"))
        listOf("router.lan", "192.168.1", "192.168.1.256", "fe80::1", "1.2.3.04x", "").forEach { assertNull(it, HomeNetwork.ipv4(it)) }
    }
}
