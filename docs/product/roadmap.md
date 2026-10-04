# ITMO.Widgets roadmap

## Task

v2.3 turns ITMO.Widgets into one Kotlin Multiplatform product with two
clients. The Android app moves onto shared KMP modules with Compose
Multiplatform screens, and an iOS app hosts the same content screens. It is a
parity rewrite ([0016](../decisions/0016-parity-rewrite.md)): no product
feature beyond what iOS needs, every v2.2 behaviour, text and stable
identifier stays, and installed 2.1.1 and 2.2 clients keep working against
every Backend of the cycle.

MyITMO and BARS stay the source of university data (through MyItmoApi),
Backend the source of social and moderated community data and of
authorization, the clients the owners of presentation, caches, widgets and
local preferences. Achievements, messaging, posts, followers and free-window
discovery stay outside the roadmap; do not add them opportunistically.

## Goals, in the owner's order

1. **Clean repositories.** Every repository builds and tests from one entry
   point, its docs describe the current state, dead code and duplicate layers
   are gone.
2. **An architecture that makes the next feature cheap.** One module graph and
   toolchain ([0018](../decisions/0018-module-graph-and-toolchain.md)), recipes
   and a generator, rules enforced by tests.
3. **Shared UI on a shared spine.** Android screens become Compose
   Multiplatform screens in `commonMain`
   ([0017](../decisions/0017-cmp-ui-in-common-main.md)) on MyItmoApi 2.x
   ([0025](../decisions/0025-myitmoapi-2-kmp.md)) and an in-repo Core 2.0
   client ([0026](../decisions/0026-core-2-backend-client.md)), with Koin as
   the only DI ([0019](../decisions/0019-koin-per-lane.md)) and one
   Navigation 3 shell ([0020](../decisions/0020-navigation-3.md)).
4. **Material 3 Expressive.** A token schema at today's values, parity ports,
   one token flip and a component pass
   ([0021](../decisions/0021-m3-expressive-order.md)).
5. **An iOS app** with full Android parity that ships in v2.3 in some form
   ([0023](../decisions/0023-ios-client.md)).

## Parity rules

- The `v2.2` tag is the baseline: each port is checked against the v2.2
  screen, test and identifier and can be reverted alone.
- Stable identifiers (manifest components, App Link paths, worker classes and
  work names, shortcut ids, notification channels, preference, file and
  Keystore names) keep their values; a 2.2 data directory upgrades in place
  ([0030](../decisions/0030-release-lines-and-data-continuity.md)).
- Screenshots move from emulator matrices to JVM screenshot tests; the full
  appearance matrix runs before a release
  ([0022](../decisions/0022-jvm-screenshot-tests.md)).
- One visible deviation: sport «Запись» success messages become snackbars.
- A v2.2 bug is fixed on `release/2.2` first and ported forward; changes Google
  Play forces before 2.3 ship as 2.2.x and never widen v2.3.
- Android widgets stay RemoteViews and feed WidgetKit through a shared
  timeline ([0027](../decisions/0027-remoteviews-widgets.md)); one Russian
  string catalog and one icon registry serve both clients
  ([0028](../decisions/0028-strings-and-icons.md)).

## Tracks

| Track | Content | Repositories |
|---|---|---|
| Clean-up | build hygiene, dead code, docs drift guard, changelog fragments | all |
| Architecture | module graph, toolchain, DI rules, recipes, generator | Android |
| Shared spine | KMP domain and data, MyItmoApi 2.x, Core 2.0, Koin, Navigation 3 shell | Android, MyItmoApi |
| Feature ports | home and QR (the pilot), schedule, sport, recordbook, social, settings, resources and reviews, account | Android |
| Look | Material 3 Expressive token schema, flip, component pass | Android |
| iOS | SwiftUI shell, system screens, WidgetKit, Control, App Shortcuts, push | Android repository |
| Backend 1.8.0 | platform field, account deletion, 401, push for iOS, Spring Boot 4.1, MyItmoApi 2.0.0 | Backend |
| Web | admin per platform, iOS landing and links, account deletion text | Web |

Lanes push their own branches, one integrator merges into `v2.3/next`, and the
owner moves `master` in batches
([0029](../decisions/0029-lanes-and-integrator.md)). Deploys, tags, releases
and store uploads need the owner's word each time.

