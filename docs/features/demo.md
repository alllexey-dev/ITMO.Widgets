# Demo session

Product behaviour: [Как это работает: демо-режим](../how-it-works/demo.md).

A hidden session with fictional data for the Google Play review and store and
landing screenshots. It needs no ITMO.ID account, network, Backend or My ITMO;
nothing of it leaves the device. Rationale: decision
[0015](../decisions/0015-demo-mode.md).

## Entry

- Five taps on the sign-in logo (`auth_logo`), each at most 1.5 s after the
  previous (`DemoEntryTaps`, `REQUIRED_TAPS`, `MAX_INTERVAL`), timed on the
  injected `kotlin.time.TimeSource` (`TimeSource.Monotonic` in `authModule`).
  Taps during a sign-in or a session change are ignored.
- On success `confirmDemoEntry` (`shell/entries/AccountEntries.kt`) confirms
  with haptics and a toast, then `SessionRepository.startDemo()` replaces the
  screen.
- The logo stays decorative for accessibility (`detectTapGestures`, no click).

## Session

- `SessionState.SignedIn(user, demo = true)` with `DemoPeople.ME` (ISU 999001),
  also answered by `DemoCurrentUserProvider` while the flag is on.
- `startDemo()` cancels widget work, runs every `SessionDataCleaner`, clears
  the ITMO.ID tokens and stores `demo_active` (`DemoPreferences`). It skips
  Backend identity sync, FCM token sync, device registration and signed-in
  effects, so no background work is scheduled.
- `demo_active` survives process death: `initialize()` restores the demo
  before looking at tokens. A real sign-in clears it.
- `ShellGate` skips the first-run flow without marking it passed and refuses
  `My ITMO` in the browser, `Подключить устройство` and `Подключённые устройства`; `ShellHost` does not check for
  updates.
- The banner `demo_banner` sits above the bottom bar; its `Войти`, like
  `Выйти`, goes through `signOut()`, whose demo branch clears caches and the
  flag without unregistering a device.

## Gate

`core/demo/DemoMode` (`isActive()`, `observeActive()`) is implemented by
`feature/auth/data/DataStoreDemoMode` and bound in `authModule`. Every class
whose constructor takes a network client (`BackendClient` or its `*Api`s,
`MyItmoClient`, `BarsClient`, `ItmoIdClient`, Ktor's `HttpClient`) also takes
`DemoMode` and checks it where it calls the network:

- reads answer with the feature's fictional data from its `data/demo` package;
- writes and pages outside the app fail with `AppError.DemoUnavailable`.

The Konsist rule `network clients are gated by demo mode` (`GateRulesTest`)
enforces the constructor parameter (`core/network` is exempt); the per-feature
`*DemoGateTest` and `DemoNetworkGateTest` check the behaviour.

Non-obvious per-area rules:

- The connection to ITMO.Widgets (`Сервер ITMO.Widgets`) reads as on
  (`CustomServicesRepository.isEnabled`) while the stored choice is kept; `isChangeable()` is false.
- `Синхронизация с календарём` returns `CalendarSyncResult.DEMO_UNAVAILABLE`;
  `.ics` export works on the demo schedule.
- Sport auto-sign pushes are ignored (`SportSignPushHandler`).
- Widgets do not show demo data on Android: without an ITMO.ID token they stay
  signed out. Store widget images come from `WidgetPreviewImagesTest` (`ITMO_WRITE_WIDGET_PREVIEWS=1`).

## Data set

- `core/demo` holds the people (`DemoPeople`, ISUs in the 999xxx range no real student has), subjects
  (`DemoStudy`) and sport slots (`DemoSportSlots`); each feature's `data/demo` builds on them, so all
  screens name the same subjects and people.
- Dates come from `AcademicTimeProvider` (no direct `now()`); when today has no sport lesson left,
  `DemoSportSlots.extraSlots` adds two from the next ten-minute mark. `StoreScreenshotCapture` walks the set
  through the real `MainActivity` for the Play and landing screenshots; `DemoContentTest` guards it.

## iOS

- Same five taps (`DemoEntryTaps` in `AuthViewModel`); a VoiceOver
  announcement replaces the toast.
- `IosBackendGate` never lets the demo call Backend; the router refuses keys
  that need a real account (`error_demo_unavailable`).
- Unlike Android, widgets read the demo from session-v1.json.
- Debug launch flags: `-itmoDemo`, `-itmoSignedOut`, `-itmoOnboarding`. Most UI
  tests run on the demo session.
