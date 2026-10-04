# 0026 Core 2.0 is a client-only KMP module; Backend owns the wire DTOs

**Decision (2026-10-03).** Backend owns every wire DTO (Jackson, in Backend's `dto/`; 92 option A). Core 2.0 mirrors them as a
hand-written, client-only Kotlin Multiplatform module of this repository, is never published and never invents a
field, route, default or enum value (Backend first, Core mirrors).
- Module `:shared:backend-client`, package `dev.alllexey.itmowidgets.client`; Ktor with an injected engine (OkHttp on
  Android, Darwin on iOS) and kotlinx.serialization; an `AccessTokenSource`; typed `BackendException` declared with
  `@Throws`; one API per area; no public `ApiResponse<T>`. No dependency on MyItmoApi or another shared module.
- Contract guard (07 Q2 (a)): golden fixtures and the OpenAPI snapshot are vendored from a named Backend commit by a
  sync script (regenerated, never hand-merged); a conformance test lists every route as mirrored or as not mirrored
  with a reason. The Kotlin code is hand-written, not generated from OpenAPI.
- Privacy- and authorization-affecting fields have no wire defaults: a missing field fails decoding and never widens
  access. Unknown values fail for authorization-affecting enums (`RestrictionCapability` keeps unknown → `ALL`, the
  most restrictive); `UNKNOWN` exists only for display enums and is never encoded (13 Q7 (b)).
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
- The app swaps feature by feature and drops Core 1.x last. The Konsist `DemoMode` gate keys on client type
  names, so it lists the 2.0 types before the first swap; `DemoMode` and `BackendGate` stay in holders.
- Installed 2.1 and 2.2 keep their released Core; Backend stays additive while `app.minimum` ≤ 2.2.

**Supersedes.** In `AGENTS.md`, § Source-of-truth boundaries on Core as a Retrofit library mirrored with
MockWebServer tests, and the Core build block of § Build and verify; 13 Q5's recommendation of a Core 2.0.0 artifact in its own repository.

**Revisit when.** A non-app consumer needs the client (publish it then); a wire variant needs lenient parsing
(SP-02); OpenAPI cannot express the sealed sport types (SP-18); `app.minimum` reaches 2.3 (archive Core 1.x).

**Settled.** Owner, 2026-10-03, unless named otherwise; reversible until the area it touches is ported.
- 07 Q3: (a) the moderation API is not ported. 07 Q8: (a) iOS targets from day one, compile-only until Xcode.
- 07 Q4: (b) no duplicate-key or quoted-number rejection, quoted booleans on capability fields accepted; the shape,
  enum and invariant checks stay.
- 07 Q5: (b) the new package `dev.alllexey.itmowidgets.client` (D3); the name stands unless the owner vetoes it
  before the first Core 2.0 card.
- 07 Q7: (a) `Instant` + an explicit Moscow zone + `kotlin.uuid.Uuid` (a String value class if the opt-in is
  unacceptable at Kotlin 2.4.20). 13 Q7: (b), as above.
- 92 Q3: 401 for missing or invalid credentials in Backend 1.8.0, under 92's two conditions (A10). 92 Q6: (a) the
  OpenAPI snapshot on Boot 3.5 with springdoc 2.8, bumped with Boot 4.

**Evidence.**
- SP-02: PASS (`BackendJson` = config A with the note's `UNKNOWN` table; privacy and capability fields fail on missing, `null` or unknown; sport subtypes by `@SerialName`, no `type` property)
- SP-10: PASS (Apple klibs compile on the CLT-only Mac; the klib job runs on Linux inside `verify-quick`, no macOS `ios-klibs` job)
- SP-15a: PASS (Darwin sends the DELETE body, so `unregisterCurrent` stays a DELETE with a JSON body)
- SP-18: PASS (springdoc 2.8.17 emits `oneOf` and the discriminator with a test-side `OpenApiCustomizer`; `ModerationCaseTarget` needs `discriminatorMapping`; no hand-written schemas)
