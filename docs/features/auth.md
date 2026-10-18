# Sign-in

Product behaviour: [Как это работает: вход и аккаунт](../how-it-works/account.md).

`feature/auth` signs the user in to ITMO.ID, keeps the session and signs out.
Credentials are typed only on the official ITMO pages; the app receives the
tokens ITMO.ID issues and never sees a password. See also
[Demo session](demo.md) and [First-run flow](onboarding.md).

## Screen

- Gate key `AppRoutes.Auth`, shown by `ShellGate.surface` for `SignedOut`,
  `SigningOut` and `ReauthenticationRequired`.
- `AuthRoute`/`AuthScreen` in `:shared:feature-account`; `AccountEntries.kt`
  launches `LoginActivity` for a result and plays the demo confirmation.
- `AuthUiState` follows `SessionRepository.state`; errors map in
  `toAuthText` (a malformed token response counts as `Unauthorized`).
- The look (DECISIONS.md, Вход): the app mark in its tile, the name, one line on
  what the app does (or that the session ended), one main path `Войти через
  ITMO.ID` with `Логин и пароль — только на странице ITMO.ID`, and `Другие
  способы входа`, which opens a sheet with the refresh token card (`Войти по
  токену`). The unofficial notice closes the screen. The demo stays on five taps
  on the mark (ADR 0015); nothing on the screen names it.

## ITMO.ID page

`LoginActivity` hosts the shared `LoginScreen` with a WebView in a
`SwipeRefreshLayout` (Compose's pull-to-refresh cannot see a WebView scroll).

- Every fresh open clears WebView history, cache, `WebStorage` and all
  cookies, then loads `https://my.itmo.ru/`. JavaScript and DOM storage are on;
  file and content access, pop-ups, mixed content, the HTTP cache and
  third-party cookies are off.
- Navigation: any HTTPS page with a host stays inside
  (`HttpsNavigationPolicy`), so ITMO.ID can hand over to VK or another
  provider. Other links open externally only when HTTPS; a page that starts
  outside the policy is stopped and shows the error.
- Tokens: when a page starts at exactly `https://my.itmo.ru/login/callback`
  (`ItmoAuthUrlPolicy.isTokenCallback`), the asset
  `token_refresh_interceptor.js` watches `XMLHttpRequest` for the token
  endpoint and passes the response to the `ItmoAuthBridge` JS interface, which
  accepts it only while the WebView is still on the callback page.

## Session

`SessionRepositoryImpl` implements `core/session/SessionRepository`.

- Refresh-token sign-in: the `Другие способы входа` sheet keeps the token in
  `remember` (SwiftUI `@State`) only, never in saved state, and drops it when it
  closes; `signInWithRefreshToken` refreshes it once via the stateless
  `identity.refresh`, and the current session stays until that succeeds.

- `initialize()`: an active demo restores the demo; no refresh token is
  `SignedOut`; an expired one is `ReauthenticationRequired`; otherwise
  `SignedIn` from the ID token, then the Backend sync.
- Tokens refresh on demand only: MyItmoApi's `TokenManager` refreshes an access
  token expiring within 30 s before a request, and a My ITMO 401 gets one forced
  refresh and one retry. No refresh timer; widgets and workers keep the session
  alive by their requests.
- A refresh ITMO.ID rejects ends the session while the app runs:
  `ReauthenticationRequired`, tokens, caches and settings left until the next
  sign-in. "Rejected" is a `MyItmoException.Auth` inside the refresh
  (`RefreshRejections.guard`): a non-5xx OAuth `error` JSON or a locally
  expired refresh token. Timeouts, I/O, ITMO.ID 5xx, garbled answers and My
  ITMO's own 401/403 are not rejections: the request fails retriably.
- ITMO.ID lifetimes (observed 2026-10-11): access 30 minutes, refresh 30 days,
  sliding with every refresh; a rotation does not revoke the old refresh token.
- Sign-in accepts a token response of at most 32 KiB
  (`MAX_TOKEN_RESPONSE_LENGTH`) with non-blank tokens and positive expirations.
  Then: prepare widgets, run every `SessionDataCleaner` (the ITMO.ID page's
  sign-in skips the web store's `WebStoreDataCleaner`, see below), clear the demo flag,
  store tokens, publish `SignedIn`, run signed-in effects, sync Backend
  identity, the FCM token and device registration (their failures never fail
  the sign-in). A failure before that clears tokens and data and ends
  `SignedOut`.
- `signOut()` publishes `SigningOut` first (no screen renders cleared caches),
  then unregisters the device, prepares widgets, runs the cleaners, clears
  tokens, runs signed-out effects, ends `SignedOut` (confirm: [profile](me.md)).

## iOS

Same shared ViewModels and `SessionRepository` under SwiftUI
([iOS app](../ios.md#data-sharing)).

- `DefaultBackendIdentitySync` in shared core publishes the current ID token through
  `PUT /api/users/me/id-token`, after refreshing an expiring access token. Android
  retries failures with WorkManager; iOS retries on each `AppForeground` return
  while signed in. Initialization, sign-in and enabling custom services sync on
  both platforms. Demo and disabled custom services send nothing.

- `AuthScreen` (`iosApp/Sources/Features/Auth/`) with the same look; the refresh token
  is `AuthOtherWaysSheet`. `ItmoSignInScreen`: `WKWebView` on the default data store with the same
  `token_refresh_interceptor.js`, accepted only from the main frame of
  `https://my.itmo.ru` on `/login/callback`. As on Android, every load (a
  fresh page, a retry) first clears WebKit's website data
  (`AppPlatform.removeWebsiteData`) and loads the page only after that; the
  sign-in itself keeps the store.
- Tokens are one Keychain item, `myitmo_tokens`; `TokenManager` refreshes them
  under a cross-process file lock. Sign-out also clears the Keychain, the App
  Group container and WebKit's website data.
- The demo entry is the same five taps ([demo](demo.md#ios)).

## Web store after sign-in

The ITMO.ID page's sign-in (`completeItmoIdLogin`) keeps the web store: the
`WebStoreDataCleaner` (`WebSessionDataCleaner` on Android,
`WebsiteDataSessionDataCleaner` on iOS) is the one cleaner it skips. The page
ran in that store after it was cleared (both platforms clear it when the page
opens), so its ITMO.ID SSO cookies belong to the account of the new tokens,
and the BARS sign-in and renewal use them
([ADR 0012](../decisions/0012-bars-background-renewal.md#the-sign-in-keeps-the-itmoid-session)).
A refresh-token sign-in, the demo, sign-out and a failed sign-in clear it as
before.

## Stable identifiers

Quoted from `StableIdentifiersTest`: the token file `myitmo_tokens.enc` in
`noBackupFilesDir`, the Keystore alias `itmo_widgets_myitmo_tokens_v1` with the
cipher prefix `v1:`, and the DataStore file `app_preferences`, which holds
`demo_active`.
