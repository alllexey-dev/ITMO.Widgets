# Connected clients and «Устройства»

Design: `vibe/plans/connected-clients-plan.md` and `vibe/plans/connected-clients-ux.md` (local). Backend contract:
`../itmo-widgets-backend/docs/contracts/connections.md`. Code: `feature/connections` of `:shared:feature-account`
(domain, data, ViewModels, Compose screens), `iosApp/Sources/Features/Connections/` (SwiftUI over the same
ViewModels).

A connection is a client the phone approved: an agent (`itmo-widgets-agent`), an MCP server (`itmo-mcp`) or the web
version. Agents and MCP servers get their own ITMO.ID session, signed in on the phone; the web gets none. The phone
never hands out its own session or its ITMO.ID cookie.

## Entry points

- Профиль: the row «Устройства» before «Настройки» with the number of installs and live clients and a warning pill
  when a client's ITMO sign-in ends within 7 days or is closed (`DevicesOverview`, `MeUiState.devices`).
- `AppRoutes.Devices` (screen), `AppRoutes.Connection(id)` (screen), `AppRoutes.Pairing(link)` (sheet). The demo
  session refuses all three (`ShellGate`).
- The push `DEVICE_CONNECTED_PAYLOAD` opens «Устройства»; the reminder notification opens the client
  (`NotificationDestination.Connection`, `ACTION_OPEN_CONNECTION` with `EXTRA_CONNECTION`).
- App Links `https://<host>/pair#1.<CODE>.<pk>.<s>` (the alpha's `#v1.` is read too) open the pairing sheet.
- Settings has no row: «Сервер ITMO.Widgets» lost its «Устройства» page.

## «Устройства» (`DevicesViewModel`)

Two sources, joined by `sid`:

- Backend `GET /api/users/me/devices` (`DevicesRepository`, last answer kept in memory, shown at once): installs and
  connections. Revoked connections whose ITMO session is still open come with `revokedAt` and are hidden.
- ITMO.ID `GET /api/profile/sessions` through MyItmoApi's `ItmoIdSessions` (`ItmoLoginsRepository`) with a `profile`
  token that the phone's own SSO cookie (`ItmoIdCookies`) gets silently. No usable cookie: the section is a note.

| Section | Rows |
|---|---|
| connect | «Подключить устройство», or the server gate when the opt-in is off |
| Этот телефон | the current install, named by `ThisPhone` (Android: the user-visible `Settings.Global.DEVICE_NAME`, kept on the phone; iOS: the model); its sheet has no button |
| Мои устройства | other installs with a device registration (a name), or whose `sid` ITMO.ID still lists; «Вход в ИТМО закончен» when it no longer does. The install id is new after every sign-in, so earlier sign-ins of a phone leave nameless rows, which stay hidden |
| Подключённые | live clients, a pill for a sign-in ending within 7 days or closed |
| Другие входы в ITMO.ID | sessions that match no install, no client (live or revoked) and not this phone |
| switch | «Выглядеть как обычный браузер» (`SignInLabel`) |

- Install sheet «Выйти на этом устройстве»: ends the install's `sid` at ITMO.ID (that app signs out at its next
  refresh), then `POST /api/users/me/installs/{id}/sign-out` drops its push.
- Session sheet «Завершить вход» and «Завершить все другие входы»: `DELETE /api/account/sessions/{sid}`.
- Reconciliation (plan 6.2): after every load the phone ends the open sessions of revoked connections and reports
  `POST /api/users/me/connections/{id}/itmo-ended`; a `sid` ITMO.ID no longer lists is reported too.
- Профиль counts exactly the rows «Этот телефон», «Мои устройства» and «Подключённые» (`Devices.shownCount`, with the
  last ITMO.ID listing of this screen); not «Другие входы в ITMO.ID», revoked clients or hidden installs.
- After an action here, or a change from another screen (pairing, renewal, revoke), both sources load again at once
  and 3 s later: ITMO.ID and Backend show changes a moment late.
- Labels, IP addresses and ITMO times stay on the phone; nothing from ITMO.ID is sent to Backend or logged.

## A client (`ConnectedClientViewModel`)

- «Вход в ИТМО»: until when, a warning within 7 days, an error when closed; «Обновить вход» appears in the last 14
  days and opens the renewal sheet with the client's command (`itmo-widgets-agent renew`, `itmo-mcp renew`) and
  «Сканировать QR-код»; the scanned `RENEW` link continues in the pairing sheet.
