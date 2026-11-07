# 0021 Material 3 Expressive through one token schema

**Amended by [0031](0031-swiftui-ios-screens.md):** the iOS token clause below.
**Amended 2026-10-26 (owner):** a widget may draw M3 Expressive's wavy progress indicator for the current lesson
(the cards widget): on Android two clipped vector drawables moved by their image level (RemoteViews, ADR 0027), on
iOS a SwiftUI `Shape`. Everything else in widgets keeps the res palette and shapes.

**Decision (2026-10-03).** The Android look is Material 3 Expressive (M3E), driven by
one token schema in `:shared:designsystem`.
- `ItmoTheme` in `commonMain` has M3E-shaped tokens: colour roles with one
  extended-colour mechanism, a shape scale with `largeIncreased` and the named
  `cardSummary` radius, emphasized type roles, `MotionScheme` and spacing. The brand
  static scheme applies below API 31 and on iOS, dynamic colour on API 31 and
  later. `itmo-tokens.json` exports the tokens.
- Expressive components sit behind `Itmo*` wrappers (`ItmoLoadingIndicator`,
  `ItmoButtonGroup`, `ItmoFabMenu`, `ItmoSplitButton`, `ItmoFloatingToolbar`) and
  the one `ItmoTheme(expressive = ...)` switch, on for every screen.

**Rules.**
- Pins: JetBrains material3 matching the CMP version, and Jetpack material3 pinned
  `strictly` to the version that JetBrains material3 maps to, in the design-system
  convention; one material3 version on the app classpath. A component that line
  lacks waits for the next CMP material3: no hand-rolled copies, no actuals on a
  newer Jetpack alpha.
- Experimental and expressive APIs only inside `shared/designsystem`, behind
  `Itmo*` wrappers, so a switch of the material3 line stays local; no raw colour,
  `.dp` spacing or font size outside it (Konsist enforces it).
- Widgets, the QR tile, shortcuts and notification icons keep their res palette;
  no M3E there (ADR 0027), except the wavy lesson progress (amendment above).
- iOS: `scripts/ios/gen-tokens.py` regenerates `Tokens.generated.swift` from
  `itmo-tokens.json` whenever the tokens change (`check-tokens.sh` fails while it
  is stale). SwiftUI screens use native iOS controls and take colour, spacing and
  type values from these tokens; the M3E component look is Android's only
  (ADR 0031).
- The web app mirrors the tokens in its own `tokens.json` or records a divergence
  in its design doc.

**Why.** An M3E-shaped schema means component APIs never change twice, and the
wrappers keep a material3 line switch inside one module. Unpinned mixing of
JetBrains and Jetpack material3 fails at runtime with `NoSuchMethodError`.

**Revisit when.** A material3 or CMP bump fails the expressive components
(linkage error, missing component), or sheet scenes fail on a device.
