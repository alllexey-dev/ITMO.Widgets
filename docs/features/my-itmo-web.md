# My ITMO in the app

Product behaviour: [Как это работает: вход и аккаунт](../how-it-works/account.md#my-itmo-в-приложении).

`feature/web` opens `https://my.itmo.ru/` in a WebView overlay for everything the app does not show
natively. The website keeps its own browser session: the app injects no token, adds no JavaScript
bridge and logs no console output. App sign-in is a separate WebView ([Sign-in](auth.md)).

## Screen

- Key `AppRoutes.MyItmoWeb` (home tab). The demo session refuses it before it opens (`ShellGate`).
  No ViewModel: the WebView is the state.
- `MyItmoWebScreen` (`:shared:feature-account`, `feature/web/ui/`) is stateless with the browser as a
  slot; `AccountEntries.kt` registers one `MyItmoBrowser` (`:app`, `feature/web/ui/MyItmoBrowser.kt`)
  per entry, which derives `MyItmoWebState` (`Loading`, `Shown`, `Failed`) from WebView callbacks.
- The slot is never removed from composition, so the WebView survives every state; in `Failed` the
  host hides it from touches and TalkBack. Retry and refresh load the last trusted page.
- Back goes through the web history; the entry takes Back only while `MyItmoBrowser.canGoBack`, so
  without history the system gesture previews closing.
- Recreation restores from `rememberSaveable`: web history (`my_itmo_browser`), last trusted URL
  (`my_itmo_url`) and the error flag (`my_itmo_error`).

## Navigation policy

`MyItmoWebPolicy` decides every request:

- `INTERNAL`: HTTPS on `my.itmo.ru` or `id.itmo.ru` exactly, port 443 or none, no user info. Only
  these load inside.
- `EXTERNAL`: another HTTPS URL with a host and no user info, for a user-gesture main-frame navigation.
  Opens in an external app.
- `BLOCKED`: everything else, including redirects and frames that try to leave. A blocked main frame
  shows the error state.

A page that starts outside `INTERNAL` is stopped; a main-frame network, HTTP or TLS error shows the
error state (an HTTP failure before `onPageStarted` is not hidden by a late start).

WebView settings: JavaScript and DOM storage on; file and content access, mixed content, automatic and
multiple windows off; cookies on, third-party cookies off; console messages swallowed.

## Session data

`WebSessionDataCleaner` (`SessionDataCleaner`): sign-out and account change delete all `WebStorage`
data, the HTTP cache and all cookies, then flush the cookie store. The website session never outlives
the app session, and the app's refresh token never reaches the website.

## Tests

`MyItmoWebPolicyTest` and `MyItmoWebScreenTest` in `:shared:feature-account`, `AccountEntriesTest`
and `UrlPolicyParityTest` in `:app`. No test request reaches My ITMO or Backend.

## iOS

A native SwiftUI `MyItmoWebScreen` (`iosApp/Sources/Features/MyItmoWeb/`), not the Compose one;
`ShellGate` refuses it in the demo.

- `MyItmoWebBrowser` runs one `WKWebView` on `WKWebsiteDataStore.default()`, the store the ITMO.ID
  sign-in uses; sign-out clears it (`IosCoreHost.clearWebsiteData`). No token injection, script bridge
  or console logging; automatic windows off, `target="_blank"` loads in the same view.
- Every navigation goes through `MyItmoWebPolicy` (`MyItmoWebDecision`); `EXTERNAL` means
  `WKNavigationType.linkActivated`. A main frame with HTTP 400 or above, a load failure or a start
  outside the official origins shows the error.
- Tests: `ITMOWidgetsTests/AccountTests`, `SnapshotTests/AccountSnapshotTests`.
