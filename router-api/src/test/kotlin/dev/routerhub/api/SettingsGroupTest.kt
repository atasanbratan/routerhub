package dev.routerhub.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGroupTest {
    @Test
    fun `ids and the English names of older extensions land in the same tab`() {
        assertEquals(SettingsGroup.WIRELESS, SettingsGroup.idOf("wireless"))
        assertEquals(SettingsGroup.WIRELESS, SettingsGroup.idOf("Wireless"))
        assertEquals(SettingsGroup.FIREWALL, SettingsGroup.idOf("Firewall "))
        assertEquals(SettingsGroup.SYSTEM, SettingsGroup.idOf("SYSTEM"))
    }

    @Test
    fun `any other group is kept as written`() {
        assertEquals("Parental controls", SettingsGroup.idOf("Parental controls"))
    }
}
