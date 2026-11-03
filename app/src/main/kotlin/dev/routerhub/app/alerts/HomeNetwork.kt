package dev.routerhub.app.alerts

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Whether this phone is on a router's own network right now, so a background check can reach it. */
object HomeNetwork {
    /**
     * True on Wi-Fi or Ethernet when [host] lies in the subnet of the active network or is its
     * gateway. Never for a router saved by name: on another network the name can lead to another
     * device, which would then receive this router's login.
     */
    fun isOn(context: Context, host: String): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        val local = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        if (!local) return false
        val target = ipv4(host) ?: return false
        val links = connectivity.getLinkProperties(network) ?: return false
        val inSubnet = links.linkAddresses.any { link ->
            val address = link.address as? Inet4Address ?: return@any false
            inSubnet(target, ipv4(address.hostAddress.orEmpty()) ?: return@any false, link.prefixLength)
        }
        val isGateway = links.routes.any { (it.gateway as? Inet4Address)?.hostAddress == host }
        return inSubnet || isGateway
    }

    /** Whether [a] and [b] share their first [prefixLength] bits. */
    fun inSubnet(a: Int, b: Int, prefixLength: Int): Boolean {
        if (prefixLength !in 1..32) return false
        val mask = if (prefixLength == 32) -1 else (-1 shl (32 - prefixLength))
        return a and mask == b and mask
    }

    /** A dotted IPv4 address as a number, or `null` for anything else (names, IPv6). */
    fun ipv4(text: String): Int? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        return parts.fold(0) { value, part ->
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 && part.length in 1..3 } ?: return null
            (value shl 8) or octet
        }
    }
}
