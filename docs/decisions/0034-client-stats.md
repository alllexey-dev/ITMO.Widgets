# 0034 One client header and one activity table for client statistics

**Status.** Accepted (2026-10-19).

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
client instance of a signed-in user and a daily aggregate. The owner reads both
through the admin API.

- **Header.** Every Backend request carries `X-Client`, a list of `key=value`
  pairs separated by `;`, for example
  `kind=android; version=2.3.0; build=20300; channel=play; os=36; install=<uuid>`.
  - Grammar: keys are `[a-z]+`, values 1-64 characters of `[A-Za-z0-9._/+-]`;
    the header is at most 512 characters. Unknown keys and invalid pairs are
    ignored, the first of duplicate keys wins, and a header without `kind` or
    `version` is ignored. A bad header never fails the request.
  - Keys:

    | Key | Who sends it | Value |
    |---|---|---|
    | `kind` | everyone | `android`, `ios`, `web`, `agent` |
    | `version` | everyone | the version name as built (`2.3.0`, the agent's release, the web build) |
    | `build` | apps | Android `versionCode`, iOS `CFBundleVersion` |
    | `channel` | everyone | an open token `[a-z0-9-]{1,16}` |
    | `source` | apps, agent | the building repository `owner/name`, or `local` |
    | `commit` | everyone | the git commit of the build |
    | `base` | custom builds | the upstream version the build is based on |
    | `os` | apps, agent | Android API level, iOS major version, `GOOS-GOARCH` |
    | `browser` | web | `<family>/<major>` (`chrome/131`, `safari/18`, `firefox/133`, `edge/131`, `other`) |
    | `install` | apps | a random UUID per install |

  - `channel` keeps today's distributions (`github`, `play`, `appstore`,
    `testflight`, `sideload`, `dev`) plus `web`, and stays open for the
    build-identity values of unofficial builds (forks, self-built apps and
    agents, `custom`); Backend stores unknown values as they come. The agent's
    release workflow and published image say `github`; any other agent build
    says `custom`.
  - `source`, `commit`, `base` and the custom `channel` come from the build
    identity the build scripts put into Android `BuildConfig` and the iOS
    `Info.plist`, and into the agent through `-ldflags`; the header only
    forwards them. A plain `go build` of the agent defaults to
    `channel=custom; source=local`.
  - `install` is generated on first use and kept in the app's settings, and a
    new one is generated after sign-out and on reinstall. It is never derived
    from hardware or advertising identifiers.
- **Official and custom builds.** A client is custom when its `channel` is not
  one of the official values above or its `source` is set to anything but
  `alllexey-dev/ITMO.Widgets` (agent: `alllexey-dev/itmo-widgets-agent`).
  Admin views show custom builds apart from official ones and group them by
  `base` (their upstream version; `version` when `base` is missing), then by
  `source`.
- **Identity without guessing.** The credential decides the instance and, for
  web and agents, the kind: an app bearer request is keyed by its `install`
  (kind `android` or `ios` from the header), a web session by
  `web_sessions.id` (kind `web`), an agent token by `agent_tokens.id` (kind
  `agent`). A row is never matched to a `devices` row by platform.
- **Tied to the user.** Every row belongs to the signed-in user who sent it.
  Anonymous requests are not recorded, so installs without the custom-services
  opt-in stay invisible.
- **Storage.**
  - `client_instances (kind, instance_id)` is unique, with `user_id`
    (`ON DELETE CASCADE`), the header fields and `first_seen_at`,
    `last_seen_at`. `ClientActivityFilter` runs after the security chain and
    upserts a row only when a field changed or `last_seen_at` is older than an
    hour, so `last_seen_at` has hour precision.
  - A nightly job writes `client_daily (day, kind, channel, source, version, base)`
    with the number of instances seen in the last 1, 7 and 30 days. It holds no
    user or instance ids and is kept indefinitely; it is the version-adoption
    history.
  - Instance rows are deleted 180 days after `last_seen_at`, and with the
    account.
- **Privacy.** An instance row is personal data: a pseudonymous install id
  linked to the account, with the app build, OS major version and, for the
  web, browser family and major version. Only admins read it. No IP, raw user
  agent, device model or request paths are stored. Backend's privacy contract
  and the account-deletion runbook list the table. The device name stays where
  it is today, in `devices`, for the push and connections UI only.
