# 0022 JVM screenshot tests replace emulator matrices; the full matrix runs before a release

**Amended by [0031](0031-swiftui-ios-screens.md):** the iOS coverage clause below.

**Decision (2026-10-03).** Visual verification of Compose screens and kit components
runs on the JVM: Roborazzi with Robolectric and ComposablePreviewScanner in each
module's `androidHostTest`, baselines committed in `<module>/screenshots/`. Every
public screen composable has `@Preview`s; four appearances render from them (light;
dark; seeded palette + font 1.3 + 320 dp; dark seeded + font 1.3 + 320 dp).

**Tiers.**
- Every PR: `./gradlew verifyQuick`, plus `scripts/verify.sh shots <module>` for UI
  changes; CI verifies every committed baseline.
- Before a release candidate: the full appearance matrix on an emulator (light,
  dark, one dynamic palette, font 1.0 and 1.3, long names, every state); the iOS
  snapshot matrix (light, dark, Dynamic Type L and AX1, 320 pt width); the upgrade
  from the previous release on a device; `scripts/ship-check.sh`. See
  [release](../release.md).

**Rules.**
- Byte-stable baselines: fixed Robolectric SDK, native graphics, paused animations,
  fixed seeds. A baseline changes only with the code behind it; names start with
  the preview name. CI never records.
- Baselines recorded on macOS verify on the Ubuntu runner.
- Screenshot content is synthetic: long Russian names live only in `@Preview`
  functions and `preview/` files.
- Semantics checks (`assertTouchTargets`, `assertNoTextOverflow`) and one
  accessibility (ATF) check run on the JVM.
- An emulator is used only where the JVM cannot render the surface: widgets
  (RemoteViews), the QR tile, shortcuts, notifications, WebView screens, Play
  in-app updates, the release tier and the instrumented subset. Instrumented tests
  run through `scripts/verify.sh ui` on an emulator, never on a personal phone.
- iOS: every SwiftUI screen and widget entry view has swift-snapshot-testing
  references in the four iOS appearances. A route still hosted through
  `ComposeUIViewController` draws into a Metal layer those snapshots miss, so it is
  checked by XCUITest screenshots and its goldens stay Roborazzi's until its
  SwiftUI screen replaces it (ADR 0031).

**Why.** An emulator matrix is slow, needs an emulator per parallel build and leaves
no committed baseline to review. JVM baselines run in every PR and show a
reviewable diff. Seed and font scale are preview inputs, so the matrix needs no
device; the release tier keeps one full emulator pass for what Robolectric cannot
reproduce.

**Consequence.** CI wall time grows with the baseline count and is watched against
the CI timeout. A screenshot over 2 s or an unstable record is a defect of the
harness, not of the change under test.

**Supersedes.** The rule that every meaningful UI change is verified on an emulator
in every appearance before it is called done: screens are checked by baselines,
and the emulator checklist runs in the release tier.

**Revisit when.** Robolectric or Roborazzi stops supporting the CMP or Android
versions in use; CI time for screenshots passes the CI job timeout.
