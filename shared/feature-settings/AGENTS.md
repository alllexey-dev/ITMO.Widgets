# shared/feature-settings

## Owns
- `commonMain`, packages `dev.alllexey.itmowidgets.feature.settings.*`: settings as data. `domain/` (the page model
  `SettingItem`, `SettingSection`, `SettingsPage`, `SettingRowId`, `SettingsEvent`), `data/` (settings, sharing,
  schedule preferences, widget appearance and custom-services repositories), `presentation/` (`SettingsViewModel`
  with one page provider per `SettingsPage`, `DiagnosticsViewModel`, `IcsExportViewModel`), `ui/` (`SettingsScreen`
  and its dialogs, `diagnostics/`, `ics/`, `preview/`).
- `di/`: the Koin modules `settingsModule` (screens, page providers) and `settingsDataModule` (one single per
  repository, also read by other features); `iosMain`: `settingsIosModule` and the iOS accesses.
- `strings_settings.xml` in `composeResources/values/`, exported to `:app` as Android resources.
- `commonTest`: data, domain, ViewModels; `androidHostTest`: `SettingsModuleTest`, screens, dialogs and
  `SettingsScreenshotTest`, whose baselines also stand for the app's hosts.
- Not here: `SettingsFragment`, `DiagnosticsFragment`, `IcsExportBottomSheet`, the calendar and background-work
  dialogs, `AndroidBackgroundWorkAccess`, `AndroidQuickSettingsTileAccess` and `CustomSpoilerRepositoryImpl` stay in
  `app/` under `feature/settings/`; `di/bridge/SettingsBridge.kt` bridges Koin and Hilt.

## Depends on
- `:shared:core` (the preference stores and their keys), `:shared:designsystem`; `:shared:testing` in tests only.
  Koin, the KMP lifecycle and its saved state.
- Backend's `users` area behind `BackendGate` for the sharing (privacy) settings.

## Verify
`scripts/verify.sh quick`, `scripts/verify.sh shots feature-settings`; `klibs feature-settings` after `iosMain`.

## Hot files
- Every file here: lane L14; `iosMain`, `iosTest`: lane L18. `build.gradle.kts`: L14 writes, L04 reviews.
- `strings_settings.xml`: L14; keys are never renamed.
- Stable identifiers: none held here; the preference keys live in `:shared:core`, the DataStore file
  `app_preferences` is an entry of `StableIdentifiersTest`.
- `SettingsRulesTest` (Konsist, lane L06): the page model is free of Android `R`, Compose and resource ints.

## Docs
- [Settings](../../docs/settings.md) ([where it lives](../../docs/settings.md#where-it-lives)),
  [settings as data](../../docs/architecture.md#settings-as-data); ADR
  [0004](../../docs/decisions/0004-privacy-audiences.md) (privacy audiences).
