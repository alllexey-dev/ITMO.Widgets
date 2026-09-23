# Releases and compatibility

## Release sequence

1. **v2.0.1** restores full functional parity with the legacy application on the
   refactored Hilt/repository architecture: authentication, onboarding, FCM, QR,
   all widgets, settings, diagnostics, schedule and sport. Not finished yet; the
   roadmap's "completed baseline" wording is aspirational.
2. **v2.0.2** is reserved for compatibility or bug-fix work found after v2.0.1.
   No product features.
3. **v2.1** adds the social and study-context layer: explicit friendships and
   privacy, own and public profiles, friends on a lesson, lesson details, map
   hand-off and the subject hub.
4. **v2.1.1** is a bug-fix release on top of v2.1: current study groups on
   every screen, sign-in through third-party providers on the ITMO.ID page,
   cached content kept on screen during refresh, skeleton first loads. No
   product features, no Core or Backend change.
5. **v2.2** adds moderated community resources and reviews, legacy review
   import, personal Google Sheet mappings, schedule change tracking, BARS mark
   notifications, range calendar export, verified App Links, sharing, the QR
   quick-settings tile with app shortcuts, and the home feed.

Achievements, messaging, posts, followers and free-window discovery are outside
the roadmap. Do not add them opportunistically.

Development happens on `master`, which builds as `2.2-SNAPSHOT` (version code
5); the latest release is `2.1.1`. A release APK is signed with
`app-keystore.jks` (alias `key0`, the same certificate as 2.0.1) through the
ignored `keystore.properties`; the checklist is in `vibe/release-2.1.md`.

## Version compatibility

Core and Backend use their own semantic versions. Each Android release records
the minimum Core and Backend it needs.

| Android | Core | Backend | MyItmoApi | Notes |
|---|---|---|---|---|
| 2.2-SNAPSHOT (development) | 1.7.0-SNAPSHOT (Maven Local) | 1.7.0-SNAPSHOT, PostgreSQL V4 (`V4__subject_links.sql`) | 1.8.1 | Subject links need the V4 schema and the links API; debug builds only, not released. |
| 2.1.1 | 1.2.0 | 1.2.1 (`770293b`, current groups in every profile response) | 1.8.1 | Client-only release; works against Backend 1.2.0 as well, then lesson friends may show an older group. |
| 2.1 | 1.2.0 (Maven Central, tag `1.2.0`) | 1.2.0, commit `a70cab1` or later | 1.8.1 (Maven Central, tag `1.8.1`) | Backend 1.2.0 requires the PostgreSQL cutover; 2.0.x clients are rejected by it and are told to update through `GET /api/app/version`. |
| 2.0.x (legacy) | 1.1.x | 1.1.6 | 1.6.0 | MariaDB backend, reciprocal friend requests, boolean privacy. |

Core is published to Maven Local during development and to Maven Central for a
release (decision 0003 covers the snapshot period). Public publication of Core
or MyItmoApi and any deployment happen only on explicit request.

## What each release must not break

- Existing enabled legacy privacy settings become `Друзья`; disabled ones stay
  `Никто`. An upgrade never broadens an audience.
- Widgets keep the single established style and smart update scheduling.
- Refresh tokens stay on the device; Backend receives only the access token.
- The production database reset is a fresh PostgreSQL cluster after the Android
  2.1 release, with a verified backup and separate approval. It is not an
  in-place MariaDB migration.

## Environments

| Environment | URL | Used by |
|---|---|---|
| Development | `https://dev.widgets.alllexey.dev` | Debug builds (`BuildConfig.WIDGETS_BASE_URL`) |
| Production | `https://widgets.alllexey.dev` | Release builds |

The base URL is selected per build type in `app/build.gradle.kts`, never
hard-coded in a Hilt module. A release build must never point at development.
