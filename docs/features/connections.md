# Connected clients and «Устройства»

Backend contract: `../itmo-widgets-backend/docs/contracts/connections.md`. Code: `feature/connections` of
`:shared:feature-account` (domain, data, ViewModels, Compose screens), `iosApp/Sources/Features/Connections/`
(SwiftUI over the same ViewModels).

A connection is a client the phone approved: an agent (`itmo-widgets-agent`), an MCP server (`itmo-mcp`) or the web
version, each an entry of Backend's client registry ([ADR 0040](../adr/0040-client-platform.md)). Agents and MCP
servers can get their own ITMO.ID session, signed in on the phone; the web gets none. The phone never hands out its
own session or its own ITMO.ID cookie; what a client gets of its own session is the user's choice of grants.

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
- A client without an ITMO sign-in (paired without ITMO grants, or after «Забрать доступ к ИТМО»; Backend sends no
  `itmo` for a live connection only then, `Connection.hasItmoSignIn`): «Входа в ИТМО нет» in the neutral tone and
  «Войти в ИТМО», which opens the same renewal sheet. No bell item, no pill, and «Отключить» says only that the
  ITMO.Widgets access ends.
- «Полный доступ к ITMO.ID» (`grant_sso_title`, «БАРС, ИСУ и другие сервисы ИТМО») in the same group while
  `ConnectionInfo.itmoGrants` has `itmo:sso`.
- «Забрать доступ к ИТМО» (danger row, last in the group) while the sign-in is open, with or without full access:
  its dialog names what the client loses (My ITMO, and full access when held) and that the ITMO.Widgets access stays.
  The phone ends the client's `sid` with its own cookie, as «Отключить» does, then
  `POST /api/users/me/connections/{id}/itmo-closed` and a reload. Taking the cookie alone back is impossible: every
  ITMO item shares the session. Without the phone's cookie the end fails, the error shows and nothing is reported.
- «Доступ»: Backend's `ScopeGrant`s as switches. Off applies at once; on asks for the screen lock first; the last
  scope stays on. The web has no switches.
- «Отключить»: `revoke`, then the phone ends the client's `sid` and reports `itmo-ended`; a failure is left to the
  reconciliation. Without a cookie the dialog says the sign-in ends when the device next calls.

## Pairing (`PairingViewModel`)

