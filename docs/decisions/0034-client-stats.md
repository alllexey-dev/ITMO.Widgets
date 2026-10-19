# 0034 One client header and one activity table for client statistics

**Status.** Proposed (2026-10-19).

**Context.** The owner wants one place that answers "which app versions are in
use, how many Android and iOS installs are active, and how many web sessions and
agents are connected". Today eight separate mechanisms each answer part of it:

1. **`X-App-Version` guessed onto a device row.** The shared client sends
   `<version> (<build>); <platform>; <distribution>` on every Backend call
   (`shared/backend-client/.../client/ClientVersion.kt`, `.../http/BackendHttp.kt`).
   Backend `ClientVersionFilter` records it only for bearer requests. A request
   names no device, so `DeviceRepository.recordClientVersion` writes only when the
   user has exactly one device of that platform. Two phones of one platform
   record nothing. An Android report without an FCM row is dropped. An iOS
   report without one creates a fake device `iOS, no push token`, shared by every
   pushless iPhone of the user and removable only with the account
   (Backend `docs/contracts/app-version.md`, Devices without push). Only the last
   build per device is kept, so adoption over time cannot be rebuilt. The
   in-memory dedup map (`ClientVersionService`, 1 h, wiped at 10 000 users) is
   per instance.
2. **FCM registration as activity.** `devices.last_login` is set by
   `POST /api/device/register-device`. The app re-registers only when the token,
   owner or build changes (`DefaultFcmTokenSync`), so a phone used daily looks
   inactive. Rows go only on unregister, an FCM `UNREGISTERED` send error or
   account deletion; no retention.
3. **Dashboard counts.** `activeDevices7d/30d` count `last_login` (registration).
   The client-versions view uses `GREATEST(last_login, app_version_seen_at)`, so
   the two disagree. `devicesByPlatform` counts every row ever and no page shows
   it. The daily series keeps only each device's latest day.
4. **Admin "last seen"** is `max(devices.last_login, web_sessions.last_seen_at)`.
   It ignores the build reports and agent polls.
5. **Web.** The web app sends no version. `web_sessions.user_agent` keeps the raw
   browser string; the dashboard counts sessions created (sign-ins), not active
   browsers.
6. **Agent.** It sends `User-Agent: itmo-widgets-agent/<version>`, but Backend
   reads a version only at pairing and keeps it on the challenge for one day.
   `agent_tokens` has no version, OS or architecture, and no admin view counts
   agents.
7. **`ItmoClientPolicy`** counts bearer tokens per `azp` in memory and logs the
   counts hourly. They are lost on restart, count requests rather than installs,
   and Android and iOS share one client id.
8. **Outside Backend.** The srvscripts console guesses "app/browser/script" from
   Caddy access-log user agents per IP. Ad-hoc SQL runs through
   `platform db itmowidgets prod`. GitHub release downloads are counted by hand.

**Decision.** Every client names itself in one header. Backend keeps one row per
client instance and a daily aggregate. The owner reads both through one admin
route.

- **Header.** Every Backend request carries
  `X-Client: kind=<kind>; version=<name>; build=<build>; channel=<channel>; os=<os>; install=<id>`.
  - Printable ASCII, at most 256 characters; unknown keys are ignored; a
    malformed header is ignored and never fails the request.
  - `kind` is `android`, `ios`, `web` or `agent`.
  - `channel` takes today's distribution values (`github`, `play`, `appstore`,
    `testflight`, `sideload`, `dev`) plus `web` and `selfhosted`.
  - `os` is a major version only: Android API level, iOS major, or the agent's
    `GOOS-GOARCH`. Web sends none.
  - `install` is sent only by the apps. It is a random UUID generated on first
    use and stored in the app's settings. A new one is generated after sign-out
    and on reinstall. It is never derived from hardware or advertising
    identifiers.
  - The web sends its build commit as `version`. The agent keeps its
    `User-Agent` and adds the header.
- **Identity without guessing.** The instance is the install id for app bearer
  requests, `web_sessions.id` for a web session, and `agent_tokens.id` for an
  agent token. A row is never matched to a `devices` row by platform.
