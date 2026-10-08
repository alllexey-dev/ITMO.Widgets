# shared/feature-sport

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.sport.*`: the sport tab, sign-up, another user's sport.
  `data/` (`repository/`, `mapper/`, `push/` with `SportSignPushBooker`, `home/`, `debug/` ports, `demo/`),
  `domain/` (`model/`, `repository/`), `navigation/` (`SportRoutes`), `presentation/` (`common/`, `my/`, `sign/`,
  `user/`), `ui/` (the same split plus `details/`, `home/`), `cards/` (preview card fixtures).
- `di/`: the Koin module `sportModule`, the only graph for these types; `iosMain`: `sportIosModule`,
  `IosSportRoutes`, `SportShares`.
- `strings_sport.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `androidHostTest`: repositories, mappers, screens, `SportModuleTest`, `SportScreenshotTest` and `SportGoldenTest`
  over `src/androidHostTest/resources/sport/golden/` and the MyITMO fixtures in `resources/myitmo/sport/`.
- Not here: `SportFragment`, `UserSportFragment`, the details sheet host, `SportSignPushHandler` and the debug
  template provider stay in `app/` under `feature/sport/`; `di/bridge/SportBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core`, `:shared:designsystem`; `:shared:testing` in tests only. Koin and the KMP lifecycle.
- MyItmoApi 2.x for the catalog, bookings and score; Backend's `sport` area (behind `BackendGate`) for queues and
  forecasts; `FriendRepository` comes from `friendSelectorModule` through Koin, never an import of
  `:shared:feature-social`.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-sport`; `scripts/verify.sh klibs feature-sport`
after an `iosMain` change.

## Hot files
- Every file here: lane L11; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L11 writes, L04 reviews.
- `strings_sport.xml`: L11; keys are never renamed. The golden files: only `SPORT_GOLDEN_RECORD=1` rewrites them,
  and only for a deliberate behaviour change.
- Stable identifiers: none held here; the `sport` channel, the `/sport/` App Link path, `OPEN_SPORT` and the
  debug override files are app-side entries of `StableIdentifiersTest`.
- `SportRulesTest` (Konsist, lane L06): no sport ViewModel is scoped to the activity; only a route obtains them.

## Docs
- [Sport](../../docs/features/sport.md); [sport push handler](../../docs/features/notifications.md#sport-handler).
- [Screen in a shared module](../../docs/recipes/shared-module-screen.md),
  [endpoint end to end](../../docs/recipes/endpoint-end-to-end.md); ADR
  [0026](../../docs/decisions/0026-core-2-backend-client.md) (Backend client).
