# Core 2.0 wire conventions

The rules every type and call of `:shared:backend-client` follows so that Backend, the client and the apps agree.
They replace the "Decoding policy" of Core 1.x. The decision is
[ADR 0026](../../docs/decisions/0026-core-2-backend-client.md); route semantics live in Backend's contract
documents (docs/contracts in the Backend repository), and each area's facts are in the KDoc of its `*Api`
interface.

## Backend first

Backend owns every wire DTO. The client mirrors it and never invents a field, route, default or enum value:

- A wire change runs privacy boundary, then Backend with fixtures, then the OpenAPI snapshot, then the contract
  sync, then this module with its conformance test, then the apps.
- Type names equal Backend's DTO names, JSON names are frozen, and new request fields and query parameters are
  optional with nulls omitted.
- A fixture that does not decode is a contract question for Backend, not a client workaround.
- The client never enforces privacy and never checks `DemoMode` or the custom-services opt-in: its holders in the
  apps do.

The whole change, from the privacy boundary to a screen, is the
[endpoint end to end](../../docs/recipes/endpoint-end-to-end.md) recipe.

## Areas

`BackendClient(baseUrl, tokens, engine, version)` exposes one API per Backend area. Each lives in its own package under
`dev.alllexey.itmowidgets.client`, and its `*Api` KDoc holds the status codes and `error.code`s an app handles. The
push decoder needs no client instance.

| Property | Package, API | Routes | Backend contract |
|---|---|---|---|
| `users` | `users`, `UsersApi` | `GET /api/users/{isu}`, `GET /api/users/{isu}/friends`, `POST /api/users/lookup`, `GET` and `PUT /api/users/me/privacy`, `PUT /api/users/me/id-token`, `GET /api/users/me/data`, `GET /api/users/me/web-login/{code}`, `POST /api/users/me/web-login/{challengeId}/approve` | `B/docs/contracts/privacy.md`, `B/docs/contracts/friendships.md`, `B/docs/contracts/web.md` |
| `friends` | `friends`, `FriendsApi` | `GET /api/friends`, `GET /api/friends/requests/incoming` and `outgoing`, `POST /api/friends/{isu}/request`, `accept`, `reject` and `cancel`, `DELETE /api/friends/{isu}` | `B/docs/contracts/friendships.md` |
| `sport` | `sport`, `SportApi` | `POST /api/sport/sign/sync`, `GET /api/sport/friends/sport-bookings`, `GET /api/sport/users/{isu}/bookings`, `GET /api/sport/auto-sign/limits`; under `/api/sport/free-sign` and `/api/sport/auto-sign` each: `GET entry/my`, `POST entry/create`, `entry/{id}/cancel`, `lesson/{lessonId}/cancel` and `lesson/{lessonId}/mark-satisfied`, `queue/current` (`GET` for free-sign, `POST` for auto-sign) | `B/docs/contracts/sport-automation.md`, `B/docs/contracts/privacy.md` |
| `links` | `links`, `SubjectLinksApi` | `GET /api/subjects/{subjectId}/links`, `PUT /api/subjects/{subjectId}/links/pin`, `PUT` and `DELETE /api/links/{id}`, `PUT /api/links/{id}/vote`, `POST /api/links/{id}/report`, `GET /api/users/me/restrictions` | `B/docs/contracts/subject-links.md` |
| `reviews` | `reviews`, `TeacherReviewsApi` | `GET /api/teachers/{isu}/reviews`, `PUT` and `DELETE /api/teachers/{isu}/reviews/mine`, `GET /api/teachers/summary-levels`, `PUT /api/reviews/{id}/vote`, `POST /api/reviews/{id}/report` | `B/docs/contracts/teacher-reviews.md` |
| `device` | `device`, `DeviceApi` | `POST /api/device/register-device`, `DELETE /api/device/current` with a JSON body | `B/docs/contracts/notifications.md` |
| `app` | `app`, `AppApi` | `GET /api/app/version-info` with the optional `platform` query | `B/docs/contracts/app-version.md` |
| `schedule` | `schedule`, `ScheduleApi` | `POST /api/schedule/lessons/sync`, `GET /api/schedule/lessons/user/{isu}`, `GET /api/schedule/lessons/{pairId}/friends` | `B/docs/contracts/schedule.md`, `B/docs/contracts/privacy.md` |
| none | `push`, `FcmDecoder` | FCM data messages `FRIENDSHIP_EVENT_PAYLOAD`, `SPORT_FREE_SIGN_LESSONS_PAYLOAD`, `SPORT_AUTO_SIGN_LESSONS_PAYLOAD` | `B/docs/contracts/notifications.md` |

