# Web sign-in

Product behaviour: [Как это работает: веб-версия](../how-it-works/web.md).

The web version at `https://<domain>/app/` has no password form: a browser
signs in when the signed-in app approves its eight-character code (typed or
scanned from a QR). `core/weblogin` holds the contract, `feature/weblogin` the
parser, data, view model and sheet. Backend contract:
`../itmo-widgets-backend/docs/contracts/web.md`; web side:
`../itmo-widgets-web/web/README.md`.

## Entry point

- `Вход на сайт` on the profile tab is shown only while
  `Подключение к ITMO.Widgets` is on (`MeViewModel` maps
  `CustomServicesRepository.observeEnabled()` to
  `MeUiState.webLoginAvailable`), so there is never a row that can only fail.
- It opens the sheet key `AppRoutes.WebLogin` (`SheetPolicy(textInput =
  true)`) once; the demo session refuses it (`ShellGate`). The sheet scene
  (`ShellScenes.kt`) opens it expanded to content, up to 90 % of the window,
  and resizes for the keyboard.
- Body: `WebLoginSheetRoute` (`:shared:feature-account`,
  `feature/weblogin/ui/`). `AccountEntries.kt` registers the key and keeps the
  Android effects: the scanner (`scanWebLoginCode` in `:app`) and `onClose`.

## Reading the code

- The field follows the state (scanned or normalized code, cursor at the end)
  but never overwrites a keystroke the state has not caught up with.
- Scanner: Google's code scanner (`play-services-code-scanner`), QR only, runs
  inside Play services, so no camera permission. The manifest's
  `com.google.mlkit.vision.DEPENDENCIES` = `barcode_ui` installs the module
  with the app. The result goes to the view model captured before the scan,
  since the sheet's view may be gone by then.
- `WebLoginCode.parse` accepts a typed code (eight characters from
  `ABCDEFGHJKMNPQRSTUVWXYZ23456789`, case, spaces and dashes ignored) or a link
  `https://<host>/app/login?code=<code>`: any HTTPS host (prod and dev), path
  exactly `/app/login` (trailing slash allowed), exactly one `code`, no user
  info. Anything else with `://` is not a sign-in link.
- `describeUserAgent` turns Backend's recorded User-Agent into a browser and a
  platform, each null when unknown. Order of checks matters: Chromium forks
  also send the Chrome token and iOS says `like Mac OS X`.

## States

`WebLoginViewModel` exposes one `WebLoginUiState`: `Input(code, error)`,
`Checking(code)`, `Confirm(code, preview, browser, requestedAt, approving)`,
`Done`, `Error(text, code)`.

- `requestedAt` uses the academic clock's zone; the field value lives in
  `SavedStateHandle`.
- While the approval is in flight both buttons are disabled: it may already
  have signed the browser in.
- `Done` and `Error` are polite live regions.

## Errors

| Situation | Result |
|---|---|
| Invalid code or a non-sign-in QR | inline error, the typed code stays |
| Scanner cannot start | inline hint, typing still works |
| Preview `not_found` | inline error, fix in place |
| Approval `not_found`, connection off | `Error`; `Повторить` opens an empty field |
| Network or other error | common `AppError` text; `Повторить` checks the same code |

`WebLoginRepositoryImpl` maps `not_found` -> `AppError.NotFound`,
`permission_denied` -> `Forbidden`, `restricted` -> `Restricted`, else
`Unknown`; exceptions go through `toAppError()`. A preview without data is
`Unknown`. Server messages are never shown.

## Backend routes

Both go through Core's `ItmoWidgetsApi` and the custom-services gate inside the
repository:

- `GET /api/users/me/web-login/{code}` -> `WebLoginPreview`
  `{challengeId, userAgent, createdAt, expiresAt}`; 404 `not_found` for an
  unknown, used or expired code. A blank User-Agent becomes null.
- `POST /api/users/me/web-login/{challengeId}/approve` -> empty success.

The browser side (`POST /api/web/auth/challenges`, polling, the `iw_session`
cookie) is never called by the app.

## Security

- Only the app approves: both routes accept only the app's ITMO.ID bearer token
  and ignore the web cookie, so a web session can never approve another browser.
- A code lives 2 minutes and is single-use; the browser renews it. Backend
  limits unapproved codes per client address.
- The user sees the browser and request time before `Войти`, and the sheet
  says to approve only one's own sign-in: a code read from someone else's
  screen would sign that person in to the approver's account.
- The app never sends its token to the browser; the browser gets its own
  session cookie from Backend after the approval.

## Device pairing

Groundwork for «Подключить устройство» (connect a self-hosted agent or a site
with ITMO access); nothing reaches Backend yet. The sheet recognises a pairing
link (scanned, pasted, or from an App Link) and answers `Подключение устройств
появится в следующей версии`. In `feature/weblogin/domain`:

- `PairingCrypto`: the key commitment `SHA-256("iw-pair-commit-v1" || pk || s)`,
  the HPKE `info` (`"itmo-widgets pair v1" || 0 || challengeId || kind || 0 ||
  s`) and the six-digit verification code (first 20 bits of
  `SHA-256("iw-pair-sas-v1" || challengeId || pk || s)` modulo 10^6).
- `PairingSealer.sealBundle(link, bundle)`: the `PairingBundle` JSON (`v`,
  `challengeId`, `kind`, `isu`, `refreshToken`, `refreshExpiresAt`,
  `issuedAt`, instants as ISO-8601) sealed with HPKE base mode
  DHKEM(X25519, HKDF-SHA256) / HKDF-SHA256 / ChaCha20-Poly1305, empty AAD, as
  `base64url(enc || ciphertext)`, at most 16 KiB. `HpkeSender` is Tink
  (`TinkHpkeSender`, `NO_PREFIX`) on Android and CryptoKit `HPKE.Sender`
  through `PairingHost` on iOS.
- `HandoffSession` (`TokenManagerHandoffSession`): one `forceRefresh` of
  MyItmoApi's `TokenManager` keeps the new tokens as the phone's session and
  returns the previous refresh token, never stored anywhere; the demo session
  is refused.
- `PairingHandoff.seal`: `DeviceUnlock` first (`BiometricDeviceUnlock` with
  `BIOMETRIC_WEAK | DEVICE_CREDENTIAL` on Android, LocalAuthentication
  `deviceOwnerAuthentication` on iOS), then the hand-off, then the seal; a
  cancelled or missing screen lock rotates nothing.
- Test vectors: `PairingVectors` (commonTest) and `PairingTests.swift` share
  one bundle sealed by CryptoKit and one sealed by Tink; each platform opens
  the other's, and iOS checks RFC 9180 A.2.

## iOS

- `WebLoginSheet` (`iosApp/Sources/Features/WebLogin/`) is SwiftUI over the
  same `WebLoginViewModel` ([iOS app](../ios.md#data-sharing)); the demo
  refuses the route.
- Scanner: VisionKit `DataScannerViewController`, QR only; unlike Android it
  needs the camera (`NSCameraUsageDescription`). A refused camera or no
  scanner (simulator) falls back to typing.
- A fresh `SavedStateHandle` (`WebLoginIosParameters`) per open; debug
  fixture `-itmoWebLoginFixture` (`WebLoginIosFixture`, no Backend).
