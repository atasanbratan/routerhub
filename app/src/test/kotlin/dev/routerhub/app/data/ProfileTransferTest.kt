package dev.routerhub.app.data

import dev.routerhub.api.Protocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransferTest {
    private val saved = RouterProfile(
        id = "original-id",
        name = "Home",
        firmwareId = "example.firmware",
        protocol = Protocol.SSH,
        host = "192.0.2.1",
        port = 22,
        username = "root",
        password = "do-not-export",
        useHttps = false,
        hostKeyFingerprint = "SHA256:do-not-export-either",
    )

    @Test
    fun `an export holds neither passwords nor host keys`() {
        val file = ProfileTransfer.encode(listOf(saved))
        assertFalse(file.contains("do-not-export"))
        assertFalse(file.contains("original-id"))
    }

    @Test
    fun `an imported router keeps its address and asks for its password`() {
        val imported = ProfileTransfer.decode(ProfileTransfer.encode(listOf(saved))).single()
        assertEquals(saved.copy(id = imported.id, password = "", hostKeyFingerprint = null, passwordNeeded = true), imported)
        assertNotEquals(saved.id, imported.id)
        assertTrue(imported.passwordNeeded)
    }

    @Test
    fun `a firmware that isn't installed is kept`() {
        val other = saved.copy(firmwareId = "not.installed.firmware")
        assertEquals("not.installed.firmware", ProfileTransfer.decode(ProfileTransfer.encode(listOf(other))).single().firmwareId)
    }

    @Test
    fun `routers already saved are not added twice`() {
        val second = saved.copy(host = "192.0.2.2")
        val imported = ProfileTransfer.decode(ProfileTransfer.encode(listOf(saved, second, second)))
        assertEquals(listOf("192.0.2.2"), ProfileTransfer.newOnes(imported, listOf(saved)).map { it.host })
    }

    @Test
    fun `other files are refused`() {
        listOf(
            "not json",
            "{}",
            """{"format":"something-else","version":1,"routers":[]}""",
            """{"format":"routerhub-routers","version":99,"routers":[]}""",
            """{"format":"routerhub-routers","version":1}""",
            """{"format":"routerhub-routers","version":1,"routers":[{"name":"x"}]}""",
        ).forEach { file ->
            assertThrows(file, InvalidRoutersFileException::class.java) { ProfileTransfer.decode(file) }
        }
    }

    @Test
    fun `impossible routers are refused`() {
        val port = ProfileTransfer.encode(listOf(saved)).replace("\"port\": 22", "\"port\": 70000")
        val protocol = ProfileTransfer.encode(listOf(saved)).replace("\"SSH\"", "\"CARRIER_PIGEON\"")
        val host = ProfileTransfer.encode(listOf(saved.copy(host = " ")))
        listOf(port, protocol, host).forEach { file ->
            assertThrows(file, InvalidRoutersFileException::class.java) { ProfileTransfer.decode(file) }
        }
    }
}
