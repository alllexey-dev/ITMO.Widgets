# Demo session

A hidden session with fictional data for the Google Play review and for store
and landing screenshots. It needs no ITMO.ID account, no network, no Backend
and no My ITMO; nothing of it leaves the device. The reasons for the hidden
entry and for gating in the repositories are in decision
[0015](../decisions/0015-demo-mode.md).

## Entry

Five taps on the logo of the sign-in screen (`auth_logo`), each at most 1.5 s
after the previous one (`DemoEntryTaps`, `REQUIRED_TAPS = 5`,
`MAX_INTERVAL = 1.5 s`). Every tap calls `AuthViewModel.onLogoTap()`;
`DemoEntryTaps` times the taps on the injected `kotlin.time.TimeSource`, which
`authModule` binds to `TimeSource.Monotonic` (tests use `TestTimeSource`). Taps
during a sign-in or a session change are ignored. On success the route hands
`Демо-режим` to the host: `AuthFragment` confirms with
`HapticFeedbackConstants.CONFIRM` (`VIRTUAL_KEY` below Android 11) and the toast
`Демо-режим`, then `SessionRepository.startDemo()` replaces the screen.

The logo stays decorative for accessibility services: it takes taps through a
plain pointer handler (`detectTapGestures`), not a click, and has no content
description, so there is no focus stop and no hint. The
reviewer finds the entry in the access instructions of the Play listing.

## Session

- `SessionState.SignedIn(user, demo = true)` with the fictional user
  `DemoPeople.ME` (Анна Смирнова, ISU 999001, group K3221).
- `startDemo()` cancels widget work (`prepareForSessionChange`), runs every
  `SessionDataCleaner`, clears the ITMO.ID tokens and stores `demo_active` in
  `DemoPreferences`. It does not call Backend identity sync, FCM token sync,
  device registration or the signed-in lifecycle effects, so no background
  work is scheduled.
- `demo_active` survives process death: `initialize()` restores the demo
  session before it looks at tokens. A real sign-in (`replaceSession`) clears
  the flag.
- `DemoCurrentUserProvider` wraps the ID-token provider and answers
  `DemoPeople.ME` while the flag is on.
- `MainActivity` skips the first-run flow without marking it passed and does
  not check for updates. `My ITMO` in the browser and `Вход на сайт` show the
  toast `Недоступно в демо` instead of opening.
- The banner `demo_banner` sits above the bottom bar: `Демо-режим · вымышленные
  данные` and `Войти`. `Войти`, like `Выйти` in the profile, goes through
  `signOut()`, whose demo branch clears every cache and the flag without
  unregistering a device and leads to the sign-in screen.

## Gate

`core/demo/DemoMode` (`suspend fun isActive()`, `fun observeActive()`) is
implemented by `feature/auth/data/DataStoreDemoMode` on `demo_active` and bound
in `SessionModule`. Every class whose constructor takes `ItmoWidgetsApi`,
`MyItmo`, `MyItmoApi`, `Bars` or the `@PublicWebClient` `OkHttpClient` also
takes `DemoMode` and checks it where it actually calls the network:

- reads answer with the feature's fictional data from its `data/demo` package;
- writes and pages outside the app fail with `AppError.DemoUnavailable`, shown
  as `Недоступно в демо`.

The Konsist rule `network clients are gated by demo mode` enforces the
constructor parameter (`core/network`, which builds the clients, is exempt);
the per-feature `*DemoGateTest` and `DemoNetworkGateTest` check the behaviour.

| Area | In the demo |
|---|---|
| Schedule, friends' schedules, friends on a lesson | `DemoSchedule`: a weekly template of the autumn semester with the sport visits, two found schedule changes |
| Schedule change check | the two changes; a check compares nothing (`Compared(0)`) |
| Sport catalog, bookings, queues, auto-sign, score | `DemoSport` on `core/demo/DemoSportSlots`; sign-up, cancel and queues fail |
| Recordbook, mark news, own sheet totals | `DemoRecordbook`; read news are kept in memory only; BARS requests and connecting a sheet fail |
| Friends, requests, people search, person profiles | `DemoSocial`; every relationship action fails |
| Teacher reviews and tone dots | `DemoReviews`; writing, voting and reporting fail |
| Subject links | `DemoSubjectLinks`; adding, editing, voting, pinning and reporting fail |
| QR pass | `DemoQr`: a code no turnstile accepts |
| `Подключение к ITMO.Widgets` | reads as on (`CustomServicesRepository.isEnabled`), the stored choice is kept; `isChangeable()` is false and the switch answers `Недоступно в демо` |
| Privacy | the default audiences; changes fail |
| `Синхронизация с календарём` | `CalendarSyncResult.DEMO_UNAVAILABLE` |
| `Выгрузить в .ics` | works on the demo schedule (`MyItmoOwnScheduleSource`) |
| Update offer | none |
| Sport auto-sign pushes | ignored (`SportSignPushHandler`) |