Wire types that two areas share (`UserProfile`, `UserData`, the vote and report requests) live in `common`; the
transport (`http`), the `Json` and its serializers (`json`) and `BackendException` (`error`) are shared by every
area. The Backend operations without a client function are listed under [Not mirrored](#not-mirrored).

## Json

One `Json` for every call (`BackendJson`):

| Setting | Value | Why |
|---|---|---|
| `ignoreUnknownKeys` | `true` | Backend may add response fields at any time |
| `explicitNulls` | `false` | Request nulls are omitted; a response field may be absent or `null` when it is nullable |
| `encodeDefaults` | `true` | Request fields with a Kotlin default are still sent |
| `coerceInputValues` | off | A `null` or unknown value must never turn into a declared default |
| `isLenient` | off | No observed wire variant needs it |

Strictness (07 Q4 (b)):

- A missing required field, a `null` in a non-null field, a wrong shape and a model `init` invariant fail decoding.
- Quoted numbers (`"2"` for an `Int`) and quoted booleans (`"true"`, capability fields included) are accepted.
  Duplicate keys are not rejected: the last value wins.
- Privacy- and authorization-affecting fields have no wire defaults: the three `UserPrivacySettings` audiences,
  every `UserCapabilities` field (`canViewFriends` included) and the `can*` flags of `TeacherReviewsResponse`. A
  missing one fails decoding and never widens access.
- Integer ranges and cross-field rules live in `init { require(...) }` (`ResourceVoteRequest.value` is -1, 0 or
  1); a failing invariant in an answer is a contract failure.
- Polymorphic types carry their discriminator only through `@SerialName` on the subtypes (sport `free`/`auto`), and
  are encoded through the base serializer.

## Enums

The wire value is the constant's name; only a JSON string is accepted. Each wire enum names its policy through a
serializer in the `json` package (13 Q7 (b)):

| Policy | Serializer | Unknown value | Enums |
|---|---|---|---|
| Strict | `StrictEnumSerializer` | fails decoding | `RelationshipState`, `SharingVisibility`, `LinkVisibility`, `FriendshipEvent`, the request-only enums (`ReportReason`) |
| Most restrictive | `FallbackEnumSerializer` | decodes as `ALL` | `RestrictionCapability` |
| Display | `UnknownTolerantEnumSerializer` | decodes as `UNKNOWN` | `QueueEntryStatus`, `LinkCategory`, `SubjectLinkStatus`, `TeacherReviewKind`, `TeacherReviewStatus`, `SummaryLevel`, `SummaryConfidence`, `SummaryScaleKind`, `SummaryScaleValue` |

The sport `type` discriminator is strict as well. `UNKNOWN` is decode-only: encoding it fails, and inside a request
that is a `BackendException.Contract` before anything is sent, so an app never offers to save a value it could not
read (a link whose category is `UNKNOWN`). Consumers map `UNKNOWN` explicitly.

## Time and IDs

| Wire | Kotlin | Serializer |
|---|---|---|
| Date-time with an offset | `kotlin.time.Instant` | `WireInstantSerializer`: reads Jackson's form and Gson's FCM form without `:00` seconds, writes UTC |
| Date (`2026-01-05`) | `kotlinx.datetime.LocalDate` | `IsoLocalDateSerializer` |
| Time (`08:20`) | `kotlinx.datetime.LocalTime` | `IsoLocalTimeSerializer` |
| UUID | `kotlin.uuid.Uuid` | `UuidSerializer`, lowercase on write |
| ISU number | `Int` | built-in |
| Entity IDs (lessons, subjects, flows) | `Long` | built-in |

The client reads no clock and knows no zone: the apps convert instants with the academic time zone (07 Q7 (a)).

## Errors

Every call returns its unwrapped value or throws a `BackendException`; Backend's `ApiResponse` envelope is
internal. The status is mapped before the body is decoded, so an HTML 502 or an empty 401 never reaches the decoder.

| Failure | Exception |
|---|---|
| HTTP 401 | `Unauthorized` (never retried with a refreshed token) |
| HTTP 403 | `Forbidden(code)`, distinct from 401 |
| HTTP 404 | `NotFound(code)` |
| Any other non-2xx status | `Http(status, code)` |
| Token source, connection or body read failed | `Transport(cause)` |
| A 2xx answer that fails decoding or an invariant, `success=false`, no `data` for a value call, or a request body that cannot be encoded | `Contract(cause)` |

`code` is Backend's `error.code` (`restricted`, `permission_denied`, `access_denied`, `not_found`,
`invalid_request`, `unauthorized`, `csrf`) or `null` when the error body is empty or not Backend's envelope. The
server `message` is never kept: user-visible text belongs to the apps. Tokens, headers and bodies are never logged.

