package dev.routerhub.app.data

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import java.io.FileDescriptor
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.random.Random

/**
 * Asks devices on the local network whether they are there. A reply means "online right now"; no
 * reply means nothing, because many devices ignore ping (Windows' firewall, iPhones, dozing phones).
 */
object Pinger {
    const val INTERVAL_MILLIS = 5_000L
    private const val TIMEOUT_MILLIS = 1_000L
    private const val ICMP_ECHO_REQUEST: Byte = 8
    private const val ICMP_ECHO_REPLY: Byte = 0

    /** A reply counts this long, so one ping a dozing phone misses doesn't hide its badge. */
    const val FRESH_MILLIS = 12_000L

    private val ipv4 = Regex("""(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})""")

    /** Only private IPv4 literals: never a name to look up, never an address outside the home network. */
    fun canPing(address: String?): Boolean {
        val parts = address?.let(ipv4::matchEntire)?.groupValues?.drop(1)?.map(String::toInt) ?: return false
        if (parts.any { it > 255 }) return false
        val (a, b) = parts
        return a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168)
    }

    /**
     * One ICMP echo through the unprivileged ping socket Android gives apps; blocks for up to a
     * second, so call it off the main thread. Not InetAddress.isReachable: when ping goes unanswered
     * it tries TCP and counts a refused connection as "there", which a VPN or NAT does for any address.
     */
    fun answers(address: String): Boolean {
        val socket = try {
            Os.socket(OsConstants.AF_INET, OsConstants.SOCK_DGRAM, OsConstants.IPPROTO_ICMP)
        } catch (_: ErrnoException) {
            return false
        }
        return try {
            val target = InetAddress.getByName(address)
            val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
            // Echo request with a sequence number of our own; the kernel fills in the identifier and the checksum.
            val sequence = Random.nextInt(1, 0x10000)
            val request = byteArrayOf(ICMP_ECHO_REQUEST, 0, 0, 0, 0, 0, (sequence shr 8).toByte(), sequence.toByte())
            Os.sendto(socket, request, 0, request.size, 0, target, 0)
            awaitReply(socket, target, sequence, deadline)
        } catch (_: ErrnoException) {
            false // timed out, or the network said the host is unreachable
        } catch (_: IOException) {
            false
        } finally {
            try {
                Os.close(socket)
            } catch (_: ErrnoException) {
            }
        }
    }

    private fun awaitReply(socket: FileDescriptor, target: InetAddress, sequence: Int, deadline: Long): Boolean {
        val reply = ByteArray(64)
        val from = InetSocketAddress(0)
        while (true) {
            val left = deadline - System.currentTimeMillis()
            if (left <= 0) return false
            // poll, not a receive timeout: setsockoptTimeval needs Android 10.
            val ready = StructPollfd().apply {
                fd = socket
                events = OsConstants.POLLIN.toShort()
            }
            if (Os.poll(arrayOf(ready), left.toInt()) == 0) return false
            val size = Os.recvfrom(socket, reply, 0, reply.size, 0, from)
            // Only our own reply counts: a late one to an earlier ping can reach a socket that reuses its identifier.
            if (isReplyTo(reply, size, sequence) && from.address == target) return true
        }
    }

    /** Whether [packet] is the echo reply carrying [sequence]. */
    internal fun isReplyTo(packet: ByteArray, size: Int, sequence: Int): Boolean =
        size >= 8 && packet[0] == ICMP_ECHO_REPLY &&
            ((packet[6].toInt() and 0xff) shl 8 or (packet[7].toInt() and 0xff)) == sequence

    /** Whether a reply at [lastReply] still counts at [now]. */
    fun isFresh(lastReply: Long?, now: Long): Boolean = lastReply != null && now - lastReply < FRESH_MILLIS
}
