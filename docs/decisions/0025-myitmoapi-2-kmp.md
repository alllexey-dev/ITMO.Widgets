# 0025 MyItmoApi 2.x is a Kotlin Multiplatform library beside 1.x

**Decision (2026-10-03).** MyItmoApi stays the only client of official ITMO.ID, My ITMO and BARS endpoints and gets a second,
Kotlin Multiplatform line in the same repository.
- Coordinates `dev.alllexey:my-itmo-api-kmp`, root package `dev.alllexey.itmoapi` (`itmoid`, `myitmo`, `bars`),
  `2.0.0-SNAPSHOT` until the 2.0.0 release commit; fixed once 2.0.0 is on Central. Built in `kmp/` beside the
  Maven 1.x tree; `kmp/` becomes the root after both consumers left 1.x.
- Targets `jvm` (bytecode 11; also the app's Android variant, so only APIs Android has), `iosArm64`,
  `iosSimulatorArm64`. Ktor with an injected engine (OkHttp on the JVM, Darwin on iOS), kotlinx.serialization.
- `suspend` API, one public interface per area, typed exceptions declared with `@Throws`, injected token storage
  and `Clock`, no global client or token state. English KDoc of observed shapes; unknown fields stay commented
  declarations; nullable only where null was observed.
- Every 1.x area is ported except the deprecated election endpoint and its `Flow*` models. The password login, the
  shared-client SSO and `obtainCodeFromSession` are not ported; 1.x keeps them. ADR 0012's cookie replay holds on
  both engines: no shared cookie storage, no cache, no redirects, every `Set-Cookie` returned.
- 1.x (`dev.alllexey:my-itmo-api`, `api.myitmo`, `api.bars`) takes `1.8.x` fixes only and stays source- and
  binary-compatible until Backend and the app have left it.
- Release lines: tags `1.*` run the Maven release, tags `2.*` the KMP release, both from `master`; once `release/1.x`
  exists, `1.*` tags come from there. Every tag and every Central «Publish» is the owner's.

**Why.** Gradle resolves one version per coordinate, so reusing `my-itmo-api` would force Core, Backend and the app
to switch in one step; a new artifact and package let 1.x and 2.x share a classpath while each consumer moves. A
`kmp/` directory instead of a long-lived branch, because agents work in worktrees off one integration branch. iOS
needs a KMP client, and the port is mechanical apart from auth. No consumer uses the password flow, credentials are
entered only on official ITMO pages, and decision 0012 rejected `obtainCodeFromSession`.

**Consequence.**
- The app takes 2.x through the pinned composite build (ADR 0024): the session with one token writer first, then
  feature by feature; 1.x leaves the app last. Backend moves in its single 1.8.0 release after M2, from Central.
- Backend stays on Kotlin 2.2.x in v2.3, so 2.x may publish with `languageVersion`/`apiVersion` 2.2 (SP-09).
- A parity harness decodes the same synthetic fixtures with the 1.x Gson and the 2.x Json; every difference is
  reviewed. Gson leniency is kept only where a wire variant was observed (SP-02).
- Darwin cookies, redirects and DELETE bodies may need an Apple-specific path (SP-15a, SP-15b); SP-10 picks the
  release runner. `DemoMode` and the custom-services opt-in stay in the consumers.

**Supersedes.** Nothing; the `AGENTS.md` rule that MyItmoApi is the only client of official endpoints stands.

**Revisit when.** A consumer cannot use the published Kotlin level; Darwin cannot keep ADR 0012's invariants
(SP-15b fails); a non-Kotlin consumer appears; the library moves into this repository.

**Settled.** Owner, 2026-10-03; each is fixed once 2.0.0 is on Central.
- 06 Q1–Q4: (b) a new artifact and package, (a) in `kmp/`, (a) the password flow dropped, (a) every area but the
  deprecated endpoint.
- 06 Q5: (a) `kotlin.time.Instant`. Fixtures assert the observed `+03:00`, the apps convert through
  `AcademicTimeProvider`, and decoders accept offsets without seconds (`…T17:00+03:00`), which `Instant.parse` rejects.
- 06 Q6: (a) non-null defaults plus `coerceInputValues` for the about 171 fields without null evidence; nullable only
  where null was seen.
- 06 Q7: (a) `suspend`, an interface per area, wire envelopes with `requireResult()`, typed exceptions, hand-written
  Ktor calls as in Core 2.0.

**Evidence.**
- SP-02: PASS (`ItmoApiJson` = config B with zero `isLenient` fields plus `WireInstantSerializer`; no per-field serializers)
- SP-09: PASS (publish with `KOTLIN_2_2` and `coreLibrariesVersion = "2.2.0"`, both needed; a Kotlin 2.2.21 consumer compiles and runs)
- SP-10: PASS (Apple klibs compile on the CLT-only Mac; release runner Linux x86_64, byte-identical klibs with `-Xklib-relative-path-base`)
- SP-15a: PASS (no fallback: Darwin sends DELETE bodies; read `Set-Cookie` through Ktor `setCookie()`; `followRedirects = false`, no cookie storage)
- SP-15b: pending (the same on the iOS simulator)
