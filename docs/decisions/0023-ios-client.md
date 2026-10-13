# 0023 The iOS client: a SwiftUI app in this repository over shared Kotlin

**Partly superseded by [0031](0031-swiftui-ios-screens.md):** content screens are
SwiftUI, not CMP screens hosted through `ComposeUIViewController`; the minimum iOS
is 26.1 since 2026-10-13, not 18.

**Decision (2026-10-03).** The iOS client lives in `iosApp/` in this repository:
XcodeGen generates the project from `iosApp/project.yml` (no tracked
`.xcodeproj`), and only the app target links one Kotlin umbrella framework,
`Shared`.
- Shape: a SwiftUI shell (`TabView` with five roots, a `NavigationStack` router fed
  by the shared route queue); screens in SwiftUI over shared ViewModels (SKIE and
  the `@Observable` adapter `ObservableViewModel`). Routes not yet moved are
  hosted through `ComposeUIViewController` (ADR 0031).
- Widgets, the QR Control and App Shortcuts are WidgetKit and App Intents without
  Kotlin, reading versioned App Group JSON (ADR 0027). The notification service
  extension uses a Swift micro-client without Kotlin. Widgets read App Group files
  and never call the network; push text resolves from the bundle by `loc-key`.
- Minimum iOS 18 (iOS 26.1 since ADR 0031's amendment of 2026-10-13).
  Identifiers: bundle ID `dev.alllexey.itmowidgets` (`.widgets`,
  `.notification-service`), App Group
  `group.dev.alllexey.itmowidgets`, Keychain group
  `$(AppIdentifierPrefix)dev.alllexey.itmowidgets.shared`; they are stable
  (ADR 0016). App Group and Keychain IDs come from build settings into
  `Info.plist` and entitlements, never literals in Swift or Kotlin.
- Icons: SF Symbols, from the one icon registry (ADR 0028).
- Signing, push, Universal Links and the App Store need the Apple developer
  account; everything else runs unsigned on the simulator and CI.

**Why.** System surfaces are native and cheap in SwiftUI. Extensions cannot carry
Kotlin: the widget extension has about 30 MB (KT-66589), and a Kotlin/Native
notification service peaked above its limit where a Swift one did not. iOS 18
because Controls need it and it runs on the same iPhones as iOS 17. `iosApp/` here
lets one PR change shared code and its Swift consumer; IDs from build settings let
a free team or an unsigned sideload work without code edits.

**Consequence.**
- Apple targets build with Kotlin/Native on macOS; `ios-check` runs on a GitHub
  macOS runner on every PR. The Xcode and simulator pins are in
  [the iOS app](../ios.md).
- The app works fully without push (`alertsAllowed=false` stops Backend sport
  attempts); demo mode is the App Review path.

**Revisit when.** Kotlin or SKIE lag the Xcode the App Store requires; the
notification service's device footprint leaves room for Kotlin; the App Group or
Keychain setup fails on a device.
