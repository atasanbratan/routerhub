# Releasing

Releases work the way Mihon's do: pushing a version tag builds a signed APK on GitHub Actions and
publishes it as a GitHub release, with the version's CHANGELOG section as the notes. The app's
**About → Check for updates** reads the latest release, and the website's download button links to it.

## Checklist

1. In `app/build.gradle.kts`, raise `versionCode` by one and set `versionName` (for example `0.2.0`).
2. In `CHANGELOG.md`, rename `## [Unreleased]` to `## [0.2.0] - YYYY-MM-DD`, add a new empty
   `## [Unreleased]` above it, and update the compare links at the bottom.
3. Commit both, then tag and push:

   ```bash
   git tag v0.2.0
   git push origin main v0.2.0
   ```

The [Release workflow](../.github/workflows/release.yml) then:

- checks that the tag equals `v` + `versionName`, and that CHANGELOG.md has a section for it;
- runs the SDK and app unit tests, and builds the release APK signed with the release key;
- refuses the APK unless its certificate is the expected one (`APP_SIGNER_SHA256` in the workflow);
- publishes `routerhub-v0.2.0.apk` with the notes and a SHA-256 checksum table.

The same run builds the Google Play bundle (`app-play-release.aab`, workflow artifact **play-bundle**);
upload it in Play Console. See [Google Play](#google-play) below.

A failed run publishes nothing. Fix the cause, delete the tag (`git push origin :v0.2.0`, `git tag -d v0.2.0`)
and tag again.

## Google Play

Play allows neither extension discovery (the all-packages permission) nor loading code from APKs installed
outside Play, so the app has two flavors:

| Flavor | Extensions | Updates | Built by |
| --- | --- | --- | --- |
| `github` | separate APKs, signature-checked | About → Check for updates | `:app:assembleGithubRelease` |
| `play` | the official ones compiled in from `routerhub-extensions` | Google Play | `:app:bundlePlayRelease` |

The `play` flavor reads the extension sources from a checkout of routerhub-extensions next to this repository,
or wherever `-Prouterhub.extensions=<path>` points. A router added to the extensions repository reaches Play
users with the next app release.

Play re-signs the app with its own app signing key; our app key is the **upload key**. So the Play and GitHub
builds have different signatures, and a phone can't switch between them without reinstalling.

## Signing keys

There are two keys, kept outside the repositories:

| Key | Signs | Fingerprint (SHA-256) |
| --- | --- | --- |
| App | the RouterHub APK | `7B:80:90:88:79:C6:E9:23:D5:E7:DE:32:CB:56:10:AC:4C:93:E0:8C:C1:DF:F9:81:75:61:98:0C:9F:C0:EC:4D` |
| Extensions | official extension APKs | `7F:3B:12:B9:54:80:3F:03:AB:99:96:56:3F:83:4C:7E:D2:37:AD:55:EB:E6:31:A2:CC:4C:26:EE:FB:EF:0E:39` |

Each repository has the same four Actions secrets, holding its own key:

| Secret | Value |
| --- | --- |
| `SIGNING_KEY` | the PKCS#12 keystore, Base64 encoded on one line |
| `ALIAS` | the key alias |
| `KEY_STORE_PASSWORD` | the keystore password |
| `KEY_PASSWORD` | the key password (the same as the keystore password for these keys) |

Losing the app key means users must uninstall to update, and losing the extensions key means every
existing app build stops treating new extensions as Official. Keep offline backups of both.

The extensions key's fingerprint is also `routerhub.officialSigners` in `gradle.properties`, so the app
recognises official extensions. A fork that publishes its own extensions replaces it.

## The website

`website/` is a VitePress site, deployed to GitHub Pages by the [Website workflow](../.github/workflows/website.yml)
whenever it changes on `main`. Run it locally with:

```bash
npm --prefix website install
npm --prefix website run dev
```

The extension list is read in the browser from the `repo` branch of the extensions repository, which that
repository's Publish workflow updates; the site does not need rebuilding when extensions change.
