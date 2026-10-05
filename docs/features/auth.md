# Sign-in

`feature/auth` signs the user in to ITMO.ID, keeps the session and signs out.
Credentials are typed only on the official ITMO pages; the app receives the
tokens ITMO.ID issues and never sees a password. The hidden demo entry on the
same screen is described in [Demo session](demo.md); the first-run flow after
sign-in in [First-run flow](onboarding.md).

## Screen

`AuthFragment` is `auth`, the start destination of `main_nav_graph`.
`MainActivity` returns to it for `SignedOut`, `SigningOut` and
`ReauthenticationRequired`: it dismisses overlays, hides the bottom bar and
resets the graph unless `auth` is already shown.

`AuthUiState` follows `SessionRepository.state`:

- `Initializing`: only the progress with «Проверяем сессию».
- Content: the logo (`auth_logo`, decorative for accessibility services), the
  application name, three lines «Расписание и QR-пропуск на главном экране»,
  «Спорт с автозаписью на занятия», «Друзья и их расписание», the buttons
  «Войти через ITMO.ID» and «Другой способ входа», the notes «Логин и пароль
  вводятся только на странице ITMO.ID.» and «Неофициальное приложение. Не
  связано с Университетом ИТМО.».
- `ReauthenticationRequired` adds «Сессия истекла. Войдите снова, чтобы
  продолжить.»; the feature lines stay.
- A refresh-token sign-in or a sign-out in progress shows «Входим» and
  disables both buttons.
- An error is one line of text under the buttons; starting a sign-in clears it.

## ITMO.ID page

«Войти через ITMO.ID» starts `LoginActivity` for a result (title «Вход через
ITMO.ID», close in the toolbar, pull to reload). Every fresh open clears the
WebView history, cache, `WebStorage` and all cookies, then loads
`https://my.itmo.ru/`. The WebView runs JavaScript and DOM storage; file and
content access, pop-up windows, mixed content, the HTTP cache and third-party
cookies are off.

- Navigation: any HTTPS page with a host stays inside
  (`HttpsNavigationPolicy`), so ITMO.ID can hand over to VK or another
  provider. Other links open in an external app only when they are HTTPS; a
  page that starts outside the policy is stopped and shows the error.
- Tokens: when a page starts at exactly `https://my.itmo.ru/login/callback`
  (`ItmoAuthUrlPolicy.isTokenCallback`), the activity injects the asset
  `token_refresh_interceptor.js`. It watches `XMLHttpRequest` for the ITMO.ID
  token endpoint and passes a response with access, refresh and ID tokens to
  the `ItmoAuthBridge` JavaScript interface. The bridge accepts it only while
  the WebView is still on the callback page.
- `InteractiveLoginViewModel.completeLogin` hands the JSON to
  `SessionRepository.completeItmoIdLogin` and shows «Входим» over the page.
  Success finishes with `RESULT_OK`; a failure or a main-frame load error
  hides the page and shows the error text («Страница ITMO.ID не открылась.»
  for a load error) with «Повторить», which loads a clean page again. A result
  other than `RESULT_OK` clears the error on the sign-in screen.

## Refresh token

«Другой способ входа» opens the dialog «Вход по Refresh token» with «Этот
способ — на случай, когда вход через ITMO.ID не работает. Токен останется на
устройстве.», the field «Refresh token» and «Отмена» / «Войти». An empty field
shows «Вставьте Refresh token»; the field is cleared on submit and on dismiss.
`DefaultRefreshTokenAuthenticator` refreshes the trimmed token through a fresh
MyItmoApi `MyItmo` client off the main thread.

## Session

`SessionRepositoryImpl` implements `core/session/SessionRepository`.

- `initialize()`: an active demo restores the demo session; no refresh token
  is `SignedOut`; an expired refresh token is `ReauthenticationRequired`;
  otherwise `SignedIn` with the user decoded from the ID token, then the
  Backend sync below.
- A sign-in accepts a token response of at most 32 KiB whose access, refresh
  and ID tokens are not blank and whose expirations are positive. It then
  prepares widgets for the change, runs every `SessionDataCleaner`, clears the
  demo flag, stores the tokens, publishes `SignedIn`, runs the signed-in
  effects and syncs Backend identity, the FCM token and the device
  registration; their failures are recorded and never fail the sign-in. A
  failure before that clears the tokens and the data and ends `SignedOut`.
- `signOut()` publishes `SigningOut` first, so no screen renders its cleared
  caches, then unregisters the device, prepares widgets, runs the cleaners,
  clears the tokens, runs the signed-out effects and ends `SignedOut`. The
  confirmation lives in the [profile tab](me.md).

Errors map in `toAuthText`: `AppError.Network` is «Не удалось связаться с
ITMO.ID. Проверьте интернет.», `AppError.Unauthorized` (also a malformed token
response) is «Сессию подтвердить не вышло. Войдите заново или проверьте Refresh
token.», anything else «Вход не завершился. Попробуйте ещё раз.».

## Stable identifiers

Quoted from `StableIdentifiersTest`: the token file `myitmo_tokens.enc` in
`noBackupFilesDir`, the Keystore alias `itmo_widgets_myitmo_tokens_v1` with the
cipher prefix `v1:`, and the DataStore file `app_preferences`, which holds
`demo_active`.

## Tests

JVM: `ItmoAuthUrlPolicyTest`, `AuthViewModelTest`, `DemoEntryTapsTest`,
`SessionRepositoryImplTest`. `LoginActivity` has a layout-only capture in
`DesignComponentsVisualTest`; the sign-in screen has no visual test.
