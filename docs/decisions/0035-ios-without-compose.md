# 0035 The iOS framework links no Compose

**Status.** Proposed (2026-10-19). Phase A (strings) is in; phase B (screens out of `commonMain`) waits for the open
feature PRs.

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
  dependencies.
- Compose strings. Compose reads the catalog only through `:shared:compose-strings` (one `Res` over every catalog
  file), which becomes Android-only; feature modules generate no CMP `Res` of their own. The catalog files stay where
  ADR 0028 puts them (`src/commonMain/composeResources/values/strings_<owner>.xml`), as build-logic input.
- Design system. The Compose kit (theme, components, drawables, fonts) is Android-only. What iOS takes from it, the
  widget palette of a `ThemeSpec`, is computed without Compose (material-color-utilities, which material-kolor
  already brings).
- Rules. Konsist bans Compose and CMP text in every `iosMain` (phase A) and Compose in `commonMain` of `:shared:core`
  and the feature modules (phase B).

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
- Phase B moves about 104 Compose files of the feature modules and 120 of the design system, and the Compose-free
  samples and `QrCodeGenerator` out of their `ui` packages (Swift reads them).
- Measurements are in the phase PRs.

**Amends.**
- ADR 0028: Compose `Res` exists only on Android; on iOS `UiText` resolves from the `.xcstrings` tables; the CMP root
  locale override is gone from iOS.
- ADR 0031, amendment of 2026-10-12: `:shared:compose-strings` and the feature modules' CMP leave iOS as described
  here.
- ADR 0017: screens live in `androidMain`, not `commonMain`.

**Revisit when.** A screen is shared with iOS again, or the Compose compiler can no longer be limited to one target.