## Request headers

Besides what Ktor and the engine add (`Accept`, `Content-Type`, the engine's `User-Agent`), the client sets these
headers on every request. Only `BackendClient` sets them, so MyItmoApi and other clients on the same engine never
send them.

| Header | Value | Source |
|---|---|---|
| `Authorization` | `Bearer <access token>`, absent without a token | `AccessTokenSource`, asked per request |
| `X-App-Version` | `<versionName> (<build>); <platform>; <distribution>`, absent when `version` is `null` | `ClientVersion` |

`X-App-Version` names the build only, in printable ASCII: Android sends
`<versionName> (<versionCode>); android; github|play` (`NetworkModule.provideClientVersion`), iOS
`<CFBundleShortVersionString> (<CFBundleVersion>); ios; appstore|dev` (`IosClientVersion`, `dev` for a debug
binary). No user, device or OS data. Backend ignores unknown headers; BK-VER1 reads it.

## Engines and iOS

The module has no `androidMain` or `iosMain` code and no Ktor engine artifact: the caller passes the engine, builds
the one `BackendClient` of the process through `BackendClientFactory` in `:shared:core` and closes the engine itself.

- Android passes MyItmoApi's OkHttp `defaultEngine()` (`app/src/main/java/dev/alllexey/itmowidgets/di/NetworkModule.kt`).
- iOS passes `darwinHttpEngine()`
  (`shared/core/src/iosMain/kotlin/dev/alllexey/itmowidgets/core/network/DarwinHttpEngine.kt`): a URLSession without
  cookie storage, cookie handling or URL cache, never `usePreconfiguredSession`. Darwin sends the JSON body of
  `DELETE /api/device/current` as OkHttp does (SP-15a).
- Swift sees `BackendException` through `@Throws`: every public suspend function, interface members included, is
  `@Throws(BackendException::class, CancellationException::class)`, and `FcmDecoder`'s functions throw
  `BackendException`. The public API has no `ApiResponse<T>`, no generics and does not rely on default arguments,
  so every nullable parameter is passed explicitly.
- `commonTest` runs on the iOS simulator with `scripts/ios/test.sh kn :shared:backend-client`; Linux CI compiles the
  iOS klibs with `scripts/verify.sh klibs`.

## Tests

`commonTest` runs on the Android host and the iOS simulator:

- Wire types: a round trip and the strictness cases per type; enums per policy.
- Fixtures are read by absolute path from `src/commonTest/resources` (`Fixtures.read`), whose location the build
  file generates as `TEST_RESOURCES_DIR`; `assertJsonEquals` compares semantically (key order, absent nulls,
  date-times by instant).
- Routes: each area lists one `RouteCase` per public function in its `<Area>RouteCases`, and its request
  assertions check the method, path, query and body each case sends.

## Fixtures

Backend records golden fixtures of what its routes answer and what clients send (`B/src/test/resources/contract/`)
and an OpenAPI snapshot (`B/docs/openapi.json`). This module tests against a vendored copy of both:

- `src/commonTest/resources/contract/` is generated by `scripts/sync-backend-contract.sh <backend-sha>`: Backend's
  contract directory byte for byte (`http/<area>/`, `requests/`, `fcm/`, `index.json`, `README.md`), the snapshot
  as `openapi.json` and `BACKEND_COMMIT` (the full SHA on line 1, then Backend's version and the commit date). The
  commit must be merged into Backend's `v2.3/next` and not older than the recorded one (`--allow-older` goes back).
- Never edit the copy by hand, and never hand-merge a rebase conflict in it: rerun the script with the newer
  commit. `scripts/sync-backend-contract.sh --check` re-extracts the recorded commit and fails on any drift.
- A Backend pull request that changes a fixture or the snapshot names it in a `Contract change:` line; an L19 card
  then re-syncs and fixes what no longer decodes.
- Each area claims its fixtures in `src/commonTest/resources/claims/<area>.txt` (Backend's area directory name), one
  `contract/`-relative path per line, `#` comments allowed. The area's `<Area>VendoredFixturesTest` checks exactly
  the claimed paths: a route answer decodes through the area API and re-encodes to the fixture's `data` (a field
  the model lacks or misnames fails), a request body decodes and encodes back to the same JSON.
- `VendoredContractTest` checks that `BACKEND_COMMIT` starts with a 40-hex SHA, every JSON file parses,
  `index.json` lists every fixture and every file under `contract/` is claimed exactly once: by an area, by
  `NotMirrored`, or by the test itself (`BACKEND_COMMIT`, `README.md`, `index.json`, `openapi.json` and the error
  bodies under `errors/`, which it maps through the client). An unclaimed file fails.

### Sync procedure

1. Pick a Backend commit merged into Backend's `v2.3/next` whose pull request named a `Contract change:`.
2. Run `scripts/sync-backend-contract.sh <backend-sha>`, then `scripts/sync-backend-contract.sh --check`. The script
   only reads Backend's clone; a commit the clone lacks exits 2 with the fetch command to run.
3. Run the module's tests. A new route or field fails conformance until it is mirrored in its area (or listed in
   `NotMirrored` with its reason); a new fixture fails `VendoredContractTest` until an area claims it.
4. When the snapshot ships a field from `PendingBackendFields`, its entry fails: delete it in the same change.
5. Commit the vendored directory, the claims and the client change together, so `BACKEND_COMMIT` always names the
   contract the tests ran against.

### Conformance

`ContractConformanceTest` compares the client with the OpenAPI snapshot at the recorded commit:

- Routes: every area's `RouteCases` (aggregated in `AllRouteCases`) runs against the recording mock engine; each
  request must be one OpenAPI operation (path templates as patterns) with the same query parameter names. Every
  operation is either mirrored or covered by exactly one `NotMirrored` entry, never both, and every entry still
  matches an operation.
- Models: every type in `WireModels` matches the component schema of the same name: the same property names, a
  non-null property without a default is `required`, a property Backend may send as `null` is nullable, enum
  constants except `UNKNOWN` equal the schema's `enum`, a nested model names the same schema, and the sealed sport
  types have the schema's `oneOf` with the `type` values `free` and `auto`. A model that reaches an unlisted model
  or enum fails, so a new top-level request or answer type joins `WireModels`.
- `PendingBackendFields` lists the client fields Backend has not shipped yet, each with its Backend card
  (`RegisterDeviceRequest.platform`, `alertsAllowed` and `appVersion`: BK-16b). An entry the snapshot already has
  fails, so the list cannot go stale.
- Seeded-drift tests (a renamed field, an extra enum value, an unknown route, an unlisted operation, an unclaimed
  fixture and others) prove that each check fails.

### Not mirrored

These Backend operations have no client function, on purpose; `NotMirrored` holds the same list with its fixtures.

| Operations | Why |
|---|---|
| Every route under `/api/moderation` | CO-02, ADR 0026: the moderation API is not ported. No Kotlin consumer; moderators work in Web, which uses `/api/admin` |
| Every route under `/api/admin` | Web only: the admin pages |
| Every route under `/api/web` | Web only: the browser sign-in and its session cookie; the apps approve a sign-in through `/api/users/me/web-login` |
| `GET /api/app/version` | The latest Android version as a bare string, kept for 2.0.x; the apps read `/api/app/version-info` |
| `GET /api/users/me/roles` | Web only: the apps have no role-dependent screen |
| `POST /api/sport/free-sign/entry/{id}/mark-satisfied` and its auto-sign twin | By entry ID, kept for released clients; the apps mark entries satisfied by lesson |

### Using the contract from other modules

Another module's tests read the vendored files by path from their working directory, which Gradle sets to the
module directory: from `app/` that is `../shared/backend-client/src/commonTest/resources/contract/`. Never copy
vendored files into another module; a copy drifts from the recorded commit without `--check` noticing.
