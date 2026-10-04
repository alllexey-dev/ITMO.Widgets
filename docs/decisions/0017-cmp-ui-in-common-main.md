# 0017 Compose Multiplatform screens in `commonMain` of shared modules

**Decision (2026-10-03).** Every content screen, sheet body and dialog is a stateless
Compose Multiplatform composable in `commonMain` of its
`:shared:feature-<x>` module: `<Name>Screen`, `<Name>SheetContent` or
`<Name>Dialog`, taking state and callbacks, with previews beside it. Only
`<Name>Route` or the host obtains the ViewModel. On Android a `ComposeView`
inside today's Fragment, sheet or dialog class hosts it until the single
Navigation 3 shell swap (ADR 0020) deletes the hosts; on iOS the SwiftUI shell
hosts the same composable through `ComposeUIViewController` (ADR 0023).

**Rules.**
- A port is one change: the composable, its screenshot tests, the host switched
  to `ComposeView`, and the View class, layout, View tests and debug host line
  deleted. No screen is written twice.
- Screens read strings from `composeResources` (ADR 0028); XML layouts keep
  `R` until the port deletes them.
- WebView and other platform-view screens (sign-in, My ITMO web, BARS login,
  the cropper) stay in `app/` and enter common screens only through an
  `AndroidView` slot.
- Material components come from JetBrains material3 matching CMP 1.12.1 (ADR
  0021); the look stays at v2.2 values until the token flip.

**Why.** The iOS client shares content screens (ADR 0023). Jetpack Compose in
`:app` would move all 40 screen units, the component kit and the strings again
for iOS; screens in `commonMain` move once. Compose in `commonMain` also forces
multiplatform strings and shared ViewModels, which ADRs 0019 and 0028 settle.
Fragment hosts let each screen ship alone without first replacing navigation.

**Consequence.** A screen module cannot import `android.*`, `java.*`, `R`,
Hilt or `javax.inject`; Konsist enforces it. Hybrid XML and Compose screens
live side by side until the shell swap; nothing ships in between.

**Supersedes.**
- `AGENTS.md` § Hard rules, "Android stays on XML, Fragments, ViewBinding,
  Navigation, Hilt and WorkManager. No Compose without an explicit migration
  decision." (Hilt and Navigation also by ADRs 0019 and 0020).
- `docs/design.md` § Principle, the words "a new UI framework" in "Unification does
  not mean a redesign, a new UI framework or features outside the roadmap."

**Revisit when.** SP-05b fails (sheet ports on a device); the pilot overruns
its time box; a material3 bump breaks the `strictly` pin (ADR 0021).

**Settled.** D1 and A2 settled 90 Q1 and 12 Q3-Q4. Fallback C-b (screen
composables in `app/feature/<x>/ui` on Jetpack, shared presentation, SwiftUI
screens on iOS) is not taken: SP-04, SP-05a and SP-13 found no blocker. A later
failure that needs it is the owner's decision and a superseding ADR.

**Evidence.**
- SP-04: PARTIAL (the expressive API compiles in `commonMain` on both material3 lines; mixing with a newer Jetpack material3 breaks at runtime unless it is pinned `strictly` to the JetBrains mapping; 94 Q6 goes to the owner)
- SP-05: pending (SP-05a PASS: `ComposeView` bodies in the real MDC sheets, 31/31 roles through the bridge, drag, IME and recreation pass on the host; SP-05b device checks pending)
- SP-13: PASS (SP-13a PASS, SP-13b PASS; CMP resources resolve in the APK on AGP 9.3.3; a Hilt Fragment hosts a `commonMain` screen through `ComposeView`; fallback C-b not needed)
