# 0028 One Russian string catalog generated outward; Material Symbols Rounded with an SF Symbol registry

**Amended by [0035](0035-ios-without-compose.md):** CMP `Res` serves Compose on Android only; iOS resolves
`UiText` from the `.xcstrings` tables, Kotlin included.

**Decision (2026-10-03).** Strings have one source, Android-syntax `strings_<owner>.xml` files in Russian, generated outward:
- **Source.** `strings_<owner>.xml` in the owning module's `src/commonMain/composeResources/values/`;
  `strings_platform.xml` lives in `:shared:core`, Android-only files in `app/src/main/res/values/`.
- **Outputs.** The CMP plugin generates `Res.string`/`Res.plurals` for Compose and `UiText`. `exportAndroidStrings`
  writes generated copies into `:app` `res` for `R.string` and XML (`strings_platform.xml` and the files Android
  system surfaces need). `exportAppleStrings` writes committed
  `iosApp/Shared/Strings/*.xcstrings`: one table per catalog file; `strings_platform.xml` and the iOS-only
  `iosApp/Strings/strings_ios*.xml` go to `Localizable` (APNs resolves `loc-key` only there); `InfoPlist` and
  `AppShortcuts` rows come from `apple-tables.properties`. Generated files say so and are never edited by hand.
- **Key invariant.** `StringResource.key` == Android resource name == `.xcstrings` key == APNs `loc-key`. A key is
  unique across all catalog files, so Compose, Android and Swift resolve the same key (ADR 0031 makes `UiText`
  carry the key instead of a CMP `StringResource`).
- **Frozen platform keys.** `strings_platform.xml` holds the 81 ids that reach Android system surfaces (52 direct, 29
  through shared text builders); 20 are bound from XML (manifest 5, `res/xml` 7, widget layouts 10) and can only be
  `@string` resources. The list in `scripts/strings-frozen-keys.txt` is never renamed: installed iOS clients resolve
  push keys from it.
- **Russian everywhere.** The app runs in Russian on a non-Russian phone, so plurals and library strings are Russian:
  Android per-app locale `ru` (`locales_config.xml`, `setApplicationLocales`, `localeFilters`, `withAppLocale()` for
  non-Activity contexts on API 26–32), the CMP root locale override, a ru-only iOS bundle.
- **Icons.** Material Symbols Rounded, wght 400, opsz 24, FILL 0; FILL 1 only on the selected tab; 960 viewport
  drawn at 24 dp, fetched by `scripts/icons-fetch.py`, never hand-drawn. One registry `docs/design/icons.tsv` (`id`,
  `symbol`, `fill`, `kind`, `sf_symbol`, `note`; kinds `shared`, `custom`, `android`) generates the Kotlin `AppIcon`
  lookup, CMP drawables and Swift `AppSymbol`. SwiftUI and system surfaces (Controls, App Shortcuts, which take only
  symbol images) use the SF Symbol; CMP screens use the Material one; brand marks are custom symbols on both.

**Rules.** `checkStringCatalog` runs in `:app:preBuild`: positional placeholders only, complete Russian plurals, one
key across files, no `translatable`, every frozen key present, no URL values, generated outputs fresh. Konsist bans
`R` in `commonMain`. Russian literals in Kotlin only in previews.

**Why.** Under the iOS hybrid (ADR 0023) Swift with no Kotlin in process (widgets, NSE, Controls, push fallback)
reads about 35–45 % of the catalog (estimate), and CMP resources live in APK assets, not in `R`, so CMP alone
cannot serve system surfaces. Two hand-kept catalogs would drift; moko-resources adds a second resource system
next to CMP. Android picks plural rules from the device language, so an English phone shows «5 пары» today.
Rounded is what every tracked rule and Web already use and the Material style closest to SF Symbols.

**Consequence.** About 300 lines of build logic with golden tests that the project maintains. Every string PR
regenerates its own `.xcstrings` table. A new language later is a `values-<lang>/` folder in the same pipeline.

**Supersedes.** The earlier Outlined icon family on a 24 dp viewport.

**Revisit when.** CMP resources stop working in the KMP library plugin (shared presentation then emits typed
keys); the export grows costly (fallback moko-resources); a second app language is planned.

**Also.**
- `.xcstrings` committed, with `exportAppleStrings --check` in CI. Web keeps its own labels plus a drift test.
