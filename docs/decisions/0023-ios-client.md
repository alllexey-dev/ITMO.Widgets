# 0023 The iOS client is a SwiftUI shell around the shared CMP screens

**Decision (2026-10-03).** iOS ships in v2.3 in some form (D2) as a hybrid (D1), from `iosApp/` in this repository: XcodeGen
generates the project from `iosApp/project.yml` (no tracked `.xcodeproj`), linking one Kotlin umbrella framework.
- Shape: a SwiftUI shell (`TabView` with five roots, a `NavigationStack` router fed by the shared route queue);
  system screens in SwiftUI over shared ViewModels (SKIE, an `@Observable` adapter): onboarding, sign-in
  (`WKWebView`), settings, web sign-in scanner, My ITMO web, update offer, diagnostics, account deletion (stretch);
  content screens in CMP through `ComposeUIViewController`: home, QR pass, schedule, sport, social and me, recordbook
  and subject page, reviews and links. Widgets, the QR Control and App Shortcuts are WidgetKit and App Intents without
  Kotlin, reading versioned App Group JSON (ADR 0027). The NSE (sport auto-sign, friendship texts) uses a Swift
  micro-client without Kotlin; widgets read App Group caches and never call the network; push text resolves from
  the bundle by `loc-key`.
- Minimum iOS 18; marketing version follows the app (2.3.0), build numbers come from CI. Identifiers (the owner
  confirms them at T13; irreversible after the first TestFlight build): bundle ID `dev.alllexey.itmowidgets`
  (`.widgets`, `.notification-service`), App Group `group.dev.alllexey.itmowidgets`, Keychain group
  `$(AppIdentifierPrefix)dev.alllexey.itmowidgets.shared`; App Group and Keychain IDs come from build settings into
  `Info.plist` and entitlements, never literals in Swift or Kotlin.
- Icons: SF Symbols on SwiftUI and system surfaces, Material Symbols Rounded in CMP, one registry (DS-06).
- Scope (full parity, 11 Q8 (b)): MVP = the screens above but deletion, BARS and marks, EventKit calendar and `.ics`,
  widgets (QR, single lesson, day, Lock Screen), QR Control, App Shortcuts, background refresh, push, Universal Links.
  Stretch = NSE auto-sign on a device, App Store, in-app deletion, `usernotifications.filtering`, Smart App Banner.
- Gate T13 (developer account, Team ID, IDs, APNs key, App Store Connect record, an iPhone); all else runs on the
  simulator and CI. By the date T13 opens: F0 ≤ D30 MVP + stretch on TestFlight external at T16; F1 D30–D48 MVP on
  TestFlight internal at T16; F2 later: simulator-verified, CI-built, TestFlight 1–2 weeks after T13; F3 no account
  at T16: an unsigned `.ipa` developer preview on a GitHub prerelease, on the owner's word.

**Why.** CMP content screens serve both platforms (ADR 0017); system surfaces are cheap and native in SwiftUI;
extensions cannot carry Kotlin (widget about 30 MB, KT-66589; NSE about 24 MB). iOS 18 because Controls need it (no
availability branch for the QR Control) and it runs on the same iPhones as iOS 17 (XS/XR and later). Apple's App
Store figures for 2026-06-07 (developer.apple.com/support/app-store, read 2026-10-03; split per 9to5Mac, 2026-06-10):
iOS 26 on 79 % of all iPhones, iOS 18 on 14 %, earlier 7 % (last four years: 86/11/3), so iOS 18+ reaches 93 % of
all and 97 % of recent iPhones; iOS 17 could win only part of the 7 %. `iosApp/` here lets one PR change shared code
and its Swift consumer; IDs from build settings let a free team or an F3 sideload work without code edits.

**Consequence.**
- Apple targets build in the `kn` slot; `ios-check` runs on a GitHub macOS runner on every PR. T10 (Xcode 26.4, an
  iOS 18+ runtime, XcodeGen) gates `iosApp/`; T12 is the simulator pilot; device checks wait for T13.
- The app works fully without push (`alertsAllowed=false` stops Backend sport attempts); demo mode is the App Review
  path; review notes cite guideline 4.8 for the ITMO.ID sign-in. Android never waits for the account.
- iOS needs Backend 1.8.0 in production (BK-16, BK-17, BK-18; one release before the RC, ADR 0030), dev until then.

**Supersedes.** Nothing (ADR 0028 narrows the Material-only icon rule).

**Revisit when.** T13's date changes the ladder case; SP-15b, SP-21 or SP-23 fails; SP-16b's device footprint
makes a Kotlin NSE fit (a superseding ADR); Kotlin or SKIE lag the Xcode the App Store requires (the iOS 27 SDK
from April 2027); a CMP screen fails App Review or accessibility on iOS.

**Settled.** Owner, 2026-10-03, unless named otherwise.
- 11 Q3: (b) a Swift micro-client in the NSE for token refresh, QR and sport sign-in; (c) widgets read caches only.
- 11 Q4 iOS 18 (A11); 11 Q6 SF Symbols in SwiftUI, Material Symbols in CMP (A8).
- 11 Q5: (a) collapse id + passive notifications; `usernotifications.filtering` is requested after T13.
- 11 Q8: (b) full parity in v2.3; IO-09d, IO-09f and IO-15 are in the MVP.

**Evidence.**
- SP-10: PASS (Apple klibs compile on the CLT-only Mac; publish host Linux x86_64 with `-Xklib-relative-path-base`; linking waits for T10)
- SP-15a: PASS (Darwin sends DELETE bodies; cookie replay works with `HTTPShouldSetCookies = false`, no cookie storage, `URLCache = null`); SP-15b: pending (the same on the simulator)
- SP-16a: FAIL (estimate; K/N NSE proxy peak RSS 21.0 MB > 18 MB, so 11 Q3 falls to (b) the Swift micro-client; phys footprint 7.15 MB vs Swift 4.75 MB would be a go if the owner applies the rule to footprint); SP-16b: pending (NSE and widget memory on a device after T13)
- SP-21: pending (`WKWebView` sign-in, hidden BARS login, cookie export)
- SP-23: pending (App Group, Keychain group and NSE on the simulator; free-team limits; F3 package)
