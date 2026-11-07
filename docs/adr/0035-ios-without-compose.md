# 0035 The iOS framework links no Compose

**Status.** Accepted (2026-10-19). Implemented for core and every feature module; the iOS framework links no Compose.

**Decision.** After ADR 0031 no iOS screen draws Compose, so the `Shared` framework stops carrying Compose
Multiplatform at all.
- Text. iOS resolves every `UiText` from the bundle's string tables, the generated `.xcstrings` (ADR 0028). Swift
  reads them with `UiText.resolved`; Kotlin code of the app process (notifications, widget files, shares, the widget
  gallery's samples) reads them through the graph's `UiTextResolver`, which `IosPlatform.resolveText` implements over
  the same Swift resolver. No Compose resolver and no CMP resource is read on iOS, and the CMP resources are not
  packed into the app.
- Screens. Compose UI leaves `commonMain` for `androidMain` of the same module, as `:shared:feature-map` already has
  it: screens, sheets, dialogs, routes, their previews and Compose-only helpers. `commonMain` keeps domain, data,
  presentation (view models, UI state, the samples Swift snapshot tests read) and `UiText`. The Compose compiler runs
  for the Android target only; Compose, Coil Compose, `lifecycle-runtime-compose` and `koin-compose` are `androidMain`
  dependencies. The convention `itmowidgets.cmp.android` does this per module (`:shared:feature-qr` first).
- Compose strings. Compose reads the catalog only through `:shared:compose-strings` (one `Res` over every catalog
  file), which becomes Android-only; feature modules generate no CMP `Res` of their own. The catalog files stay where
  ADR 0028 puts them (`src/commonMain/composeResources/values/strings_<owner>.xml`), as build-logic input.
- Design system. The Compose kit (theme, components, drawables, fonts) is Android-only. What iOS took from it, the
  widget palette of a `ThemeSpec`, is `:shared:core`'s, built without Compose (material-color-utilities, which
  material-kolor already brings), so no iOS code reads the design system.
- Rules. Konsist bans Compose, CMP text and the design system in every `iosMain` (phase A) and Compose in
  `commonMain` of `:shared:core` and the feature modules (phase B).

**Why.**
- The framework drags Compose, Skiko and the CMP resources into an app that never draws them: a larger binary and
  header, a longer link, and a second string path beside the tables Swift and the extensions read.
- Swift resolves, not a Kotlin `NSBundle` reader: Kotlin/Native turns the `NSString` a table returns into a Kotlin
  `String`, which drops the `.stringsdict` plural rules, so Kotlin could format Russian plurals only with a second
  implementation of them. The Swift resolver is the one `StringsTests` holds against the tables.
- `androidMain` of the same module, not Android-only UI modules: the screenshot tests (`androidHostTest` with the
  preview scanner) and the Koin modules stay where they are, `internal` keeps working, and the build gains no eleven
  modules and no second feature isolation scope. `:shared:feature-map` shows the layout works.

**Consequence.**
- Kotlin/Native unit tests have no string tables: they pass a fake `UiTextResolver` and check keys and arguments;
  the Russian text on iOS is checked by `StringsTests` in the hosted app tests.
- `AppLocale.install()` (now in `:shared:core`) keeps the app process Russian for whatever reads the preferred
  languages; it no longer serves Compose.
- About 104 Compose files of the feature modules moved to `androidMain`; the Compose-free samples, `LinkRows`,
  `LinkCaptions` and `QrCodeGenerator` stay in `commonMain` (Swift reads them), with `PreviewFixtures` and
  `GroupPosition` moved to `:shared:core` for them.
- `:shared:designsystem` and `:shared:compose-strings` have the Android target only (`itmowidgets.cmp.kit`); `:shared:testing`
  keeps its fakes for every target and its Compose helpers on Android.
- `AppRoute` extends no Nav3 type: `navigation3-runtime` brought the Compose runtime to iOS, and the Android shell
  takes the keys as `NavEntry<AppRoute>` (amends ADR 0020).
- Measurements are in the phase PRs.

**Amends.**
- ADR 0028: Compose `Res` exists only on Android; on iOS `UiText` resolves from the `.xcstrings` tables; the CMP root
  locale override is gone from iOS.
- ADR 0031, amendment of 2026-10-12: `:shared:compose-strings` and the feature modules' CMP leave iOS as described
  here.
- ADR 0017: screens live in `androidMain`, not `commonMain`.
- ADR 0020: route keys live in `commonMain` without `navigation3-runtime`.

**Revisit when.** A screen is shared with iOS again, or the Compose compiler can no longer be limited to one target.
