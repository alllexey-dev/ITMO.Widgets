# My ITMO in the app

`feature/web` opens the official website `https://my.itmo.ru/` in a WebView
overlay for everything the app does not show natively. The website keeps its
own browser session: the app injects no token, adds no JavaScript bridge and
logs no console output. Sign-in to the app itself is a separate WebView, see
[Sign-in](auth.md).

## Screen

`MyItmoWebFragment` is the overlay destination `my_itmo_web`
(`AppScreen.MY_ITMO_WEB`), opened by the second button on the home tab. The
demo session refuses it in `MainActivity` with the toast «Недоступно в демо».
The fragment has no ViewModel: the WebView is its state.

The screen is `MyItmoWebScreen` in `:shared:feature-account`
(`feature/web/ui/`), stateless, with the browser as a slot
(`browser: @Composable (Modifier) -> Unit`). The Fragment hosts it through
`itmoComposeView` and fills the slot with an `AndroidView` WebView; it derives
the screen's `MyItmoWebState` from the WebView callbacks:

- `Loading`: a page loads; the 4 dp indeterminate progress line runs under
  the top bar over the visible page.
- `Shown`: the page is loaded; the line's 4 dp stay empty, so the page never
  jumps.
- `Failed`: the error page «Не удалось открыть My ITMO» / «Проверьте
  подключение к интернету и попробуйте ещё раз» with «Повторить», which loads
  the last trusted page, covers the browser; the Fragment also hides the
  WebView, so it takes no touches and TalkBack does not read it.

- Top bar «My ITMO» with «Закрыть», «Обновить страницу» (reloads the last
  trusted page) and the overflow «Ещё» with «Открыть в браузере» (opens
  `https://my.itmo.ru/` outside the app). The bar shows no subtitle.
- Back goes through the web history first and leaves the screen when there is
  none; the close button leaves at once.
- One WebView per Fragment view: built in the `AndroidView` factory, kept in
  every state (the slot is never removed from composition), destroyed when the
  composition releases it or in `onDestroyView`. Recreation restores the web
  history (`WebView.saveState` under `my_itmo_browser`), the last trusted URL
  (`my_itmo_url`) and the error flag (`my_itmo_error`); a restored error stays
  an error until a retry or Back.

## Navigation policy

`MyItmoWebPolicy` decides every request:

- `INTERNAL`: HTTPS on `my.itmo.ru` or `id.itmo.ru` exactly, port 443 or none,
  no user info in the URL. Only these load inside.
- `EXTERNAL`: another HTTPS URL with a host and no user info, for a main-frame
  navigation the user started with a gesture. It opens in an external app;
  without one a Snackbar says «Нет приложения, чтобы открыть ссылку».
- `BLOCKED`: everything else, including redirects and frames that try to leave.
  A blocked main frame shows the error state.

A page that starts outside `INTERNAL` is stopped and shows the error. A
main-frame network or HTTP error shows the error state; an HTTP failure that
arrives before `onPageStarted` is not hidden by a late start. A TLS error
cancels the load and shows the error.

WebView settings: JavaScript and DOM storage on; file and content access,
mixed content, automatic and multiple windows off; cookies on, third-party
cookies off; console messages swallowed.

## Session data

`WebSessionDataCleaner` is a `SessionDataCleaner`: sign-out and an account
change delete all `WebStorage` data, the WebView HTTP cache and all cookies,
then flush the cookie store. The website session never outlives the app
session, and the app's refresh token never reaches the website.

## Debug host and tests

`MyItmoWebPreviewFragment` (debug source set) overrides
`onBrowserCreated` (no HTTP cache) and `interceptRequest` to answer every
request with synthetic HTML, keeping the real WebView lifecycle; it runs in
`SettingsNavigationTestActivity`. No request reaches My ITMO or Backend.

- JVM: `MyItmoWebPolicyTest`; `MyItmoWebScreenTest` (the slot stays composed
  at one size in every state, the line and the error page, the callbacks).
- Goldens: the `MyItmoWebScreen` previews `loading`, `shown` and `error` in
  four appearances, with a placeholder in the browser slot, in
  `AccountScreenshotTest`.
- Instrumented: `HomeWebVisualTest` reads the screen through the test tags of
  `MyItmoWebTestTags` (`loading`, `state_container`, `web_reload`,
  `web_close`) and the WebView through `MyItmoWebFragment.browser`.

## iOS

`MyItmoWebScreen` (`iosApp/Sources/Features/MyItmoWeb/`) is the iOS screen of
the route `AppRoutes.MyItmoWeb`: a SwiftUI screen pushed on the tab's stack
under the native bar, not the Compose `MyItmoWebScreen` (it has no iOS browser
slot). The demo session refuses it before the route map (`ShellGate`), as on
Android. Until the home tab is hosted on iOS (IO-09) the route has no entry
point.

- Bar: «My ITMO», «Обновить страницу» and the menu «Ещё» with «Открыть в
  браузере» (`https://my.itmo.ru/` in Safari). The system back button replaces
  the close button; the edge swipe inside the page goes back in its history.
- States: the same three as Android (`MyItmoWebLoad`). The 4 pt progress line
  follows WebKit's load progress and keeps its height while hidden; the error
  page covers the browser, which then takes no touches and is hidden from
  VoiceOver. «Повторить» and «Обновить страницу» load the last trusted page.
- `MyItmoWebBrowser` runs one `WKWebView` on `WKWebsiteDataStore.default()`,
  the store the ITMO.ID sign-in uses; sign-out clears it
  (`IosCoreHost.clearWebsiteData`). No token injection, no script bridge, no
  console logging; automatic windows are off and a `target="_blank"` page
  loads in the same browser.
- Every navigation goes through `MyItmoWebPolicy` (`MyItmoWebDecision`):
  `INTERNAL` loads inside, `EXTERNAL` (a main frame the user tapped:
  `WKNavigationType.linkActivated`) opens outside the app, `BLOCKED` is
  cancelled and a blocked main frame shows the error. A main frame that answers
  HTTP 400 or above, fails to load or starts outside the official origins
  shows the error too.
- Tests: `ITMOWidgetsTests/AccountTests` (the decisions for official pages,
  tapped and redirected pages, frames and other schemes; the route and its
  chrome), `SnapshotTests/AccountSnapshotTests` (loading and error around a
  stand-in browser).
