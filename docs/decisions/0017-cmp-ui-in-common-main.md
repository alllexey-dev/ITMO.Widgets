# 0017 Compose Multiplatform screens in `commonMain` of shared modules

**Amended by [0035](0035-ios-without-compose.md) (proposed):** screens move to `androidMain` of their module.

**Amended by [0031](0031-swiftui-ios-screens.md):** iOS screens are SwiftUI; Compose
screens move out of `commonMain` into Android-only code screen by screen.

**Decision (2026-10-03).** Every content screen, sheet body and dialog is a stateless
Compose composable in `commonMain` of its `:shared:feature-<x>` module:
`<Name>Screen`, `<Name>SheetContent` or `<Name>Dialog`, taking state and callbacks,
with previews beside it. Only `<Name>Route` or the host obtains the ViewModel. On
Android the Navigation 3 shell (ADR 0020) shows it. On iOS a route that has not
moved to SwiftUI yet is hosted through `ComposeUIViewController` (ADR 0031).

**Rules.**
- Screens read strings from `composeResources` (ADR 0028).
- WebView and other platform-view screens (sign-in, My ITMO web, BARS login,
  the cropper) stay in `app/` and enter common screens only through an
  `AndroidView` slot.
- Material components come from JetBrains material3 matching the CMP version
  (ADR 0021).
- A screen module cannot import `android.*`, `java.*` or `R`; Konsist enforces it.

**Why.** Screens in `commonMain` were meant to serve Android and iOS from one
source, and Compose in `commonMain` forces multiplatform strings and shared
ViewModels (ADRs 0019 and 0028). ADR 0031 keeps the shared ViewModels and drops the
shared UI on iOS.

**Supersedes.** The rule that Android stays on XML, Fragments, ViewBinding and
Navigation (Hilt and Navigation also by ADRs 0019 and 0020).

**Revisit when.** Settled by ADR 0031.
