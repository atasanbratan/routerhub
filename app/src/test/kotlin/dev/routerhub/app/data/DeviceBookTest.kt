package dev.routerhub.app.data

import dev.routerhub.api.ConnectedClient
import dev.routerhub.api.ConnectionMedium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceBookTest {
    private val phone = ConnectedClient("02:00:00:00:00:01", "192.0.2.10", "phone", medium = ConnectionMedium.WIRELESS)
    private val laptop = ConnectedClient("02:00:00:00:00:02", "192.0.2.11")

    @Test
    fun `seeing a device remembers it and keeps its name`() {
        val named = listOf(KnownDevice(phone.macAddress, alias = "Pat's phone", known = true))
        val device = DeviceBook.record(named, listOf(phone), 1000).single()
        assertEquals("Pat's phone", device.alias)
        assertTrue(device.known)
        assertEquals("phone", device.hostname)
        assertEquals(1000L, device.lastSeen)
    }

    @Test
    fun `a device that is only blocked is not counted as seen`() {
        val away = ConnectedClient(laptop.macAddress, online = false, internetBlocked = true)
        assertTrue(DeviceBook.record(emptyList(), listOf(away), 1000).isEmpty())
    }

    @Test
    fun `old address details stay when the router stops reporting them`() {
        val before = DeviceBook.record(emptyList(), listOf(phone), 1000)
        val device = DeviceBook.record(before, listOf(phone.copy(ipAddress = null, hostname = null)), 2000).single()
        assertEquals("192.0.2.10", device.ipAddress)
        assertEquals("phone", device.hostname)
    }

    @Test
    fun `rows are online, then blocked and away, then seen before, latest first`() {
        val blocked = ConnectedClient("02:00:00:00:00:03", online = false, internetBlocked = true)
        val devices = listOf(
            KnownDevice("02:00:00:00:00:04", lastSeen = 10),
            KnownDevice("02:00:00:00:00:05", lastSeen = 20),
            KnownDevice(phone.macAddress, lastSeen = 30),
        )
        val rows = DeviceBook.rows(listOf(blocked, phone), devices)
        assertEquals(
            listOf(DeviceState.ONLINE, DeviceState.BLOCKED, DeviceState.SEEN, DeviceState.SEEN),
            rows.map { it.state },
        )
        assertEquals("02:00:00:00:00:05", rows[2].client.macAddress)
    }

    @Test
    fun `a blocked device keeps its name in the list`() {
        val blocked = ConnectedClient(laptop.macAddress, online = false, internetBlocked = true)
        val row = DeviceBook.rows(listOf(blocked), listOf(KnownDevice(laptop.macAddress, alias = "Kids tablet"))).single()
        assertEquals("Kids tablet", row.title)
    }

    @Test
    fun `a name given to a device nobody has seen yet doesn't list it as seen`() {
        assertTrue(DeviceBook.rows(emptyList(), listOf(KnownDevice(laptop.macAddress, alias = "x"))).isEmpty())
    }

    @Test
    fun `the name falls back to the host name, then the address`() {
        assertEquals("phone", DeviceRow(phone, null, DeviceState.ONLINE).title)
        assertEquals(laptop.macAddress, DeviceRow(laptop, null, DeviceState.ONLINE).title)
    }

    @Test
    fun `update adds a new device`() {
        val device = DeviceBook.update(emptyList(), laptop.macAddress) { it.copy(known = true) }.single()
        assertTrue(device.known)
        assertNull(device.lastSeen)
    }

    @Test
    fun `the list is capped, and named devices stay`() {
        val many = (0 until 300).map { KnownDevice("mac$it", lastSeen = it.toLong()) } + KnownDevice("named", alias = "keep", lastSeen = 0)
        val kept = DeviceBook.record(many, emptyList(), 1000)
        assertEquals(200, kept.size)
        assertTrue(kept.any { it.mac == "named" })
        assertTrue(kept.any { it.mac == "mac299" })
    }

    @Test
    fun `a device never seen on the router is new`() {
        val book = DeviceBook.record(emptyList(), listOf(phone), now = 1)
        assertEquals(listOf(laptop), DeviceBook.newDevices(book, listOf(phone, laptop)))
    }

    @Test
    fun `the first look at a router only learns, it reports nothing`() {
        assertTrue(DeviceBook.newDevices(emptyList(), listOf(phone, laptop)).isEmpty())
    }

    @Test
    fun `a blocked device that is away isn't new`() {
        val book = DeviceBook.record(emptyList(), listOf(phone), now = 1)
        val away = ConnectedClient("02:00:00:00:00:09", online = false, internetBlocked = true)
        assertTrue(DeviceBook.newDevices(book, listOf(phone, away)).isEmpty())
    }

    private val minute = 60_000L

    @Test
    fun `sightings close together make one visit, a gap starts a new one`() {
        var visits = DeviceBook.extendVisits(emptyList(), 0)
        visits = DeviceBook.extendVisits(visits, 15 * minute)
        visits = DeviceBook.extendVisits(visits, 30 * minute)
        assertEquals(listOf(Visit(0, 30 * minute)), visits)
        visits = DeviceBook.extendVisits(visits, 30 * minute + DeviceBook.VISIT_GAP_MILLIS + 1)
        assertEquals(2, visits.size)
        assertEquals(Visit(30 * minute + DeviceBook.VISIT_GAP_MILLIS + 1, 30 * minute + DeviceBook.VISIT_GAP_MILLIS + 1), visits.last())
    }

    @Test
    fun `old and surplus visits are dropped`() {
        val gap = DeviceBook.VISIT_GAP_MILLIS + 1
        val many = (0 until DeviceBook.MAX_VISITS + 5).fold(emptyList<Visit>()) { visits, i -> DeviceBook.extendVisits(visits, i * gap) }
        assertEquals(DeviceBook.MAX_VISITS, many.size)
        assertEquals((DeviceBook.MAX_VISITS + 4) * gap, many.last().end)
        val later = DeviceBook.extendVisits(many, many.last().end + DeviceBook.KEEP_VISITS_MILLIS + 1)
        assertEquals(1, later.size)
    }

    @Test
    fun `a clock that went back starts a new visit instead of shrinking one`() {
        val visits = DeviceBook.extendVisits(listOf(Visit(10 * minute, 20 * minute)), 15 * minute)
        assertEquals(listOf(Visit(10 * minute, 20 * minute), Visit(15 * minute, 15 * minute)), visits)
    }

    @Test
    fun `recording clients extends their visits`() {
        val first = DeviceBook.record(emptyList(), listOf(phone), now = 0)
        val second = DeviceBook.record(first, listOf(phone), now = 10 * minute)
        assertEquals(listOf(Visit(0, 10 * minute)), second.single().visits)
    }
}
