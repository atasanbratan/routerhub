# Contributing to RouterHub

Thank you for wanting to help. RouterHub is small enough for one person to understand all of it, and every
kind of contribution counts.

## Ways to help

- **Add support for a router.** Write an extension. That is the most valuable thing you can do, and it lives
  in the [extensions repository](https://github.com/atasanbratan/routerhub-extensions). Start with
  [docs/writing-an-extension.md](docs/writing-an-extension.md).
- **Test on hardware you own.** Some extensions have only been unit-tested. A report of what worked and what
  did not (with anything personal removed) is a real contribution.
- **Improve the app.** Fix a bug, polish the UI, add a missing piece of the settings renderer.
- **Translate.** The app's strings are English-only today, so the first step is moving them into resources.
- **Improve the docs**, answer questions in [Discussions](https://github.com/atasanbratan/routerhub/discussions),
  or help triage issues.

Look for the [`good first issue`](https://github.com/atasanbratan/routerhub/labels/good%20first%20issue) and
[`help wanted`](https://github.com/atasanbratan/routerhub/labels/help%20wanted) labels.

For anything bigger than a bug fix, please open an issue or a discussion first, so nobody spends a weekend
on something that cannot be merged. Security problems go to [SECURITY.md](SECURITY.md), not to a public issue.

## Setting up

You need JDK 17 or newer and the Android SDK (platform 36 and build-tools 36). Android Studio brings both.

```bash
git clone https://github.com/<you>/routerhub.git
cd routerhub
./gradlew :router-api:test :app:assembleGithubDebug
```

If Gradle cannot find the SDK, create `local.properties` with `sdk.dir=/path/to/Android/Sdk` (Android Studio
does this for you). That file is git-ignored.

To work on the app together with extensions, check
[routerhub-extensions](https://github.com/atasanbratan/routerhub-extensions) out next to it and install an
extension APK, for example the Demo extension, which needs no router at all.

## Layout

```
router-api/   pure-JVM contract shared with extensions: keep it small and stable
app/          the host: Compose UI, router profiles, session engine, extension loader
docs/         architecture, settings contract, how to write an extension
```

## Rules of this codebase

1. **`router-api` is append-only.** Extensions are built against it months before they meet an app, so new
   model fields go at the end with defaults, new interface methods need default bodies, and nothing is
   renamed or removed. `ExtensionContract.API_VERSION` rises when extensions may start depending on
   something new. See [docs/architecture.md](docs/architecture.md).
2. **No vendor logic in the app.** If the app needs to know a router's name to behave, the contract is
   missing something: extend the contract instead.
3. **Credentials never leak.** Do not log them, do not put them in `toString()`, do not echo them back.
4. **Writes are never replayed** and settings are validated before anything is sent.
5. **No real data in the repository.** That means code, tests, fixtures and screenshots: no real SSIDs,
   passwords, MAC addresses, serial numbers, hostnames or public IP addresses. Use `02:00:00:00:00:xx`
   MACs and the documentation address ranges (`192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24`).
6. **Test what is pure.** Parsing and logic get unit tests. For UI changes, attach a screenshot to the pull
   request (the Demo extension makes that easy and keeps real data out of it).

Style is the official Kotlin style. Comments should explain *why*, not restate the code.

## Making a change

1. Fork the repository and create a branch from `main`.
2. Make the change, with tests where it makes sense.
3. Run `./gradlew :router-api:test :app:assembleGithubDebug`. CI runs the same.
4. Open a pull request and fill in the template. Keep it focused: one change per pull request.
5. Be patient and kind. This is a volunteer project, and reviews can take a few days.

Write commit messages in the imperative mood ("Show refusals under toggles"), with the reason in the body when
it is not obvious.

## Licensing

RouterHub is licensed under the [Apache License 2.0](LICENSE). By contributing you agree that your
contribution is licensed under the same terms (section 5 of the license).

## Code of Conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
