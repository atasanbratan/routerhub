package dev.routerhub.app.alerts

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** Whether this phone is on a router's own network right now, so a background check can reach it. */
object HomeNetwork {
    /**
     * True when [host] lies in the subnet of a Wi-Fi or Ethernet network the phone is on, or is its
     * gateway. Never for a router saved by name: on another network the name can lead to another
     * device, which would then receive this router's login.
     */
    fun isOn(context: Context, host: String): Boolean {
        val target = ipv4(host) ?: return false
        return localLinks(context).any { links ->
            val inSubnet = links.linkAddresses.any { link ->
                val address = link.address as? Inet4Address ?: return@any false
                inSubnet(target, ipv4(address.hostAddress.orEmpty()) ?: return@any false, link.prefixLength)
            }
            inSubnet || links.routes.any { (it.gateway as? Inet4Address)?.hostAddress == host }
        }
    }

    /**
     * The Wi-Fi and Ethernet networks the phone is on. Not just the active network: with a VPN on,
     * that is the VPN, whose addresses and gateway say nothing about the router.
     */
    @Suppress("DEPRECATION") // allNetworks: its replacement needs a callback; this is a one-off look.
    fun localLinks(context: Context): List<LinkProperties> {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        return connectivity.allNetworks.filter { network ->
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@filter false
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }.mapNotNull(connectivity::getLinkProperties)
    }

    /** The routers of [links]: IPv4 default-route gateways, leaving out 0.0.0.0, which a VPN or point-to-point link has. */
    fun gateways(links: List<LinkProperties>): List<String> = links.flatMap { it.routes }
        .filter { it.isDefaultRoute }
        .mapNotNull { (it.gateway as? Inet4Address)?.takeUnless { gateway -> gateway.isAnyLocalAddress || gateway.isLoopbackAddress }?.hostAddress }
        .distinct()

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
