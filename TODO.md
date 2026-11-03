# RouterHub roadmap

From competitor review analysis, November 2026. Do these in order.

## Update 0.2: the complete household workflow

Connect → recognise devices → change settings safely → block → unblock → recover.

### 1. Finish what's built
- [x] Test block, allow, reserve and remove reservation on a real OpenWrt router (rules save and undo; whether a block cuts internet is still unproven, as the router had no internet)
- [ ] Confirm a blocked device really loses internet, once the router is online
- [x] Run the live tests on OpenWrt 19.07, 21.02, 22.03, 23.05 and 25.12 in VMs, then publish the results in `docs/openwrt-ubus.md` of routerhub-extensions (needs QEMU in `D:\vm\qemu`)
- [x] Push `issues-1-2-4-5`, `openwrt-client-actions` and the extensions branch `openwrt-ubus`
- [x] Merge into `main`; released as app 0.2.0 with extensions 1.1.0 (2026-11-03)
- [x] Point the extensions' SDK commit in `gradle/libs.versions.toml` at the pushed API v5 commit (1a5e1f8)
- [x] Set `versionCode` to 2

### 2. Guided connection
- [x] Detect the router's address from the phone's network, with no location permission
- [x] Identify OpenWrt, Tenda and TP-Link and suggest the right type
- [x] Label it "Router admin password, not your Wi-Fi password", with help on where to find it
- [x] Show default passwords as hints and never try them automatically
- [x] Separate four failures: wrong password, router not reachable, firmware not supported, stale session
- [x] Suggest a factory reset only as a last resort, with a warning about what it erases

### 3. Recognisable devices
- [x] Device names and a "known device" mark, stored per router on the phone and included in backups
- [x] Clients shows online, previously seen and blocked, and keeps names on blocked devices

### 4. Block on more routers
- [x] Client actions in the Tenda extension, through its MAC filter (unit-tested only: try it on the AC10)
- [ ] Client actions in the TP-Link extension (needs the TD-W8960N to test)
- [x] Say plainly where it doesn't (Privacy and tested routers page)

### 5. Safe, honest changes
- [x] Warn before anything that could cut the user off (Wi-Fi switch and Wi-Fi pages ask first; OpenWrt network pages undo themselves)
- [x] Check the router after saving: saved, not saved, or couldn't confirm
- [x] Recovery steps in plain words

### 6. Trust
- [x] In-app list of tested routers and what works on each
- [x] "How your passwords are stored" page
- [x] Small screens with large text (720x1280, font 1.3x): new screens fit and scroll
- [ ] Test on Android 16 or newer (an API 37 image is installed; needs more free memory than this PC has with the VMs running)

## Update 0.3: reasons to come back
- [x] Stop relying on English text: stable group ids for tabs, translatable extension text (API v5)
- [x] App translations: Spanish, Indonesian, Portuguese, Arabic; right-to-left checked in Arabic (needs a native speaker's review)
- [x] Extension translations in the same four languages (needs a native speaker's review; text built at run time stays English)
- [x] Online badge on clients that answer ping, every 5 seconds while the Clients tab is open (try it on a real phone)
- [x] New-device alerts, on the home network only (about every 15 minutes); opt-in per router on the Clients tab (try it on a real phone overnight)
- [x] Connection history, stating that it only covers what the app has seen (device sheet, 20 visits within 14 days)
- [x] Pause (1 hour), resume and schedules per device on OpenWrt, tested in VMs on 21.02 and 23.05
- [ ] The same for Tenda and TP-Link, if their firmware has time rules (needs the hardware)
- [x] App lock with the phone's fingerprint, face or screen lock (unlocking not tried yet: the emulator has no screen lock)
- [x] Basic mode for unsupported routers, clearly labelled as limited (reachability, what the login page says, web page, support request link)

## Update 0.4 and later
- [ ] More routers, chosen from user requests and hardware we can test (candidates: TP-Link, ZTE, Huawei, MiFi)
- [ ] Decide with real user feedback first: speed limits, daily time allowances, MiFi SMS and USSD, widgets, usage charts

## Not planned
- Remote access
- Modem calls
- Getting into other people's Wi-Fi
- Paid plans, until users tell us what they'd pay for
