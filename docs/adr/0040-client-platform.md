# 0040 Connections become a platform: registered clients, grants chosen on the phone

**Status.** Accepted (2026-11-27); step 1 is being built. Amends [connections](../features/connections.md)
and Backend's `docs/contracts/connections.md`.

## Context

Today the phone connects three hard-coded client types (`agent`, `mcp`, `web`). An agent or MCP server gets its own
ITMO.ID session, signed in on the phone in an isolated browser, labelled on id.itmo.ru/activity and ended from
«Устройства»; the session's refresh token reaches the client sealed with HPKE to the client's key, so Backend never
sees it. What a client gets is fixed by its type.

Two needs break that:

- **Choice of credential.** itmo-mcp needs BARS, which signs in only through the SSO cookie (ADR 0012); the refresh
  token reaches only my.itmo and lapses after about 30 days unused, while the session lives 90 days. The isolated
  sign-in already ends with the session's own `KEYCLOAK_IDENTITY` (a JWT with `sid` and `exp` = sign-in + 90 days),
  which the phone throws away. The cookie signs in to any ITMO service as the user: my.itmo, BARS, ISU, ITMO.ID.
- **The owner's other services.** itmo-instapick (a hosted Spring app whose users paste my.itmo refresh tokens for
  the stable and the dev ITMO clients) should instead show a QR, and the user approves on the phone what it gets.
  Each such service must not need a Backend or app change of its own.

## Decision

### 1. A registry of clients

The registry lists only the owner's own services and is maintained by the owner alone. It lives in Backend's
database from the start: tables `connection_clients` (one row per client app), `connection_client_grants` (what it
may request) and `connection_client_keys` (public keys of confidential clients, several at once for rotation; a public
key is not a secret). A Flyway migration seeds today's three types; the owner changes rows through admin routes
(`/api/admin/connection-clients/**`, admin role, audited like the other admin actions) and later the web cabinet. No
developer self-service. Backend caches the registry in memory and reloads it on every admin change. One row per
client app:

| Field | Meaning |
|---|---|
| `id` | stable slug: `itmo-widgets-agent`, `itmo-mcp`, `web`, `itmo-instapick` |
| `name`, `homepage`, `iconUrl` | what the phone shows |
| `deployment` | `self_hosted` (runs on the user's machine, one user) or `hosted` (a service holding many users' data) |
| `auth` | `public` (no secret: CLI, MCP, desktop) or `confidential` (proves itself with its key) |
| `status` | `active` or `suspended` |
| `grants` | the [grants](#2-grants) it may request: per grant, required or not, on by default or not, and its `purpose` |
| `maxPerUser`, `returnUrl` | live connections per user; where the phone sends the user after a same-device approval |

- Only registered clients can pair. Today's three types are the first three entries; `client: agent|mcp|web` on the
  wire maps to them.
- A `confidential` client signs challenge creation and the claim with a short JWT (`EdDSA`, `aud` Backend, `jti`,
  60 s) checked against the public key in its entry; the private key stays on the client's server (itmo-instapick is
  closed source, so it qualifies). The card then says «Проверено». A `public` client cannot prove
  who it is: the card shows the registry's name with «Запущено на вашем компьютере?», as today.
- Setting `suspended` revokes all the client's connections in the same transaction; every owner's phone then ends their ITMO sessions
  through the existing reconciliation. Backend cannot end ITMO sessions itself.
- Opening the registry to other developers later (self-service, a `testing` status, terms) is a separate decision.

### 2. Grants

A grant is one thing the phone can hand over. The client requests a set; the registry caps it; the user chooses.

| Grant | What the client gets | Who makes it | Risk |
|---|---|---|---|
| `widgets:<scope>` | the connection token works on that Widgets scope (today's scopes) | Backend | low |
| `identity` | a Backend-signed JWT at the claim: `sub` ISU, name, `aud` client id, 5 min | Backend | low |
| `itmo:refresh:<oidcClient>` | the refresh token of the client's own ITMO.ID session for one ITMO OIDC client (`student-personal-cabinet`, the dev stand's client, ...) | phone | medium: that service in full, writes included |
| `itmo:sso` | the same session's `KEYCLOAK_IDENTITY` | phone | high: every ITMO service and ITMO.ID |

- Widgets grants keep their texts on Backend (ADR 0036). ITMO grants are code in the app (how to mint them, the ITMO
  OIDC clients it knows) and their texts and warnings are app strings: Backend must not be able to soften a warning.
- An app that does not know a requested ITMO grant shows it as «Недоступно в этой версии»; if the grant is required,
  it refuses with «Обновите приложение».
- Policy, enforced by the registry: `itmo:sso` only for `self_hosted` clients. A hosted service never gets the cookie;
  it would hold every user's whole account.
- All ITMO grants of one connection come from one ITMO.ID session: one sign-in on the phone, then silent authorizes
  for each OIDC client with the isolated store's cookie, every callback caught before it loads. The phone checks that
  each token and the cookie carry the same `sid` and the phone's ISU.

### 3. The card: the user picks what to send

The card lists the request as switches, grouped «ITMO.Widgets» and «ИТМО»:

- required grants: on and locked, marked «Нужно для работы»; declining them is «Отклонить»;
- optional low and medium grants: on;
- `itmo:sso`: off every time, with the warning under it; turning it on asks a confirmation dialog;
- then the screen lock, as today.

Each switch has three texts, from three owners:

