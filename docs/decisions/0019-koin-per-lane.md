# 0019 Koin per feature beside Hilt, then Koin only

**Decision (2026-10-03).** Shared code uses Koin 4.2.2. It arrives one feature at a time
beside Hilt and replaces Hilt after the Navigation 3 shell swap.
- A feature's Koin module lives only in `feature-<x>/di` of its shared module;
  common Koin modules only in `core/di` of `:shared:core`.
- ViewModels in `commonMain` are created with `viewModelOf` and take
  `SavedStateHandle`; a Hilt Fragment host obtains them with `koinViewModel()`.
- Bridges live only in `app/.../di/bridge/**` (debug overrides in the debug
  source set): Hilt to Koin through an `@EntryPoint` read in a Koin `single`,
  Koin to Hilt through a `@Provides` that reads Koin after one idempotent
  starter. Core contracts are bridged once in `CoreBridge.kt`, feature types
  in the owner feature's `<Feature>Bridge.kt`.
- One graph per binding: a type is constructed by Hilt or by Koin, never both.
- Koin starts before `super.onCreate()` of the Application, and every Koin to
  Hilt bridge calls the same starter, because WorkManager can run a worker
  before the Application finishes.
- Every Koin module has a graph check that fails on a missing binding, a
  duplicate and a cycle.
- After the shell swap Hilt leaves in three steps: workers, widget providers,
  the QS tile and the FCM service via `KoinComponent` with unchanged class
  names; then the Application and the three Activities; then the Hilt plugin,
  the bridges and `EntryPointAccessors` in tests.

**Why.** Compose in `commonMain` brings the ViewModels there, and
`@HiltViewModel` is Android-only. A global swap before the feature work would
freeze about 250 files for 2-4 days and edit 47 classes that the Compose ports
delete anyway. Koin's runtime DSL bridges to Hilt entry points without code
generation; Metro would need graph factories fed by Hilt, which is unverified.
Koin needs no compiler plugin, which keeps the Kotlin lockstep small.

**Consequence.** Two DI frameworks coexist for weeks; Konsist (`DiRulesTest`)
keeps Koin modules, `GlobalContext` and `EntryPointAccessors` in their places,
and the bridges are deleted with Hilt.

**Supersedes.** The Hilt part of the `AGENTS.md` § Hard rules line "Android
stays on XML, Fragments, ViewBinding, Navigation, Hilt and WorkManager" (with
ADR 0017).

**Revisit when.** A feature's DI shape cannot cross the bridges, or in v2.4, when Metro's compile-time
graph can be weighed against a Koin-only app.

**Settled.** A4 settled 90 Q2 with (a), Koin per lane; 12 Q1's Metro is kept
for v2.4. No global DI swap before the feature lanes: SP-12a and SP-12b passed,
and Koin's graph check (`koin-compiler-plugin` plus one JVM graph test) caught
every seeded error.

**Evidence.**
- SP-11: PASS (the bridge keeps Hilt for plain classes moved into a KMP module; `@HiltViewModel` cannot cross it: one `:app` binding module per VM, or Koin `viewModelOf`)
- SP-12: PASS (SP-12a PASS, SP-12b PASS; Koin 4.2.2 `commonMain` VM with `SavedStateHandle` in a Hilt Fragment survives recreation and process death, bridges both ways, `startKoin` before `super.onCreate()`; Koin covers all 5 DI shapes, Metro 1.4.5 also passes; no global DI swap, no owner decision)
