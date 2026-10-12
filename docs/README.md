# ITMO.Widgets documentation

Everything here describes the current state of the apps. History is in
[`CHANGELOG.md`](../CHANGELOG.md); the short contributor guide with build
commands and hard rules is [`AGENTS.md`](../AGENTS.md).

## Product

- [Releases](product/releases.md) — release sequence, the `github` and `play`
  distribution variants, release lines and version codes, client and Backend
  compatibility, the backlog.
- [Release](release.md) — the steps and checks of a release.

## Engineering

- [Architecture](architecture.md) — package structure, layers, enforced rules,
  cross-cutting decisions, testing conventions.
- [Design](design.md) — visual language, components, states, verification matrix.
- [Settings](settings.md) — the complete user-facing settings contract.
- [iOS app](ios.md) — the SwiftUI shell, the `Shared` framework, toolchain
  pins, build and verification.

## Recipes

- [Recipes](recipes/README.md) — durable how-tos for repeated Android and
  shared-module work.
- [Background check](recipes/background-check.md) — a periodic or one-off
  WorkManager check from worker to iOS mapping.
- [Screen in a shared module](recipes/shared-module-screen.md) — a stateless
  CMP screen in `shared/feature-<x>` with its route, ViewModel, Koin module and
  goldens.
- [Endpoint end to end](recipes/endpoint-end-to-end.md) — a Backend route or a
  MyITMO endpoint from its source of truth to the screen.

## Features

- [Screen inventory](features/screens.md) — every Fragment, sheet, dialog and
  Activity with its ViewModel, entry, debug host and visual tests.
- [Sign-in](features/auth.md) — the sign-in screen, the ITMO.ID page and the
  refresh-token dialog, the session lifecycle and sign-out.
- [First-run flow](features/onboarding.md) — the three steps after sign-in, the
  root gate and the replay from maintenance.
- [Home and quick actions](features/home.md) — the home feed of cards, the QR
  pass, the quick-settings tile, the app shortcuts and the My ITMO entry.
- [Schedule](features/schedule.md) — academic schedule, friends' schedules,
  pending sport rows, lesson details.
- [Schedule changes](features/schedule-changes.md) — the background check of
  the own schedule on the device, the notification, the marks and the history.
- [Calendar](features/calendar.md) — the kept synchronization into the phone's
  calendar and the one-off `.ics` export.
- [Friend selector](features/friend-selector.md) — the sheet that picks whose
  schedule is shown: recent chips, friends and people search, the result.
- [Sport](features/sport.md) — catalog, bookings, queues, cards and details.
- [Recordbook](features/recordbook.md) — MyITMO recordbook, BARS overlay,
  physical-education link, the list of subjects.
- [Subject page](features/subject-page.md) — the one-page subject screen: the
  result card, links, controls, teachers and nearest lessons.
- [Mark tracking](features/marks-tracking.md) — new marks from My ITMO, BARS and
  sheets checked on the device, the notification and the dots.
- [Sheet scores](features/sheet-scores.md) — the own total from a public Google
  Sheet: connecting, downloading, the subject page and the list.
- [Subject links](features/resources.md) — link categories and audiences,
  chips, sheets, the local mode without the connection.
- [Social](features/social.md) — friends, requests, people search, person profiles.
- [Profile tab](features/me.md) — the own identity, the social and application
  rows, project links and sign-out.
- [Teacher reviews](features/reviews.md) — own reviews with premoderation, votes
  and reports, and Reviews copies in person profiles; the AI summary and the
  teacher tone dots; the editor and the report.
- [Notifications](features/notifications.md) — FCM receiver, token sync,
  handlers, the local schedule-changes and marks notifications.
- [Widgets](features/widgets.md) — schedule and QR widgets and their previews.
- [QR pass](features/qr.md) — the pass screen, its cache, the quick-settings
  tile and the `qr_pass` shortcut.
- [My ITMO in the app](features/my-itmo-web.md) — the official website in a
  WebView overlay, its navigation policy and session cleanup.
- [Update offer](features/update.md) — version check, reminder policy and
  «Обновить» in the `github` and `play` variants.
- [Web sign-in](features/web-login.md) — approving a browser's sign-in to the
  web version with a QR or a typed code.
- [Demo session](features/demo.md) — the hidden entry, the gate in the
  repositories, what works and what is refused, the fictional data set.
- [App Links and sharing](features/app-links.md) — shared profile and sport
  lesson links, their routes in the app and the site fallback.
- [Debug tools](features/debug.md) — the debug-build screen: test refresh
  token, academic date, sport overrides, one-off background checks.

## Decisions

Short records of choices that were argued once; the index with statuses is
[decisions/README.md](decisions/README.md). Add a new file instead of editing an
old one when a decision changes. The latest:
[0031 iOS screens move to native SwiftUI](decisions/0031-swiftui-ios-screens.md).

## Related repositories

- [Backend](https://github.com/alllexey-dev/itmo-widgets-backend): users,
  friendships, privacy, sport queues, push delivery; its `docs/README.md`
- [MyItmoApi](https://github.com/alllexey-dev/my-itmo-api): the typed client
  for MyITMO and BARS, with its 2.x migration guide
- [Web version and landing](https://github.com/alllexey-dev/itmo-widgets-web)
- [Core 1.x](https://github.com/alllexey-dev/itmo-widgets-core), frozen at
  `1.7.0`; Core 2.0 is `shared/backend-client` in this repository
