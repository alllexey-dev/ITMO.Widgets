# Backend client

- Core 2.0 is built into the app as `:shared:backend-client`: a Kotlin
  Multiplatform client of ITMO.Widgets Backend on Ktor and
  kotlinx.serialization with one API per area (users, friends, sport, links,
  reviews, device, app, schedule), a push decoder that needs no client, an
  `AccessTokenSource` and typed `BackendException`s. Android passes the
  OkHttp engine and iOS the Darwin engine; the module is not published.
- Privacy and capability fields have no wire defaults, so a missing one fails
  decoding instead of widening access; `RestrictionCapability` falls back to
  the most restrictive value and only display enums decode unknown values as
  `UNKNOWN`.
- The module tests against Backend's golden fixtures and OpenAPI snapshot,
  vendored at a recorded Backend commit by
  `scripts/sync-backend-contract.sh`; `ContractConformanceTest` fails on an
  unmirrored route, a model that drifts from the snapshot or an unclaimed
  fixture. Rules and the area table are in
  `shared/backend-client/CONTRACT.md`.
- Areas still on Core 1.x (`itmo-widgets-core` 1.7.0, frozen) keep it until
  they move to Core 2.0.
- Every request to Backend carries `X-App-Version`
  (`<versionName> (<build>); <platform>; <distribution>`, e.g.
  `2.3.0-beta.1 (20291); android; github`) from `ClientVersion`: Android
  builds it from `BuildConfig` and the flavor, iOS from the bundle
  (`appstore`, or `dev` for a debug binary). MyItmoApi and other hosts never
  get it.
