# 0020 One Navigation 3 shell with `@Serializable` routes

**Decision (2026-10-03).** The Android app ends v2.3 with one Navigation 3 1.2.x Compose
shell in `MainActivity`, replacing the two `NavHost`s, the nav graphs, the
coordinator and the overlay host in a single swap.
- Routes are `@Serializable` keys on `navigation3-runtime` in `commonMain`;
  a key declares its kind (tab root, overlay screen, sheet, dialog) and its
  sheet policy. Feature keys join one polymorphic `SerializersModule`.
- Sheets and dialogs are overlay scenes: a bottom-sheet scene strategy on
  `ModalBottomSheet` and `DialogSceneStrategy`. Results go back through the
  Navigation 3 result API instead of Fragment results.
- Each tab keeps its own back stack and saveable state; overlay screens draw
  above the bar; sheets and dialogs above everything.
- Entry intents, App Links and shortcuts become routes through one parser that
  reproduces today's routing table. A `RouteQueue` with `MainRouteQueue`'s
  contract feeds the shell: one pending route, newer replaces older, handed
  out once its tab is selected and the session is ready.
- Screens stay stateless and take callbacks (ADR 0017); only `<Name>Route`
  reads the ViewModel and turns callbacks into navigation. The same screen
  serves the Fragment host, the shell and the SwiftUI host.
- Activities that exist today stay Activities. `navigation3-ui` has no iOS
  artifact, so scenes live in `app/`; the iOS shell is SwiftUI and mirrors
  the route queue (ADR 0023).
- The new shell is built beside the old one, filled tab by tab, switched on,
  made default, and the old shell is deleted after one soak.

**Why.** Navigation 3 is Google's recommendation for Compose-only apps, keeps
the back stack as plain state, and has multiplatform keys and deep links in
1.2.0. Nav2 type-safe routes would need Kotlin-DSL graphs first, thrown away
at the end. One swap at the end means `AppNavigator` and the Fragment hosts do
not change while the feature screens are ported.

**Consequence.** Until the swap, navigation code is frozen except for
announced exceptions; feature work adds keys, the shell owner registers them.
Predictive back on sheets needs `android:enableOnBackInvokedCallback`.

**Supersedes.** The Navigation part of the `AGENTS.md` § Hard rules line
"Android stays on XML, Fragments, ViewBinding, Navigation, Hilt and
WorkManager" (with ADR 0017).

**Revisit when.** SP-05b fails (predictive back on a sheet scene on a device),
or a sheet case cannot be expressed as a scene.

**Settled.** A5 settled 12 Q5 with (a), Navigation 3. The fallbacks (Nav2
type-safe routes, or sheets managed by the host outside the back stack) are
not taken: SP-14 passed.

**Evidence.**
- SP-14: PASS (Nav3 1.2.0 overlay scenes carry sheets, dialogs, results, App Links, per-tab back stacks and restore, 8/8 under Robolectric; no fallback; predictive back on a sheet scene waits for SP-05b on a device)
