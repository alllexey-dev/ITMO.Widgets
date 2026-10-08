# shared/feature-home

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.home.*`: the home feed. `data/` (the DataStore card
  preferences and hint stores, `HintHomeCardSource`), `domain/` (`HomeHints`), `presentation/` (`HomeViewModel`,
  `HomeUiState`), `ui/` (`HomeRoute`, `HomeScreen`, `HintHomeCards`, previews).
- `di/`: the Koin module `homeModule`. The feed reads every `HomeCardSource` and `HomeCardRenderer` with
  `getAll()`; each contributor registers its own under its own qualifier (home's is `hintCardsQualifier`).
- `iosMain`: `homeIosModule(widgets)`, `IosHomeHintStatus`, `HomeCardCapabilities` (the card kinds iOS offers).
- `strings_home.xml` in `composeResources/values/`; not exported to `:app`.
- `commonTest`: ViewModel, stores, hint source and screen tests (`HomeFakes`); `androidHostTest`: `HomeModuleTest`,
  `HomeScreenshotTest`.
- Not here: the cards of other features (each feature's `home/` package); `HomeFragment` and
  `AndroidHomeHintStatus` in `app/` under `feature/home/`, bound through `di/bridge/HomeBridge.kt`.

## Depends on
- `:shared:core` (the `HomeCardSource` contract, layout preferences, opt-in, clock), `:shared:designsystem`;
  `:shared:testing` in tests only. Koin and the KMP lifecycle.
- No network client: the feed knows its sources only as a set, never a feature type.

## Verify
`scripts/verify.sh quick`, then `scripts/verify.sh shots feature-home`; `scripts/verify.sh klibs feature-home` after
an `iosMain` change.

## Hot files
- Every file here: lane L09; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L09 writes, L04 reviews.
- `strings_home.xml`: L09; keys are never renamed.
- Stable identifiers: none held here; the `today` and `qr_pass` shortcuts are app-side entries of
  `StableIdentifiersTest`.
- `HomeRulesTest` (Konsist, lane L06): the home feed UI parses no dates or times.

## Docs
- [Home](../../docs/features/home.md) ([feed](../../docs/features/home.md#feed)); the home rows of
  [settings](../../docs/settings.md#home-screen).
- [Screen in a shared module](../../docs/recipes/shared-module-screen.md); ADRs
  [0017](../../docs/decisions/0017-cmp-ui-in-common-main.md) and [0019](../../docs/decisions/0019-koin-per-lane.md).
