---
title: Privacy policy
description: What RouterHub does and does not do with your data.
lastUpdated: false
prev: false
next: false
---

# Privacy policy

RouterHub is an open-source app with no accounts, no analytics, no advertising and no servers of its own.

## What stays on your phone

- **Router profiles**: the address, user name and password of each router you add. Passwords are encrypted
  with a key held in the Android Keystore and never leave the device. Backups by Android are turned off for
  the app, so they are not copied to a cloud backup either.
- **Preferences** such as the theme.
- **Extensions you trusted**, and SSH host keys you accepted.

Uninstalling the app deletes all of it.

## Who the app talks to

- **Your routers**, at the addresses you entered, to read their status and change their settings. This
  happens on your own network.
- **GitHub** (`api.github.com`), only when you tap **Check for updates**, to see whether a newer version
  exists. GitHub receives the request like any website visit, including your IP address; see
  [GitHub's privacy statement](https://docs.github.com/site-policy/privacy-policies/github-general-privacy-statement).

Nothing else is sent anywhere. The app contains no crash reporting or tracking libraries; the full list of
libraries is under **More → About → Open source licenses**, and the source code is
[on GitHub](https://github.com/atasanbratan/routerhub).

## Extensions

Extensions run inside the app and are given the credentials of the routers that use them. Official
extensions are reviewed and built from the
[extensions repository](https://github.com/atasanbratan/routerhub-extensions). The app refuses extensions
from unknown signers until you trust them explicitly; only trust extensions whose source you know.

## This website

This site is hosted on GitHub Pages and uses no cookies or analytics. GitHub may log visits as described in
its privacy statement. The download and extension pages load data from GitHub in your browser.

## Changes

Changes to this policy are made in the open in the
[website's source](https://github.com/atasanbratan/routerhub/tree/main/website). Questions: open an issue
or a discussion on GitHub.

*Last updated: October 29, 2026.*
