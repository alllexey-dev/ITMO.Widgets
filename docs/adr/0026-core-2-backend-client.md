# 0026 Core 2.0 is a client-only KMP module; Backend owns the wire DTOs

**Decision (2026-10-03).** Backend owns every wire DTO (Jackson, in Backend's `dto/`). Core 2.0 mirrors them as a
hand-written, client-only Kotlin Multiplatform module of this repository, is never published and never invents a
field, route, default or enum value (Backend first, Core mirrors).
- Module `:shared:backend-client`, package `dev.alllexey.itmowidgets.client`; Ktor with an injected engine (OkHttp on
  Android, Darwin on iOS) and kotlinx.serialization; an `AccessTokenSource`; typed `BackendException` declared with
  `@Throws`; one API per area; no public `ApiResponse<T>`. No dependency on MyItmoApi or another shared module.
- Contract guard: golden fixtures and the OpenAPI snapshot are vendored from a named Backend commit by a
  sync script (regenerated, never hand-merged); a conformance test lists every route as mirrored or as not mirrored
  with a reason. The Kotlin code is hand-written, not generated from OpenAPI.
- Privacy- and authorization-affecting fields have no wire defaults: a missing field fails decoding and never widens
  access. Unknown values fail for authorization-affecting enums (`RestrictionCapability` keeps unknown → `ALL`, the
  most restrictive); `UNKNOWN` exists only for display enums and is never encoded.
- Not ported: the moderation API (`/api/moderation/**`, about 22 types, no Kotlin consumer; Web uses `/api/admin`)
  and the 1.x methods without an app consumer.
- Wire rules (what may change while `app.minimum` ≤ 2.2; 401 vs 403): Backend `docs/contracts/compatibility.md`.
- Core 1.x (`itmo-widgets-core`) is frozen at `1.7.0`, with 1.7.x security fixes only on request. Released `1.2.0`
  and `1.7.0` stay only in Backend's `compatCore120Test` and `compatCore170Test`; the repository is archived once
  `app.minimum` ≥ 2.3.

**Why.** Backend already duplicates 62 of Core's 87 types. Owning all of them touches 45 main and 22 test Backend
files once; a shared KMP model would touch 108 and 59, need Boot 4 first, fall back to Jackson silently for
Backend-only envelopes and invert the "Backend first" order. It also ends Backend's build of Core `master` on every
deploy. Inside this repository the client changes atomically with its app consumers and needs no publish step. The
new package ends the split `core.model` package, lets 1.x and 2.0 share the app's classpath during the swap and lets
Konsist guard the whole client. A generator would lose the strict invariants and the sealed sport types.

**Consequence.**
- A wire change runs: privacy boundary → Backend + fixtures → OpenAPI → sync → Core 2.0 + conformance → apps.
- The app uses only Core 2.0. The Konsist `DemoMode` check keys on the client type names; `DemoMode` and
  `BackendGate` stay in their holders.
- Installed 2.1 and 2.2 keep their released Core; Backend stays additive while `app.minimum` ≤ 2.2.

**Supersedes.** Core as a separately published Retrofit library mirrored with MockWebServer tests.

**Revisit when.** A non-app consumer needs the client (publish it then); a wire variant needs lenient parsing;
OpenAPI cannot express the sealed sport types; `app.minimum` reaches 2.3 (archive Core 1.x).

**Rules.**
- No duplicate-key or quoted-number rejection, quoted booleans on capability fields accepted; the shape, enum and
  invariant checks stay.
- `Instant` + an explicit Moscow zone + `kotlin.uuid.Uuid` (a String value class if the opt-in is unacceptable at
  Kotlin 2.4.20). Display enums only get `UNKNOWN`, as above.
- 401 for missing or invalid credentials from Backend 1.8.0. The OpenAPI snapshot follows Backend's
  Spring Boot and springdoc versions.