- **Storage.** One new table, plus one table for history:
  - `client_instances (kind, instance_id)` is unique, with `user_id`,
    `version`, `build`, `channel`, `os`, `first_seen_at` and `last_seen_at`.
    `ClientActivityFilter` replaces `ClientVersionFilter` after the security
    chain. It upserts a row only when a field changed or `last_seen_at` is older
    than an hour, so `last_seen_at` has hour precision.
  - A nightly job writes `client_daily (day, kind, version, channel, os)` with
    the number of instances seen in the last 1, 7 and 30 days. It holds no user
    or instance ids and is kept indefinitely; it is the version-adoption
    history.
  - Instance rows are deleted 180 days after `last_seen_at`, and with the
    account (account-deletion runbook).
  - Anonymous requests are not recorded, so installs without the
    custom-services opt-in stay invisible, as they are today.
- **Minimal data.** No IP, no raw user agent, no device model, no request
  paths. The device name stays where it is today, in `devices`, for the push
  and connections UI only.
- **Reading.** `GET /api/admin/system/clients` returns, per kind, the active
  instances for 1/7/30 days broken down by version, channel and os, plus the
  `client_daily` series for a chart. It replaces
  `GET /api/admin/system/client-versions` and the dashboard's `activeDevices*`
  and `devicesByPlatform`. Admin "last seen" becomes the user's latest
  `client_instances.last_seen_at`. A read-only SQL view over both tables serves
  the srvscripts console.
- **`devices` goes back to push.** It keeps tokens, names and alerts only. The
  build columns and the pushless iOS rows end with the transition below.

**Why.** The header rides on calls the clients already make: no background
heartbeat, no extra battery or network, and no new consent beyond the
custom-services opt-in that already gates every Backend call. The install id
fixes the root crutch, requests that name no device, with a random value and
not a hardware identifier. The credential row already identifies web sessions
and agents, so they need no id of their own. Splitting live rows (bounded,
deleted after 180 days) from anonymous daily aggregates gives history without
keeping personal data longer.
- Rejected: Firebase Analytics or another SDK (a third party, a new privacy
  surface, and nothing for web or agent).
- Rejected: parsing Caddy access logs (no identity behind NAT, and request
  logs are the wrong place for user data).
- Rejected: a heartbeat endpoint (an extra scheduled call per device, while the
  header is free).
- Rejected: extending `X-App-Version` in place (no key/value room for `install`
  and `os` without breaking its strict parser).

**Consequence.** Migration, in order. Backend goes first and stays additive,
following Backend's wire compatibility rule:
1. **Backend.**
   - Migration `client_instances` + `client_daily`.
   - `ClientActivityFilter` reading `X-Client`. Until `app.minimum` reaches the
     release that sends it, it also maps a legacy `X-App-Version` to `kind`,
     `version`, `build` and `channel`, keyed by the existing device guess.
   - The nightly job and the 180-day retention.
   - `GET /api/admin/system/clients`, plus the account-deletion runbook and
     docs.
   - `ClientVersionFilter` keeps writing `devices.app_*` during the transition.
2. **App (this repository).**
   - `shared/backend-client` builds `X-Client` from `ClientVersion` and an
     `InstallId` held in settings, regenerated on sign-out.
   - The same for Android, iOS and the Swift copy in the notification extension
     (`PushHandler.swift`).
   - The demo session sends nothing, as it calls no Backend.
3. **Web.**
   - The client sends `X-Client: kind=web; version=<commit>; channel=web`.
   - The System page shows the new route in place of `ClientVersionsCard`, and
     the dashboard tiles move to it.
4. **Agent.** It sends `X-Client: kind=agent; version=<ldflags version>; channel=selfhosted; os=<GOOS-GOARCH>`
   on every request.
5. **Cleanup** (a later Backend release, once `app.minimum` sends `X-Client`).
   - Stop writing `devices.app_*` and creating pushless iOS rows; drop the
     columns in a later expand-only step.
   - Remove `ClientVersionService` and the dashboard's registration-based
     counts.
   - srvscripts needs no change. The console may later show the SQL view.

**Open questions.**
- Should installs that never opted in to custom services be counted through an
  anonymous ping without a user? The proposal: no.
- Is a new install id after sign-out right? It undercounts nothing, but a
  re-signed-in phone counts as a new instance for the day.
- Retention: 180 days for instance rows, aggregates kept indefinitely?
- Web: record a browser family and major version (derived from `User-Agent`)
  or nothing beyond the web build?
- Should GitHub release downloads and Play statistics join this view? That
  needs API tokens on Backend.

**Revisit when.** A client cannot send headers (a widget-only surface), or the
owner needs per-feature usage, which this record deliberately does not collect.
