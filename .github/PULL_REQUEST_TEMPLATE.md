## What and why

<!-- What does this change, and what problem does it solve? Link the issue if there is one. -->

## How to test

<!-- Steps, or the tests you added. The Demo extension is handy for trying UI changes without a router. -->

## Checklist

- [ ] `./gradlew :router-api:test :app:assembleDebug` passes
- [ ] UI change: a screenshot is attached (taken with the Demo extension, so no real data shows)
- [ ] `router-api` change: append-only (new fields last, with defaults; new methods with default bodies), and `API_VERSION` considered
- [ ] No real router data anywhere (SSIDs, passwords, MAC addresses, serial numbers, public IPs) in code, tests or screenshots
- [ ] Docs updated if behaviour changed
