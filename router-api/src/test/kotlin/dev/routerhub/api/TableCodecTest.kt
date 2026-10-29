package dev.routerhub.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TableCodecTest {

    @Test
    fun `round-trips rows`() {
        val rows = listOf(
            listOf("Web server", "80", "TCP"),
            listOf("Cameras", "8000", "TCP/UDP"),
        )

        assertEquals(rows, TableCodec.decode(TableCodec.encode(rows)))
    }

    @Test
    fun `an empty table is the empty string both ways`() {
        assertEquals("", TableCodec.encode(emptyList()))
        assertTrue(TableCodec.decode("").isEmpty())
    }

    @Test
    fun `separators and backslashes inside cells survive`() {
        val rows = listOf(listOf("tab\there", "line\nbreak", "back\\slash", "\\t literal"), listOf("", "x", "", ""))

        assertEquals(rows, TableCodec.decode(TableCodec.encode(rows)))
    }

    @Test
    fun `empty cells keep their position`() {
        val rows = listOf(listOf("10.0.0.0/24", "", "eth0", ""))

        assertEquals(rows, TableCodec.decode(TableCodec.encode(rows)))
    }
}
