# Web sign-in and device pairing

Product behaviour: [Как это работает: веб-версия](../how-it-works/web.md).
The first row of `Устройства` (a settings page under «Сервер ITMO.Widgets») and
the sheet are called `Подключить устройство`: the same sheet signs a browser in and pairs a device
([Device pairing](#device-pairing)). Both need the connection to ITMO.Widgets.

The web version at `https://<domain>/app/` has no password form: a browser
signs in when the signed-in app approves its eight-character code (typed or
scanned from a QR). Backend contract:
`../itmo-widgets-backend/docs/contracts/web.md`; web side:
`../itmo-widgets-web/web/README.md`.

## Entry point

- `Подключить устройство` is on `Устройства` (`SettingsPage.DEVICES`), which «Сервер ITMO.Widgets» lists only
  while connected, so there is never a row that can only fail.
- It opens the sheet key `AppRoutes.WebLogin` (`SheetPolicy(textInput =
  true)`) once; the demo session refuses it (`ShellGate`). The sheet scene
  (`ShellScenes.kt`) opens it expanded to content, up to the height below the status bar,
  and resizes for the keyboard.

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

`WebLoginViewModel` exposes one `WebLoginUiState` (`Input`, `Checking`, `Confirm`, `Unlocking`,
`Rotating`, `Sending`, `Done`, `Error`); the field value lives in `SavedStateHandle`, `requestedAt`
uses the academic clock's zone. While the approval is in flight both buttons are disabled: it may
already have signed the browser in. `Done` and `Error` are polite live regions.

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

The same sheet connects a self-hosted agent: a link
`https://<host>/pair#v1.<CODE>.<pk>.<s>` from the scanner, a pasted link or an
App Link (`PairLink`, our two hosts only). Backend contract:
`../itmo-widgets-backend/docs/contracts/pairing.md`.

1. `PairingRepository.open(code, link)` calls `POST
   /api/users/me/pair/{code}/open` and shows the card only when Backend's
   `keyCommitment` equals `PairingCrypto.keyCommitment(pk, s)` of the link
   (`QR не совпадает с запросом` otherwise). The key never comes from Backend:
   a typed code of a keyed kind is refused with `Для этого устройства
   отсканируйте QR или вставьте ссылку` (the old web-login route answers 404
   for such a code, then `open` names the kind).
2. `Confirm` shows the kind, the device's self-given name and version, the
   time, that it gets full My ITMO access, the permissions (switches for a new
   agent, read-only for an agent session), the shared-session notice and the
   six-digit verification code to compare with the device. `Отмена` rejects
   the challenge.
3. `Подключить`: `Unlocking` (the screen lock), `Rotating` (the hand-off),
   `Sending` (`POST .../{challengeId}/approve` with the sealed bundle and, for
   a new agent, the scopes), then `Done`.

| Situation | Result |
|---|---|
| Commitment differs | `QR не совпадает с запросом`, no retry |
| `WEB_ITMO` or a kind this version does not know | `Обновите приложение…` |
| 403 `permission_denied` (agent access off) | `Свои клиенты пока недоступны…` |
| 409 (five agents) | `Подключено слишком много устройств…` |
| Screen lock cancelled / not set up | back to the card / `Включите блокировку экрана…` |
| Rotation rejected | `Войдите в приложение заново`, nothing sent |
| Network | common text; `Повторить` opens the link again |

The link stays in the view model's memory; the saved state never holds it.

- `PairingCrypto`: the key commitment `SHA-256("iw-pair-commit-v1" || pk || s)`,
  the HPKE `info` (`"itmo-widgets pair v1" || 0 || challengeId || kind || 0 ||
  s`) and the six-digit verification code (first 20 bits of
  `SHA-256("iw-pair-sas-v1" || challengeId || pk || s)` modulo 10^6).
- `PairingSealer.sealBundle(link, bundle)`: the `PairingBundle` JSON (`v`,
  `challengeId`, `kind`, `isu`, `refreshToken`, `refreshExpiresAt`,
  `issuedAt`) sealed with HPKE base mode DHKEM(X25519, HKDF-SHA256) /
  HKDF-SHA256 / ChaCha20-Poly1305, empty AAD, as `base64url(enc ||
  ciphertext)`, at most 16 KiB. `HpkeSender` is Tink (`TinkHpkeSender`,
  `NO_PREFIX`) on Android and CryptoKit `HPKE.Sender` through `PairingHost` on
  iOS.
- `HandoffSession` (`TokenManagerHandoffSession`): one `forceRefresh` keeps
  the new tokens as the phone's session and returns the previous refresh
  token, never stored anywhere; the demo session is refused.
- `PairingHandoff.seal`: `DeviceUnlock` first (`BiometricDeviceUnlock` on the
  activity on Android, `IosDeviceUnlock` on iOS), then the hand-off, then the
  seal with the signed-in ISU; a cancelled or missing screen lock rotates
  nothing.
- Test vectors: `PairingVectors` (commonTest) and `PairingTests.swift` share
  one bundle sealed by CryptoKit and one sealed by Tink; `AgentPairingVectorsTest`
  pins the commitment, verification code and `info` the Go agent
  (`itmo-widgets-agent`, `internal/pair`) computes for the same inputs, and a
  bundle its suite sealed; the agent's `Target.Open` opened both phone vectors.

## Connected devices

`Подключённые устройства` (`AppRoutes.Connections`, `ConnectionsViewModel`) opens from the sheet,
from `Устройства` and from a new-connection push. `GET /api/users/me/connections` lists web sessions
and agents (kind, name, connected and last-active time, the shared-session badge). `Отключить` asks
first; `POST /api/users/me/connections/{id}/revoke` ends Backend access at once. A device with the
shared My ITMO session deletes that session itself on its next call; otherwise only an ITMO.ID
sign-out ends it (which also signs the phone out), and the dialog says so.

Backend pushes `DEVICE_CONNECTED_PAYLOAD` after every connection and
`SPORT_AGENT_BOOKED_PAYLOAD` when an agent booked a lesson
([notifications](notifications.md)).

## iOS

- `WebLoginSheet` (`iosApp/Sources/Features/WebLogin/`) is SwiftUI over the
  same `WebLoginViewModel` ([iOS app](../ios.md#data-sharing)); the demo
  refuses the route.
- Scanner: VisionKit `DataScannerViewController`, QR only; unlike Android it
  needs the camera (`NSCameraUsageDescription`). A refused camera or no
  scanner (simulator) falls back to typing.
- A fresh `SavedStateHandle` (`WebLoginIosParameters`) per open; debug
  fixture `-itmoWebLoginFixture` (`WebLoginIosFixture`, no Backend).