| Text | Example for `itmo:sso` of itmo-mcp | Comes from | Why there |
|---|---|---|---|
| title and what it gives | «Полный доступ к ITMO.ID» / «Вход в любой сервис ИТМО от вашего имени: My ITMO, БАРС, ИСУ и сам ITMO.ID» | app strings | the same for every client; Backend cannot change it |
| warning (high risk only) | «Включайте, только если это ваше устройство и вы ему доверяете» | app strings | the same; Backend cannot soften it |
| purpose: why this client needs it, after «Зачем: » | «чтобы ИИ-ассистент видел баллы БАРС» | the registry entry's `purpose`, through `PairingPreview` | per client, changes without an app release; the owner writes it |

The client's own request carries no text: a `public` client could claim anything. The client says the same in its own
interface: `itmo-mcp pair` prints which grants it asks for and what stops working without each (without `itmo:sso`:
no BARS tools), and its tool list later names the grant a missing tool needs.

A renewal asks for nothing new and has no card; a renewal that requests new grants shows a card with only those.

### 4. Wire, protocol 2

```
CreateChallengeRequest {kind, clientId, keyCommitment, name, version?, platform?,
                        grants?: [{id, required?}], clientAssertion?}
PairingPreview += {app: ClientAppInfo, grants: GrantRequest[], verified: boolean}
ApprovePairingRequest {sealedBundle?, granted: string[], itmo?: {sid, expiresAt}}
PairingClaimResponse += {identity?: string}
```

The sealed bundle, protocol 2 (HPKE, `info` and the 16 KiB limit unchanged):

```
{v: 2, challengeId, kind, clientId, isu, issuedAt,
 itmo?: {sid, expiresAt, items: [{grant: "itmo:refresh:student-personal-cabinet", value, expiresAt},
                                 {grant: "itmo:sso", value, expiresAt}]}}
```

Clients ignore unknown items. `granted` is the phone's statement of what it sealed (only requested grants, 400
otherwise); Backend stores it on the connection and shows it in `ConnectionInfo`, a label it cannot verify. Backend
still never sees an ITMO credential: the ciphertext is relayed once and deleted at the claim.

### 5. Same-device sign-in

A mobile client opens the pairing link itself; the App Link opens ITMO.Widgets, the user approves, the phone opens the
client's `returnUrl` from the registry (never from the link), and the client claims as usual. No new mechanism.

### 6. Revocation

- A whole connection: «Отключить», as today (Backend at once, then the phone ends the `sid`).
- Widgets grants: switches on the client screen, at once.
- ITMO grants: only by ending the session; «Забрать доступ к ИТМО» (on any client with an open ITMO sign-in) ends
  the client's `sid` and calls the phone-only
  `POST /api/users/me/connections/{id}/itmo-closed`. The connection stays; a renewal brings the ITMO grants back.
  Taking back the cookie alone is impossible, since every ITMO item shares the session.
- A whole client app: suspension in the registry.

### 7. Storage and logs

Phone: ITMO items in memory only between the sign-in and the seal, redacted `toString`, never logged; the isolated
store is deleted as today. Backend: the registry tables, the granted list per connection, no credential. Clients: their
business; first-party ones keep them in a 0600 state file and wipe them after logout on `unpair` or a 401.

## Compatibility

- Protocol 1 requests (`client`, `scopes`, no `grants`) map to `{widgets scopes, itmo:refresh:student-personal-cabinet
  required}` for `agent` and `mcp` and get the v1 bundle byte for byte. itmo-widgets-agent needs no change.
- Released apps open with protocol 1; Backend answers a challenge that needs protocol 2 with 409 `update_required`.
  The app opens with `?protocol=2`.
- Backend lands first, with the migration seeding today's three types.

## Rollout

1. Backend registry tables seeded with the three types, admin routes, grants, protocol 2; app card with grant switches, the three texts and
   `itmo:sso`; itmo-mcp asks for `itmo:sso` with `--itmo-cookie` and gets BARS.
2. `identity` (needs a Backend signing key on the server), confidential clients, same-device return; the first hosted
   service (itmo-instapick) as an entry.

## Rejected

- Hand-coding each new client type and credential: every new service would be a Backend and app release, and the
  card's texts would live in three places.
- Sharing the phone's own ITMO.ID session (no password on the phone): the client could not be labelled or ended
  without signing the phone out. Each connection keeps its own session; the system password autofill in the isolated
  page makes the sign-in short.
- The phone sealing new grants later to a stored client key: Backend would hand the phone that key, so a malicious
  Backend could swap it and receive the credentials. New ITMO grants come only through a client-started renewal.
- Widgets scopes carrying ITMO credentials: a scope turns off at once, a credential cannot be taken back.

## Risks and open questions

- **ITMO's position.** ITMO.ID is ITMO's identity provider; even for the owner's own services, handing its sessions
  to other programs is not sanctioned by ITMO. The honest session label keeps that visible to ITMO.
- **Hosted services.** A hosted service holding many users' refresh tokens is a target; `itmo:sso` is barred for it.
- **Stolen client state.** With `itmo:sso`, a leaked state file is the whole ITMO account for up to 90 days or until
  revoked.
- **To check on devices.** The cookie is in the isolated store when the callback is caught (Android profile cookie
  manager, iOS `nonPersistent()`); silent authorizes for a second OIDC client from that store; whether the cookie
  outlives 30 days without use.
