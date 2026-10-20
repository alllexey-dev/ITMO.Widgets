# Debug tools

`feature/debug` is a screen of developer controls for debug builds. A release build has no entry to it
and reads none of its overrides. User-facing counterparts: [Settings](../settings.md#debug-only-controls).

## Entry and gate

- Key `AppRoutes.DebugTools`, opened from the [profile tab](me.md) row shown only when
  `BuildConfig.DEBUG`. `shellEntries(debugTools = BuildConfig.DEBUG)` in `ShellEntries.kt` registers
  the key only in a debug build, so a release registry cannot open it (`RegistryCompletenessTest`).
- Konsist `debug code is gated` (`DebugRulesTest`): every `File*` store and
  `*RefreshTokenController` of `core/debug` and `feature/debug`, and `FileAcademicTimeOverrideStore`,
  need `@DebugOnly` and a `BuildConfig.DEBUG` check; the rest only delegates to them.

## Screen

- `DebugToolsScreen(state, actions)` (`feature/debug/ui/DebugToolsScreen.kt`) stays in `:app`: debug
  tools never move to `shared/` or reach iOS. Strings in `strings_debug.xml`.
- `DebugToolsEntry` (`AccountEntries.kt`) takes `DebugToolsViewModel` (app `DebugModule.kt`); events
  (`RecreateActivity`, `RefreshTokenUpdated`, `RefreshTokenUpdateFailed`) go to
  `handleDebugToolsEvent` (`DebugToolsEffects.kt`).
- Cards:
  - Test sign-in by refresh token via `SessionRepository.signInWithRefreshToken` (the normal session
    transition, previous account data cleared). The token lives only in the dialog's memory state,
    never in saved state.
  - Test academic date: moves `AcademicTimeProvider` for schedule and sport only; sign-in, caches and
    the QR pass keep system time.
  - Test sport scores: 0..9999 each for attendance and bonus.
  - Template sport lessons switch.
  - One-off checks on the real account: `ScheduleChangeTracking.checkNow()` (`schedule-changes-now`),
    `MarkTracking.checkNow()` (`marks-check-now`, `ExistingWorkPolicy.REPLACE`, network, tag `marks`),
    and `Проверить продление БАРС`, `BarsSessionProbe.start()`: `BarsCookieProbeWorker`
    (`bars-cookie-probe`, 120 s delay to kill the app and start cold), read-only, writes no cookies or BARS header; logcat
    `BarsCookieProbe` holds only outcome, step, counts and process age.
- The opt-in section is always hidden (the opt-in lives in settings).
- Every override change recreates the activity, so every screen reads the new value from the start.
  The open dialog survives recreation; typed text does not.

## iOS

No debug tools screen (`showDebugTools` is false). A Debug build takes launch arguments instead
(`-itmoDemo`, `-itmoRunRefresh` and others, listed in [the iOS app](../ios.md)); Release ignores
them.
