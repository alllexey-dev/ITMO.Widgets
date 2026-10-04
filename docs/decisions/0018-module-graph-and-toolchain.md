# 0018 Module graph and toolchain for v2.3

**Decision (2026-10-03).** The repository gets this module tree, declared at once with
every v2.3 library, so no later change edits `settings.gradle.kts`:

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

- Shared modules target `android`, `iosArm64` and `iosSimulatorArm64`.
  Packages do not change: a move is a `git mv`, so every class name in
  `StableIdentifiersTest` stays valid.
- Rules: no `android.*`, `java.*`, `R`, Hilt or `javax.inject` in `commonMain`;
  features never depend on each other; `ui -> presentation -> domain <- data`
  inside a feature; time only through `AcademicTimeProvider` or `Clock`.
- `app/` keeps what is Android-only: Activities and WebView screens, the
  RemoteViews widgets, workers, FCM, the QS tile and shortcuts, Keystore,
  calendar sync, the Play code scanner, update actions, debug tools, and Hilt
  until it leaves (ADR 0019).
- Toolchain: Kotlin 2.4.20, AGP 9.3.3 with built-in Kotlin (no 9.4.x, which is
  outside Kotlin 2.4.20's tested KMP range), compileSdk 37, Gradle 9.7.0, KSP
  instead of kapt, Hilt 2.60.1, JVM 17 bytecode in every module, Android
  Studio Quail 2 or later.
- Library pins live in the version catalog and follow their ADRs: CMP 1.12.1
  (0017, 0021), Koin 4.2.2 (0019), Navigation 3 1.2.x (0020), Ktor 3.6.0 and
  kotlinx.serialization 1.11.0 (0026), kotlinx-datetime 0.8.0, Roborazzi
  1.76.0 (0022), SKIE 0.10.15 (0023). One owner bumps the toolchain and the
  catalog; other changes add a library as a one-line catalog change.

**Why.** The iOS client is the second in-repo reuse site that 0001 named as
its trigger. Kept packages make every move a pure rename for parallel work.
AGP 9.3.x is the newest line inside Kotlin 2.4.20's tested KMP range.

**Consequence.** Only the toolchain owner edits build files; Konsist rules
become module- and Compose-aware.

**Supersedes.** 0001 Single Gradle module.

**Revisit when.** Kotlin 2.5 ships, or Konsist runs on Kotlin 2.4.20 without
the test-runtime compiler pin.

**Settled.** A3 settled 90 Q5 (AGP 9.3.x) and 12 Q6 (Kotlin 2.4.20 now,
2.5.x after v2.3). Konsist stays, with the SP-03 pin; no replacement.

**Evidence.**
- SP-01: PASS (AGP 9.3.3, Gradle 9.7.0, built-in Kotlin 2.4.20, KSP and Hilt 2.60.1 build both flavors; no `legacy-kapt`; AGP pick 9.3.3, also passed by SP-13a)
- SP-03: PASS (Konsist 0.17.3 runs on Kotlin 2.4.20 with a test-runtime pin `kotlin-compiler-embeddable:2.4.10`; no replacement)
- SP-09: PASS (a 2.4.20-built library with `languageVersion`/`apiVersion` 2.2 and `coreLibrariesVersion` 2.2.0 runs in a Kotlin 2.2.21 consumer)
- SP-13: PASS (SP-13a PASS, SP-13b PASS; CMP 1.12.1 resources with the Android-KMP library plugin on AGP 9.3.3; forces compileSdk 37; Konsist covers `commonMain` with the 2.4.10 pin)
