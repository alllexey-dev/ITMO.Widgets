# ITMO.Widgets documentation

Everything here describes the current state of the Android application. History
is in [`CHANGELOG.md`](../CHANGELOG.md); rules for agents are in
[`AGENTS.md`](../AGENTS.md).

## Product

- [Releases](product/releases.md) — release sequence, minimum compatible Core
  and Backend, deferred features.
- [Roadmap](product/roadmap.md) — staged delivery plan for v2.1 and v2.2 with
  progress.

## Engineering

- [Architecture](architecture.md) — package structure, layers, enforced rules,
  cross-cutting decisions, testing conventions.
- [Design](design.md) — visual language, components, states, verification matrix.
- [Settings](settings.md) — the complete user-facing settings contract.

## Features

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
- [Update offer](features/update.md) — version check and reminder policy.
- [Web sign-in](features/web-login.md) — approving a browser's sign-in to the
  web version with a QR or a typed code.

## Decisions

Short records of choices that were argued once. Add a new file instead of
editing an old one when a decision changes.

- [0001 Single Gradle module](decisions/0001-single-module.md)
- [0002 Flyway V1 is immutable](decisions/0002-immutable-v1.md)
- [0003 Snapshot versions until 2.1](decisions/0003-snapshot-versions.md)
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

## Sibling repositories

- Backend: `../itmo-widgets-backend/docs/README.md`
- Core: `../itmo-widgets-core/docs/contract.md`
- MyItmoApi: `../MyItmoApi/README.md`
- Web version and landing: `../itmo-widgets-web/web/README.md`