Local appearance settings, filters and every screen work as usual. Widgets do
not show demo data: without an ITMO.ID token they stay in their signed-out
state. Widget images for the store and the landing come from
`WidgetPreviewImageCapture`.

## Data set

One consistent set: `core/demo` holds the people (`DemoPeople`, ISUs in the
999xxx range no real student has), the subjects with their teachers and flows
(`DemoStudy`) and the weekly sport slots (`DemoSportSlots`); each feature's
`data/demo` builds its own models from them, so the schedule, the recordbook,
the links, the reviews and the sport tab name the same subjects and people.
Dates come from `AcademicTimeProvider`: today and two weeks around it by the
weekly template; there are no direct `now()` calls. The sport sign page opens on
today, so when the template has nothing left there (late in the evening, on a
Sunday) `DemoSportSlots.extraSlots` adds two fitness lessons to today from the
next ten-minute mark; `DemoContentTest` checks today always has a lesson to sign
up for.

`StoreScreenshotCapture` (the Google Play frames, which are also the landing's
screenshots) walks this set through the real `MainActivity`, and the debug
`HomeFixture` reads it too. `RecordbookPreviewFixtures` and the profile
previews of `:shared:feature-social` keep their own edge cases for the visual
tests.

## iOS

The iOS app ([iOS app](../ios.md)) runs the same demo session on the same
shared data set.

- Entry: the same five taps on the sign-in logo (`DemoEntryTaps` in
  `AuthViewModel`); the logo is hidden from VoiceOver. On success a success
  haptic and a VoiceOver announcement of `Демо-режим` replace the toast, then
  `SessionRepository.startDemo()` replaces the screen.
- The demo skips the first-run flow, as on Android, and shows the tabs with
  the demo banner (`demo_banner_text` and its sign-in button, which signs out
  of the demo) above the tab bar. Every shared repository checks `DemoMode`
  before a request and `IosBackendGate` never lets the demo call Backend, so
  every screen opens with zero network calls; the router refuses keys that
  need a real account (`error_demo_unavailable`).
- Widgets read the demo from session-v1.json: the QR widget shows the demo
  pass labelled `Демо-режим`, the schedule widgets their demo state.
- Debug builds: `-itmoDemo` opens the demo at launch, `-itmoSignedOut` starts
  signed out, `-itmoOnboarding` shows the first-run flow over the demo. Most
  UI tests run on the demo session.
- Tests: `UITests/OnboardingUITests` (five taps open the demo),
  `UITests/ShellUITests` (the banner and its sign-out) and every feature's UI
  tests, which run on the demo.

## Tests

- JVM: `DemoEntryTapsTest`, the demo cases of `SessionRepositoryImplTest` and
  `AuthViewModelTest`, `DemoNetworkGateTest`, `ScheduleDemoGateTest`,
  `SportDemoGateTest`, `RecordbookDemoGateTest`, `SocialDemoGateTest`,
  `ReviewsDemoGateTest`, `CustomServicesRepositoryImplTest`, and
  `DemoContentTest` (every person belongs to the set, no placeholder words or
  test ISUs, dates within two weeks of today, known ITMO buildings; on a
  weekday and a Sunday clock).
- Instrumented: `DemoModeFlowTest` (five taps open the demo, it survives
  recreation and ends with sign-in; every main screen has content and writes
  are refused) and `StoreScreenshotCapture` (every store frame in the Compose
  shell has content, no error or empty state and no test wording; it saves the
  PNGs only with `captureScreenshots=true`).

```bash
./gradlew :app:testGithubDebugUnitTest
./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.alllexey.itmowidgets.app.DemoModeFlowTest
```
