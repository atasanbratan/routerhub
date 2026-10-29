package dev.routerhub.app.extension

import dev.routerhub.extension.demo.DemoExtension
import dev.routerhub.extension.openwrt.OpenWrtExtension
import dev.routerhub.extension.tenda.TendaAcExtension
import dev.routerhub.extension.tplink.TpLinkAdslExtension

/**
 * The official extensions, compiled into the Play build from routerhub-extensions (see
 * app/build.gradle.kts). Package names and labels match the separate APKs of the GitHub build.
 */
internal val bundledExtensions: List<BundledExtension> = listOf(
    BundledExtension("dev.routerhub.extension.demo", "Demo (fake router)") { DemoExtension() },
    BundledExtension("dev.routerhub.extension.openwrt", "OpenWrt (SSH)") { OpenWrtExtension() },
    BundledExtension("dev.routerhub.extension.tenda", "Tenda AC") { TendaAcExtension() },
    BundledExtension("dev.routerhub.extension.tplink", "TP-Link ADSL2+") { TpLinkAdslExtension() },
)
