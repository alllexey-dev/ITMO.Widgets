# Web sign-in

The web version at `https://<domain>/app/` has no password and no ITMO.ID form
of its own: a browser signs in when a phone that is already signed in to the app
approves it. The browser shows an eight-character code and a QR with the same
code; the app reads one of them, shows which browser asked and signs it in after
`Войти`. `core/weblogin` holds the contract, `feature/weblogin` the parser, the
data, the view model and the sheet. The Backend contract is
`../itmo-widgets-backend/docs/contracts/web.md`; the web side is described in
`../itmo-widgets-web/web/README.md`.

## Entry point

`Вход на сайт` (`ic_computer`) is a row of the `Приложение` group on the profile
tab, between the connection hint and `Настройки`. It is visible only while
`Подключение к ITMO.Widgets` is on: `MeViewModel` maps
`CustomServicesRepository.observeEnabled()` to `MeUiState.webLoginAvailable`,
and `MeRenderer` hides the row and its divider otherwise. Without the connection
the profile shows the connection hint instead, so there is never a row that can
only fail.

The row calls `AppNavigator.openWebLogin()`, which shows `WebLoginBottomSheet`
on the Activity's FragmentManager, once (a second tap while it is open or after
the state is saved does nothing). The sheet opens expanded to its content through
`core/ui/BottomSheets.kt` `expandToContent()` and re-measures only when the step
changes, not on every keystroke.

## Reading the code

The sheet has one field, `Код с сайта`, a `Сканировать QR` button and
`Продолжить`. `Продолжить` and the keyboard's Go action submit the field.

The scanner is Google's code scanner (`play-services-code-scanner` 16.1.0),
limited to QR codes. It runs inside Play services, so the app needs no camera
permission. The manifest declares `com.google.mlkit.vision.DEPENDENCIES` =
`barcode_ui`, so Play installs the scanner module with the app instead of on the
first scan. If the scanner cannot start, the field says
`Сканер недоступен, введите код` and typing still works. The scan result is
delivered to the view model captured before the scan, because the answer may
arrive after the sheet's view is gone.

`WebLoginCode.parse` accepts two inputs:

- A typed code: eight characters from `ABCDEFGHJKMNPQRSTUVWXYZ23456789` (no
  `0 O 1 I L`). Case, spaces and dashes are ignored, so `abcd-efgh` and
  `ABCD EFGH` are the same code.
- A scanned link `https://<host>/app/login?code=<code>`. Any HTTPS host is
  accepted so production and dev both work; the path must be exactly
  `/app/login` (a trailing slash is allowed), the query must hold exactly one
  `code`, and a link with user info (`https://user@host/...`) is rejected. The
  value is URL-decoded and normalized like a typed code.

Anything else with `://` is not a sign-in link.

## Browser description

`describeUserAgent` reads the User-Agent that Backend recorded for the browser
and returns a browser and a platform, each null when unknown. Order matters
because Chromium forks also say `Chrome/` and iOS says `like Mac OS X`.

| Browser | Recognised by |
|---|---|
| `Яндекс Браузер` | `YaBrowser/`, `YaSearchBrowser/` |
| Edge | `Edg/`, `EdgA/`, `EdgiOS/`, `Edge/` |
| Opera | `OPR/`, `OPT/`, `Opera` |
| Samsung Internet | `SamsungBrowser/` |
| Firefox | `Firefox/`, `FxiOS/` |
| Chrome | `Chrome/`, `CriOS/`, `Chromium/` |
| Safari | `Safari/` together with `Version/` |

Platforms: Windows, iOS (`iPhone`, `iPod`), iPadOS (`iPad`), Android, ChromeOS
(`CrOS`), macOS, Linux. The card reads `Chrome на macOS`, `Chrome`,
`Браузер на Android` or `Браузер`. `UiText.resolve()` resolves `UiText`
arguments first, so the format takes localized names.

## States

`WebLoginViewModel` exposes one `WebLoginUiState`:

| State | Sheet |
|---|---|
| `Input(code, error)` | Field, `Сканировать QR`, `Продолжить` (enabled when the field is not blank); `error` under the field |
| `Checking(code)` | Same, disabled, progress inside `Продолжить` |
| `Confirm(code, preview, browser, requestedAt, approving)` | Browser card (`Chrome на macOS`, `Запрошен в 12:04` in the academic clock's zone), `Подтверждайте только свой вход`, `Войти`, `Отмена` |
| `Done` | `Готово — вернитесь в браузер`, `Закрыть` |
| `Error(text, code)` | Error icon, text, `Повторить` |

The field value lives in `SavedStateHandle`, so it survives recreation. `Отмена`
returns to the field with the code. While `Войти` is in flight both buttons are
disabled: the approval may already have signed the browser in.

## Errors

| Situation | Result |
|---|---|
| Field is not a valid code | `Нужен код из 8 символов` under the field |
| Scanned QR is not a sign-in link | `Это не QR для входа на сайт`; the typed code stays |
| Scanner cannot start | `Сканер недоступен, введите код` |
| Preview answers `not_found` | `Код не найден или устарел` under the field, to fix in place |
| Approval answers `not_found` | Error `Код не найден или устарел`; `Повторить` opens an empty field |
| Connection is off | Error `Нужно подключение к ITMO.Widgets`; `Повторить` opens an empty field |
| Network or any other error | The common `AppError` text; `Повторить` checks the same code again |

`WebLoginRepositoryImpl` maps Backend codes `not_found` → `AppError.NotFound`,
`permission_denied` → `Forbidden`, `restricted` → `Restricted`, anything else →
`Unknown`; exceptions go through `toAppError()`. A preview without data is
`Unknown`. Server messages are never shown.

## Backend routes

Both calls go through Core's `ItmoWidgetsApi` and the custom-services gate
inside the repository:

- `GET /api/users/me/web-login/{code}` → `WebLoginPreview`
  `{challengeId, userAgent, createdAt, expiresAt}`; 404 `not_found` for an
  unknown, used or expired code. A blank User-Agent becomes null.
- `POST /api/users/me/web-login/{challengeId}/approve` → empty success.

The browser side (`POST /api/web/auth/challenges`, polling
`GET /api/web/auth/challenges/{id}`, the `iw_session` cookie) is not called by
the app.

## Security

- Only the app approves: both routes accept only the app's ITMO.ID bearer token
  and ignore the web cookie, so a web session can never approve another browser.
- A code lives 2 minutes and is single-use; the browser renews it. Backend limits
  unapproved codes per client address.
- The user sees the browser and the request time before `Войти`, and the sheet
  says to approve only one's own sign-in: a code read from someone else's screen
  would sign that person in to the approver's account.
- The app never sends its token to the browser. The browser gets its own
  session cookie from Backend after the approval.

## Tests

- JVM: `WebLoginCodeTest` (codes, links, rejected hosts and paths),
  `BrowserDescriptionTest` (User-Agent table), `WebLoginRepositoryImplTest`
  (gate, error codes, blank User-Agent), `WebLoginViewModelTest` (every state
  and error path), `MeViewModelTest` (row visibility).
- Instrumented: `WebLoginVisualTest` on the `WebLoginPreviewActivity` debug
  host with a fixture repository: the approve flow, wrong and expired codes, a
  large font on a narrow screen; the profile row only with the connection is
  checked in `SettingsNavigationTestActivity`.
