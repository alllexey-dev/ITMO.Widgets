# 0031 iOS screens are native SwiftUI over shared Kotlin

**Status.** Accepted (2026-10-12).

**Decision.** Every iOS screen is a native SwiftUI view over shared Kotlin. Shared
Compose Multiplatform screens hosted through `ComposeUIViewController` are replaced
screen by screen and are not added any more. Android keeps its Compose screens.
- Shared code is pure Kotlin: domain, data and view models. Compose UI leaves
  `commonMain` for Android-only code; the shared modules are restructured for it.
- One view model per screen serves both platforms. Android draws it in Compose,
  iOS in SwiftUI. Swift reads the view model through a typed adapter: today SKIE
  with `ObservableViewModel` (`iosApp/Sources/Bridge/ObservableViewModel.swift`).
- View models and UI text do not depend on Compose. `UiText` holds a string key
  and its arguments; each platform resolves the key from its own catalog
  (Android resources, the generated `.xcstrings` tables, ADR 0028).
- Each SwiftUI screen has swift-snapshot-testing references in the iOS
  appearance matrix (ADR 0022).
- The move is incremental: a SwiftUI screen lands with its tests, and the CMP host
  of that route (the factory in `shared/ios/.../screens/`, its Swift wrapper, the
  `.compose` route target) is deleted in the same change.

**Why.** The target design is native SwiftUI on iOS 26 and Material 3 Expressive
on Android, prototyped in the design lab (public gallery
https://design.widgets.alllexey.dev). CMP screens draw Material inside a Metal
layer: they cannot match iOS navigation, sheets, lists, text selection, Dynamic
Type behaviour or system materials, and every iOS idiom has to be imitated.
The system screens that are already SwiftUI (settings, sign-in, the first-run
flow, the web sign-in) show that a SwiftUI screen over a shared view model costs
little once the view model is platform-neutral.

**Consequence.**
- Each screen is written twice, once in Compose and once in SwiftUI, over one
  view model. Behaviour and copy live in the view model and the catalog, so the
  two views differ only in presentation.
- Shared modules lose the Compose and CMP resources dependencies; strings are
  generated per platform from the one catalog (ADR 0028).
- iOS screens are covered by swift-snapshot-testing references instead of
  XCUITest screenshots of CMP hosts.
- Until a route moves, its CMP host keeps working as described in
  [the iOS app](../ios.md).

**Supersedes.**
- ADR 0023, the clause that iOS content screens are CMP screens hosted through
  `ComposeUIViewController`.

**Amends.**
- ADR 0017: iOS no longer hosts the `commonMain` screens.
- ADR 0021: the iOS clause on regenerating Swift tokens; SwiftUI screens use native
  controls and take only the shared token values.
- ADR 0022: the iOS coverage clause; every SwiftUI screen has snapshot references.

**Open questions.** Not decided by this record:
- The minimum iOS version. ADR 0023 sets iOS 18; the prototypes use iOS 26 APIs
  such as Liquid Glass and `tabViewBottomAccessory`.
- How view models are exposed to Swift: the current SKIE and
  `ObservableViewModel` adapter or another one.
- The order in which screens move.

**Revisit when.** SwiftUI cannot express a screen's behaviour over the shared view
model, or keeping two views per screen costs more than a shared UI would save.