- «Доступ»: Backend's `ScopeGrant`s as switches. Off applies at once; on asks for the screen lock first; the last
  scope stays on. The web has no switches.
- «Отключить»: `revoke`, then the phone ends the client's `sid` and reports `itmo-ended`; a failure is left to the
  reconciliation. Without a cookie the dialog says the sign-in ends when the device next calls.

## Pairing (`PairingViewModel`)

1. The link (scanner, paste or App Link) is parsed by `PairLink`; `open` returns the request, shown only when
   Backend's `keyCommitment` equals `SHA-256("iw-pair-commit-v1" || pk || s)` of the link.
2. The card shows the client, the six-digit code `SHA-256("iw-pair-sas-v1" || challengeId || pk || s)` (first 20 bits
   mod 10^6) and the requested scopes as switches (narrowing only). A `RENEW` of an agent or MCP has no card: the
   code stays over the sign-in page.
3. Screen lock (`DeviceUnlock`: `BiometricPrompt`, LocalAuthentication).
4. Agents and MCP: the official page (`ItmoIdClient.loginUrl`, PKCE) in an isolated browser: Android an
   `androidx.webkit` profile `itmo-client-sign-in` deleted afterwards (`MULTI_PROFILE` required, else «Обновите
   Android System WebView»), iOS `WKWebsiteDataStore.nonPersistent()`. Every main-frame navigation goes through
   `onSignInNavigation`; the callback is caught before it loads and the code exchanged by `ClientSignInImpl`. Tokens
   of another ISU end at once (`logout`) and say «Это другой аккаунт ИТМО». The session's end comes from ITMO.ID,
   else the sign-in plus 90 days.
5. The bundle `{v, challengeId, kind, isu, issuedAt, itmo: {sid, expiresAt, refreshToken, refreshExpiresAt}}` is
   sealed with HPKE (X25519, HKDF-SHA256, ChaCha20-Poly1305; Tink, CryptoKit) to `pk` with
   `info = "itmo-widgets pair v1" || 0 || challengeId || kind || 0 || s`, as the agent and itmo-mcp open it
   (Backend's contract text omits the trailing `0 || s`). Approve sends the bundle, the chosen scopes (`CONNECT`
   only) and `{sid, expiresAt}`. A failed approval ends the new session; a renewal ends the old `sid` after.
6. The web is approved without a sign-in and without a bundle.

The link, the PKCE secrets and the client's tokens live only in memory; the saved state keeps the pasted text.

## Sign-in label

`SignInLabel` (`SignInLabelPreferences`, on by default) decides the User-Agent of new sign-ins, which fixes the row on
id.itmo.ru/activity: on, an ordinary browser (`ItmoSessionLabel.browserUserAgent(platform)`) for clients and the
WebView's own UA for the app; off, `ItmoSessionLabel.botUserAgent(...)` for clients and the WebView's UA with
` ITMO Widgets bot/<version>` for the app (`appSignInUserAgent`).

## Reminder

`ConnectionsBellSource` (a `BellSource`) shows a bell item for every client whose sign-in ends within 7 days or is
closed and, once per sign-in end, a local notification on the `connections` channel. It runs when the bell refreshes
(Сегодня), so the reminder comes at the first refresh after the day; there is no background job of its own.

## Texts

Scope titles and descriptions come from Backend (`ScopeInfo`, by `Accept-Language`, which every Backend request
carries as `APP_LANGUAGE`): an exception to ADR 0028, so a new scope needs no app release. Everything else is in
`strings_connections.xml`.

## Tests

- Core 2: `ConnectionsRequestTest`, `ConnectionsContractTest`, `ConnectionsVendoredFixturesTest`,
  `AcceptLanguageHeaderTest`, conformance.
- Shared: `PairingViewModelTest`, `DevicesViewModelTest`, `ConnectedClientViewModelTest`, `PairingRepositoryImplTest`,
  `ConnectionsReminderTest`, `PairingCryptoTest`, `PairingSealerTest`, `TinkHpkeSenderTest` (CryptoKit interop).
- JVM screenshots `DevicesScreen_*`, `ConnectionScreen_*`, `PairingSheet_*` from `ConnectionsPreviews.kt`.
- Not covered by automated tests: the isolated WebView sign-in against the real id.itmo.ru (checks C1, C6, C7 of the
  plan run on devices).
