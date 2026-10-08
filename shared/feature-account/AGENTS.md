# shared/feature-account

## Owns
- `commonMain`, six packages under `dev.alllexey.itmowidgets.feature`, each with its own `data/`, `domain/`,
  `presentation/`, `ui/` as needed: `auth` (ITMO.ID sign-in, session, demo entry), `onboarding` (first-run gate and
  steps), `me` (the profile tab), `weblogin` (sign-in on the web by code), `web` (`MyItmoWebPolicy`,
  `MyItmoWebScreen`) and `update` (the update offer and gate).
- `di/` per package, the Koin modules: `authDataModule`, `authModule`, `onboardingDataModule`, `onboardingModule`,
  `meModule`, `webLoginDataModule`, `webLoginModule`, `updateModule`; `iosMain`: `accountIosModule`,
  `updateIosModule`, the onboarding and web-login parameters and the iOS session ports.
- `strings_auth.xml`, `strings_onboarding.xml`, `strings_me.xml`, `strings_weblogin.xml`, `strings_web.xml`,
  `strings_update.xml` in `composeResources/values/`; not exported to `:app`.
- `commonTest`: repositories, ViewModels, policies; `androidHostTest`: one Koin graph test per package, screens and
  `AccountScreenshotTest`; `iosTest`: the iOS modules and the App Store listing.
- Not here: `LoginActivity`, the account Fragments, `WebLoginBottomSheet`, `WebSessionDataCleaner` and the update
  action of each distribution (`app/src/github`, `app/src/play`) stay in `app/` under `feature/<package>/`; the
  bridges are `di/bridge/AccountAuthBridge.kt`, `AccountOnboardingBridge.kt` and `AccountUpdateBridge.kt`.

## Depends on
- `:shared:core` (session, token storage contracts, `DemoMode`), `:shared:designsystem`; `:shared:testing` in tests
  only. Koin and the KMP lifecycle.
- MyItmoApi 2.x for ITMO.ID and MyITMO; Backend's `users`, `device` and `app` areas behind `BackendGate`.

## Verify
`scripts/verify.sh quick`, `scripts/verify.sh shots feature-account`; `klibs feature-account` after `iosMain`.

## Hot files
- Every file here: lane L16; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L16 writes, L04 reviews.
- The six `strings_<package>.xml`: L16; keys are never renamed.
- Stable identifiers: none held here; the token file `myitmo_tokens.enc`, its Keystore alias and `v1:` prefix are
  app-side entries of `StableIdentifiersTest`; the `demo_active` key lives in core's `DemoPreferences`.
- `MeRulesTest` (Konsist, lane L06): the debug tools row shows only in debug builds.

## Docs
- [Auth](../../docs/features/auth.md) ([stable identifiers](../../docs/features/auth.md#stable-identifiers)),
  [onboarding](../../docs/features/onboarding.md), [Me](../../docs/features/me.md),
  [web login](../../docs/features/web-login.md), [MyITMO web](../../docs/features/my-itmo-web.md),
  [update](../../docs/features/update.md); ADR [0015](../../docs/decisions/0015-demo-mode.md) (demo mode).