## Gates

Product milestones in order. Each opens when its condition holds; the live
state is kept by the integrator, not here.

| Gate | What it means for the product |
|---|---|
| T5 Kickoff | `master` builds `2.3-SNAPSHOT`; `release/2.2` carries 2.2.x fixes and Play uploads |
| T11 Recipe frozen | The pilot, the QR pass on the KMP spine, runs on Android through every layer; feature ports copy its recipe |
| T12 iOS pilot | The same QR screen runs in the iOS shell on the simulator |
| T13 Apple account | Developer account, identifiers, APNs key and an iPhone exist; device builds and TestFlight become possible |
| T14 Ports done | Every Android screen is a shared CMP screen; only Fragment hosts and the old shell remain |
| T15 Flip | The Material 3 Expressive values replace today's values in one change; then the Navigation 3 shell becomes the default |
| M2 MyItmoApi 2.0.0 | The KMP MyItmoApi is published; release builds and Backend use it |
| R Backend 1.8.0 | The single v2.3 Backend release is in production, at least a week before T16 |
| T16 Release candidate | Required work merged or cut, full appearance matrix, 2.2 → 2.3 upgrade on a device, M2 and R reached |

After T16 the owner signs and publishes Android 2.3; iOS follows the ladder
below.

## iOS

Scope ([0023](../decisions/0023-ios-client.md)):

| Tier | Content |
|---|---|
| MVP, "ITMO.Widgets 2.3.0 for iPhone" | sign-in, onboarding, demo; home, schedule with lesson details, sport, QR pass, profile and social; recordbook with the subject page, own sheet totals, the BARS overlay and mark tracking; reviews and subject links with editors, votes and reports; calendar sync and `.ics` export; settings, web sign-in scanner, update offer, diagnostics; WidgetKit QR, lesson, day and Lock Screen widgets; QR Control; App Shortcuts; background refresh; push; Universal Links |
| Stretch | sport auto-sign in a notification service extension, App Store release, in-app account deletion, Smart App Banner |
| v2.4 | the QR widget's custom spoiler image |

Minimum iOS 18, marketing version 2.3.0, bundle ID
`dev.alllexey.itmowidgets`. iOS needs Backend 1.8.0 in production and uses the
development Backend until then.

How iOS ships depends on when T13 opens:

| Case | iOS in v2.3 |
|---|---|
| F0, account early | MVP and stretch on TestFlight external at T16; App Store after Android GA |
| F1, account mid-cycle | MVP on TestFlight internal at T16, external about a week later |
| F2, account late | Android ships on time; iOS stays simulator-verified and CI-built, TestFlight 1–2 weeks after T13 |
| F3, no account at T16 | On the owner's word, an unsigned developer preview on a GitHub prerelease for self-signing, without push |

## v2.4 backlog

Parked by the parity rule; each needs a decision before it becomes work.

- Glance widgets on Android ([0027](../decisions/0027-remoteviews-widgets.md)).
- The iOS QR widget's custom spoiler image.
- A sport score widget (attendances, bonus and debt toward 100 points).
- English localisation.
- Blocking users, deferred from the v2.1 social layer.
- Strict verification of flow audiences for subject links (ISU or zkTLS,
  [0008](../decisions/0008-community-moderation.md)).
- Student sections of the web version, signed in with an ITMO.ID token kept in
  the browser; Backend does not proxy MyITMO.
- Release signing in CI, waiting for the owner's signing-secret decision.
- Proposed without a verdict: sport debt and history, the official teacher
  card, request statuses, scholarship totals, a live "идёт пара" update,
  lesson reminders, electives, the study plan, a session view.

## Delivered

- v2.0.1 (tag `v2.0.1`): legacy parity on the Hilt and repository
  architecture.
- v2.1 and v2.1.1: friendships and privacy, profiles, friends on a lesson,
  lesson details, map hand-off, the subject hub; the PostgreSQL Backend.
- v2.2 (tag `v2.2`, 2026-10-03): subject links and teacher reviews with
  moderation, AI summaries, own sheet totals, schedule changes and mark
  notifications, calendar sync and export, App Links and sharing, the QR tile
  and shortcuts, the home feed, the demo session and the Play variant.
- Details are in [`CHANGELOG.md`](../../CHANGELOG.md), the tags and decisions
  0001–0015.
