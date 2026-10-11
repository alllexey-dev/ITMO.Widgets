# 0021 Material 3 Expressive through a token schema, behaviour-preserving ports, one flip and a component pass

**Decision (2026-10-03).** The look moves to Material 3 Expressive (M3E) in four steps, in this order:
1. **Schema at today's values.** `ItmoTheme` in `commonMain` of `:shared:designsystem` has M3E-shaped tokens
   from the first commit: colour roles with one extended-colour mechanism, a shape scale with `largeIncreased`,
   emphasized type roles, `MotionScheme` and spacing. The values are v2.2's: the static M3 baseline scheme (`#6750A4`,
   what API 26–30 get from `Theme.Material3.DynamicColors.DayNight`) below API 31 and on iOS, dynamic colour on API
   31+, and the off-scale 24 dp summary radius as the named token `cardSummary`. `itmo-tokens.json` exports them.
2. **Ports at v2.2 look.** Each CMP port matches its XML screen against JVM reference captures (ADR 0022); a
   deliberate deviation is listed in the PR body.
3. **One flip** once every port is merged or cut and the M3E values are chosen. Only token values and re-recorded
   baselines change: one golden change, revertable alone, landing before the Navigation 3 shell swap (ADR 0020).
4. **Component pass.** `Itmo*` wrappers (`ItmoLoadingIndicator`, `ItmoButtonGroup`, `ItmoFabMenu`,
   `ItmoSplitButton`, `ItmoFloatingToolbar`) sit behind `ItmoTheme(expressive = false)`, which the flip turns on;
   then one pass per feature module beside the shell swap. Onboarding and account chrome are cut first.

**Rules.**
- Pins: CMP 1.12.1 with JetBrains material3 `1.12.0-alpha03`, and Jetpack material3
  `strictly("1.5.0-alpha22")` in the design-system convention; one material3 version on the app classpath. A
  component that line lacks waits for the next CMP material3: no hand-rolled copies, no actuals on a
  newer Jetpack alpha. Each material3 or CMP bump re-runs the expressive API checks below.
- Experimental and expressive APIs only inside `shared/designsystem`, behind `Itmo*` wrappers, so a switch of the
  material3 line stays local; no raw colour, `.dp` spacing or font size outside it (Konsist enforces it).
- No MDC 1.14 interim and no XML restyle: an XML screen keeps the v2.2 look until its port deletes it.
- Widgets, the QR tile, shortcuts and notification icons keep their res palette; no M3E there (ADR 0027).
- iOS regenerates its Swift tokens from `itmo-tokens.json` in the flip PR. The web app mirrors the tokens: its
  `tokens.json` has the same shape, and after the flip it copies Android's schemes, shapes, type and motion, or keeps
  its `#3a5488` palette and records that divergence in its design doc if Android keeps the M3 baseline palette.

**Why.** An M3E-shaped schema means component APIs never change twice. Today's values let every port prove it
matches v2.2 with a screenshot compare; a port that also restyles hides its regressions. One flip is reviewed once
instead of 40 screens each choosing values, and the values are picked looking at the ported app. An MDC 1.14 interim
would restyle XML that the ports delete.

**Flip before the shell swap.** The flip runs before the Navigation 3 shell swap rather than after it: the XML chrome
the swap replaces never ships in between, the new shell is built and recorded once on final tokens, and the component
pass runs beside the swap instead of after it. If the flip is not ready, it waits for the swap and nothing else
changes.

**Consequence.** Until the flip the app looks like v2.2. Baselines are re-recorded once, at the flip (baseline PRs
wait about a quarter day). Late values postpone the flip and the v2.2 look ships; component passes are cut to 2.3.x
first.

**Supersedes.** No ADR. `docs/design.md` § Colour and surfaces and § Geometry stay until they are rewritten for the
flip.

**Revisit when.** A material3 or CMP bump fails the expressive API checks (linkage error, missing component); sheet
scenes fail on a device; the M3E values are not ready at the flip.

**Settled.**
- CMP 1.12.1 + material3 `1.12.0-alpha03` with the `strictly` pin (2026-10-03). Reversible: the wrappers keep a
  switch to `1.13.0-alpha01` inside `shared/designsystem`.
- The M3E values are picked on the ported app before the flip (static colours below API 31 and on iOS,
  `cardSummary` radius, tab labels and intensity, FAB pairs). If they are late there is no flip and the v2.2 look
  ships.
- The flip runs before the shell swap.

**Evidence.**
- Expressive API spike: PARTIAL (every expressive component the kit names is in `commonMain` on `1.12.0-alpha03` and `1.13.0-alpha01`; unpinned mixing with Jetpack alpha29 fails with `NoSuchMethodError`, the `strictly("1.5.0-alpha22")` pin passes)
- Theme bridge spike: PASS (`ItmoTheme` bridge reads the 31 roles from the Activity theme, ARGB-equal with the XML theme in light, dark and seeded contexts)
- Seeded palette spike: PASS (`dynamic*ColorScheme` over an MDC context does not match; MaterialKolor 5.0.1 `PaletteStyle.Content`, SPEC_2021 matches MDC 31/31, so `ItmoTheme` takes an explicit seed)
