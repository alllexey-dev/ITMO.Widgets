# 0019 Koin is the only dependency graph

**Decision (2026-10-03, revised 2026-10-12).** Dependency injection is Koin on both
platforms.
- A feature's Koin module lives in `feature/<x>/di` of its shared module; common
  modules in `core/di` of `:shared:core`; Android-only bindings in `:app`'s `di`
  package (debug fixtures in `di.bridge` of the debug source set); iOS-only
  bindings in `iosMain` of the owning module, `<Area>IosModule.kt`.
- ViewModels are declared with `viewModelOf` and take `SavedStateHandle` where
  they read navigation arguments.
- Android starts the one graph through `KoinStarter.ensureStarted`, called first in
  `Application.onCreate()` and by any component that can run before it (workers,
  widget broadcasts). It is the only reader of the global Koin context. iOS starts
  its graph once with `startKoinIos` over `IosKoinModules.all`.
- `allowOverride(false)`; a Koin module file defines each type once.
- Every Koin module has a graph test that fails on a missing binding, a duplicate
  and a cycle (Koin's `verify()` on the JVM, a resolving test in `iosTest`).

**Why.** Shared ViewModels live in `commonMain`, and `@HiltViewModel` is
Android-only. Koin needs no compiler plugin, which keeps the Kotlin lockstep small,
and one graph per platform removes the bridges two frameworks would need.

**Consequence.** Konsist (`DiRulesTest`) keeps Koin modules and the global context
in their places.

**Supersedes.** The Hilt part of the rule that Android stays on XML, Fragments,
ViewBinding, Navigation, Hilt and WorkManager (with ADR 0017).

**Revisit when.** Metro's compile-time graph is weighed against Koin.
