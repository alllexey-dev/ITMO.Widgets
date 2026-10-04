# 0021 Material 3 Expressive through a token schema, parity ports, one flip and a component pass

**Decision (2026-10-03).** The look moves to Material 3 Expressive (M3E) in four steps, in this order:
1. **Schema at today's values (DS-01).** `ItmoTheme` in `commonMain` of `:shared:designsystem` has M3E-shaped tokens
   from the first commit: colour roles with one extended-colour mechanism, a shape scale with `largeIncreased`,
   emphasized type roles, `MotionScheme` and spacing. The values are v2.2's: the static M3 baseline scheme (`#6750A4`,
   what API 26–30 get from `Theme.Material3.DynamicColors.DayNight`) below API 31 and on iOS, dynamic colour on API
   31+, and the off-scale 24 dp summary radius as the named token `cardSummary`. `itmo-tokens.json` exports them.
2. **Parity ports.** Each CMP port matches its XML screen against JVM reference captures (ADR 0022); a deliberate
   deviation is listed in the PR body.
3. **One flip (M3-02) at gate T15** = T14 (every port merged or cut) plus the owner's values. Only token values and
   re-recorded baselines change: one golden change, revertable alone, landing before the shell swap (SH-1).
4. **Component pass (M3-04).** `Itmo*` wrappers (`ItmoLoadingIndicator`, `ItmoButtonGroup`, `ItmoFabMenu`,
   `ItmoSplitButton`, `ItmoFloatingToolbar`) sit behind `ItmoTheme(expressive = false)`, which the flip turns on;
   then one pass per feature module beside SH-1. Onboarding and account chrome are cut first.

**Rules.**
- Pins (94 Q6 (a)): CMP 1.12.1 with JetBrains material3 `1.12.0-alpha03`, and Jetpack material3
  `strictly("1.5.0-alpha22")` in the design-system convention; one material3 version on the app classpath. A
  component that line lacks waits for the next CMP material3 (90 Q4 (a)): no hand-rolled copies, no actuals on a
  newer Jetpack alpha. Each material3 or CMP bump re-runs the SP-04 checks.
- Experimental and expressive APIs only inside `shared/designsystem`, behind `Itmo*` wrappers, so a switch of the
  material3 line stays local; no raw colour, `.dp` spacing or font size outside it (Konsist, KN-02b).
- No MDC 1.14 interim and no XML restyle: an XML screen keeps the v2.2 look until its port deletes it.
- Widgets, the QR tile, shortcuts and notification icons keep their res palette; no M3E there (ADR 0027).
- iOS regenerates its Swift tokens from `itmo-tokens.json` in the flip PR. Web mirrors the tokens through L22: W's
  `tokens.json` has the same shape, and after the flip W copies A's schemes, shapes, type and motion, or keeps its
  `#3a5488` palette and records that divergence in its design doc if Android keeps the M3 baseline palette.

**Why.** An M3E-shaped schema means component APIs never change twice. Today's values let every port prove parity
with a screenshot compare; a port that also restyles hides its regressions. One flip is reviewed once instead of 40
screens each choosing values, and the owner picks them looking at the ported app. An MDC 1.14 interim would restyle
XML that the ports delete (90 K10).

**Deviation from 91.** 91 puts the flip after SH-1. Here it runs at T15, before SH-1 (master §2.1): the XML chrome
SH-1 replaces never ships in between, the new shell is built and recorded once on final tokens, and M3-04 runs
beside SH-1 instead of after it. On the owner's veto M3-02 waits for SH-1 and nothing else changes.

**Consequence.** Until T15 the app looks like v2.2. Baselines are re-recorded once, at the flip (baseline PRs wait
about a quarter day). Late values postpone the flip and the parity look ships; passes are cut to 2.3.x first.

**Supersedes.** No ADR. `docs/design.md` § Colour and surfaces and § Geometry stay until DC-07a–c rewrite them.

**Revisit when.** A material3 or CMP bump fails the SP-04 checks (linkage error, missing component); SP-05b fails;
the owner's values are not ready by T15.

**Settled.**
- 94 Q6: (a) CMP 1.12.1 + material3 `1.12.0-alpha03` with the `strictly` pin (owner, 2026-10-03). Reversible: the
  wrappers keep a switch to (b) `1.13.0-alpha01` inside `shared/designsystem`.
- The M3E values are the owner's, picked on the ported app before T15 (static colours below API 31 and on iOS,
  `cardSummary` radius, tab labels and intensity, FAB pairs). If they are late there is no flip and the parity look
  ships.
- The flip runs before SH-1 unless the owner vetoes it before M3-02 merges.

**Evidence.**
- SP-04: PARTIAL (every expressive component the kit names is in `commonMain` on `1.12.0-alpha03` and `1.13.0-alpha01`; unpinned mixing with Jetpack alpha29 fails with `NoSuchMethodError`, the `strictly("1.5.0-alpha22")` pin passes; 94 Q6 input: (a) + `strictly`)
- SP-05: PASS (SP-05a PASS; `ItmoTheme` bridge reads the 31 roles from the Activity theme, ARGB-equal with the XML theme in light, dark and seeded contexts)
- SP-06: PASS (`dynamic*ColorScheme` over an MDC context does not match; MaterialKolor 5.0.1 `PaletteStyle.Content`, SPEC_2021 matches MDC 31/31, so `ItmoTheme` takes an explicit seed)
