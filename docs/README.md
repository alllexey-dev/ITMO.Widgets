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

- [Getting started](getting-started.md) - prerequisites, first build and run
  on Android and iOS, everyday commands, local secret files.
- [Building your own copy](building.md) - an installable APK and `.ipa`
  without the owner's keys, the `dist` workflow, forks, build identity.
- [Architecture](architecture.md) - modules, layers, DI, navigation, time,
  demo mode, the opt-in gate, storage, widgets, background work, push.
- [One feature end to end](feature-flow.md) - the schedule from the remote
  clients to the screen and the widgets; how to add a call, a screen or a
  background check.
- [Testing](testing.md) - kinds of tests, how to run and re-record them, CI.
- [Troubleshooting](troubleshooting.md) - known build and test pitfalls.

## Reference

- [Design](design.md) - tokens, components, states, platform styles.
- [Settings](settings.md) - the user-facing settings and their defaults.
- [iOS app](ios.md) - the SwiftUI shell, toolchain, identifiers, build.
- [Adding a SwiftUI screen](recipes/ios-host.md) - an iOS screen over a shared
  view model.
- [Releases](product/releases.md) - distribution variants, release lines,
  version codes, client and Backend compatibility.
- [Release](release.md) - the steps and checks of a release.

## Features

- [Sign-in](features/auth.md) - the sign-in screen, the ITMO.ID page, the
  refresh-token dialog, the session lifecycle and sign-out.
- [First-run flow](features/onboarding.md) - the steps after sign-in and the
  replay from settings.
- [Сегодня and quick actions](features/home.md) - Сегодня, the bell, the QR pass
  entry, the tile, the app shortcuts.
- [Schedule](features/schedule.md) - own and friends' schedules, pending sport
  rows, lesson details.
- [Schedule changes](features/schedule-changes.md) - the background check of
  the own schedule, the notification, the marks on lessons and in the bell.
- [Calendar](features/calendar.md) - sync into the phone's calendar and the
  `.ics` export.
- [Friend selector](features/friend-selector.md) - the sheet that picks whose
  schedule is shown.
- [Campus map](features/map.md) - floor plans, room mapping and the router behind
  «Маршрут».
- [Sport](features/sport.md) - catalog, bookings, queues, cards and details.
- [Recordbook](features/recordbook.md) - MyITMO record book, BARS overlay, the
  list of subjects.
- [Subject page](features/subject-page.md) - the result card, links,
  controls, teachers and nearest lessons.
- [Mark tracking](features/marks-tracking.md) - new marks checked on the
  device, the notification and the dots.
- [Sheet scores](features/sheet-scores.md) - the own total from a public
  Google Sheet.
- [Subject links](features/resources.md) - link categories and audiences,
  sheets, the local mode without the connection.
- [Social](features/social.md) - friends, requests, people search, profiles.
- [Profile tab](features/me.md) - identity, social and application rows,
  sign-out.
- [Teacher reviews](features/reviews.md) - own reviews, votes and reports, the
  AI summary and tone dots, the editor.
- [Notifications](features/notifications.md) - push, token sync, local
  notifications.
- [Widgets](features/widgets.md) - schedule and QR widgets and their previews.
- [QR pass](features/qr.md) - the pass screen, its cache, the tile and the
  shortcut.
- [My ITMO in the app](features/my-itmo-web.md) - the official site in a web
  view, its navigation policy and session cleanup.
- [Update offer](features/update.md) - version check and «Обновить» in the
  `github` and `play` variants.
- [Web sign-in](features/web-login.md) - approving a browser's sign-in to the
  web version.
- [Demo session](features/demo.md) - the hidden entry, the gate in the
  repositories, the fictional data set.
- [App Links and sharing](features/app-links.md) - shared profile and sport
  links and their routes.
- [Debug tools](features/debug.md) - the debug-build screen: test refresh
  token, academic date, sport overrides, one-off checks.

## Decisions

Short records of choices that were argued once; the index with statuses is
[decisions/README.md](decisions/README.md). Add a new file instead of editing an
old one when a decision changes. The latest:
[0031 iOS screens move to native SwiftUI](decisions/0031-swiftui-ios-screens.md).

## Related repositories

- [Backend](https://github.com/alllexey-dev/itmo-widgets-backend): users,
  friendships, privacy, sport queues, push delivery; its `docs/README.md`.
- [MyItmoApi](https://github.com/alllexey-dev/my-itmo-api): the typed client
  for MyITMO and BARS.
- [Web version and landing](https://github.com/alllexey-dev/itmo-widgets-web).
