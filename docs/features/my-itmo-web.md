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

- Toolbar «My ITMO» with «Закрыть», the current host as the subtitle, and the
  menu items «Обновить страницу» (reloads the last trusted page) and «Открыть в
  браузере» (opens `https://my.itmo.ru/` outside the app).
- A 4 dp indeterminate progress line under the toolbar shows while a page
  loads; when hidden it is `INVISIBLE`, so the page never jumps.
- Error state «Не удалось открыть My ITMO» / «Проверьте подключение к интернету
  и попробуйте ещё раз» with «Повторить», which loads the last trusted page.
- Back goes through the web history first and leaves the screen when there is
  none; the toolbar close leaves at once.
- Recreation restores the web history (`WebView.saveState`), the last trusted
  URL and the error flag; a restored error stays an error until a
  retry or Back.

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
`interceptRequest` to answer every request with synthetic HTML, keeping the
real WebView lifecycle; it runs in `SettingsNavigationTestActivity`. No request
reaches My ITMO or Backend.

- JVM: `MyItmoWebPolicyTest`.
- Instrumented: `HomeWebVisualTest`.
