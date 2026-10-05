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

## Swift

Every public suspend function, interface members included, is
`@Throws(BackendException::class, CancellationException::class)`. The public API has no `ApiResponse<T>`, no
generics and does not rely on default arguments.

## Tests

`commonTest` runs on the Android host and the iOS simulator:

- Wire types: a round trip and the strictness cases per type; enums per policy.
- Fixtures are read by absolute path from `src/commonTest/resources` (`Fixtures.read`), whose location the build
  file generates as `TEST_RESOURCES_DIR`; `assertJsonEquals` compares semantically (key order, absent nulls,
  date-times by instant).
- Routes: each area lists one `RouteCase` per public function in its `<Area>RouteCases`, and its request
  assertions check the method, path, query and body each case sends.