- **Reading.**
  - `GET /api/admin/system/clients` returns, per kind, active instances for
    1/7/30 days by build group (official or custom, version, channel, source),
    by OS or browser, and the `client_daily` series.
  - The admin user page lists the user's clients from `client_instances`: kind,
    version, build, channel, source, commit, base, OS or browser, first and
    last seen; agents also show their token name.
  - Admin "last seen" becomes the user's latest `client_instances.last_seen_at`
    when it is later than today's sources.
  - The srvscripts console reads the tables through its read-only role.
- **`devices` goes back to push.** It keeps tokens, names and alerts only. The
  build columns and the pushless iOS rows end with the transition below.

**Why.** The header rides on calls the clients already make: no background
heartbeat, no extra battery or network, and no new consent beyond the
custom-services opt-in that already gates every Backend call. The install id
fixes the root crutch, requests that name no device, with a random value and
not a hardware identifier. The credential row already identifies web sessions
and agents, so they need no id of their own. Tying rows to users lets the
owner see a user's clients and versions when supporting them; splitting live
rows (deleted after 180 days) from anonymous daily aggregates gives history
without keeping personal data longer. Build identity in the header keeps
forks and self-built apps visible and apart instead of mixing them into
official versions.
- Rejected: Firebase Analytics or another SDK (a third party, a new privacy
  surface, and nothing for web or agent).
- Rejected: parsing Caddy access logs (no identity behind NAT, and request
  logs are the wrong place for user data).
- Rejected: a heartbeat endpoint (an extra scheduled call per device, while the
  header is free).
- Rejected: extending `X-App-Version` in place (no key/value room for `install`,
  `os` and the build identity without breaking its strict parser).

**Consequence.** Migration, in order. Backend goes first and stays additive,
following Backend's wire compatibility rule:
1. **Backend.**
   - Migration `client_instances` + `client_daily`, the filter, the nightly job,
     the 180-day retention, `GET /api/admin/system/clients`, clients on the
     admin user page, the privacy contract and the account-deletion runbook.
   - Until `app.minimum` reaches the release that sends `X-Client`, a bearer
     request with only `X-App-Version` is recorded as one legacy instance per
     user and platform, and an agent without `X-Client` by its
     `User-Agent: itmo-widgets-agent/<version>`.
   - `ClientVersionFilter` keeps writing `devices.app_*` during the transition.
2. **App (this repository).**
   - `shared/backend-client` builds `X-Client` from the build identity, the OS
     version and an install id held in settings, regenerated on sign-out.
   - The same for Android, iOS and the Swift copy in the notification extension
     (`PushHandler.swift`).
   - The demo session sends nothing, as it calls no Backend.
3. **Web.** The client sends `kind=web; version=<build>; channel=web;
   commit=<sha>; browser=<family>/<major>`. The System page shows the new route
   and the user page lists the user's clients.
4. **Agent.** Build identity through `-ldflags` (version = upstream version,
   commit, channel `github` only in the release workflow and the published
   image, otherwise `custom`; source = the building repository or `local`). It
   sends `kind=agent; version=...; channel=...; source=...; commit=...;
   os=<GOOS-GOARCH>` on every request.
5. **Cleanup** (a later Backend release, once `app.minimum` sends `X-Client`).
   - Stop writing `devices.app_*` and creating pushless iOS rows; drop the
     columns in a later expand-only step.
   - Remove `ClientVersionService`, `GET /api/admin/system/client-versions` and
     the dashboard's registration-based counts.

**Owner answers (2026-10-19).**
- Rows are tied to users, and the admin view lists a user's clients.
- The web records both the browser family and version and the web build.
- Installs without the custom-services opt-in are not counted.
- A new install id after sign-out is fine.
- Retention is 180 days for instance rows; daily totals are kept indefinitely.
- Forks, self-built apps and self-built agents are shown apart from official
  builds, by upstream version.
- GitHub release downloads and Play statistics are out of scope for now.

**Revisit when.** A client cannot send headers (a widget-only surface), or the
owner needs per-feature usage, which this record deliberately does not collect.
