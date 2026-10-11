# 0022 JVM screenshot tests replace emulator matrices; the full matrix runs before a release

**Decision (2026-10-03).** Visual verification of CMP screens and kit components runs on the JVM: Roborazzi 1.76.0 with
Robolectric 4.17 and ComposablePreviewScanner 0.9.3 in each module's `androidHostTest`, baselines committed in
`<module>/screenshots/`. Every public screen composable has `@Preview`s; the 4 appearances of today's matrix (light;
dark; seeded palette + font 1.3 + 320 dp; dark seeded + font 1.3 + 320 dp) render from them. Before a port, JVM
reference captures of the XML screen (`app/screenshots/`) give the v2.2 baseline (ADR 0021).

**Tiers.**
- Every PR: `./gradlew verifyQuick`, plus `scripts/verify.sh shots <module>` for UI changes; CI `verify-quick`
  verifies every committed baseline.
- Feature, when a feature is finished: its feature doc and a `CHANGELOG.md` entry, every screen state covered by
  baselines.
- Release, before the release candidate: the full appearance matrix on an emulator (light, dark, one dynamic palette,
  font 1.0 and 1.3, long names, every state); the iOS snapshot matrix (light, dark, Dynamic Type L and AX1, iPhone SE
  width); the 2.2 → 2.3 upgrade on a device; `scripts/ship-check.sh`. See [release](../release.md).

**Rules.**
- Byte-stable baselines: fixed Robolectric SDK, native graphics, paused animations, fixed seeds. A baseline changes
  only with the code behind it; names start with the preview name, so changes touch disjoint files. CI never records.
- Baselines recorded on macOS must verify on the Ubuntu runner; the cross-OS choice (tolerance or CI recording) is
  made once and holds for every module.
- Screenshot content is synthetic: long Russian names live only in `@Preview` functions and `preview/` files.
- Semantics checks (`assertTouchTargets`, `assertNoTextOverflow`) and one accessibility (ATF) check run on the JVM.
- An emulator is used only where the JVM cannot render the surface: widgets (RemoteViews), the QR tile, shortcuts,
  notifications, WebView screens, Play in-app updates, the release tier and the instrumented subset. Instrumented
  tests run through `scripts/verify.sh ui` on an emulator, never on a personal phone.
- iOS: swift-snapshot-testing for SwiftUI and widget entry views; CMP hosts draw into a Metal layer those snapshots
  miss, so they get XCUITest screenshots and their goldens stay Roborazzi's.

**Why.** An emulator matrix is slow, needs an emulator per parallel build and leaves no committed baseline to
review; it would be the bottleneck and still miss regressions. JVM baselines run in every PR, show a reviewable diff
and let ports prove they match v2.2 pixel by pixel. Seed and font scale are preview inputs, so the matrix needs no
device; the release tier keeps one full emulator pass for what Robolectric cannot reproduce.

**Consequence.** Each View port deletes its instrumented visual test in the same PR. CI wall time grows with the
baseline count and is watched against the CI timeout. A screenshot over the 2 s threshold or an unstable record is
a defect of the harness, not of the change under test.

**Supersedes.**
- `AGENTS.md` § Hard rules: "Every meaningful UI change is verified on an emulator in light and dark theme, one dynamic
  palette, font scale 1.0 and 1.3, with long names and every state. Compilation is not visual verification."
- `AGENTS.md` § Definition of done, item 5: "Light, dark and dynamic themes and accessibility were checked visually."
- `docs/design.md` § Verification matrix ("Every meaningful UI change is checked on an emulator before it
  is called done"): its checklist moves to the release tier; screens are checked by baselines.

**Revisit when.** Robolectric or Roborazzi stops supporting the CMP or Android versions in use; CI time
for screenshots passes the CI job timeout.

**Settled.** The seeded palette took its first fallback: MDC's seeded source does not match, so `ItmoTheme` takes
an explicit seed through MaterialKolor (31/31 roles match MDC) and dynamic palettes stay in the JVM matrix. The
threshold is 2 s per screenshot (2026-10-03).

**Evidence.**
- Screenshot spike: PASS (Roborazzi 1.76.0 + ComposablePreviewScanner 0.9.3 render the 4-appearance matrix with seeds and font 1.3 on the JVM, byte-stable, ≤ 1.44 s per capture; ATF checks run; only unseeded wallpaper colours stay for the emulator pass)
- Resources spike: PASS (the scanner finds a `commonMain` `@Preview`; Roborazzi renders it and a Fragment host byte-stably in `androidHostTest`; verify lines need `--rerun`)
