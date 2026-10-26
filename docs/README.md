# ITMO.Widgets documentation

Everything here describes the current state of the apps. History is in
[`CHANGELOG.md`](../CHANGELOG.md); how to contribute is in
[`CONTRIBUTING.md`](../CONTRIBUTING.md).

## Как это работает

[Как это работает](how-it-works/README.md) - the product for students and
non-developer contributors, in Russian: what each feature gives, where its data
comes from, what stays on the phone and what goes to the server, the rules and
limits. Start there; the documents below are the developer reference.

## Start here

- [Getting started](getting-started.md) - first build and run on Android and iOS, local secret files.
- [Building your own copy](building.md) - an installable APK and `.ipa` without the owner's keys, forks, build identity.
- [Architecture](architecture.md) - modules, layers, DI, navigation, gates, storage, widgets, background work, push.
- [One feature end to end](feature-flow.md) - the schedule from the clients to the screen; adding a call or a screen.
- [Testing](testing.md) - every build and test command, kinds of tests, re-recording, CI.
- [Troubleshooting](troubleshooting.md) - known build and test pitfalls.

## Reference

- [Design](design.md) - tokens, components, states, the shell.
- [Settings](settings.md) - the user-facing settings, their keys and defaults.
- [iOS app](ios.md) - the SwiftUI app, toolchain, identifiers, build.
- [Adding a SwiftUI screen](recipes/ios-host.md) - an iOS screen over a shared view model.
- [Releases](product/releases.md) - release lines, version codes, compatibility, backlog.
- [Release](release.md) - the steps and checks of a release.

## Features

- [Sign-in](features/auth.md) - ITMO.ID, the refresh-token dialog, the session lifecycle.
- [First-run flow](features/onboarding.md) - the steps after sign-in.
- [Сегодня](features/home.md) - the feed, the bell, the tile and shortcuts.
- [Schedule](features/schedule.md) - own and friends' schedules, the friend picker, lesson details.
- [Schedule changes](features/schedule-changes.md) - the on-device check and its notification.
- [Calendar](features/calendar.md) - calendar sync and `.ics` export.
- [Campus map](features/map.md) - floor plans, room mapping, the router.
- [ISU messages](features/isu-messages.md) - the own ISU inbox, read on the device.
- [Sport](features/sport.md) - catalog, bookings, queues.
- [Recordbook](features/recordbook.md) - the MyITMO record book and the BARS overlay.
- [Subject page](features/subject-page.md) - one subject's result, links, teachers, lessons.
- [Mark tracking](features/marks-tracking.md) - new marks checked on the device.
- [Sheet scores](features/sheet-scores.md) - the own total from a public Google Sheet.
- [Subject links](features/resources.md) - link categories, audiences, the local mode.
- [Social](features/social.md) - friends, requests, people search, profiles, privacy.
- [Profile tab](features/me.md) - the Профиль tab.
- [Teacher reviews](features/reviews.md) - reviews, votes, reports, the AI summary.
- [Notifications](features/notifications.md) - push, token sync, local notifications, channels.
- [Widgets](features/widgets.md) - schedule, sport and QR widgets.
- [QR pass](features/qr.md) - the pass, its cache, the tile.
- [Wi-Fi](features/wifi.md) - ITMO-Corp, the password from ISU, the quick connect.
- [My ITMO in the app](features/my-itmo-web.md) - the web view and its navigation policy.
- [Update offer](features/update.md) - the version check per distribution variant.
- [Connected clients](features/connections.md) - «Устройства», pairing agents, MCP servers and the web, ITMO.ID sessions.
- [Demo session](features/demo.md) - the hidden entry and the gate in the repositories.
- [App Links and sharing](features/app-links.md) - shared links and their routes.
- [Debug tools](features/debug.md) - the debug-build screen.

## Decisions

Short records of choices that were argued once; the index with statuses is
[decisions/README.md](decisions/README.md). Add a new file instead of editing an
old one when a decision changes.

## Related repositories

- [Backend](https://github.com/alllexey-dev/itmo-widgets-backend): users,
  friendships, privacy, sport queues, links, reviews, push delivery; its
  `docs/README.md`.
- [MyItmoApi](https://github.com/alllexey-dev/my-itmo-api): the only client of
  MyITMO and BARS, a KMP library on Maven Central (`dev.alllexey:my-itmo-api-kmp`).
- [Web version and landing](https://github.com/alllexey-dev/itmo-widgets-web):
  keeps its own copy of the design tokens.