The app opens every code with `?protocol=2`, so Backend answers both protocols ([ADR 0040](../adr/0040-client-platform.md)):
a protocol-1 challenge (a client that sent `scopes`: today's agent, itmo-mcp without `--itmo-cookie`, the web) gets the
card with scope switches and the v1 bundle byte for byte; a protocol-2 one (a client that sent `grants`) gets the
grant card and the v2 bundle. The preview's `protocol` decides; a newer protocol, or a protocol-2 request that
requires an ITMO grant this version cannot mint, is «Обновите приложение, чтобы подключить это устройство».

1. The link (scanner, paste or App Link) is parsed by `PairLink`; `open` returns the request, shown only when
   Backend's `keyCommitment` equals `SHA-256("iw-pair-commit-v1" || pk || s)` of the link.
2. The card shows the client (the web: browser and OS; an agent or MCP: name, version, platform), on a `CONNECT` the
   caution «Этот QR-код на вашем экране?» (approve only a QR or link the user opened on their own device), and the
   requested scopes as switches (narrowing only). A `RENEW` opens only for the owner's own live connection, so it has
   no caution, and a `RENEW` of an agent or MCP has no card at all. There is no code to compare ([why](#why-no-code)).

   Protocol 2: the grants as switches in registry order, grouped «ITMO.Widgets» (`widgets:*`) and «ИТМО» (`itmo:*`), see
   [Grants](#grants). Approving needs a Widgets grant on a new connection. A `RENEW` shows a card only when a requested
   ITMO grant is not held yet (`granted: false`), with only those; the held ones go along without a switch.
3. Screen lock (`DeviceUnlock`: `BiometricPrompt`, LocalAuthentication). iOS answers once the app is active again: a
   WKWebView that comes up while the Face ID UI still holds the app inactive never loads or takes a touch. The page
   focuses its e-mail field at once; the pairing sheet stays under it, so its `.content` height must not count the
   keyboard (`ItmoSheet` ignores the keyboard safe area, #613), else the sheet re-lays out without end and the app
   hangs.
4. Agents and MCP (protocol 2: only when an ITMO grant is on): the official page (`ItmoIdClient.loginUrl`, PKCE) in an
   isolated browser: Android an `androidx.webkit` profile `itmo-client-sign-in` deleted afterwards (`MULTI_PROFILE`
   required, else «Обновите Android System WebView»), iOS `WKWebsiteDataStore.nonPersistent()`. Every main-frame
   navigation goes through `isSignInCallback`; the callback is caught before it loads and goes to `onSignInCallback`,
   where `ClientSignInImpl` exchanges the code. Tokens of another ISU end at once (`logout`) and say «Это другой
   аккаунт ИТМО». The session's end comes from ITMO.ID, else the sign-in plus 90 days.

   With `itmo:sso` on, the host reads `KEYCLOAK_IDENTITY` from the isolated store when it catches the callback,
   before the store goes (`SigningIn.readIdentity`): Android the profile's `getCookieManager().getCookie(
   "https://id.itmo.ru/auth/realms/itmo/")` (`KeycloakIdentityCookie.fromCookieHeader`), iOS the store's
   `httpCookieStore.getAllCookies` by that name on id.itmo.ru (`ItmoIdentityCookie`). `ClientSignInImpl` decodes its
   JWT payload without checking the signature (`IdTokenClaims`): its `sid` (`session_state` of older tokens) must be
   the token response's session, its `exp` is when it ends. A missing or foreign cookie ends the new session and says
   `pair_identity_missing`: the user granted it, so pairing without it would be wrong.
5. Protocol 1: the bundle `{v, challengeId, kind, isu, issuedAt, itmo: {sid, expiresAt, refreshToken,
   refreshExpiresAt}}` (`PairingBundle`). Protocol 2: `{v: 2, challengeId, kind, clientId, isu, issuedAt, itmo: {sid,
   expiresAt, items: [{grant, value, expiresAt}]}}` (`PairingBundleV2`), `items` exactly the granted ITMO grants in
   request order: the refresh token with its expiry, the cookie with its `exp`. With only `itmo:sso` the refresh token
   of the exchange is dropped unused, never logged out (that would end the session the cookie belongs to). Either is
   sealed with HPKE (X25519, HKDF-SHA256, ChaCha20-Poly1305; Tink, CryptoKit) to `pk` with
   `info = "itmo-widgets pair v1" || 0 || challengeId || kind || 0 || s`, as the agent and itmo-mcp open it
   (Backend's contract text omits the trailing `0 || s`). Approve sends the bundle, `{sid, expiresAt}` and the chosen
   scopes (protocol 1, `CONNECT` only) or `granted` (protocol 2, every required grant included). A failed approval
   ends the new session; a renewal ends the old `sid` after.
6. The web, and a protocol-2 client with every ITMO grant off, are approved without a sign-in and without a bundle.

### Grants

| Grant | Switch | Texts |
|---|---|---|
| `widgets:<scope>` | on; on and locked when required | Backend's `title` and `description` |
| `itmo:refresh:student-personal-cabinet` | on; on and locked when required | «Вход в My ITMO» (`grant_refresh_*`) |
| `itmo:sso` | off every time; turning it on asks «Дать полный доступ к ITMO.ID?» | «Полный доступ к ITMO.ID», the warning in the error colour (`grant_sso_*`) |
| another `itmo:*` | off and locked, «Недоступно в этой версии»; required: the pairing is refused | its id |

- A required grant says «Нужно для работы»; declining it is «Отмена».
- A grant with the registry's `purpose` says «Зачем: …» under it.
- ITMO grant texts and the warning are app strings, so Backend cannot soften them; Widgets texts and the purpose
  change without a release.

The link, the PKCE secrets, the client's tokens and its `KEYCLOAK_IDENTITY` live only in memory, with redacted
`toString`s, and are never logged; the saved state keeps the pasted text.

### Why no code

Both screens used to show a six-digit code (`SHA-256("iw-pair-sas-v1" || challengeId || pk || s)`) to compare
before approving. It caught only one case: the phone opening a QR or link other than the one on the genuine screen in
front of the user (a link someone sent). It did not stop phishing: a page or CLI the attacker controls shows the
attacker's QR and the matching code. Nor was it needed against Backend swapping the client's key: the phone checks
`keyCommitment` against the scanned link. What protects a pairing now:

- the key commitment check (Backend cannot substitute `pk`);
- a short code life (2 minutes until opened, 10 after) and the first opener owning the code;
- the caution on a new connection's card and the screen lock before every approval;
- `DEVICE_CONNECTED_PAYLOAD` on all the user's devices after every new connection, and «Отключить» in «Устройства».

Older app, web, agent and itmo-mcp builds may still show a code; nothing reads it, and the wire is unchanged.

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

Scope titles and descriptions come from Backend (`ScopeInfo` and the `widgets:*` entries of `GrantRequest`, by
`Accept-Language`, which every Backend request carries as `APP_LANGUAGE`), as does a grant's `purpose` from the
registry: an exception to ADR 0028, so a new scope or client needs no app release. Everything else, the ITMO grants'
texts included, is in `strings_connections.xml`.

## Tests

- Core 2: `ConnectionsRequestTest`, `ConnectionsContractTest`, `ConnectionsVendoredFixturesTest`,
  `AcceptLanguageHeaderTest`, conformance.
- Core 2 conformance runs ahead of Backend's snapshot for protocol 2: `PendingBackendFields`,
  `PendingBackendRoutes` (`itmo-closed`) and `PendingBackendQueries` (`?protocol=2`) fail once a re-sync brings them.
- Shared: `PairingViewModelTest` (grant card, `itmo:sso` confirmation, bundle v2 items, no sign-in without ITMO
  grants, renewal cards), `DevicesViewModelTest`, `ConnectedClientViewModelTest` («Забрать»),
  `PairingRepositoryImplTest`, `ClientSignInImplTest` (the identity cookie against the token response's `sid`),
  `IdTokenClaimsTest`, `ConnectionsReminderTest`, `PairingCryptoTest`, `PairingSealerTest` (v1 unchanged, v2),
  `TinkHpkeSenderTest` (CryptoKit interop).
- JVM screenshots `DevicesScreen_*`, `ConnectionScreen_*`, `PairingSheet_*` from `ConnectionsPreviews.kt`; iOS
  `ConnectionsSnapshotTests` (`pair-grants`, `mcp-full-access`), `PairingTests` (the identity cookie among a
  store's cookies).
- Not covered by automated tests: the isolated WebView sign-in against the real id.itmo.ru (checks C1, C6, C7 of the
  plan run on devices); whether `KEYCLOAK_IDENTITY` is in the isolated store when the callback is caught (Android
  profile cookie manager, iOS `nonPersistent()`) and its `sid` equals the token response's; the client opening a v2
  bundle and using the cookie (itmo-mcp); «Забрать» ending the session on the real ITMO.ID.
