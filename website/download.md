---
title: Download
description: Download the latest RouterHub APK.
lastUpdated: false
editLink: false
prev: false
next: false
---

# Download

<DownloadButton />

RouterHub is distributed as a signed APK on [GitHub Releases](https://github.com/atasanbratan/routerhub/releases).
Every release lists what changed and the SHA-256 checksum of the APK.

1. Download the APK on your Android phone (Android 9 or newer) and open it.
2. Allow your browser or file manager to install apps when Android asks.
3. Open RouterHub, then install an [extension](/extensions/) for your router.

Updates: open **More → About → Check for updates** in the app. Releases are always signed with the same key,
so a new version installs over the old one and keeps your routers.

::: details Verify the signature
The release APK is signed with the certificate whose SHA-256 fingerprint is:

`7B:80:90:88:79:C6:E9:23:D5:E7:DE:32:CB:56:10:AC:4C:93:E0:8C:C1:DF:F9:81:75:61:98:0C:9F:C0:EC:4D`

Check it with `apksigner verify --print-certs routerhub-vX.Y.Z.apk`.
:::
