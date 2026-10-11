# 0023 The iOS client is a SwiftUI shell around the shared CMP screens

**Decision (2026-10-03).** iOS ships in v2.3 in some form, as a hybrid, from `iosApp/` in this repository: XcodeGen
generates the project from `iosApp/project.yml` (no tracked `.xcodeproj`), linking one Kotlin umbrella framework.
- Shape: a SwiftUI shell (`TabView` with five roots, a `NavigationStack` router fed by the shared route queue);
  system screens in SwiftUI over shared ViewModels (SKIE, an `@Observable` adapter): onboarding, sign-in
  (`WKWebView`), settings, web sign-in scanner, My ITMO web, update offer, diagnostics, account deletion (stretch);
  content screens in CMP through `ComposeUIViewController`: home, QR pass, schedule, sport, social and me, recordbook
  and subject page, reviews and links. Widgets, the QR Control and App Shortcuts are WidgetKit and App Intents without
  Kotlin, reading versioned App Group JSON (ADR 0027). The NSE (sport auto-sign, friendship texts) uses a Swift
  micro-client without Kotlin; widgets read App Group caches and never call the network; push text resolves from
  the bundle by `loc-key`.
- Minimum iOS 18; marketing version follows the app (2.3.0), build numbers come from CI. Identifiers (confirmed
  together with the Apple developer account; irreversible after the first TestFlight build): bundle ID
  `dev.alllexey.itmowidgets` (`.widgets`, `.notification-service`), App Group `group.dev.alllexey.itmowidgets`,
  Keychain group `$(AppIdentifierPrefix)dev.alllexey.itmowidgets.shared`; App Group and Keychain IDs come from build
  settings into `Info.plist` and entitlements, never literals in Swift or Kotlin.
- Icons: SF Symbols on SwiftUI and system surfaces, Material Symbols Rounded in CMP, one registry (ADR 0028).
- Scope (the full Android feature set): MVP = the screens above but deletion, BARS and marks, EventKit calendar and
  `.ics`, widgets (QR, single lesson, day, Lock Screen), QR Control, App Shortcuts, background refresh, push,
  Universal Links. Stretch = NSE auto-sign on a device, App Store, in-app deletion, `usernotifications.filtering`,
  Smart App Banner.
- Signing, push and the App Store need the Apple developer account (Team ID, IDs, APNs key, App Store Connect
  record, an iPhone); all else runs on the simulator and CI. What ships depends on when the account is available,
  counted in days from the start of v2.3: by day 30, MVP + stretch on external TestFlight at the release candidate;
  between days 30 and 48, MVP on internal TestFlight at the release candidate; later, simulator-verified and
  CI-built, TestFlight 1-2 weeks after the account; no account at the release candidate, an unsigned `.ipa`
  developer preview on a GitHub prerelease, if explicitly chosen.

**Why.** CMP content screens serve both platforms (ADR 0017); system surfaces are cheap and native in SwiftUI;
extensions cannot carry Kotlin (widget about 30 MB, KT-66589; NSE about 24 MB). iOS 18 because Controls need it (no
availability branch for the QR Control) and it runs on the same iPhones as iOS 17 (XS/XR and later). Apple's App
Store figures for 2026-06-07 (developer.apple.com/support/app-store, read 2026-10-03; split per 9to5Mac, 2026-06-10):
iOS 26 on 79 % of all iPhones, iOS 18 on 14 %, earlier 7 % (last four years: 86/11/3), so iOS 18+ reaches 93 % of
all and 97 % of recent iPhones; iOS 17 could win only part of the 7 %. `iosApp/` here lets one PR change shared code
and its Swift consumer; IDs from build settings let a free team or an unsigned sideload work without code edits.

**Consequence.**
- Apple targets build with Kotlin/Native on macOS; `ios-check` runs on a GitHub macOS runner on every PR. `iosApp/`
  needs Xcode 26.4 or later (the pin is in [iOS app](../ios.md)), an iOS 18+ runtime and XcodeGen; device checks wait for the Apple
  developer account.
- The app works fully without push (`alertsAllowed=false` stops Backend sport attempts); demo mode is the App Review
  path; review notes cite guideline 4.8 for the ITMO.ID sign-in. Android never waits for the account.
- iOS needs Backend 1.8.0 in production (one release before the release candidate, ADR 0030), dev until then.

**Supersedes.** Nothing (ADR 0028 narrows the Material-only icon rule).

**Revisit when.** The date of the Apple developer account changes what ships; the simulator checks of the Darwin
HTTP client, the `WKWebView` sign-in or the App Group and Keychain setup fail; the NSE's device footprint makes a
Kotlin NSE fit (a superseding ADR); Kotlin or SKIE lag the Xcode the App Store requires (the iOS 27 SDK from April
2027); a CMP screen fails App Review or accessibility on iOS.

**Settled.** 2026-10-03.
- A Swift micro-client in the NSE for token refresh, QR and sport sign-in; widgets read caches only.
- iOS 18; SF Symbols in SwiftUI, Material Symbols in CMP.
- Collapse id + passive notifications; `usernotifications.filtering` is requested once the Apple developer account
  exists.
- The full Android feature set in v2.3; marks, links and reviews are in the MVP.

**Evidence** (state at the time of the decision).
- Apple klibs spike: PASS (Apple klibs compile on the CLT-only Mac; publish host Linux x86_64 with `-Xklib-relative-path-base`; linking needed Xcode)
- Darwin HTTP spike: PASS (Darwin sends DELETE bodies; cookie replay works with `HTTPShouldSetCookies = false`, no cookie storage, `URLCache = null`); the same on the simulator was pending
- NSE memory spike: FAIL (estimate; K/N NSE proxy peak RSS 21.0 MB > 18 MB, so the NSE uses the Swift micro-client; phys footprint 7.15 MB vs Swift 4.75 MB would be a go if the limit applied to footprint); NSE and widget memory on a device was pending
- `WKWebView` sign-in, hidden BARS login and cookie export: pending
- App Group, Keychain group and NSE on the simulator, free-team limits, the unsigned package: pending
