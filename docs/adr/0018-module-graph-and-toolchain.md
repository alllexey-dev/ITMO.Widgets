# 0018 Module graph and toolchain

**Decision (2026-10-03).** The repository has this module tree:

```text
build-logic/            conventions: android-app, kmp-library, cmp-ui, testing, strings
app/                    Android application, depends on every shared module
shared/core/            core contracts, models, time, navigation args, common strings
shared/designsystem/    theme, tokens, component kit, icons; depends on :shared:core
shared/testing/         test kit, fake Clock, FakeFileSystem, preview harness (core-free)
shared/backend-client/  Backend client (ADR 0026)
shared/feature-{qr,home,schedule,sport,recordbook,social,settings,resources,reviews,account}/
shared/ios/             umbrella framework "Shared" for iosApp/
konsist/                architecture rules over app/ and shared/
```

- Shared modules target `android`, `iosArm64` and `iosSimulatorArm64`. Class
  names listed in `StableIdentifiersTest` keep their packages (ADR 0016).
- Rules: no `android.*`, `java.*` or `R` in `commonMain`; features never depend on
  each other; `ui -> presentation -> domain <- data` inside a feature; time only
  through `AcademicTimeProvider` or `Clock`.
- `app/` keeps what is Android-only: Activities and WebView screens, the
  RemoteViews widgets, workers, FCM, the QS tile and shortcuts, Keystore,
  calendar sync, the Play code scanner, update actions and debug tools.
- Toolchain: Kotlin 2.4.20, AGP 9.3.x with built-in Kotlin (AGP 9.4.x is outside
  Kotlin 2.4.20's tested KMP range), compileSdk 37, Gradle 9.7.0, JVM 17 bytecode
  in every module. Konsist runs with a test-runtime pin of
  `kotlin-compiler-embeddable`.
- Library versions live in the version catalog and follow their ADRs. Toolchain
  and catalog bumps are changes of their own.

**Why.** The iOS client is a second in-repo consumer of the shared code, which a
single `:app` module could not serve. Kept packages make every move a pure rename.

**Consequence.** Build files change only in toolchain changes; Konsist rules are
module- and Compose-aware. ADR 0031 moves Compose UI out of the shared modules
into Android-only code, which changes the `ui` layer of this tree.

**Revisit when.** Kotlin 2.5 ships, or Konsist runs on Kotlin 2.4.20 without the
test-runtime compiler pin.
