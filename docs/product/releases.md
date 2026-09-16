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
4. **v2.2** adds moderated community resources and reviews, legacy review
   import, personal Google Sheet mappings, schedule change tracking, range
   calendar export, verified App Links, sharing and the home feed.

Achievements, messaging, posts, followers and free-window discovery are outside
the roadmap. Do not add them opportunistically.

The Android branch `itmo-widgets-v2.1` is the public-release branch. The app
currently builds as `2.1-SNAPSHOT` (version code 4); the branch name does not
imply release readiness.

## Version compatibility

Core and Backend use their own semantic versions. Each Android release records
the minimum Core and Backend it needs.

| Android | Core | Backend | MyItmoApi | Notes |
|---|---|---|---|---|
| 2.1-SNAPSHOT (current branch) | 1.2.0-SNAPSHOT, commit `28bcb92` | 1.2.0-SNAPSHOT, commit `79da56a` | 1.8.0 | Snapshot numbers stay fixed until 2.1 ships; a matching number alone does not prove the API is present. See decision 0003. |
| 2.0.x (legacy) | 1.1.x | 1.1.6 | 1.6.0 | MariaDB backend, reciprocal friend requests, boolean privacy. |

Core is published to Maven Local during development. Public publication of Core
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
