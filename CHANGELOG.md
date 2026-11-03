# Changelog

All notable changes to the RouterHub app are listed here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

The release workflow copies the section of the version being released into its GitHub release, so
write entries for people who use the app: what they can now do, what works better, what was fixed.
Add them under **Unreleased** as you go; releasing renames that heading to the new version.

## [Unreleased]

## [0.2.0] - 2026-11-03

This version needs the 1.1 extensions, published alongside it; older app versions can't load them.

### Added

- **Your devices:** name devices and mark the ones you know. Blocked devices and devices seen before stay
  listed, and each device shows when this app saw it connected.
- **Online badge:** devices that answer right now are marked, checked every few seconds while the
  clients list is open.
- **Actions on a device:** copy its MAC address, IP address or name, and block, allow or reserve its
  address where the router supports it. On OpenWrt also pause its internet for an hour, and set internet
  schedules.
- **New-device alerts,** optional for each router: a check about every 15 minutes while your phone is on
  the router's network, which only logs in to a router that looks like the one you opened in the app.
- **App lock** with your phone's fingerprint, face or screen lock.
- **Finding your router:** the app finds the router on your network and suggests its type, and explains
  which password it needs.
- **Basic mode** for routers no extension supports yet: checks that the router answers, opens its web
  page, and links to a support request.
- **Router screen tabs:** Device, Wireless, Clients, Network, Firewall and System.
- **Languages:** Spanish, Portuguese, Indonesian and Arabic, with right-to-left layout; extensions are
  translated too. On Android 13 and newer you can pick the app's language in the system settings.
- **Privacy and tested routers** page under More.
- Export your routers to a file and import them on another phone, under More. The file holds no passwords
  or SSH host keys: each imported router asks for its password when you first open it.
- New routers through the extensions: TP-Link Wi-Fi routers with the classic web interface (tested on the
  TL-WR940N), and OpenWrt from 21.02 to 25.12 through LuCI.

### Changed

- After saving settings, the app reads them back from the router and tells you whether it kept them.
- **Save** on a settings page stays off until a password that must be typed twice matches.

### Fixed

- Settings pages land in the right tab in every language.
- Router values such as "13 of 56 MiB" read correctly in right-to-left languages.

## [0.1.0] - 2026-10-29

### Added

- First public version: routers list, router status and connected devices, reboot, Wi-Fi switches and
  settings pages, all provided by extensions installed as separate APKs.
- Signature checks for extensions, with explicit trust for unknown signers.
- Mihon-style Material 3 interface with dynamic color and a pure black dark mode.
- An About screen: version (tap to copy debug information), check for updates, what's new, open source
  licenses, privacy policy, and links to the website and GitHub.
- Extensions from the official RouterHub extensions repository are recognised as **Official**.
- A Google Play edition with the official extensions built in.

[Unreleased]: https://github.com/atasanbratan/routerhub/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/atasanbratan/routerhub/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/atasanbratan/routerhub/releases/tag/v0.1.0
