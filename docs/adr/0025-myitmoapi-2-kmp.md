# 0025 MyItmoApi 2.x is a Kotlin Multiplatform library beside 1.x

**Amended (2026-10-12).** 2.1 adds an easy entry for third-party developers (`MyItmo.login`, `fromRefreshToken`,
`fromStorage`), including the password login ported from 1.x. It lives in the library only: the app still signs in on
the official ITMO.ID page and never handles a password. The sentence below about the password login not being ported
describes 2.0.

**Decision (2026-10-03).** MyItmoApi stays the only client of official ITMO.ID, My ITMO and BARS endpoints and gets a second,
Kotlin Multiplatform line in the same repository.
- Coordinates `dev.alllexey:my-itmo-api-kmp`, root package `dev.alllexey.itmoapi` (`itmoid`, `myitmo`, `bars`),
  Built in `kmp/` beside the
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
  exists, `1.*` tags come from there. Tags and the Central «Publish» are manual steps.

**Why.** Gradle resolves one version per coordinate, so reusing `my-itmo-api` would force Core, Backend and the app
to switch in one step; a new artifact and package let 1.x and 2.x share a classpath while each consumer moves. A
`kmp/` directory instead of a long-lived branch, so both lines build and change on one branch. iOS
needs a KMP client, and the port is mechanical apart from auth. No consumer uses the password flow, credentials are
entered only on official ITMO pages, and decision 0012 rejected `obtainCodeFromSession`.

**Consequence.**
- The app uses only 2.x, from Maven Central; a local checkout is used only when `MYITMOAPI_DIR` points at it
  (composite build, for library development, ADR 0024).
- 2.x publishes with `languageVersion`/`apiVersion` 2.2 and `coreLibrariesVersion = "2.2.0"`, so a Kotlin 2.2
  consumer such as Backend compiles against it.
- A comparison harness decodes the same synthetic fixtures with the 1.x Gson and the 2.x Json; every difference is
  reviewed. Gson leniency is kept only where a wire variant was observed.
- Darwin cookies, redirects and DELETE bodies may need an Apple-specific path. `DemoMode` and the custom-services
  opt-in stay in the consumers.

**Supersedes.** Nothing; MyItmoApi stays the only client of official endpoints.

**Revisit when.** A consumer cannot use the published Kotlin level; Darwin cannot keep ADR 0012's invariants on the
iOS simulator; a non-Kotlin consumer appears; the library moves into this repository.

**Rules.**
- `kotlin.time.Instant`. Fixtures assert the observed `+03:00`, the apps convert through `AcademicTimeProvider`,
  and decoders accept offsets without seconds (`…T17:00+03:00`), which `Instant.parse` rejects.
- Non-null defaults plus `coerceInputValues` for the about 171 fields without null evidence; nullable only where
  null was seen.
- `suspend`, an interface per area, wire envelopes with `requireResult()`, typed exceptions, hand-written Ktor calls
  as in Core 2.0.
