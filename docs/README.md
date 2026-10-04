# ITMO.Widgets documentation

Everything here describes the current state of the Android application. History
is in [`CHANGELOG.md`](../CHANGELOG.md); rules for agents are in
[`AGENTS.md`](../AGENTS.md).

## Product

- [Releases](product/releases.md) — release sequence, the `github` and `play`
  distribution variants, release lines and version codes, client and Backend
  compatibility.
- [Roadmap](product/roadmap.md) — v2.3 goals, parity rules, gates, iOS tiers,
  the v2.4 backlog.

## Process

- [Release checklist](process/release-checklist.md) — the order and checks of
  a release, with the owner's steps marked.

## Engineering

- [Architecture](architecture.md) — package structure, layers, enforced rules,
  cross-cutting decisions, testing conventions.
- [Design](design.md) — visual language, components, states, verification matrix.
- [Settings](settings.md) — the complete user-facing settings contract.

## Process

- [Workflow](process/workflow.md) — work tiers, the cross-repo chain,
  verification tiers, batches, closing a feature.
- [Plan template](process/plan-template.md) — lane files, the card format,
  executing a card.
- [Parallel agents](process/parallel-agents.md) — worktrees and branches,
  build slots, emulators, admission and the pull rule.
- [Ownership](process/ownership.md) — hot-file writers per wave, hand-ins,
  the module `AGENTS.md` shape, rules for writing docs.

## Features

- [Screen inventory](features/screens.md) — every Fragment, sheet, dialog and
  Activity with its ViewModel, entry, debug host and visual tests.
- [First-run flow](features/onboarding.md) — the three steps after sign-in, the
  root gate and the replay from maintenance.
- [Schedule](features/schedule.md) — academic schedule, friends' schedules,
  pending sport rows, lesson details, schedule changes checked on the device.
- [Sport](features/sport.md) — catalog, bookings, queues, cards and details.
- [Recordbook](features/recordbook.md) — MyITMO recordbook, BARS overlay,
  physical-education link, the one-page subject screen, new marks checked on
  the device, the own total from a public Google Sheet.
- [Subject links](features/resources.md) — link categories and audiences,
  chips, sheets, the local mode without the connection.
- [Social](features/social.md) — friends, requests, people search, person profiles.
- [Teacher reviews](features/reviews.md) — own reviews with premoderation, votes
  and reports, and Reviews copies in person profiles; the AI summary and the
  teacher tone dots; the editor and the report.
- [Notifications](features/notifications.md) — FCM receiver, token sync,
  handlers, the local schedule-changes and marks notifications.
- [Widgets](features/widgets.md) — schedule and QR widgets and their previews.
- [Update offer](features/update.md) — version check, reminder policy and
  «Обновить» in the `github` and `play` variants.
- [Web sign-in](features/web-login.md) — approving a browser's sign-in to the
  web version with a QR or a typed code.
- [Demo session](features/demo.md) — the hidden entry, the gate in the
  repositories, what works and what is refused, the fictional data set.
- [App Links and sharing](features/app-links.md) — shared profile and sport
  lesson links, their routes in the app and the site fallback.

## Decisions

Short records of choices that were argued once. Add a new file instead of
editing an old one when a decision changes.

- [0001 Single Gradle module](decisions/0001-single-module.md) — superseded by 0018
- [0002 Flyway V1 is immutable](decisions/0002-immutable-v1.md)
- [0003 Snapshot versions until 2.1](decisions/0003-snapshot-versions.md) — superseded by 0024
- [0004 Privacy audiences without reciprocity](decisions/0004-privacy-audiences.md)
- [0005 Explicit friendships, crossed request accepts](decisions/0005-friendships.md)
- [0006 People search via MyITMO plus lookup](decisions/0006-people-search.md)
- [0007 Push delivery guard by recipient ISU](decisions/0007-push-guard.md)
- [0008 Subject links: schedule-flow audiences, premoderation only for everybody](decisions/0008-community-moderation.md)
- [0009 One person profile, direct My ITMO identity and copied reviews](decisions/0009-person-profile.md)
- [0010 Own teacher reviews verified through ISU flows](decisions/0010-review-verification.md)
- [0011 AI summaries of teacher reviews through Gemini behind a proxy](decisions/0011-ai-review-summaries.md)
- [0012 BARS is renewed in the background by replaying ITMO.ID with cookies](decisions/0012-bars-background-renewal.md)
- [0013 Schedule changes are detected on the device](decisions/0013-schedule-changes-on-device.md)
- [0014 Own totals from public Google Sheets are read on the device](decisions/0014-sheet-scores-on-device.md)
- [0015 A hidden demo session gated in the repositories](decisions/0015-demo-mode.md)
- [0016 v2.3 is a parity rewrite](decisions/0016-parity-rewrite.md)
- [0017 Compose Multiplatform screens in `commonMain` of shared modules](decisions/0017-cmp-ui-in-common-main.md)
- [0018 Module graph and toolchain for v2.3](decisions/0018-module-graph-and-toolchain.md)
- [0019 Koin per feature beside Hilt, then Koin only](decisions/0019-koin-per-lane.md)
- [0020 One Navigation 3 shell with `@Serializable` routes](decisions/0020-navigation-3.md)
- [0021 Material 3 Expressive through a token schema, parity ports, one flip and a component pass](decisions/0021-m3-expressive-order.md)
- [0022 JVM screenshot tests replace emulator matrices; the full matrix runs before a release](decisions/0022-jvm-screenshot-tests.md)
- [0023 The iOS client is a SwiftUI shell around the shared CMP screens](decisions/0023-ios-client.md)
- [0024 MyItmoApi 2.x reaches the app through a pinned composite build](decisions/0024-pinned-composite-build.md)
- [0025 MyItmoApi 2.x is a Kotlin Multiplatform library beside 1.x](decisions/0025-myitmoapi-2-kmp.md)
- [0026 Core 2.0 is a client-only KMP module; Backend owns the wire DTOs](decisions/0026-core-2-backend-client.md)
- [0027 Android widgets stay RemoteViews; a shared timeline feeds them and WidgetKit](decisions/0027-remoteviews-widgets.md)
- [0028 One Russian string catalog generated outward; Material Symbols Rounded with an SF Symbol registry](decisions/0028-strings-and-icons.md)
- [0029 Lanes push their own branches, one integrator merges, the owner moves master](decisions/0029-lanes-and-integrator.md)
- [0030 Release lines, version codes and data continuity](decisions/0030-release-lines-and-data-continuity.md)

## Sibling repositories

- Backend: `../itmo-widgets-backend/docs/README.md`
- Core: `../itmo-widgets-core/docs/contract.md`
- MyItmoApi: `../MyItmoApi/README.md`
- Web version and landing: `../itmo-widgets-web/web/README.md`
