# Decisions

Records of choices that were argued once. A changed decision gets a new record
that supersedes or amends the old one; the old one carries a note at the top.
Backend decisions live in the
[Backend repository](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/README.md)
and keep their numbers.

- 0001 Single Gradle module - superseded by 0018, deleted
- [0002 Flyway V1 is immutable](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0002-immutable-v1.md) - Backend
- 0003 Snapshot versions - superseded by 0024, deleted
- [0004 Privacy audiences without reciprocity](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0004-privacy-audiences.md) - Backend
- [0005 Explicit friendships, crossed request accepts](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0005-friendships.md) - Backend
- [0006 People search via MyITMO plus lookup](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0006-people-search.md) - Backend
- [0007 Push delivery guard by recipient ISU](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0007-push-guard.md) - Backend
- [0008 Subject links: schedule-flow audiences, premoderation only for everybody](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0008-community-moderation.md) - Backend
- [0009 One person profile, direct My ITMO identity and copied reviews](0009-person-profile.md) - accepted
- [0010 Own teacher reviews verified through ISU flows](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0010-review-verification.md) - Backend
- [0011 AI summaries of teacher reviews through Gemini behind a proxy](https://github.com/alllexey-dev/itmo-widgets-backend/blob/dev/docs/decisions/0011-ai-review-summaries.md) - Backend
- [0012 BARS is renewed in the background by replaying ITMO.ID with cookies](0012-bars-background-renewal.md) - accepted
- [0013 Schedule changes are detected on the device](0013-schedule-changes-on-device.md) - accepted
- [0014 Own totals from public Google Sheets are read on the device](0014-sheet-scores-on-device.md) - amended by 0032
- [0015 A hidden demo session gated in the repositories](0015-demo-mode.md) - accepted
- [0016 Stable identifiers keep their values](0016-parity-rewrite.md) - historical; the stable identifier rule holds
- [0017 Compose Multiplatform screens in `commonMain` of shared modules](0017-cmp-ui-in-common-main.md) - superseded by 0031 and 0035
- [0018 Module graph and toolchain](0018-module-graph-and-toolchain.md) - accepted
- [0019 Koin is the only dependency graph](0019-koin-per-lane.md) - accepted
- [0020 One Navigation 3 shell with `@Serializable` routes](0020-navigation-3.md) - amended by 0035
- [0021 Material 3 Expressive through one token schema](0021-m3-expressive-order.md) - amended by 0031
- [0022 JVM screenshot tests replace emulator matrices](0022-jvm-screenshot-tests.md) - amended by 0031 and 0035
- [0023 The iOS client: a SwiftUI app in this repository over shared Kotlin](0023-ios-client.md) - partly superseded by 0031 (screens, minimum iOS)
- [0024 MyItmoApi 2.x from Maven Central, a local checkout as an opt-in composite build](0024-pinned-composite-build.md) - accepted
- [0025 MyItmoApi 2.x is a Kotlin Multiplatform library beside 1.x](0025-myitmoapi-2-kmp.md) - accepted
- [0026 Core 2.0 is a client-only KMP module; Backend owns the wire DTOs](0026-core-2-backend-client.md) - accepted
- [0027 Android widgets stay RemoteViews; a shared timeline feeds them and WidgetKit](0027-remoteviews-widgets.md) - accepted
- [0028 One Russian string catalog generated outward; Material Symbols Rounded with an SF Symbol registry](0028-strings-and-icons.md) - amended by 0035 and 0036
- 0029 Lanes and integrator - process, deleted
- [0030 Release lines, version codes and data continuity](0030-release-lines-and-data-continuity.md) - accepted
- [0031 iOS screens are native SwiftUI over shared Kotlin](0031-swiftui-ios-screens.md) - accepted, amended by 0035
- [0032 The most current score source with a value wins](0032-score-source-priority.md) - accepted
- [0033 Campus plans come from a static mirror, outside the Backend opt-in](0033-campus-map-mirror.md) - accepted
- [0034 One client header and one activity table for client statistics](0034-client-stats.md) - accepted
- [0035 The iOS framework links no Compose](0035-ios-without-compose.md) - accepted
- [0036 Scope texts of connected clients come from Backend](0036-backend-scope-texts.md) - accepted
